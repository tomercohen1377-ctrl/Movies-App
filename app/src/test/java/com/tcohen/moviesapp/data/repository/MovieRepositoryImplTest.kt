package com.tcohen.moviesapp.data.repository

import androidx.paging.testing.asSnapshot
import com.tcohen.moviesapp.data.local.LocalMovieDataSource
import com.tcohen.moviesapp.data.remote.TmdbRemoteDataSource
import com.tcohen.moviesapp.data.remote.dto.VideoResponse
import com.tcohen.moviesapp.domain.model.Movie
import com.tcohen.moviesapp.fakeMovie
import com.tcohen.moviesapp.fakeMovieDetailDto
import com.tcohen.moviesapp.fakeVideoListResponse
import com.tcohen.moviesapp.util.MainDispatcherRule
import com.tcohen.moviesapp.util.NetworkResult
import com.tcohen.moviesapp.util.NetworkStatusProvider
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MovieRepositoryImplTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val remoteDataSource: TmdbRemoteDataSource = mockk()
    private val localDataSource: LocalMovieDataSource = mockk()
    private val networkStatusProvider: NetworkStatusProvider = mockk {
        every { isCurrentlyOnline() } returns false   // server sync path disabled in unit tests
    }
    private val repository: MovieRepositoryImpl by lazy {
        MovieRepositoryImpl(remoteDataSource, localDataSource, networkStatusProvider)
    }

    // ── getMovieDetail ────────────────────────────────────────────────────────

    @Test
    fun `getMovieDetail returns Success with mapped domain object`() = runTest {
        val dto = fakeMovieDetailDto(id = 5)
        coEvery { remoteDataSource.getMovieDetails(5) } returns dto

        val result = repository.getMovieDetail(5) as NetworkResult.Success

        assertEquals(5, result.data.id)
        assertEquals("Test Movie 5", result.data.title)
        assertEquals(120, result.data.runtime)
    }

    @Test
    fun `getMovieDetail returns NetworkError when API throws`() = runTest {
        coEvery { remoteDataSource.getMovieDetails(any()) } throws java.io.IOException("connection refused")

        val result = repository.getMovieDetail(999)

        assertTrue(result is NetworkResult.Error)
    }

    // ── getTrailer ────────────────────────────────────────────────────────────

    @Test
    fun `getTrailer returns Success with YouTube trailer key when online`() = runTest {
        every { networkStatusProvider.isCurrentlyOnline() } returns true
        coEvery { remoteDataSource.getMovieVideos(1) } returns fakeVideoListResponse(trailerKey = "abc123")

        val result = repository.getTrailer(1) as NetworkResult.Success

        assertNotNull(result.data)
        assertEquals("abc123", result.data?.key)
    }

    @Test
    fun `getTrailer returns Success with null when offline`() = runTest {
        every { networkStatusProvider.isCurrentlyOnline() } returns false

        val result = repository.getTrailer(1) as NetworkResult.Success

        assertNull(result.data)
    }

    @Test
    fun `getTrailer returns Success with null when no YouTube trailers exist`() = runTest {
        every { networkStatusProvider.isCurrentlyOnline() } returns true
        coEvery { remoteDataSource.getMovieVideos(1) } returns fakeVideoListResponse(trailerKey = null)

        val result = repository.getTrailer(1) as NetworkResult.Success

        assertNull(result.data)
    }

    @Test
    fun `getTrailer prefers official trailers over unofficial`() = runTest {
        every { networkStatusProvider.isCurrentlyOnline() } returns true
        val response = fakeVideoListResponse().copy(
            results = listOf(
                VideoResponse(
                    key = "unofficial_key",
                    site = "YouTube", type = "Trailer", official = false,
                    publishedAt = "2024-06-01T00:00:00.000Z"
                ),
                VideoResponse(
                    key = "official_key",
                    site = "YouTube", type = "Trailer", official = true,
                    publishedAt = "2024-01-01T00:00:00.000Z"
                )
            )
        )
        coEvery { remoteDataSource.getMovieVideos(1) } returns response

        val result = (repository.getTrailer(1) as NetworkResult.Success).data

        assertEquals("official_key", result?.key)
    }

    @Test
    fun `getTrailer ignores non-YouTube sources`() = runTest {
        every { networkStatusProvider.isCurrentlyOnline() } returns true
        val response = fakeVideoListResponse().copy(
            results = listOf(
                VideoResponse(
                    key = "vimeo_key",
                    site = "Vimeo", type = "Trailer", official = true,
                    publishedAt = "2024-01-01T00:00:00.000Z"
                )
            )
        )
        coEvery { remoteDataSource.getMovieVideos(1) } returns response

        val result = (repository.getTrailer(1) as NetworkResult.Success).data

        assertNull(result)
    }

    // ── toggleFavorite ────────────────────────────────────────────────────────

    @Test
    fun `toggleFavorite inserts when movie is not already favorited`() = runTest {
        val movie = fakeMovie(id = 3)
        coEvery { localDataSource.isFavorite(3) } returns false
        coJustRun { localDataSource.insertFavorite(any(), any()) }

        repository.toggleFavorite(movie)

        coVerify { localDataSource.insertFavorite(any(), any()) }
    }

    @Test
    fun `toggleFavorite deletes when movie is already favorited`() = runTest {
        val movie = fakeMovie(id = 4)
        coEvery { localDataSource.isFavorite(4) } returns true
        coJustRun { localDataSource.deleteFavoriteById(4) }

        repository.toggleFavorite(movie)

        coVerify { localDataSource.deleteFavoriteById(4) }
    }

    // ── getFavorites (paging) ─────────────────────────────────────────────────

    @Test
    fun `getFavorites returns paged items from cache when offline`() = runTest {
        // networkMonitor already returns false (offline) in this test class.
        val movies = listOf(fakeMovie(1), fakeMovie(2))
        coEvery { localDataSource.getFavoritesPaged(any(), 0) } returns movies
        coEvery { localDataSource.getFavoritesPaged(any(), 20) } returns emptyList()

        val snapshot = repository.getFavorites().asSnapshot()

        assertEquals(2, snapshot.size)
        assertEquals(1, snapshot[0].id)
        assertEquals(2, snapshot[1].id)
    }

    @Test
    fun `getFavorites returns empty snapshot when no cached favorites`() = runTest {
        coEvery { localDataSource.getFavoritesPaged(any(), any()) } returns emptyList()

        val snapshot = repository.getFavorites().asSnapshot()

        assertEquals(emptyList<Movie>(), snapshot)
    }

    // ── observeIsFavorite ─────────────────────────────────────────────────────

    @Test
    fun `observeIsFavorite returns true when movie is favorited`() = runTest {
        every { localDataSource.observeIsFavorite(5) } returns flowOf(true)

        val result = repository.observeIsFavorite(5).first()

        assert(result)
    }

    @Test
    fun `observeIsFavorite returns false when movie is not favorited`() = runTest {
        every { localDataSource.observeIsFavorite(5) } returns flowOf(false)

        val result = repository.observeIsFavorite(5).first()

        assert(!result)
    }
}
