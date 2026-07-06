package com.tcohen.moviesapp.presentation.moviedetail

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.tcohen.moviesapp.data.favorites.FavoritesChangeNotifier
import com.tcohen.moviesapp.domain.repository.MovieRepository
import com.tcohen.moviesapp.fakeMovieDetail
import com.tcohen.moviesapp.fakeVideoResult
import com.tcohen.moviesapp.presentation.navigation.Screen
import com.tcohen.moviesapp.util.ApiError
import com.tcohen.moviesapp.util.MainDispatcherRule
import com.tcohen.moviesapp.util.NetworkResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    private val notifier: FavoritesChangeNotifier = mockk(relaxed = true)

    private fun savedStateHandleFor(movieId: Int = 1) =
        SavedStateHandle(mapOf(Screen.MovieDetail.ARG_MOVIE_ID to movieId))

    private fun createViewModel(movieId: Int = 1): MovieDetailViewModel =
        MovieDetailViewModel(repository, notifier, savedStateHandleFor(movieId))

    private val MovieDetailViewModel.successState get() = uiState.value as? MovieDetailUiState.Success
    private val MovieDetailViewModel.errorMessage get() = (uiState.value as? MovieDetailUiState.Error)?.message

    private fun stubMovieDetailOk(id: Int = 1, isFavorite: Boolean = false) {
        coEvery { repository.getMovieDetail(id) } returns NetworkResult.Success(fakeMovieDetail(id))
        coEvery { repository.getTrailer(id) } returns NetworkResult.Success(fakeVideoResult("abc123"))
        coEvery { repository.isFavorite(id) } returns NetworkResult.Success(isFavorite)
    }

    @Test
    fun `successful load produces Success with movie trailer and isFavorite`() = runTest {
        stubMovieDetailOk(id = 1, isFavorite = true)

        val vm = createViewModel()

        val s = vm.successState
        assertNotNull(s)
        assertEquals(fakeMovieDetail(1), s?.movie)
        assertEquals("abc123", s?.trailerKey)
        assertEquals(true, s?.isFavorite)
    }

    @Test
    fun `null trailer produces Success with null trailerKey`() = runTest {
        coEvery { repository.getMovieDetail(1) } returns NetworkResult.Success(fakeMovieDetail())
        coEvery { repository.getTrailer(1) } returns NetworkResult.Success(null)
        coEvery { repository.isFavorite(1) } returns NetworkResult.Success(false)

        val vm = createViewModel()

        assertNull(vm.successState?.trailerKey)
    }

    @Test
    fun `trailer error is treated as no trailer (graceful degradation)`() = runTest {
        coEvery { repository.getMovieDetail(1) } returns NetworkResult.Success(fakeMovieDetail())
        coEvery { repository.getTrailer(1) } returns NetworkResult.Error("Service temporarily unavailable.", 503)
        coEvery { repository.isFavorite(1) } returns NetworkResult.Success(false)

        val vm = createViewModel()

        assertNotNull(vm.successState?.movie)
        assertNull(vm.successState?.trailerKey)
    }

    @Test
    fun `404 detail error produces Error state with not-found message`() = runTest {
        coEvery { repository.getMovieDetail(1) } returns
            NetworkResult.Error("The resource you requested could not be found.", 404)
        coEvery { repository.getTrailer(1) } returns NetworkResult.Success(null)
        coEvery { repository.isFavorite(1) } returns NetworkResult.Success(false)

        val vm = createViewModel()

        assertEquals("The resource you requested could not be found.", vm.errorMessage)
    }

    @Test
    fun `unexpected error produces Error with fallback message`() = runTest {
        coEvery { repository.getMovieDetail(1) } returns NetworkResult.Error(ApiError.UNEXPECTED.message)
        coEvery { repository.getTrailer(1) } returns NetworkResult.Success(null)
        coEvery { repository.isFavorite(1) } returns NetworkResult.Success(false)

        val vm = createViewModel()

        assertEquals(ApiError.UNEXPECTED.message, vm.errorMessage)
    }

    @Test
    fun `missing movieId produces Error state without crashing`() = runTest {
        val vm = MovieDetailViewModel(repository, notifier, SavedStateHandle())

        assertTrue(vm.uiState.value is MovieDetailUiState.Error)
    }

    @Test
    fun `Reload intent re-fetches detail from repository`() = runTest {
        stubMovieDetailOk(id = 1)

        val vm = createViewModel()
        vm.processIntent(MovieDetailIntent.Reload)

        coVerify(exactly = 2) { repository.getMovieDetail(1) }
    }

    @Test
    fun `ToggleFavorite on success updates state to favorited`() = runTest {
        stubMovieDetailOk(id = 1, isFavorite = false)
        coEvery { repository.addFavorite(any()) } returns NetworkResult.Success(Unit)

        val vm = createViewModel()
        vm.processIntent(MovieDetailIntent.ToggleFavorite)

        assertTrue(vm.successState?.isFavorite == true)
        coVerify { repository.addFavorite(any()) }
    }

    @Test
    fun `ToggleFavorite when already favorited calls removeFavorite`() = runTest {
        stubMovieDetailOk(id = 1, isFavorite = true)
        coEvery { repository.removeFavorite(any()) } returns NetworkResult.Success(Unit)

        val vm = createViewModel()
        vm.processIntent(MovieDetailIntent.ToggleFavorite)

        coVerify { repository.removeFavorite(any()) }
        assertFalse(vm.successState?.isFavorite == true)
    }

    @Test
    fun `ToggleFavorite on Error reverts UI state and emits ShowSnackbar effect`() = runTest {
        stubMovieDetailOk(id = 1, isFavorite = false)
        coEvery { repository.addFavorite(any()) } returns NetworkResult.Error("Server down", 503)

        val vm = createViewModel()
        assertEquals(false, vm.successState?.isFavorite)

        vm.effects.test {
            vm.processIntent(MovieDetailIntent.ToggleFavorite)
            val effect = awaitItem()
            assertTrue(effect is MovieDetailEffect.ShowSnackbar)
            assertEquals(
                "Server down",
                (effect as MovieDetailEffect.ShowSnackbar).message,
            )
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(false, vm.successState?.isFavorite)
        coVerify { repository.addFavorite(any()) }
    }

    @Test
    fun `loads correct movie when movieId is 5`() = runTest {
        stubMovieDetailOk(id = 5)

        val vm = createViewModel(movieId = 5)

        assertEquals(5, vm.successState?.movie?.id)
        coVerify { repository.getMovieDetail(5) }
    }

    @Test
    fun `ToggleFavorite on success notifies the favorites change bus`() = runTest {
        stubMovieDetailOk(id = 1, isFavorite = false)
        coEvery { repository.addFavorite(any()) } returns NetworkResult.Success(Unit)

        val vm = createViewModel()
        vm.processIntent(MovieDetailIntent.ToggleFavorite)

        coVerify { notifier.notifyChanged() }
    }

    @Test
    fun `ToggleFavorite on Error does NOT notify the favorites change bus`() = runTest {
        stubMovieDetailOk(id = 1, isFavorite = false)
        coEvery { repository.addFavorite(any()) } returns NetworkResult.Error("down", 503)

        val vm = createViewModel()
        vm.effects.test {
            vm.processIntent(MovieDetailIntent.ToggleFavorite)
            awaitItem() // consume the snackbar effect
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 0) { notifier.notifyChanged() }
    }
}
