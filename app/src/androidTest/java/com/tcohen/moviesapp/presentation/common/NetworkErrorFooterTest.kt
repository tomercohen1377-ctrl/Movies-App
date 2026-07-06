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
class NetworkErrorFooterTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun networkErrorFooter_showsOfflineMessage() {
        composeTestRule.setContent {
            MoviesAppTheme {
                NetworkErrorFooter(onRetry = {})
            }
        }

        composeTestRule
            .onNodeWithText("No internet — showing cached results.")
            .assertIsDisplayed()
    }

    @Test
    fun networkErrorFooter_showsRetryButton() {
        composeTestRule.setContent {
            MoviesAppTheme {
                NetworkErrorFooter(onRetry = {})
            }
        }

        composeTestRule.onNodeWithText("Retry").assertIsDisplayed()
    }

    @Test
    fun networkErrorFooter_retryButton_invokesCallback() {
        var retried = false
        composeTestRule.setContent {
            MoviesAppTheme {
                NetworkErrorFooter(onRetry = { retried = true })
            }
        }

        composeTestRule.onNodeWithText("Retry").performClick()

        assertTrue(retried)
    }

    @Test
    fun networkErrorFooter_retryButton_notCalledBeforeClick() {
        var retried = false
        composeTestRule.setContent {
            MoviesAppTheme {
                NetworkErrorFooter(onRetry = { retried = true })
            }
        }

        assertTrue(!retried)
    }
}
