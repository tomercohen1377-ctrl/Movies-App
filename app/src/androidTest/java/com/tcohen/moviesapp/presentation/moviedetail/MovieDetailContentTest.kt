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

        composeTestRule.onNodeWithText("Overview 1", substring = true).assertIsDisplayed()
    }

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

        composeTestRule.onAllNodesWithContentDescription("Arrival")[0].assertIsDisplayed()
    }

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

        assertTrue(!playerReady)
    }
}
