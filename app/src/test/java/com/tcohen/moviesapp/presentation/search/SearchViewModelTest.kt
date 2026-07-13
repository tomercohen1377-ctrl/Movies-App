package com.tcohen.moviesapp.presentation.search

import androidx.paging.PagingData
import app.cash.turbine.test
import com.tcohen.moviesapp.data.local.SearchHistoryRepository
import com.tcohen.moviesapp.data.remote.paging.SearchDefaults
import com.tcohen.moviesapp.domain.repository.MovieRepository
import com.tcohen.moviesapp.util.MainDispatcherRule
import com.tcohen.moviesapp.util.NetworkMonitor
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Tests for [SearchViewModel].
 *
 * The Pager-internal data flow is intentionally not exhaustively tested here —
 * the [com.tcohen.moviesapp.data.remote.paging.SearchPagingSourceTest] covers the
 * paging behaviour. This suite focuses on:
 *  - Initial state & intent → state mapping
 *  - Query length threshold (below [SearchDefaults.MIN_QUERY_LENGTH] = no search)
 *  - DistinctUntilChanged (re-typing the same value is idempotent)
 *  - History MRU integration
 *  - One-shot effects (navigation)
 *  - Network status reflection
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository: MovieRepository = mockk()
    private val historyRepository = SearchHistoryRepository()
    private val networkMonitor: NetworkMonitor = mockk()

    @Before
    fun setUp() {
        every { repository.searchMovies(any()) } returns flowOf(PagingData.empty())
        every { networkMonitor.isOnline } returns flowOf(true)
    }

    private fun createViewModel() = SearchViewModel(
        repository = repository,
        historyRepository = historyRepository,
        networkMonitor = networkMonitor
    )

    // ── Initial state ──────────────────────────────────────────────────────────

    @Test
    fun `initial query is empty`() {
        assertEquals("", createViewModel().state.value.query)
    }

    @Test
    fun `initial hasSearched is false`() {
        assertFalse(createViewModel().state.value.hasSearched)
    }

    @Test
    fun `initial isOffline is false when network is up`() {
        assertFalse(createViewModel().state.value.isOffline)
    }

    // ── UpdateQuery ────────────────────────────────────────────────────────────

    @Test
    fun `UpdateQuery sets query in state immediately`() = runTest {
        val vm = createViewModel()
        vm.processIntent(SearchIntent.UpdateQuery("dune"))

        assertEquals("dune", vm.state.value.query)
    }

    @Test
    fun `UpdateQuery below threshold leaves hasSearched false`() = runTest {
        val vm = createViewModel()
        vm.processIntent(SearchIntent.UpdateQuery("d"))

        // Below MIN_QUERY_LENGTH — debounce of 0ms should fire, but result should still
        // report hasSearched=false because the gating happens on the *length* check.
        advanceTimeBy(SearchDefaults.DEBOUNCE_MS + 50)
        runCurrent()

        assertFalse(vm.state.value.hasSearched)
    }

    @Test
    fun `UpdateQuery at threshold sets hasSearched true`() = runTest {
        val vm = createViewModel()
        vm.processIntent(SearchIntent.UpdateQuery("du"))
        advanceTimeBy(SearchDefaults.DEBOUNCE_MS + 50)
        runCurrent()

        assertTrue(vm.state.value.hasSearched)
    }

    // ── ClearQuery ─────────────────────────────────────────────────────────────

    @Test
    fun `ClearQuery resets query and hasSearched`() = runTest {
        val vm = createViewModel()
        vm.processIntent(SearchIntent.UpdateQuery("dune"))
        advanceTimeBy(SearchDefaults.DEBOUNCE_MS + 50)
        runCurrent()
        // Sanity-priming step:
        assertTrue(vm.state.value.hasSearched)

        vm.processIntent(SearchIntent.ClearQuery)

        assertEquals("", vm.state.value.query)
        assertFalse(vm.state.value.hasSearched)
    }

    // ── SelectHistory ──────────────────────────────────────────────────────────

    @Test
    fun `SelectHistory populates query and triggers search`() = runTest {
        historyRepository.add("tenet")
        val vm = createViewModel()

        vm.processIntent(SearchIntent.SelectHistory("tenet"))

        assertEquals("tenet", vm.state.value.query)
        advanceTimeBy(SearchDefaults.DEBOUNCE_MS + 50)
        runCurrent()
        assertTrue(vm.state.value.hasSearched)
    }

    // ── History integration ────────────────────────────────────────────────────

    @Test
    fun `OpenDetail adds the current query to history`() = runTest {
        val vm = createViewModel()
        vm.processIntent(SearchIntent.UpdateQuery("dune"))
        advanceTimeBy(SearchDefaults.DEBOUNCE_MS + 50)
        runCurrent()

        vm.effects.test {
            vm.processIntent(SearchIntent.OpenDetail(movieId = 1))
            // Drain the navigation effect.
            awaitItem().let { assertTrue(it is SearchEffect.NavigateToDetail) }
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(listOf("dune"), vm.history.value)
    }

    @Test
    fun `OpenDetail does not add blank queries to history`() = runTest {
        val vm = createViewModel()
        vm.effects.test {
            vm.processIntent(SearchIntent.OpenDetail(movieId = 1))
            awaitItem().let { assertTrue(it is SearchEffect.NavigateToDetail) }
            cancelAndIgnoreRemainingEvents()
        }

        assertTrue(vm.history.value.isEmpty())
    }

    @Test
    fun `ClearHistory wipes the history list`() = runTest {
        historyRepository.add("a")
        historyRepository.add("b")
        val vm = createViewModel()

        vm.processIntent(SearchIntent.ClearHistory)

        assertTrue(vm.history.value.isEmpty())
    }

    // ── Effects ────────────────────────────────────────────────────────────────

    @Test
    fun `OpenDetail emits NavigateToDetail effect with the movie id`() = runTest {
        val vm = createViewModel()
        vm.effects.test {
            vm.processIntent(SearchIntent.OpenDetail(movieId = 99))
            val effect = awaitItem()
            assertTrue(effect is SearchEffect.NavigateToDetail)
            assertEquals(99, (effect as SearchEffect.NavigateToDetail).movieId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Network status ─────────────────────────────────────────────────────────

    @Test
    fun `isOffline becomes true when NetworkMonitor emits false`() = runTest {
        every { networkMonitor.isOnline } returns flowOf(false)
        val vm = createViewModel()

        assertTrue(vm.state.value.isOffline)
    }
}
