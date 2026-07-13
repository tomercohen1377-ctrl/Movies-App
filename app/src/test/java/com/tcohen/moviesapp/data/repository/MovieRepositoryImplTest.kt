package com.tcohen.moviesapp.data.repository

import com.tcohen.moviesapp.data.local.dao.FavoriteDao
import com.tcohen.moviesapp.data.local.dao.MovieDao
import com.tcohen.moviesapp.data.remote.api.SafeApiCaller
import com.tcohen.moviesapp.data.remote.api.TmdbApiService
import com.tcohen.moviesapp.data.remote.dto.VideoResponse
import com.tcohen.moviesapp.domain.model.Movie
import com.tcohen.moviesapp.fakeFavoriteEntity
import com.tcohen.moviesapp.fakeMovie
import com.tcohen.moviesapp.fakeMovieDetailDto
import com.tcohen.moviesapp.fakeVideoListResponse
import com.tcohen.moviesapp.util.MainDispatcherRule
import com.tcohen.moviesapp.util.NetworkMonitor
import com.tcohen.moviesapp.util.NetworkResult
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertTrue
import androidx.paging.testing.asSnapshot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MovieRepositoryImplTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val apiService: TmdbApiService = mockk()
    private val movieDao: MovieDao = mockk()
    private val favoriteDao: FavoriteDao = mockk()
    private val networkMonitor: NetworkMonitor = mockk {
        every { isCurrentlyOnline() } returns false   // server sync path disabled in unit tests
    }
    private val safeApiCaller = SafeApiCaller(networkMonitor)
    private val repository: MovieRepositoryImpl by lazy {
        MovieRepositoryImpl(
            apiService, movieDao, favoriteDao, networkMonitor,
            safeApiCaller,
            accountId = "", sessionId = ""
        )
    }

    // ── getMovieDetail ────────────────────────────────────────────────────────

    @Test
    fun `getMovieDetail returns Success with mapped domain object`() = runTest {
        every { networkMonitor.isCurrentlyOnline() } returns true
        val dto = fakeMovieDetailDto(id = 5)
        coEvery { apiService.getMovieDetails(5) } returns dto

        val result = repository.getMovieDetail(5) as NetworkResult.Success

        assertEquals(5, result.data.id)
        assertEquals("Test Movie 5", result.data.title)
        assertEquals(120, result.data.runtime)
    }

    @Test
    fun `getMovieDetail returns NetworkError when API throws`() = runTest {
        every { networkMonitor.isCurrentlyOnline() } returns true
        coEvery { apiService.getMovieDetails(any()) } throws java.io.IOException("connection refused")

        val result = repository.getMovieDetail(999)

        assertTrue(result is NetworkResult.Error)
    }

    // ── getTrailer ────────���───────────────────────────────────────────────────

    @Test
    fun `getTrailer returns Success with YouTube trailer key when online`() = runTest {
        every { networkMonitor.isCurrentlyOnline() } returns true
        coEvery { apiService.getMovieVideos(1) } returns fakeVideoListResponse(trailerKey = "abc123")

        val result = repository.getTrailer(1) as NetworkResult.Success

        assertNotNull(result.data)
        assertEquals("abc123", result.data?.key)
    }

    @Test
    fun `getTrailer returns Error(NO_CONNECTION) when offline`() = runTest {
        // The repository no longer carries an offline fast-path of its own; that
        // responsibility has moved into SafeApiCaller. When offline, callers see the
        // same error envelope the rest of the API surface uses, and the view-model
        // collapses it to a "no trailer shown" UI state (`MovieDetailViewModel`).
        every { networkMonitor.isCurrentlyOnline() } returns false
        // No API stub — the pre-check inside SafeApiCaller must short-circuit before
        // the call hits the wire.

        val result = repository.getTrailer(1)

        assertTrue(result is NetworkResult.Error)
        assertEquals(
            com.tcohen.moviesapp.util.ApiError.NO_CONNECTION.message,
            (result as NetworkResult.Error).message
        )
    }

    @Test
    fun `getTrailer returns Success with null when no YouTube trailers exist`() = runTest {
        every { networkMonitor.isCurrentlyOnline() } returns true
        coEvery { apiService.getMovieVideos(1) } returns fakeVideoListResponse(trailerKey = null)

        val result = repository.getTrailer(1) as NetworkResult.Success

        assertNull(result.data)
    }

    @Test
    fun `getTrailer prefers official trailers over unofficial`() = runTest {
        every { networkMonitor.isCurrentlyOnline() } returns true
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
        coEvery { apiService.getMovieVideos(1) } returns response

        val result = (repository.getTrailer(1) as NetworkResult.Success).data

        assertEquals("official_key", result?.key)
    }

    @Test
    fun `getTrailer ignores non-YouTube sources`() = runTest {
        every { networkMonitor.isCurrentlyOnline() } returns true
        val response = fakeVideoListResponse().copy(
            results = listOf(
                VideoResponse(
                    key = "vimeo_key",
                    site = "Vimeo", type = "Trailer", official = true,
                    publishedAt = "2024-01-01T00:00:00.000Z"
                )
            )
        )
        coEvery { apiService.getMovieVideos(1) } returns response

        val result = (repository.getTrailer(1) as NetworkResult.Success).data

        assertNull(result)
    }

    // ── toggleFavorite ────────────────────────────────────────────────────────

    @Test
    fun `toggleFavorite inserts when movie is not already favorited`() = runTest {
        val movie = fakeMovie(id = 3)
        coEvery { favoriteDao.isFavorite(3) } returns false
        coJustRun { favoriteDao.insert(any()) }

        repository.toggleFavorite(movie)

        coVerify { favoriteDao.insert(any()) }
    }

    @Test
    fun `toggleFavorite deletes when movie is already favorited`() = runTest {
        val movie = fakeMovie(id = 4)
        coEvery { favoriteDao.isFavorite(4) } returns true
        coJustRun { favoriteDao.deleteById(4) }

        repository.toggleFavorite(movie)

        coVerify { favoriteDao.deleteById(4) }
    }

    // ── getFavorites (paging) ─────────────────────────────────────────────────

    @Test
    fun `getFavorites returns paged items from Room when offline`() = runTest {
        // networkMonitor already returns false (offline) in this test class.
        val entities = listOf(fakeFavoriteEntity(1), fakeFavoriteEntity(2))
        // FavoritesPagingSource fetches FAVORITES_PAGE_SIZE + 1 rows to detect next page.
        coEvery { favoriteDao.getFavoritesPaged(any(), 0) } returns entities
        coEvery { favoriteDao.getFavoritesPaged(any(), 20) } returns emptyList()

        val snapshot = repository.getFavorites()
            .asSnapshot()

        assertEquals(2, snapshot.size)
        assertEquals(1, snapshot[0].id)
        assertEquals(2, snapshot[1].id)
    }

    @Test
    fun `getFavorites returns empty snapshot when no cached favorites`() = runTest {
        coEvery { favoriteDao.getFavoritesPaged(any(), any()) } returns emptyList()

        val snapshot = repository.getFavorites()
            .asSnapshot()

        assertEquals(emptyList<Movie>(), snapshot)
    }

    // ── isFavorite ────────────────────────────────────────────────────────────

    @Test
    fun `isFavorite returns true when movie is favorited`() = runTest {
        every { favoriteDao.observeIsFavorite(5) } returns flowOf(true)

        val result = repository.observeIsFavorite(5).first()

        assert(result)
    }

    @Test
    fun `isFavorite returns false when movie is not favorited`() = runTest {
        every { favoriteDao.observeIsFavorite(5) } returns flowOf(false)

        val result = repository.observeIsFavorite(5).first()

        assert(!result)
    }
}
