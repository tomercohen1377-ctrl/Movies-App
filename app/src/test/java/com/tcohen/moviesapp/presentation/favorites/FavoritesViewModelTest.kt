package com.tcohen.moviesapp.presentation.favorites

import app.cash.turbine.test
import com.tcohen.moviesapp.domain.repository.MovieRepository
import com.tcohen.moviesapp.fakeMovie
import com.tcohen.moviesapp.util.MainDispatcherRule
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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

    private lateinit var viewModel: FavoritesViewModel

    @Before
    fun setUp() {
        every { repository.getFavorites() } returns flowOf(emptyList())
        viewModel = FavoritesViewModel(repository)
    }

    // ── Initial state ─────────────────────────────────────────────────────────

    @Test
    fun `initial state has empty favorites list`() {
        assertTrue(viewModel.state.value.favorites.isEmpty())
    }

    @Test
    fun `initial isEmpty is true`() {
        assertTrue(viewModel.state.value.isEmpty)
    }

    // ── Favorites list observation ────────────────────────────────────────────

    @Test
    fun `favorites list updates when repository emits`() {
        val movies = listOf(fakeMovie(1), fakeMovie(2))
        every { repository.getFavorites() } returns flowOf(movies)

        val vm = FavoritesViewModel(repository)

        assertEquals(2, vm.state.value.favorites.size)
        assertFalse(vm.state.value.isEmpty)
    }

    @Test
    fun `isEmpty is true when list is empty`() {
        every { repository.getFavorites() } returns flowOf(emptyList())
        val vm = FavoritesViewModel(repository)
        assertTrue(vm.state.value.isEmpty)
    }

    @Test
    fun `isEmpty is false when list has items`() {
        every { repository.getFavorites() } returns flowOf(listOf(fakeMovie()))
        val vm = FavoritesViewModel(repository)
        assertFalse(vm.state.value.isEmpty)
    }

    @Test
    fun `state updates reactively when favorites change`() = runTest {
        val favoritesFlow = MutableStateFlow(listOf(fakeMovie(1)))
        every { repository.getFavorites() } returns favoritesFlow

        val vm = FavoritesViewModel(repository)
        assertEquals(1, vm.state.value.favorites.size)

        favoritesFlow.value = listOf(fakeMovie(1), fakeMovie(2))
        assertEquals(2, vm.state.value.favorites.size)
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
    fun `RemoveFavorite calls toggleFavorite when movie exists in list`() = runTest {
        val movie = fakeMovie(id = 3)
        every { repository.getFavorites() } returns flowOf(listOf(movie))
        coJustRun { repository.toggleFavorite(movie) }

        val vm = FavoritesViewModel(repository)
        vm.processIntent(FavoritesIntent.RemoveFavorite(movieId = 3))

        coVerify { repository.toggleFavorite(movie) }
    }

    @Test
    fun `RemoveFavorite is no-op when movie not in list`() = runTest {
        every { repository.getFavorites() } returns flowOf(emptyList())
        coJustRun { repository.toggleFavorite(any()) }

        val vm = FavoritesViewModel(repository)
        vm.processIntent(FavoritesIntent.RemoveFavorite(movieId = 999))

        // toggleFavorite should never be called because movie doesn't exist
        coVerify(exactly = 0) { repository.toggleFavorite(any()) }
    }

    @Test
    fun `favorites list correctly reflects all added items`() {
        val movies = (1..5).map { fakeMovie(it) }
        every { repository.getFavorites() } returns flowOf(movies)
        val vm = FavoritesViewModel(repository)

        assertEquals(5, vm.state.value.favorites.size)
        assertEquals((1..5).toList(), vm.state.value.favorites.map { it.id })
    }
}
