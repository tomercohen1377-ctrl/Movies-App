package com.tcohen.moviesapp.presentation.favorites

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tcohen.moviesapp.fakeMovie
import com.tcohen.moviesapp.presentation.common.ErrorView
import com.tcohen.moviesapp.presentation.common.MovieCard
import com.tcohen.moviesapp.presentation.common.MovieGrid
import com.tcohen.moviesapp.presentation.common.OfflineBanner
import com.tcohen.moviesapp.presentation.theme.MoviesAppTheme
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end flow tests for the Favorites screen user journey.
 *
 * FavoritesScreen itself requires Hilt injection, so these tests drive the stateless
 * composable components — simulating the empty → populated → remove flow.
 */
@RunWith(AndroidJUnit4::class)
class FavoritesFlowTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ── Empty state flow ──────────────────────────────────────────────────────

    @Test
    fun favoritesFlow_emptyStateShowsHeadline() {
        composeTestRule.setContent {
            MoviesAppTheme {
                EmptyFavoritesState()
            }
        }

        composeTestRule.onNodeWithText("No saved movies yet").assertIsDisplayed()
    }

    @Test
    fun favoritesFlow_emptyStateShowsHint() {
        composeTestRule.setContent {
            MoviesAppTheme {
                EmptyFavoritesState()
            }
        }

        composeTestRule
            .onNodeWithText("Tap the heart on any movie to save it here")
            .assertIsDisplayed()
    }

    // ── Populated favorites flow ──────────────────────────────────────────────

    @Test
    fun favoritesFlow_favoritedMovieTitleIsDisplayed() {
        val movie = fakeMovie(title = "Interstellar")
        composeTestRule.setContent {
            MoviesAppTheme {
                val pagingItems = flowOf(PagingData.from(listOf(movie))).collectAsLazyPagingItems()
                MovieGrid(movies = pagingItems) { item ->
                    if (item != null) MovieCard(movie = item, onClick = {})
                }
            }
        }

        composeTestRule.onNodeWithText("Interstellar").assertIsDisplayed()
    }

    @Test
    fun favoritesFlow_clickingFavoritedMovieInvokesCallback() {
        val movie = fakeMovie(title = "Gravity")
        var clicked = false
        composeTestRule.setContent {
            MoviesAppTheme {
                val pagingItems = flowOf(PagingData.from(listOf(movie))).collectAsLazyPagingItems()
                MovieGrid(movies = pagingItems) { item ->
                    if (item != null) MovieCard(movie = item, onClick = { clicked = true })
                }
            }
        }

        composeTestRule.onNodeWithText("Gravity").performClick()
        assertTrue(clicked)
    }

    @Test
    fun favoritesFlow_multipleFavoritedMoviesAllVisible() {
        val movies = listOf(
            fakeMovie(id = 1, title = "The Prestige"),
            fakeMovie(id = 2, title = "Memento"),
            fakeMovie(id = 3, title = "Dunkirk")
        )
        composeTestRule.setContent {
            MoviesAppTheme {
                val pagingItems = flowOf(PagingData.from(movies)).collectAsLazyPagingItems()
                MovieGrid(movies = pagingItems) { item ->
                    if (item != null) MovieCard(movie = item, onClick = {})
                }
            }
        }

        composeTestRule.onNodeWithText("The Prestige").assertIsDisplayed()
        composeTestRule.onNodeWithText("Memento").assertIsDisplayed()
        composeTestRule.onNodeWithText("Dunkirk").assertIsDisplayed()
    }

    // ── Error state flow ──────────────────────────────────────────────────────

    @Test
    fun favoritesFlow_errorStateShowsMessage() {
        composeTestRule.setContent {
            MoviesAppTheme {
                ErrorView(
                    message = "Failed to load favorites",
                    onRetry = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Failed to load favorites").assertIsDisplayed()
    }

    @Test
    fun favoritesFlow_retryCallbackFiredFromErrorState() {
        var retried = false
        composeTestRule.setContent {
            MoviesAppTheme {
                ErrorView(
                    message = "Failed to load favorites",
                    onRetry = { retried = true }
                )
            }
        }

        composeTestRule.onNodeWithText("Try Again").performClick()
        assertTrue(retried)
    }

    // ── Offline state flow ────────────────────────────────────────────────────

    @Test
    fun favoritesFlow_offlineBannerShownWhenOffline() {
        composeTestRule.setContent {
            MoviesAppTheme {
                OfflineBanner(isOffline = true)
            }
        }

        composeTestRule.onNodeWithText("You're offline").assertIsDisplayed()
    }

    @Test
    fun favoritesFlow_offlineBannerHiddenWhenOnline() {
        composeTestRule.setContent {
            MoviesAppTheme {
                OfflineBanner(isOffline = false)
            }
        }

        composeTestRule.onNodeWithText("You're offline").assertDoesNotExist()
    }

    // ── State transitions ─────────────────────────────────────────────────────

    @Test
    fun favoritesFlow_transitionFromEmptyToPopulated() {
        // State lives outside setContent so runOnIdle mutations trigger recomposition
        var movies by mutableStateOf(emptyList<com.tcohen.moviesapp.domain.model.Movie>())

        composeTestRule.setContent {
            MoviesAppTheme {
                if (movies.isEmpty()) {
                    EmptyFavoritesState()
                } else {
                    val pagingItems = flowOf(PagingData.from(movies)).collectAsLazyPagingItems()
                    MovieGrid(movies = pagingItems) { item ->
                        if (item != null) MovieCard(movie = item, onClick = {})
                    }
                }
            }
        }

        // Verify empty state is shown first
        composeTestRule.onNodeWithText("No saved movies yet").assertIsDisplayed()

        // Simulate a favorite being added (outside setContent → triggers recomposition)
        composeTestRule.runOnIdle {
            movies = listOf(fakeMovie(title = "Parasite"))
        }

        composeTestRule.onNodeWithText("Parasite").assertIsDisplayed()
    }
}
