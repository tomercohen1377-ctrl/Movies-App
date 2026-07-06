package com.tcohen.moviesapp.presentation.common

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tcohen.moviesapp.presentation.theme.MoviesAppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflineBannerTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun offlineBanner_showsTextWhenOffline() {
        composeTestRule.setContent {
            MoviesAppTheme {
                OfflineBanner(isOffline = true)
            }
        }

        composeTestRule.onNodeWithText("You're offline").assertIsDisplayed()
    }

    @Test
    fun offlineBanner_hiddenWhenOnline() {
        composeTestRule.setContent {
            MoviesAppTheme {
                OfflineBanner(isOffline = false)
            }
        }

        composeTestRule.onNodeWithText("You're offline").assertDoesNotExist()
    }
}
