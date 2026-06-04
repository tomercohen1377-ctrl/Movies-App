package com.tcohen.moviesapp.data.repository

import com.tcohen.moviesapp.data.local.dao.FavoriteDao
import com.tcohen.moviesapp.data.local.dao.MovieDao
import com.tcohen.moviesapp.data.remote.api.TmdbApiService
import com.tcohen.moviesapp.data.remote.dto.VideoDto
import com.tcohen.moviesapp.domain.model.Movie
import com.tcohen.moviesapp.fakeFavoriteEntity
import com.tcohen.moviesapp.fakeMovie
import com.tcohen.moviesapp.fakeMovieDetailDto
import com.tcohen.moviesapp.fakeVideoListResponse
import com.tcohen.moviesapp.util.MainDispatcherRule
import com.tcohen.moviesapp.util.NetworkMonitor
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
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
    private val repository: MovieRepositoryImpl by lazy {
        MovieRepositoryImpl(
            apiService, movieDao, favoriteDao, networkMonitor,
            accountId = "", sessionId = "", favoritesListId = ""
        )
    }

    // ── getMovieDetail ────────────────────────────────────────────────────────

    @Test
    fun `getMovieDetail returns mapped domain object`() = runTest {
        val dto = fakeMovieDetailDto(id = 5)
        coEvery { apiService.getMovieDetail(5) } returns dto

        val result = repository.getMovieDetail(5)

        assertEquals(5, result.id)
        assertEquals("Test Movie 5", result.title)
        assertEquals(120, result.runtime)
    }

    @Test
    fun `getMovieDetail propagates exception from API`() = runTest {
        coEvery { apiService.getMovieDetail(any()) } throws RuntimeException("Not found")

        var threw = false
        try {
            repository.getMovieDetail(999)
        } catch (_: RuntimeException) {
            threw = true
        }
        assert(threw)
    }

    // ── getTrailer ────────────────────────────────────────────────────────────

    @Test
    fun `getTrailer returns YouTube trailer key when online`() = runTest {
        every { networkMonitor.isCurrentlyOnline() } returns true
        coEvery { apiService.getMovieVideos(1) } returns fakeVideoListResponse(trailerKey = "abc123")

        val result = repository.getTrailer(1)

        assertNotNull(result)
        assertEquals("abc123", result?.key)
    }

    @Test
    fun `getTrailer returns null when offline`() = runTest {
        every { networkMonitor.isCurrentlyOnline() } returns false

        val result = repository.getTrailer(1)

        assertNull(result)
    }

    @Test
    fun `getTrailer returns null when no YouTube trailers exist`() = runTest {
        every { networkMonitor.isCurrentlyOnline() } returns true
        coEvery { apiService.getMovieVideos(1) } returns fakeVideoListResponse(trailerKey = null)

        val result = repository.getTrailer(1)

        assertNull(result)
    }

    @Test
    fun `getTrailer prefers official trailers over unofficial`() = runTest {
        every { networkMonitor.isCurrentlyOnline() } returns true
        val response = fakeVideoListResponse().copy(
            results = listOf(
                VideoDto(
                    id = "v1", key = "unofficial_key", name = "Fan Trailer",
                    site = "YouTube", type = "Trailer", official = false,
                    publishedAt = "2024-06-01T00:00:00.000Z"
                ),
                VideoDto(
                    id = "v2", key = "official_key", name = "Official Trailer",
                    site = "YouTube", type = "Trailer", official = true,
                    publishedAt = "2024-01-01T00:00:00.000Z"
                )
            )
        )
        coEvery { apiService.getMovieVideos(1) } returns response

        val result = repository.getTrailer(1)

        assertEquals("official_key", result?.key)
    }

    @Test
    fun `getTrailer ignores non-YouTube sources`() = runTest {
        every { networkMonitor.isCurrentlyOnline() } returns true
        val response = fakeVideoListResponse().copy(
            results = listOf(
                VideoDto(
                    id = "v1", key = "vimeo_key", name = "Vimeo Trailer",
                    site = "Vimeo", type = "Trailer", official = true,
                    publishedAt = "2024-01-01T00:00:00.000Z"
                )
            )
        )
        coEvery { apiService.getMovieVideos(1) } returns response

        val result = repository.getTrailer(1)

        assertNull(result)
    }

    // ── toggleFavorite ────────────────────────────────────────────────────────

    @Test
    fun `toggleFavorite inserts when movie is not already favorited`() = runTest {
        val movie = fakeMovie(id = 3)
        coEvery { favoriteDao.isFavoriteOnce(3) } returns false
        coJustRun { favoriteDao.insert(any()) }

        repository.toggleFavorite(movie)

        coVerify { favoriteDao.insert(any()) }
    }

    @Test
    fun `toggleFavorite deletes when movie is already favorited`() = runTest {
        val movie = fakeMovie(id = 4)
        coEvery { favoriteDao.isFavoriteOnce(4) } returns true
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
        every { favoriteDao.isFavorite(5) } returns flowOf(true)

        val result = repository.isFavorite(5).first()

        assert(result)
    }

    @Test
    fun `isFavorite returns false when movie is not favorited`() = runTest {
        every { favoriteDao.isFavorite(5) } returns flowOf(false)

        val result = repository.isFavorite(5).first()

        assert(!result)
    }
}
