package com.tcohen.moviesapp.presentation.favorites

import androidx.paging.PagingData
import app.cash.turbine.test
import com.tcohen.moviesapp.domain.repository.MovieRepository
import com.tcohen.moviesapp.fakeMovie
import com.tcohen.moviesapp.util.MainDispatcherRule
import com.tcohen.moviesapp.util.NetworkMonitor
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FavoritesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository: MovieRepository = mockk()
    private val networkMonitor: NetworkMonitor = mockk()

    private lateinit var viewModel: FavoritesViewModel

    @Before
    fun setUp() {
        // favoriteChanges drives flatMapLatest in the ViewModel.
        every { repository.favoriteChanges } returns flowOf()
        every { repository.getFavorites() } returns flowOf(PagingData.empty())
        // Default: online
        every { networkMonitor.isOnline } returns flowOf(true)
        viewModel = FavoritesViewModel(repository, networkMonitor)
    }

    // ── OpenDetail ────────────────────────────────────────────────────────────

    @Test
    fun `OpenDetail emits NavigateToDetail effect with correct movieId`() = runTest {
        viewModel.effects.test {
            viewModel.processIntent(FavoritesIntent.OpenDetail(movieId = 7))
            val effect = awaitItem()
            assertTrue(effect is FavoritesEffect.NavigateToDetail)
            assertEquals(7, (effect as FavoritesEffect.NavigateToDetail).movieId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── RemoveFavorite ────────────────────────────────────────────────────────

    @Test
    fun `RemoveFavorite calls toggleFavorite with the provided movie`() = runTest {
        val movie = fakeMovie(id = 3)
        coJustRun { repository.toggleFavorite(movie) }

        viewModel.processIntent(FavoritesIntent.RemoveFavorite(movie))

        coVerify { repository.toggleFavorite(movie) }
    }

    @Test
    fun `RemoveFavorite calls toggleFavorite exactly once per intent`() = runTest {
        val movie = fakeMovie(id = 5)
        coJustRun { repository.toggleFavorite(movie) }

        viewModel.processIntent(FavoritesIntent.RemoveFavorite(movie))

        coVerify(exactly = 1) { repository.toggleFavorite(movie) }
    }

    // ── Offline state ─────────────────────────────────────────────────────────

    @Test
    fun `isOffline is false when network is available`() = runTest {
        assertFalse(viewModel.state.value.isOffline)
    }

    @Test
    fun `isOffline becomes true when network is lost`() = runTest {
        every { networkMonitor.isOnline } returns flowOf(false)
        val vm = FavoritesViewModel(repository, networkMonitor)
        assertTrue(vm.state.value.isOffline)
    }
}
