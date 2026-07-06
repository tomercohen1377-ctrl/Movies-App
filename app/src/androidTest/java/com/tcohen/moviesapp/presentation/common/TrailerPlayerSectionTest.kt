package com.tcohen.moviesapp.presentation.common

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tcohen.moviesapp.presentation.theme.MoviesAppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrailerPlayerSectionTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun trailerPlayerSection_rendersWithoutCrash() {
        composeTestRule.setContent {
            MoviesAppTheme {
                TrailerPlayerSection(trailerKey = "dQw4w9WgXcQ")
            }
        }

        composeTestRule.onRoot().assertIsDisplayed()
    }

    @Test
    fun trailerPlayerSection_differentKey_rendersWithoutCrash() {
        composeTestRule.setContent {
            MoviesAppTheme {
                TrailerPlayerSection(trailerKey = "abc123XYZ")
            }
        }

        composeTestRule.onRoot().assertIsDisplayed()
    }
}
