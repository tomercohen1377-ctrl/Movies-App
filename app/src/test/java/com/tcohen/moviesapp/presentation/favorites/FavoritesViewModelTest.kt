package com.tcohen.moviesapp.presentation.favorites

import app.cash.turbine.test
import com.tcohen.moviesapp.domain.repository.MovieRepositoryBase
import com.tcohen.moviesapp.fakeMovie
import com.tcohen.moviesapp.util.MainDispatcherRule
import com.tcohen.moviesapp.util.NetworkStatusProvider
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Unit tests for [FavoritesStateHolder].
 *
 * Tests use [MovieRepositoryBase] (the shared interface) since the state holder
 * only needs non-paging operations. Paging tests belong at the ViewModel layer.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FavoritesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository: MovieRepositoryBase = mockk()
    private val networkMonitor: NetworkStatusProvider = mockk()

    private lateinit var stateHolder: FavoritesStateHolder

    private fun makeHolder(): FavoritesStateHolder = FavoritesStateHolder(
        repository,
        networkMonitor,
        CoroutineScope(mainDispatcherRule.testDispatcher + SupervisorJob())
    )

    @Before
    fun setUp() {
        every { networkMonitor.isOnline } returns flowOf(true)
        stateHolder = makeHolder()
    }

    // ── OpenDetail ────────────────────────────────────────────────────────────

    @Test
    fun `OpenDetail emits NavigateToDetail effect with correct movieId`() = runTest {
        stateHolder.effects.test {
            stateHolder.processIntent(FavoritesIntent.OpenDetail(movieId = 7))
            val effect = awaitItem()
            assertTrue(effect is FavoritesEffect.NavigateToDetail)
            assertEquals(7, (effect as FavoritesEffect.NavigateToDetail).movieId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── RemoveFavorite ───────────────────────────────────────���────────────────

    @Test
    fun `RemoveFavorite calls toggleFavorite with the provided movie`() = runTest {
        val movie = fakeMovie(id = 3)
        coJustRun { repository.toggleFavorite(movie) }

        stateHolder.processIntent(FavoritesIntent.RemoveFavorite(movie))

        coVerify { repository.toggleFavorite(movie) }
    }

    @Test
    fun `RemoveFavorite calls toggleFavorite exactly once per intent`() = runTest {
        val movie = fakeMovie(id = 5)
        coJustRun { repository.toggleFavorite(movie) }

        stateHolder.processIntent(FavoritesIntent.RemoveFavorite(movie))

        coVerify(exactly = 1) { repository.toggleFavorite(movie) }
    }

    // ── Offline state ─────────────────────────────────────────────────────────

    @Test
    fun `isOffline is false when network is available`() = runTest {
        assertFalse(stateHolder.state.value.isOffline)
    }

    @Test
    fun `isOffline becomes true when network is lost`() = runTest {
        every { networkMonitor.isOnline } returns flowOf(false)
        val sh = makeHolder()
        assertTrue(sh.state.value.isOffline)
    }
}
