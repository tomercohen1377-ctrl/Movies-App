package com.tcohen.moviesapp.presentation.favorites

import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tcohen.moviesapp.presentation.theme.MoviesAppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for the stateless composables extracted from [FavoritesScreen].
 *
 * The paging-driven [FavoritesGrid] requires a full Hilt graph to test end-to-end.
 * These tests exercise the [EmptyFavoritesState] composable in isolation.
 */
@RunWith(AndroidJUnit4::class)
class FavoritesScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ── Empty state ───────────────────────────────────────────────────────────

    @Test
    fun emptyFavoritesState_showsHeadline() {
        composeTestRule.setContent {
            MoviesAppTheme {
                EmptyFavoritesState()
            }
        }

        composeTestRule.onNodeWithText("No saved movies yet").assertIsDisplayed()
    }

    @Test
    fun emptyFavoritesState_showsHintText() {
        composeTestRule.setContent {
            MoviesAppTheme {
                EmptyFavoritesState()
            }
        }

        composeTestRule
            .onNodeWithText("Tap the heart on any movie to save it here")
            .assertIsDisplayed()
    }

    @Test
    fun emptyFavoritesState_doesNotShowRetryButton() {
        composeTestRule.setContent {
            MoviesAppTheme {
                EmptyFavoritesState()
            }
        }

        composeTestRule.onNodeWithText("Try Again").assertDoesNotExist()
    }
}
