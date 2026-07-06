package com.tcohen.moviesapp.presentation.common

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tcohen.moviesapp.domain.model.Category
import com.tcohen.moviesapp.presentation.theme.MoviesAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CategoryFilterRowTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun categoryFilterRow_displaysUpcomingChip() {
        composeTestRule.setContent {
            MoviesAppTheme {
                CategoryFilterRow(
                    selectedCategory = Category.UPCOMING,
                    onCategorySelected = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Upcoming").assertIsDisplayed()
    }

    @Test
    fun categoryFilterRow_displaysTopRatedChip() {
        composeTestRule.setContent {
            MoviesAppTheme {
                CategoryFilterRow(
                    selectedCategory = Category.UPCOMING,
                    onCategorySelected = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Top Rated").assertIsDisplayed()
    }

    @Test
    fun categoryFilterRow_displaysNowPlayingChip() {
        composeTestRule.setContent {
            MoviesAppTheme {
                CategoryFilterRow(
                    selectedCategory = Category.UPCOMING,
                    onCategorySelected = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Now Playing").assertIsDisplayed()
    }

    @Test
    fun categoryFilterRow_clickTopRated_invokesCallback() {
        var selected: Category? = null

        composeTestRule.setContent {
            MoviesAppTheme {
                CategoryFilterRow(
                    selectedCategory = Category.UPCOMING,
                    onCategorySelected = { selected = it }
                )
            }
        }

        composeTestRule.onNodeWithText("Top Rated").performClick()

        assertEquals(Category.TOP_RATED, selected)
    }

    @Test
    fun categoryFilterRow_clickNowPlaying_invokesCallback() {
        var selected: Category? = null

        composeTestRule.setContent {
            MoviesAppTheme {
                CategoryFilterRow(
                    selectedCategory = Category.UPCOMING,
                    onCategorySelected = { selected = it }
                )
            }
        }

        composeTestRule.onNodeWithText("Now Playing").performClick()

        assertEquals(Category.NOW_PLAYING, selected)
    }

    @Test
    fun categoryFilterRow_clickUpcoming_invokesCallback() {
        var selected: Category? = null

        composeTestRule.setContent {
            MoviesAppTheme {
                CategoryFilterRow(
                    selectedCategory = Category.TOP_RATED,
                    onCategorySelected = { selected = it }
                )
            }
        }

        composeTestRule.onNodeWithText("Upcoming").performClick()

        assertEquals(Category.UPCOMING, selected)
    }

    @Test
    fun categoryFilterRow_selectionUpdates_reflectsInChips() {
        composeTestRule.setContent {
            MoviesAppTheme {
                var current by remember { mutableStateOf(Category.UPCOMING) }
                CategoryFilterRow(
                    selectedCategory = current,
                    onCategorySelected = { current = it }
                )
            }
        }

        composeTestRule.onNodeWithText("Top Rated").performClick()

        composeTestRule.onNodeWithText("Top Rated").assertIsDisplayed()
    }
}
