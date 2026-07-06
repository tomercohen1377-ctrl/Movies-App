package com.tcohen.moviesapp.data.repository

import com.tcohen.moviesapp.data.auth.AuthSnapshot
import com.tcohen.moviesapp.data.auth.AuthStore
import com.tcohen.moviesapp.data.local.dao.MovieDao
import com.tcohen.moviesapp.data.remote.api.TmdbApiService
import com.tcohen.moviesapp.data.remote.dto.VideoResponse
import com.tcohen.moviesapp.data.remote.server.api.ServerApiService
import com.tcohen.moviesapp.data.remote.server.dto.ServerAddFavoriteResponse
import com.tcohen.moviesapp.data.remote.server.dto.ServerFavoriteDto
import com.tcohen.moviesapp.domain.model.Category
import com.tcohen.moviesapp.domain.model.Movie
import com.tcohen.moviesapp.fakeIsFavoriteResponse
import com.tcohen.moviesapp.fakeMovie
import com.tcohen.moviesapp.fakeMovieDetailDto
import com.tcohen.moviesapp.fakeMovieEntity
import com.tcohen.moviesapp.fakeServerFavoriteDto
import com.tcohen.moviesapp.fakeVideoListResponse
import com.tcohen.moviesapp.util.MainDispatcherRule
import com.tcohen.moviesapp.util.NetworkMonitor
import com.tcohen.moviesapp.util.NetworkResult
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MovieRepositoryImplTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val apiService: TmdbApiService = mockk()
    private val movieDao: MovieDao = mockk(relaxed = true)
    private val networkMonitor: NetworkMonitor = mockk {
        every { isCurrentlyOnline() } returns true
    }
    private val serverApiService: ServerApiService = mockk()
    private val authStore: AuthStore = mockk(relaxed = true)

    private val authSnapshot = AuthSnapshot(
        userId = "quiet-amber-fox",
        accessToken = "jwt-token",
        expiresAtEpochMs = System.currentTimeMillis() + 24L * 60 * 60 * 1000,
    )

    private val repository: MovieRepositoryImpl by lazy {
        MovieRepositoryImpl(
            apiService = apiService,
            movieDao = movieDao,
            networkMonitor = networkMonitor,
            serverApiService = serverApiService,
            authStore = authStore,
        )
    }

    // ── getMovieDetail ────────────────────────────────────────────────────────

    @Test
    fun `getMovieDetail returns Success with mapped domain object`() = runTest {
        val dto = fakeMovieDetailDto(id = 5)
        coEvery { apiService.getMovieDetails(5) } returns dto

        val result = repository.getMovieDetail(5) as NetworkResult.Success

        assertEquals(5, result.data.id)
        assertEquals("Test Movie 5", result.data.title)
        assertEquals(120, result.data.runtime)
    }

    @Test
    fun `getMovieDetail returns NetworkError when API throws`() = runTest {
        coEvery { apiService.getMovieDetails(any()) } throws java.io.IOException("refused")

        val result = repository.getMovieDetail(999)

        assertTrue(result is NetworkResult.Error)
    }

    // ── getTrailer ────────────────────────────────────────────────────────────

    @Test
    fun `getTrailer returns Success with YouTube trailer key when online`() = runTest {
        every { networkMonitor.isCurrentlyOnline() } returns true
        coEvery { apiService.getMovieVideos(1) } returns fakeVideoListResponse(trailerKey = "abc123")

        val result = repository.getTrailer(1) as NetworkResult.Success

        assertNotNull(result.data)
        assertEquals("abc123", result.data?.key)
    }

    @Test
    fun `getTrailer returns Success with null when offline`() = runTest {
        every { networkMonitor.isCurrentlyOnline() } returns false

        val result = repository.getTrailer(1) as NetworkResult.Success

        assertNull(result.data)
    }

    @Test
    fun `getTrailer ignores non-YouTube sources`() = runTest {
        every { networkMonitor.isCurrentlyOnline() } returns true
        val response = fakeVideoListResponse().copy(
            results = listOf(
                VideoResponse(
                    key = "vimeo_key",
                    site = "Vimeo",
                    type = "Trailer",
                    official = true,
                    publishedAt = "2024-01-01T00:00:00.000Z",
                )
            )
        )
        coEvery { apiService.getMovieVideos(1) } returns response

        val result = (repository.getTrailer(1) as NetworkResult.Success).data

        assertNull(result)
    }

    // ── getFavorites ────────────────────────────────────────────────────────

    @Test
    fun `getFavorites returns Error when no auth snapshot exists`() = runTest {
        every { authStore.readSnapshotBlocking() } returns null

        val result = repository.getFavorites()

        assertTrue(result is NetworkResult.Error)
        coVerify(exactly = 0) { serverApiService.getFavorites(any()) }
    }

    @Test
    fun `getFavorites maps cached MovieEntity rows to domain Movies`() = runTest {
        every { authStore.readSnapshotBlocking() } returns authSnapshot
        coEvery { serverApiService.getFavorites("quiet-amber-fox") } returns listOf(
            fakeServerFavoriteDto(id = 42, savedAt = 100L),
            fakeServerFavoriteDto(id = 7, savedAt = 50L),
        )
        coEvery { movieDao.findById(42) } returns fakeMovieEntity(42, Category.TOP_RATED)
        coEvery { movieDao.findById(7) } returns fakeMovieEntity(7, Category.NOW_PLAYING)

        val result = repository.getFavorites() as NetworkResult.Success

        assertEquals(2, result.data.size)
        assertEquals(42, result.data[0].id)
        assertEquals("Test Movie 42", result.data[0].title)
    }

    @Test
    fun `getFavorites silently drops movieIds not in MovieDao cache`() = runTest {
        every { authStore.readSnapshotBlocking() } returns authSnapshot
        coEvery { serverApiService.getFavorites("quiet-amber-fox") } returns listOf(
            fakeServerFavoriteDto(id = 1, savedAt = 1L),
            fakeServerFavoriteDto(id = 999, savedAt = 2L),
        )
        coEvery { movieDao.findById(1) } returns fakeMovieEntity(1)
        coEvery { movieDao.findById(999) } returns null

        val result = repository.getFavorites() as NetworkResult.Success

        assertEquals(1, result.data.size)
        assertEquals(1, result.data[0].id)
    }

    @Test
    fun `getFavorites propagates network error from server`() = runTest {
        every { authStore.readSnapshotBlocking() } returns authSnapshot
        coEvery { serverApiService.getFavorites(any()) } throws fakeHttp500()

        val result = repository.getFavorites()

        assertTrue(result is NetworkResult.Error)
    }

    @Test
    fun `getFavorites returns Empty Success when server list is empty`() = runTest {
        every { authStore.readSnapshotBlocking() } returns authSnapshot
        coEvery { serverApiService.getFavorites("quiet-amber-fox") } returns emptyList()

        val result = repository.getFavorites() as NetworkResult.Success

        assertEquals(emptyList<Movie>(), result.data)
    }

    // ── addFavorite / removeFavorite ────────────────────────────────────

    @Test
    fun `removeFavorite calls removeFavorite on server`() = runTest {
        every { authStore.readSnapshotBlocking() } returns authSnapshot
        coJustRun { serverApiService.removeFavorite("quiet-amber-fox", 4) }

        val result = repository.removeFavorite(fakeMovie(id = 4))

        assertTrue(result is NetworkResult.Success)
        coVerify { serverApiService.removeFavorite("quiet-amber-fox", 4) }
    }

    @Test
    fun `addFavorite calls addFavorite on server`() = runTest {
        every { authStore.readSnapshotBlocking() } returns authSnapshot
        coEvery { serverApiService.addFavorite("quiet-amber-fox", 3) } returns
            ServerAddFavoriteResponse(created = true)

        val result = repository.addFavorite(fakeMovie(id = 3))

        assertTrue(result is NetworkResult.Success)
        coVerify { serverApiService.addFavorite("quiet-amber-fox", 3) }
    }

    @Test
    fun `addFavorite returns Error without calling server when offline`() = runTest {
        every { networkMonitor.isCurrentlyOnline() } returns false

        val result = repository.addFavorite(fakeMovie(id = 5))

        assertTrue(result is NetworkResult.Error)
        coVerify(exactly = 0) { serverApiService.addFavorite(any(), any()) }
    }

    @Test
    fun `removeFavorite returns Error when offline`() = runTest {
        every { networkMonitor.isCurrentlyOnline() } returns false

        val result = repository.removeFavorite(fakeMovie(id = 5))

        assertTrue(result is NetworkResult.Error)
        coVerify(exactly = 0) { serverApiService.removeFavorite(any(), any()) }
    }

    // ── isFavorite ──────────────────────────────────────────────────────────

    @Test
    fun `isFavorite returns Success(true) for 200 with body isFavorite=true`() = runTest {
        every { authStore.readSnapshotBlocking() } returns authSnapshot
        coEvery { serverApiService.isFavorite("quiet-amber-fox", 7) } returns
            fakeIsFavoriteResponse(isFavorite = true)

        val result = repository.isFavorite(7)

        assertTrue(result is NetworkResult.Success)
        assertEquals(true, (result as NetworkResult.Success).data)
    }

    @Test
    fun `isFavorite returns Success(false) for 404`() = runTest {
        every { authStore.readSnapshotBlocking() } returns authSnapshot
        coEvery { serverApiService.isFavorite("quiet-amber-fox", 8) } returns
            retrofit2.Response.error(404, fakeBody("Not found"))

        val result = repository.isFavorite(8)

        assertTrue(result is NetworkResult.Success)
        assertEquals(false, (result as NetworkResult.Success).data)
    }

    @Test
    fun `isFavorite returns Error when offline`() = runTest {
        every { networkMonitor.isCurrentlyOnline() } returns false

        val result = repository.isFavorite(7)

        assertTrue(result is NetworkResult.Error)
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private fun fakeHttp500(): Exception =
        retrofit2.HttpException(
            retrofit2.Response.error<Any>(
                500,
                """{"error":"server"}""".toResponseBody("application/json".toMediaType())
            )
        )

    private fun fakeBody(text: String) = text.toResponseBody("application/json".toMediaType())
}
