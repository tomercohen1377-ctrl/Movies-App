package com.tcohen.moviesapp.presentation.common

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tcohen.moviesapp.presentation.theme.MoviesAppTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ErrorViewTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ── Content display ───────────────────────────────────────────────────────

    @Test
    fun errorView_displaysProvidedMessage() {
        composeTestRule.setContent {
            MoviesAppTheme {
                ErrorView(
                    message = "Failed to load movies",
                    onRetry = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Failed to load movies").assertIsDisplayed()
    }

    @Test
    fun errorView_showsRetryButton() {
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
    fun errorView_displaysNetworkMessage() {
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

    // ── Retry interaction ─────────────────────────────────────────────────────

    @Test
    fun errorView_retryButton_invokesCallback() {
        var retried = false
        composeTestRule.setContent {
            MoviesAppTheme {
                ErrorView(
                    message = "Error",
                    onRetry = { retried = true }
                )
            }
        }

        composeTestRule.onNodeWithText("Try Again").performClick()

        assertTrue(retried)
    }

    @Test
    fun errorView_retryButton_notCalledBeforeClick() {
        var retried = false
        composeTestRule.setContent {
            MoviesAppTheme {
                ErrorView(
                    message = "Error",
                    onRetry = { retried = true }
                )
            }
        }

        assertTrue(!retried)
    }
}
