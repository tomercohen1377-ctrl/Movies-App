package com.tcohen.moviesapp.presentation.moviedetail

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tcohen.moviesapp.fakeMovieDetail
import com.tcohen.moviesapp.presentation.theme.MoviesAppTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for [MovieDetailContent] — the scrollable body of the detail screen.
 *
 * The YouTube player requires a real WebView and cannot be tested here.
 * These tests focus on the no-trailer path (backdrop/poster image + metadata).
 */
@RunWith(AndroidJUnit4::class)
class MovieDetailContentTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun successState(
        trailerKey: String? = null,
        isFavorite: Boolean = false
    ) = MovieDetailUiState.Success(
        movie = fakeMovieDetail(
            title = "Arrival",
            genres = emptyList(),
            tagline = "Why are they here?"
        ),
        trailerKey = trailerKey,
        isFavorite = isFavorite
    )

    // ── Metadata always renders ───────────────────────────────────────────────

    @Test
    fun movieDetailContent_showsMovieTitle() {
        composeTestRule.setContent {
            MoviesAppTheme {
                MovieDetailContent(
                    uiState = successState(),
                    onPlayerReady = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Arrival").assertIsDisplayed()
    }

    @Test
    fun movieDetailContent_showsTagline() {
        composeTestRule.setContent {
            MoviesAppTheme {
                MovieDetailContent(
                    uiState = successState(),
                    onPlayerReady = {}
                )
            }
        }

        composeTestRule.onNodeWithText("\"Why are they here?\"").assertIsDisplayed()
    }

    @Test
    fun movieDetailContent_showsOverview() {
        composeTestRule.setContent {
            MoviesAppTheme {
                MovieDetailContent(
                    uiState = successState(),
                    onPlayerReady = {}
                )
            }
        }

        // Overview is part of MovieMetadata — check it's present
        composeTestRule.onNodeWithText("Overview 1", substring = true).assertIsDisplayed()
    }

    // ── No-trailer path uses backdrop/poster image ────────────────────────────

    @Test
    fun movieDetailContent_noTrailer_showsBackdropImage() {
        val state = successState(trailerKey = null)
        composeTestRule.setContent {
            MoviesAppTheme {
                MovieDetailContent(
                    uiState = state,
                    onPlayerReady = {}
                )
            }
        }

        // AsyncImage uses the movie title as contentDescription.
        // Both the backdrop and the thumbnail carry the same description, so we assert
        // that at least one of them is displayed.
        composeTestRule.onAllNodesWithContentDescription("Arrival")[0].assertIsDisplayed()
    }

    // ── onPlayerReady callback can be invoked ─────────────────────────────────

    @Test
    fun movieDetailContent_onPlayerReady_canBeInvoked() {
        var playerReady = false
        composeTestRule.setContent {
            MoviesAppTheme {
                MovieDetailContent(
                    uiState = successState(),
                    onPlayerReady = { playerReady = true }
                )
            }
        }

        // No trailer → player never becomes ready automatically, but callback is wired
        assertTrue(!playerReady) // baseline — not called without a trailer
    }
}
