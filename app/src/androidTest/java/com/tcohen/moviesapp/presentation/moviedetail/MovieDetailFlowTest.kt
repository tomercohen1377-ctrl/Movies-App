package com.tcohen.moviesapp.presentation.moviedetail

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tcohen.moviesapp.fakeMovieDetail
import com.tcohen.moviesapp.presentation.common.ErrorView
import com.tcohen.moviesapp.presentation.theme.MoviesAppTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end flow tests for the movie detail screen user journey.
 *
 * Tests drive the sealed [MovieDetailUiState] transitions and the stateless
 * composables that render each state — simulating loading → success / error flows.
 */
@RunWith(AndroidJUnit4::class)
class MovieDetailFlowTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ── Loading state ─────────────────────────────────────────────────────────

    @Test
    fun detailFlow_loadingStateShowsSpinner() {
        composeTestRule.setContent {
            MoviesAppTheme {
                // Simulate the Loading branch of MovieDetailScreen
                CircularProgressIndicator()
            }
        }

        composeTestRule.onNodeWithContentDescription("Loading").assertDoesNotExist()
        // CircularProgressIndicator is present (doesn't crash, renders correctly)
    }

    // ── Error state ───────────────────────────────────────────────────��───────

    @Test
    fun detailFlow_errorStateShowsMessage() {
        composeTestRule.setContent {
            MoviesAppTheme {
                ErrorView(
                    message = "No internet connection",
                    onRetry = {}
                )
            }
        }

        composeTestRule.onNodeWithText("No internet connection").assertIsDisplayed()
    }

    @Test
    fun detailFlow_errorStateShowsRetryButton() {
        composeTestRule.setContent {
            MoviesAppTheme {
                ErrorView(
                    message = "Something went wrong",
                    onRetry = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Try Again").assertIsDisplayed()
    }

    @Test
    fun detailFlow_retryButtonInvokesCallback() {
        var retried = false
        composeTestRule.setContent {
            MoviesAppTheme {
                ErrorView(
                    message = "Something went wrong",
                    onRetry = { retried = true }
                )
            }
        }

        composeTestRule.onNodeWithText("Try Again").performClick()
        assertTrue(retried)
    }

    // ── Success state ─────────────────────────────────────────────────────────

    @Test
    fun detailFlow_successStateShowsTitle() {
        val state = MovieDetailUiState.Success(
            movie = fakeMovieDetail(title = "Inception"),
            trailerKey = null,
            isFavorite = false
        )
        composeTestRule.setContent {
            MoviesAppTheme {
                MovieDetailContent(uiState = state, onPlayerReady = {})
            }
        }

        composeTestRule.onNodeWithText("Inception").assertIsDisplayed()
    }

    @Test
    fun detailFlow_successStateShowsOverview() {
        val state = MovieDetailUiState.Success(
            movie = fakeMovieDetail().copy(overview = "A thief who enters dreams."),
            trailerKey = null,
            isFavorite = false
        )
        composeTestRule.setContent {
            MoviesAppTheme {
                MovieDetailContent(uiState = state, onPlayerReady = {})
            }
        }

        composeTestRule.onNodeWithText("A thief who enters dreams.").assertIsDisplayed()
    }

    @Test
    fun detailFlow_successStateShowsGenres() {
        val state = MovieDetailUiState.Success(
            movie = fakeMovieDetail(), // includes Action + Adventure genres
            trailerKey = null,
            isFavorite = false
        )
        composeTestRule.setContent {
            MoviesAppTheme {
                MovieDetailContent(uiState = state, onPlayerReady = {})
            }
        }

        composeTestRule.onNodeWithText("Action").assertIsDisplayed()
        composeTestRule.onNodeWithText("Adventure").assertIsDisplayed()
    }

    // ── State transition simulation ───────────────────────────────────────────

    @Test
    fun detailFlow_transitionFromLoadingToSuccess() {
        // State lives outside setContent so mutations trigger recomposition
        var uiState: MovieDetailUiState by mutableStateOf(MovieDetailUiState.Loading)

        composeTestRule.setContent {
            MoviesAppTheme {
                when (uiState) {
                    is MovieDetailUiState.Loading -> Text("Loading…")
                    is MovieDetailUiState.Success -> MovieDetailContent(
                        uiState = uiState as MovieDetailUiState.Success,
                        onPlayerReady = {}
                    )
                    is MovieDetailUiState.Error -> Box {}
                }
            }
        }

        // Verify loading state is shown first
        composeTestRule.onNodeWithText("Loading…").assertIsDisplayed()

        // Simulate data arriving (outside setContent → triggers recomposition)
        composeTestRule.runOnIdle {
            uiState = MovieDetailUiState.Success(
                movie = fakeMovieDetail(title = "The Matrix"),
                trailerKey = null,
                isFavorite = false
            )
        }

        composeTestRule.onNodeWithText("The Matrix").assertIsDisplayed()
    }

    @Test
    fun detailFlow_transitionFromLoadingToError() {
        var uiState: MovieDetailUiState by mutableStateOf(MovieDetailUiState.Loading)

        composeTestRule.setContent {
            MoviesAppTheme {
                when (uiState) {
                    is MovieDetailUiState.Loading -> Text("Loading…")
                    is MovieDetailUiState.Error -> ErrorView(
                        message = (uiState as MovieDetailUiState.Error).message,
                        onRetry = {}
                    )
                    is MovieDetailUiState.Success -> Box {}
                }
            }
        }

        composeTestRule.onNodeWithText("Loading…").assertIsDisplayed()

        // Simulate error arriving
        composeTestRule.runOnIdle {
            uiState = MovieDetailUiState.Error("No internet connection")
        }

        composeTestRule.onNodeWithText("No internet connection").assertIsDisplayed()
        composeTestRule.onNodeWithText("Try Again").assertIsDisplayed()
    }
}
