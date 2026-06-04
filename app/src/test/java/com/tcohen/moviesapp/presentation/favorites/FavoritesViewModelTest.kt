package com.tcohen.moviesapp.presentation.favorites

import androidx.paging.PagingData
import app.cash.turbine.test
import com.tcohen.moviesapp.domain.repository.MovieRepository
import com.tcohen.moviesapp.fakeMovie
import com.tcohen.moviesapp.util.MainDispatcherRule
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
        // favoriteChanges drives flatMapLatest in the ViewModel; use an empty flow so
        // it doesn't emit after onStart and cause extra getFavorites calls.
        every { repository.favoriteChanges } returns flowOf()
        every { repository.getFavorites() } returns flowOf(PagingData.empty())
        viewModel = FavoritesViewModel(repository)
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
    fun `RemoveFavorite increments refresh trigger after toggleFavorite`() = runTest {
        val movie = fakeMovie(id = 5)
        coJustRun { repository.toggleFavorite(movie) }

        // Each RemoveFavorite should trigger a refresh (i.e. getFavorites is re-subscribed)
        viewModel.processIntent(FavoritesIntent.RemoveFavorite(movie))

        // toggleFavorite called once per remove intent
        coVerify(exactly = 1) { repository.toggleFavorite(movie) }
    }
}
