package com.tcohen.moviesapp.presentation.moviedetail

import androidx.lifecycle.SavedStateHandle
import com.tcohen.moviesapp.domain.repository.MovieRepository
import com.tcohen.moviesapp.fakeMovieDetail
import com.tcohen.moviesapp.fakeVideoResult
import com.tcohen.moviesapp.presentation.navigation.Screen
import com.tcohen.moviesapp.util.ApiError
import com.tcohen.moviesapp.util.MainDispatcherRule
import com.tcohen.moviesapp.util.NetworkResult
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MovieDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository: MovieRepository = mockk()

    private fun savedStateHandleFor(movieId: Int = 1) =
        SavedStateHandle(mapOf(Screen.MovieDetail.ARG_MOVIE_ID to movieId))

    private fun createViewModel(movieId: Int = 1): MovieDetailViewModel =
        MovieDetailViewModel(repository, savedStateHandleFor(movieId))

    // Convenience accessors for the sealed state ─────────────────────────────
    private val MovieDetailViewModel.successState
        get() = uiState.value as? MovieDetailUiState.Success

    private val MovieDetailViewModel.errorMessage
        get() = (uiState.value as? MovieDetailUiState.Error)?.message

    // ── Successful load ───────────────────────────────────────────────────────

    @Test
    fun `successful load produces Success state with movie and trailer`() = runTest {
        val detail = fakeMovieDetail(id = 1)
        val trailer = fakeVideoResult("abc123")
        coEvery { repository.getMovieDetail(1) } returns NetworkResult.Success(detail)
        coEvery { repository.getTrailer(1) } returns NetworkResult.Success(trailer)
        every { repository.observeIsFavorite(1) } returns flowOf(false)

        val vm = createViewModel()

        val state = vm.successState
        assertNotNull(state)
        assertEquals(detail, state?.movie)
        assertEquals("abc123", state?.trailerKey)
    }

    @Test
    fun `successful load with null trailer produces Success with null trailerKey`() = runTest {
        coEvery { repository.getMovieDetail(1) } returns NetworkResult.Success(fakeMovieDetail())
        coEvery { repository.getTrailer(1) } returns NetworkResult.Success(null)
        every { repository.observeIsFavorite(1) } returns flowOf(false)

        val vm = createViewModel()

        assertNull(vm.successState?.trailerKey)
    }

    @Test
    fun `trailer error is treated as no trailer (graceful degradation)`() = runTest {
        coEvery { repository.getMovieDetail(1) } returns NetworkResult.Success(fakeMovieDetail())
        coEvery { repository.getTrailer(1) } returns NetworkResult.Error("Service temporarily unavailable.", 503)
        every { repository.observeIsFavorite(1) } returns flowOf(false)

        val vm = createViewModel()

        // Movie still shows but trailer is absent — not an Error state
        assertNotNull(vm.successState?.movie)
        assertNull(vm.successState?.trailerKey)
    }

    // ── Error handling ────────────────────────────────────────────────────────

    @Test
    fun `network error produces Error state with no-internet message`() = runTest {
        coEvery { repository.getMovieDetail(1) } returns NetworkResult.Error(ApiError.NO_CONNECTION.message)
        coEvery { repository.getTrailer(1) } returns NetworkResult.Success(null)
        every { repository.observeIsFavorite(1) } returns flowOf(false)

        val vm = createViewModel()

        assertTrue(vm.uiState.value is MovieDetailUiState.Error)
        assertEquals(ApiError.NO_CONNECTION.message, vm.errorMessage)
        assertNull(vm.successState)
    }

    @Test
    fun `401 HTTP error produces Error state with server message`() = runTest {
        coEvery { repository.getMovieDetail(1) } returns NetworkResult.Error("Invalid API key.", 401)
        coEvery { repository.getTrailer(1) } returns NetworkResult.Success(null)
        every { repository.observeIsFavorite(1) } returns flowOf(false)

        val vm = createViewModel()

        assertEquals("Invalid API key.", vm.errorMessage)
    }

    @Test
    fun `404 HTTP error produces Error state with not-found message`() = runTest {
        coEvery { repository.getMovieDetail(1) } returns NetworkResult.Error("The resource you requested could not be found.", 404)
        coEvery { repository.getTrailer(1) } returns NetworkResult.Success(null)
        every { repository.observeIsFavorite(1) } returns flowOf(false)

        val vm = createViewModel()

        assertEquals("The resource you requested could not be found.", vm.errorMessage)
    }

    @Test
    fun `500 HTTP error produces Error state`() = runTest {
        coEvery { repository.getMovieDetail(1) } returns NetworkResult.Error("Internal error: Something went wrong.", 500)
        coEvery { repository.getTrailer(1) } returns NetworkResult.Success(null)
        every { repository.observeIsFavorite(1) } returns flowOf(false)

        val vm = createViewModel()

        assertEquals("Internal error: Something went wrong.", vm.errorMessage)
    }

    @Test
    fun `unexpected error produces Error state with fallback message`() = runTest {
        coEvery { repository.getMovieDetail(1) } returns NetworkResult.Error(ApiError.UNEXPECTED.message)
        coEvery { repository.getTrailer(1) } returns NetworkResult.Success(null)
        every { repository.observeIsFavorite(1) } returns flowOf(false)

        val vm = createViewModel()

        assertEquals(ApiError.UNEXPECTED.message, vm.errorMessage)
    }

    @Test
    fun `missing movieId produces Error state without crashing`() = runTest {
        val vm = MovieDetailViewModel(repository, SavedStateHandle()) // no movieId key

        assertTrue(vm.uiState.value is MovieDetailUiState.Error)
    }

    // ── Reload ────────────────────────────────────────────────────────────────

    @Test
    fun `Reload intent re-fetches detail from repository`() = runTest {
        val detail = fakeMovieDetail()
        coEvery { repository.getMovieDetail(1) } returns NetworkResult.Success(detail)
        coEvery { repository.getTrailer(1) } returns NetworkResult.Success(null)
        every { repository.observeIsFavorite(1) } returns flowOf(false)

        val vm = createViewModel()
        vm.processIntent(MovieDetailIntent.Reload)

        // Called twice: once on init, once on Reload
        coVerify(exactly = 2) { repository.getMovieDetail(1) }
    }

    // ── Favorite ──────────────────────────────────────────────────────────────

    @Test
    fun `ToggleFavorite calls repository when in Success state`() = runTest {
        coEvery { repository.getMovieDetail(1) } returns NetworkResult.Success(fakeMovieDetail())
        coEvery { repository.getTrailer(1) } returns NetworkResult.Success(null)
        every { repository.observeIsFavorite(1) } returns flowOf(false)
        coJustRun { repository.toggleFavorite(any()) }

        val vm = createViewModel()
        vm.processIntent(MovieDetailIntent.ToggleFavorite)

        coVerify { repository.toggleFavorite(any()) }
    }

    @Test
    fun `isFavorite = true is reflected in Success state`() = runTest {
        coEvery { repository.getMovieDetail(1) } returns NetworkResult.Success(fakeMovieDetail())
        coEvery { repository.getTrailer(1) } returns NetworkResult.Success(null)
        every { repository.observeIsFavorite(1) } returns flowOf(true)

        val vm = createViewModel()

        assertTrue(vm.successState?.isFavorite == true)
    }

    @Test
    fun `isFavorite = false is reflected in Success state`() = runTest {
        coEvery { repository.getMovieDetail(1) } returns NetworkResult.Success(fakeMovieDetail())
        coEvery { repository.getTrailer(1) } returns NetworkResult.Success(null)
        every { repository.observeIsFavorite(1) } returns flowOf(false)

        val vm = createViewModel()

        assertFalse(vm.successState?.isFavorite == true)
    }

    // ── Different movie IDs ───────────────────────────────────────────────────

    @Test
    fun `loads correct movie when movieId is 5`() = runTest {
        val detail = fakeMovieDetail(id = 5)
        coEvery { repository.getMovieDetail(5) } returns NetworkResult.Success(detail)
        coEvery { repository.getTrailer(5) } returns NetworkResult.Success(null)
        every { repository.observeIsFavorite(5) } returns flowOf(false)

        val vm = createViewModel(movieId = 5)

        assertEquals(5, vm.successState?.movie?.id)
        coVerify { repository.getMovieDetail(5) }
    }
}
