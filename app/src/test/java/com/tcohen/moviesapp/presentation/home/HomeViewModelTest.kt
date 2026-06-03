package com.tcohen.moviesapp.presentation.home

import androidx.paging.PagingData
import app.cash.turbine.test
import com.tcohen.moviesapp.domain.model.Category
import com.tcohen.moviesapp.domain.repository.MovieRepository
import com.tcohen.moviesapp.util.MainDispatcherRule
import com.tcohen.moviesapp.util.NetworkMonitor
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository: MovieRepository = mockk()
    private val networkMonitor: NetworkMonitor = mockk()

    private lateinit var viewModel: HomeViewModel

    @Before
    fun setUp() {
        every { repository.getMovies(any()) } returns flowOf(PagingData.empty())
        every { networkMonitor.isOnline } returns flowOf(true)
        viewModel = HomeViewModel(repository, networkMonitor)
    }

    // ── Initial state ─────────────────────────────────────────────────────────

    @Test
    fun `initial selectedCategory is NOW_PLAYING`() {
        assertEquals(Category.NOW_PLAYING, viewModel.state.value.selectedCategory)
    }

    @Test
    fun `initial isOffline is false when network is available`() {
        assertFalse(viewModel.state.value.isOffline)
    }

    // ── SelectCategory ────────────────────────────────────────────────────────

    @Test
    fun `SelectCategory updates selectedCategory in state`() {
        viewModel.processIntent(HomeIntent.SelectCategory(Category.TOP_RATED))
        assertEquals(Category.TOP_RATED, viewModel.state.value.selectedCategory)
    }

    @Test
    fun `SelectCategory to UPCOMING updates state correctly`() {
        viewModel.processIntent(HomeIntent.SelectCategory(Category.UPCOMING))
        assertEquals(Category.UPCOMING, viewModel.state.value.selectedCategory)
    }

    @Test
    fun `SelectCategory multiple times keeps last selected`() {
        viewModel.processIntent(HomeIntent.SelectCategory(Category.TOP_RATED))
        viewModel.processIntent(HomeIntent.SelectCategory(Category.NOW_PLAYING))
        assertEquals(Category.NOW_PLAYING, viewModel.state.value.selectedCategory)
    }

    // ── OpenDetail ────────────────────────────────────────────────────────────

    @Test
    fun `OpenDetail emits NavigateToDetail effect with correct id`() = runTest {
        viewModel.effects.test {
            viewModel.processIntent(HomeIntent.OpenDetail(movieId = 42))
            val effect = awaitItem()
            assertTrue(effect is HomeEffect.NavigateToDetail)
            assertEquals(42, (effect as HomeEffect.NavigateToDetail).movieId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `OpenDetail emits correct movieId for different movies`() = runTest {
        viewModel.effects.test {
            viewModel.processIntent(HomeIntent.OpenDetail(movieId = 99))
            assertEquals(99, (awaitItem() as HomeEffect.NavigateToDetail).movieId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Network status ────────────────────────────────────────────────────────

    @Test
    fun `isOffline is true when NetworkMonitor emits false`() {
        every { networkMonitor.isOnline } returns flowOf(false)
        val vm = HomeViewModel(repository, networkMonitor)
        assertTrue(vm.state.value.isOffline)
    }

    @Test
    fun `isOffline is false when NetworkMonitor emits true`() {
        every { networkMonitor.isOnline } returns flowOf(true)
        val vm = HomeViewModel(repository, networkMonitor)
        assertFalse(vm.state.value.isOffline)
    }
}
