package com.tcohen.moviesapp.presentation.moviedetail

import com.tcohen.moviesapp.domain.repository.MovieRepositoryBase
import com.tcohen.moviesapp.fakeMovieDetail
import com.tcohen.moviesapp.fakeVideoResult
import com.tcohen.moviesapp.util.ApiError
import com.tcohen.moviesapp.util.MainDispatcherRule
import com.tcohen.moviesapp.util.NetworkResult
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Unit tests for [MovieDetailStateHolder].
 *
 * Compared with the old ViewModel tests, there is no [SavedStateHandle] dependency —
 * [movieId] is passed directly, making tests simpler and fully platform-agnostic.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MovieDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository: MovieRepositoryBase = mockk()

    private fun createStateHolder(movieId: Int? = 1): MovieDetailStateHolder =
        MovieDetailStateHolder(
            movieId,
            repository,
            CoroutineScope(mainDispatcherRule.testDispatcher + SupervisorJob())
        )

    // Convenience accessors for the sealed state ─────────────────────────────
    private val MovieDetailStateHolder.successState
        get() = uiState.value as? MovieDetailUiState.Success

    private val MovieDetailStateHolder.errorMessage
        get() = (uiState.value as? MovieDetailUiState.Error)?.message

    // ── Successful load ───────────────────────────────────────────────────────

    @Test
    fun `successful load produces Success state with movie and trailer`() = runTest {
        val detail = fakeMovieDetail(id = 1)
        val trailer = fakeVideoResult("abc123")
        coEvery { repository.getMovieDetail(1) } returns NetworkResult.Success(detail)
        coEvery { repository.getTrailer(1) } returns NetworkResult.Success(trailer)
        coEvery { repository.isFavorite(1) } returns false
        every { repository.observeIsFavorite(1) } returns flowOf(false)

        val sh = createStateHolder()

        assertNotNull(sh.successState)
        assertEquals(detail, sh.successState?.movie)
        assertEquals("abc123", sh.successState?.trailerKey)
    }

    @Test
    fun `successful load with null trailer produces Success with null trailerKey`() = runTest {
        coEvery { repository.getMovieDetail(1) } returns NetworkResult.Success(fakeMovieDetail())
        coEvery { repository.getTrailer(1) } returns NetworkResult.Success(null)
        coEvery { repository.isFavorite(1) } returns false
        every { repository.observeIsFavorite(1) } returns flowOf(false)

        val sh = createStateHolder()

        assertNull(sh.successState?.trailerKey)
    }

    @Test
    fun `trailer error is treated as no trailer (graceful degradation)`() = runTest {
        coEvery { repository.getMovieDetail(1) } returns NetworkResult.Success(fakeMovieDetail())
        coEvery { repository.getTrailer(1) } returns NetworkResult.Error("Service temporarily unavailable.", 503)
        coEvery { repository.isFavorite(1) } returns false
        every { repository.observeIsFavorite(1) } returns flowOf(false)

        val sh = createStateHolder()

        // Movie still shows but trailer is absent — not an Error state
        assertNotNull(sh.successState?.movie)
        assertNull(sh.successState?.trailerKey)
    }

    // ── Error handling ────────────────────────────────────────────────────────

    @Test
    fun `network error produces Error state with no-internet message`() = runTest {
        coEvery { repository.getMovieDetail(1) } returns NetworkResult.Error(ApiError.NO_CONNECTION.message)
        coEvery { repository.getTrailer(1) } returns NetworkResult.Success(null)
        coEvery { repository.isFavorite(1) } returns false
        every { repository.observeIsFavorite(1) } returns flowOf(false)

        val sh = createStateHolder()

        assertTrue(sh.uiState.value is MovieDetailUiState.Error)
        assertEquals(ApiError.NO_CONNECTION.message, sh.errorMessage)
        assertNull(sh.successState)
    }

    @Test
    fun `401 HTTP error produces Error state with server message`() = runTest {
        coEvery { repository.getMovieDetail(1) } returns NetworkResult.Error("Invalid API key.", 401)
        coEvery { repository.getTrailer(1) } returns NetworkResult.Success(null)
        coEvery { repository.isFavorite(1) } returns false
        every { repository.observeIsFavorite(1) } returns flowOf(false)

        val sh = createStateHolder()

        assertEquals("Invalid API key.", sh.errorMessage)
    }

    @Test
    fun `404 HTTP error produces Error state with not-found message`() = runTest {
        coEvery { repository.getMovieDetail(1) } returns NetworkResult.Error("The resource you requested could not be found.", 404)
        coEvery { repository.getTrailer(1) } returns NetworkResult.Success(null)
        coEvery { repository.isFavorite(1) } returns false
        every { repository.observeIsFavorite(1) } returns flowOf(false)

        val sh = createStateHolder()

        assertEquals("The resource you requested could not be found.", sh.errorMessage)
    }

    @Test
    fun `500 HTTP error produces Error state`() = runTest {
        coEvery { repository.getMovieDetail(1) } returns NetworkResult.Error("Internal error: Something went wrong.", 500)
        coEvery { repository.getTrailer(1) } returns NetworkResult.Success(null)
        coEvery { repository.isFavorite(1) } returns false
        every { repository.observeIsFavorite(1) } returns flowOf(false)

        val sh = createStateHolder()

        assertEquals("Internal error: Something went wrong.", sh.errorMessage)
    }

    @Test
    fun `unexpected error produces Error state with fallback message`() = runTest {
        coEvery { repository.getMovieDetail(1) } returns NetworkResult.Error(ApiError.UNEXPECTED.message)
        coEvery { repository.getTrailer(1) } returns NetworkResult.Success(null)
        coEvery { repository.isFavorite(1) } returns false
        every { repository.observeIsFavorite(1) } returns flowOf(false)

        val sh = createStateHolder()

        assertEquals(ApiError.UNEXPECTED.message, sh.errorMessage)
    }

    @Test
    fun `null movieId produces Error state without crashing`() = runTest {
        val sh = createStateHolder(movieId = null)

        assertTrue(sh.uiState.value is MovieDetailUiState.Error)
    }

    // ── Reload ────────────────────────────────────────────────────────────────

    @Test
    fun `Reload intent re-fetches detail from repository`() = runTest {
        val detail = fakeMovieDetail()
        coEvery { repository.getMovieDetail(1) } returns NetworkResult.Success(detail)
        coEvery { repository.getTrailer(1) } returns NetworkResult.Success(null)
        coEvery { repository.isFavorite(1) } returns false
        every { repository.observeIsFavorite(1) } returns flowOf(false)

        val sh = createStateHolder()
        sh.processIntent(MovieDetailIntent.Reload)

        // Called twice: once on init, once on Reload
        coVerify(exactly = 2) { repository.getMovieDetail(1) }
    }

    // ── Favorite ──────────────────────────────────────────────────────────────

    @Test
    fun `ToggleFavorite calls repository when in Success state`() = runTest {
        coEvery { repository.getMovieDetail(1) } returns NetworkResult.Success(fakeMovieDetail())
        coEvery { repository.getTrailer(1) } returns NetworkResult.Success(null)
        coEvery { repository.isFavorite(1) } returns false
        every { repository.observeIsFavorite(1) } returns flowOf(false)
        coJustRun { repository.toggleFavorite(any()) }

        val sh = createStateHolder()
        sh.processIntent(MovieDetailIntent.ToggleFavorite)

        coVerify { repository.toggleFavorite(any()) }
    }

    @Test
    fun `isFavorite = true is reflected in Success state`() = runTest {
        coEvery { repository.getMovieDetail(1) } returns NetworkResult.Success(fakeMovieDetail())
        coEvery { repository.getTrailer(1) } returns NetworkResult.Success(null)
        coEvery { repository.isFavorite(1) } returns true
        every { repository.observeIsFavorite(1) } returns flowOf(true)

        val sh = createStateHolder()

        assertTrue(sh.successState?.isFavorite == true)
    }

    @Test
    fun `isFavorite = false is reflected in Success state`() = runTest {
        coEvery { repository.getMovieDetail(1) } returns NetworkResult.Success(fakeMovieDetail())
        coEvery { repository.getTrailer(1) } returns NetworkResult.Success(null)
        coEvery { repository.isFavorite(1) } returns false
        every { repository.observeIsFavorite(1) } returns flowOf(false)

        val sh = createStateHolder()

        assertFalse(sh.successState?.isFavorite == true)
    }

    // ── Different movie IDs ───────────────────────────────────────────────────

    @Test
    fun `loads correct movie when movieId is 5`() = runTest {
        val detail = fakeMovieDetail(id = 5)
        coEvery { repository.getMovieDetail(5) } returns NetworkResult.Success(detail)
        coEvery { repository.getTrailer(5) } returns NetworkResult.Success(null)
        coEvery { repository.isFavorite(5) } returns false
        every { repository.observeIsFavorite(5) } returns flowOf(false)

        val sh = createStateHolder(movieId = 5)

        assertEquals(5, sh.successState?.movie?.id)
        coVerify { repository.getMovieDetail(5) }
    }
}
