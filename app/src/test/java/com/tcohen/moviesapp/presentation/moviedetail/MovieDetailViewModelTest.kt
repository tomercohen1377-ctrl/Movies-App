package com.tcohen.moviesapp.presentation.moviedetail

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.tcohen.moviesapp.domain.repository.MovieRepository
import com.tcohen.moviesapp.fakeMovie
import com.tcohen.moviesapp.fakeMovieDetail
import com.tcohen.moviesapp.fakeVideoResult
import com.tcohen.moviesapp.presentation.navigation.Screen
import com.tcohen.moviesapp.util.MainDispatcherRule
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

    private fun createViewModel(movieId: Int = 1): MovieDetailViewModel {
        return MovieDetailViewModel(repository, savedStateHandleFor(movieId))
    }

    // ── Successful load ───────────────────────────────────────────────────────

    @Test
    fun `successful load populates movie and clears loading`() = runTest {
        val detail = fakeMovieDetail(id = 1)
        val trailer = fakeVideoResult("abc123")
        coEvery { repository.getMovieDetail(1) } returns detail
        coEvery { repository.getTrailer(1) } returns trailer
        every { repository.isFavorite(1) } returns flowOf(false)

        val vm = createViewModel()

        assertFalse(vm.state.value.isLoading)
        assertEquals(detail, vm.state.value.movie)
        assertEquals("abc123", vm.state.value.trailerKey)
        assertNull(vm.state.value.error)
    }

    @Test
    fun `successful load with no trailer sets trailerKey to null`() = runTest {
        coEvery { repository.getMovieDetail(1) } returns fakeMovieDetail()
        coEvery { repository.getTrailer(1) } returns null
        every { repository.isFavorite(1) } returns flowOf(false)

        val vm = createViewModel()

        assertNull(vm.state.value.trailerKey)
    }

    // ── Error handling ────────────────────────────────────────────────────────

    @Test
    fun `load failure sets error and clears loading`() = runTest {
        coEvery { repository.getMovieDetail(1) } throws RuntimeException("Network error")
        coEvery { repository.getTrailer(1) } returns null
        every { repository.isFavorite(1) } returns flowOf(false)

        val vm = createViewModel()

        assertFalse(vm.state.value.isLoading)
        assertNotNull(vm.state.value.error)
        assertEquals("Network error", vm.state.value.error)
        assertNull(vm.state.value.movie)
    }

    @Test
    fun `Reload intent re-triggers loading`() = runTest {
        val detail = fakeMovieDetail()
        coEvery { repository.getMovieDetail(1) } returns detail
        coEvery { repository.getTrailer(1) } returns null
        every { repository.isFavorite(1) } returns flowOf(false)

        val vm = createViewModel()
        vm.processIntent(MovieDetailIntent.Reload)

        // Detail called twice: once on init, once on Reload
        coVerify(exactly = 2) { repository.getMovieDetail(1) }
    }

    // ── Favorite ─────────���────────────────────────────────────────────────────

    @Test
    fun `ToggleFavorite calls repository when movie is loaded`() = runTest {
        coEvery { repository.getMovieDetail(1) } returns fakeMovieDetail()
        coEvery { repository.getTrailer(1) } returns null
        every { repository.isFavorite(1) } returns flowOf(false)
        coJustRun { repository.toggleFavorite(any()) }

        val vm = createViewModel()
        vm.processIntent(MovieDetailIntent.ToggleFavorite)

        coVerify { repository.toggleFavorite(any()) }
    }

    @Test
    fun `isFavorite true is reflected in state`() = runTest {
        coEvery { repository.getMovieDetail(1) } returns fakeMovieDetail()
        coEvery { repository.getTrailer(1) } returns null
        every { repository.isFavorite(1) } returns flowOf(true)

        val vm = createViewModel()

        assertTrue(vm.state.value.isFavorite)
    }

    @Test
    fun `isFavorite false is reflected in state`() = runTest {
        coEvery { repository.getMovieDetail(1) } returns fakeMovieDetail()
        coEvery { repository.getTrailer(1) } returns null
        every { repository.isFavorite(1) } returns flowOf(false)

        val vm = createViewModel()

        assertFalse(vm.state.value.isFavorite)
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    @Test
    fun `NavigateBack intent emits NavigateBack effect`() = runTest {
        coEvery { repository.getMovieDetail(1) } returns fakeMovieDetail()
        coEvery { repository.getTrailer(1) } returns null
        every { repository.isFavorite(1) } returns flowOf(false)

        val vm = createViewModel()

        vm.effects.test {
            vm.processIntent(MovieDetailIntent.NavigateBack)
            assertEquals(MovieDetailEffect.NavigateBack, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Different movie IDs ───────────────────────────────────────────────────

    @Test
    fun `loads correct movie when movieId is 5`() = runTest {
        val detail = fakeMovieDetail(id = 5)
        coEvery { repository.getMovieDetail(5) } returns detail
        coEvery { repository.getTrailer(5) } returns null
        every { repository.isFavorite(5) } returns flowOf(false)

        val vm = createViewModel(movieId = 5)

        assertEquals(5, vm.state.value.movie?.id)
        coVerify { repository.getMovieDetail(5) }
    }
}
