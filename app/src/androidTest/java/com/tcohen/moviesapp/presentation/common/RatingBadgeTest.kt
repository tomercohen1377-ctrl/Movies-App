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
class RatingBadgeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun ratingBadge_displaysRatingWithOneDecimalPlace() {
        composeTestRule.setContent {
            MoviesAppTheme {
                RatingBadge(rating = 7.5)
            }
        }

        composeTestRule.onNodeWithText("7.5").assertIsDisplayed()
    }

    @Test
    fun ratingBadge_roundsToOneDecimalPlace() {
        composeTestRule.setContent {
            MoviesAppTheme {
                RatingBadge(rating = 8.0)
            }
        }

        composeTestRule.onNodeWithText("8.0").assertIsDisplayed()
    }

    @Test
    fun ratingBadge_showsHighRating() {
        composeTestRule.setContent {
            MoviesAppTheme {
                RatingBadge(rating = 9.9)
            }
        }

        composeTestRule.onNodeWithText("9.9").assertIsDisplayed()
    }

    @Test
    fun ratingBadge_showsLowRating() {
        composeTestRule.setContent {
            MoviesAppTheme {
                RatingBadge(rating = 1.2)
            }
        }

        composeTestRule.onNodeWithText("1.2").assertIsDisplayed()
    }

    @Test
    fun ratingBadge_formatsZeroCorrectly() {
        composeTestRule.setContent {
            MoviesAppTheme {
                RatingBadge(rating = 0.0)
            }
        }

        composeTestRule.onNodeWithText("0.0").assertIsDisplayed()
    }
}
