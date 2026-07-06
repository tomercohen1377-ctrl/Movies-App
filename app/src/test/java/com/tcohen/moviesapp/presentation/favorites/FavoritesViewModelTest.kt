package com.tcohen.moviesapp.presentation.favorites

import app.cash.turbine.test
import com.tcohen.moviesapp.data.favorites.FavoritesChangeNotifier
import com.tcohen.moviesapp.domain.repository.MovieRepository
import com.tcohen.moviesapp.fakeMovie
import com.tcohen.moviesapp.util.MainDispatcherRule
import com.tcohen.moviesapp.util.NetworkResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FavoritesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository: MovieRepository = mockk(relaxed = true)
    private val notifier: FavoritesChangeNotifier = FavoritesChangeNotifier()

    /**
     * Construct a VM and let its init blocks complete — init triggers
     * a refresh, the bus listener is wired up, and the test scope
     * advances until everything pending is done.
     */
    private fun TestScope.createViewModelAndInit(): FavoritesViewModel =
        FavoritesViewModel(repository = repository, notifier = notifier)
            .also { advanceUntilIdle() }

    @Test
    fun `init refreshes from the server`() = runTest {
        coEvery { repository.getFavorites() } returns NetworkResult.Success(
            listOf(fakeMovie(id = 1), fakeMovie(id = 2))
        )
        createViewModelAndInit()
        coVerify(exactly = 1) { repository.getFavorites() }
    }

    @Test
    fun `init emits Empty when server returns empty list`() = runTest {
        coEvery { repository.getFavorites() } returns NetworkResult.Success(emptyList())
        val vm = createViewModelAndInit()
        assertTrue(vm.state.value is FavoritesState.Empty)
    }

    @Test
    fun `init emits Error preserving httpCode and message`() = runTest {
        coEvery { repository.getFavorites() } returns NetworkResult.Error("offline", httpCode = 0)
        val vm = createViewModelAndInit()
        val state = vm.state.value
        assertTrue("expected Error, got $state", state is FavoritesState.Error)
        state as FavoritesState.Error
        assertEquals(0, state.httpCode)
        assertEquals("offline", state.message)
    }

    @Test
    fun `RemoveFavorite on success calls removeFavorite and notifies and refreshes`() = runTest {
        coEvery { repository.getFavorites() } returns NetworkResult.Success(listOf(fakeMovie(id = 1)))
        coEvery { repository.removeFavorite(any()) } returns NetworkResult.Success(Unit)
        val vm = createViewModelAndInit()

        vm.processIntent(FavoritesIntent.RemoveFavorite(fakeMovie(id = 1)))

        coVerify { repository.removeFavorite(any()) }
        // init: 1, refresh via notifier: 1 → 2 total
        coVerify(exactly = 2) { repository.getFavorites() }
    }

    @Test
    fun `RemoveFavorite on Error emits ShowSnackbar and does NOT refresh`() = runTest {
        coEvery { repository.getFavorites() } returns NetworkResult.Success(listOf(fakeMovie(id = 1)))
        coEvery { repository.removeFavorite(any()) } returns NetworkResult.Error("Server down", 503)
        val vm = createViewModelAndInit()
        coVerify(exactly = 1) { repository.getFavorites() }

        vm.effects.test {
            vm.processIntent(FavoritesIntent.RemoveFavorite(fakeMovie(id = 1)))
            val effect = awaitItem()
            assertTrue(effect is FavoritesEffect.ShowSnackbar)
            assertEquals("Server down", (effect as FavoritesEffect.ShowSnackbar).message)
            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 1) { repository.getFavorites() }
    }

    @Test
    fun `notifier changes trigger a refresh from the server`() = runTest {
        coEvery { repository.getFavorites() } returnsMany listOf(
            NetworkResult.Success(emptyList()),
            NetworkResult.Success(listOf(fakeMovie(id = 99))),
        )
        val vm = createViewModelAndInit()
        assertTrue(vm.state.value is FavoritesState.Empty)

        // Drive a server-side change — the path MovieDetailVM uses after
        // a successful toggle.
        notifier.notifyChanged()
        advanceUntilIdle()

        assertTrue(
            "expected Success with movie 99, got ${vm.state.value}",
            vm.state.value is FavoritesState.Success,
        )
        coVerify(exactly = 2) { repository.getFavorites() }
    }

    @Test
    fun `OpenDetail emits NavigateToDetail with the same movieId`() = runTest {
        coEvery { repository.getFavorites() } returns NetworkResult.Success(emptyList())
        val vm = createViewModelAndInit()

        vm.effects.test {
            vm.processIntent(FavoritesIntent.OpenDetail(movieId = 7))
            val effect = awaitItem()
            assertTrue(effect is FavoritesEffect.NavigateToDetail)
            assertEquals(7, (effect as FavoritesEffect.NavigateToDetail).movieId)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
