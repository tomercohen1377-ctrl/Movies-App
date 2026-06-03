package com.tcohen.moviesapp.presentation.common

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tcohen.moviesapp.fakeMovie
import com.tcohen.moviesapp.presentation.theme.MoviesAppTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MovieCardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ── Title display ─────────────────────────────────────────────────────────

    @Test
    fun movieCard_displaysMovieTitle() {
        val movie = fakeMovie(title = "Inception")
        composeTestRule.setContent {
            MoviesAppTheme {
                MovieCard(movie = movie, onClick = {})
            }
        }

        composeTestRule.onNodeWithText("Inception").assertIsDisplayed()
    }

    @Test
    fun movieCard_displaysRating() {
        val movie = fakeMovie(voteAverage = 8.5)
        composeTestRule.setContent {
            MoviesAppTheme {
                MovieCard(movie = movie, onClick = {})
            }
        }

        // RatingBadge formats voteAverage to one decimal place
        composeTestRule.onNodeWithText("8.5").assertIsDisplayed()
    }

    @Test
    fun movieCard_displaysLowRating() {
        val movie = fakeMovie(voteAverage = 3.0)
        composeTestRule.setContent {
            MoviesAppTheme {
                MovieCard(movie = movie, onClick = {})
            }
        }

        composeTestRule.onNodeWithText("3.0").assertIsDisplayed()
    }

    // ── Click handling ────────────────────────────────────────────────────────

    @Test
    fun movieCard_onClick_isInvoked() {
        val movie = fakeMovie(title = "Click Test Movie")
        var clicked = false

        composeTestRule.setContent {
            MoviesAppTheme {
                MovieCard(movie = movie, onClick = { clicked = true })
            }
        }

        composeTestRule.onNodeWithText("Click Test Movie").performClick()
        assertTrue(clicked)
    }

    @Test
    fun movieCard_onClick_notCalledBeforeClick() {
        val movie = fakeMovie(title = "No Click Movie")
        var clicked = false

        composeTestRule.setContent {
            MoviesAppTheme {
                MovieCard(movie = movie, onClick = { clicked = true })
            }
        }

        // No click performed
        assertTrue(!clicked)
    }
}
