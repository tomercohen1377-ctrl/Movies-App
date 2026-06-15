package com.tcohen.moviesapp.presentation.home

import app.cash.turbine.test
import com.tcohen.moviesapp.domain.model.Category
import com.tcohen.moviesapp.util.MainDispatcherRule
import com.tcohen.moviesapp.util.NetworkStatusProvider
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Unit tests for [HomeStateHolder].
 *
 * The ViewModel is a thin wrapper; all logic lives in the shared state holder.
 * Tests create the holder with a [CoroutineScope] backed by [MainDispatcherRule]'s
 * [UnconfinedTestDispatcher] so coroutines run eagerly and state is immediately observable.
 */
class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val networkMonitor: NetworkStatusProvider = mockk()

    private lateinit var stateHolder: HomeStateHolder

    private fun makeHolder(): HomeStateHolder = HomeStateHolder(
        networkMonitor,
        CoroutineScope(mainDispatcherRule.testDispatcher + SupervisorJob())
    )

    @Before
    fun setUp() {
        every { networkMonitor.isOnline } returns flowOf(true)
        stateHolder = makeHolder()
    }

    // ── Initial state ─────────────────────────────────────────────────────────

    @Test
    fun `initial selectedCategory is NOW_PLAYING`() {
        assertEquals(Category.NOW_PLAYING, stateHolder.state.value.selectedCategory)
    }

    @Test
    fun `initial isOffline is false when network is available`() {
        assertFalse(stateHolder.state.value.isOffline)
    }

    // ── SelectCategory ────────────────────────────────────────────────────────

    @Test
    fun `SelectCategory updates selectedCategory in state`() {
        stateHolder.processIntent(HomeIntent.SelectCategory(Category.TOP_RATED))
        assertEquals(Category.TOP_RATED, stateHolder.state.value.selectedCategory)
    }

    @Test
    fun `SelectCategory to UPCOMING updates state correctly`() {
        stateHolder.processIntent(HomeIntent.SelectCategory(Category.UPCOMING))
        assertEquals(Category.UPCOMING, stateHolder.state.value.selectedCategory)
    }

    @Test
    fun `SelectCategory multiple times keeps last selected`() {
        stateHolder.processIntent(HomeIntent.SelectCategory(Category.TOP_RATED))
        stateHolder.processIntent(HomeIntent.SelectCategory(Category.NOW_PLAYING))
        assertEquals(Category.NOW_PLAYING, stateHolder.state.value.selectedCategory)
    }

    // ── OpenDetail ────────────────────────────────────────────────────────────

    @Test
    fun `OpenDetail emits NavigateToDetail effect with correct id`() = runTest {
        stateHolder.effects.test {
            stateHolder.processIntent(HomeIntent.OpenDetail(movieId = 42))
            val effect = awaitItem()
            assertTrue(effect is HomeEffect.NavigateToDetail)
            assertEquals(42, (effect as HomeEffect.NavigateToDetail).movieId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `OpenDetail emits correct movieId for different movies`() = runTest {
        stateHolder.effects.test {
            stateHolder.processIntent(HomeIntent.OpenDetail(movieId = 99))
            assertEquals(99, (awaitItem() as HomeEffect.NavigateToDetail).movieId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Network status ────────────────────────────────────────────────────────

    @Test
    fun `isOffline is true when NetworkMonitor emits false`() {
        every { networkMonitor.isOnline } returns flowOf(false)
        val sh = makeHolder()
        assertTrue(sh.state.value.isOffline)
    }

    @Test
    fun `isOffline is false when NetworkMonitor emits true`() {
        every { networkMonitor.isOnline } returns flowOf(true)
        val sh = makeHolder()
        assertFalse(sh.state.value.isOffline)
    }
}
