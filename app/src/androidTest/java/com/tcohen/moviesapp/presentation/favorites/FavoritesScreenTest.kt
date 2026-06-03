package com.tcohen.moviesapp.presentation.favorites

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tcohen.moviesapp.fakeMovie
import com.tcohen.moviesapp.presentation.theme.MoviesAppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for the stateless inner composables extracted from [FavoritesScreen].
 *
 * Note: Full [FavoritesScreen] tests require a Hilt test graph. These tests exercise
 * the UI states directly by rendering the content composables in isolation.
 */
@RunWith(AndroidJUnit4::class)
class FavoritesScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ── Empty state ───────────────────────────────────────────────────────────

    @Test
    fun favoritesState_empty_showsEmptyHeadline() {
        composeTestRule.setContent {
            MoviesAppTheme {
                FavoritesContentPreview(
                    state = FavoritesState(favorites = emptyList(), isEmpty = true),
                    onIntent = {}
                )
            }
        }

        composeTestRule.onNodeWithText("No saved movies yet").assertIsDisplayed()
    }

    @Test
    fun favoritesState_empty_showsHintText() {
        composeTestRule.setContent {
            MoviesAppTheme {
                FavoritesContentPreview(
                    state = FavoritesState(favorites = emptyList(), isEmpty = true),
                    onIntent = {}
                )
            }
        }

        composeTestRule
            .onNodeWithText("Tap the heart on any movie to save it here")
            .assertIsDisplayed()
    }

    // ── Non-empty state ─────────────────────────────────────────────────────��─

    @Test
    fun favoritesState_withMovies_showsMovieTitles() {
        val movies = listOf(fakeMovie(id = 1, title = "Movie Alpha"), fakeMovie(id = 2, title = "Movie Beta"))

        composeTestRule.setContent {
            MoviesAppTheme {
                FavoritesContentPreview(
                    state = FavoritesState(favorites = movies, isEmpty = false),
                    onIntent = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Movie Alpha").assertIsDisplayed()
        composeTestRule.onNodeWithText("Movie Beta").assertIsDisplayed()
    }

    @Test
    fun favoritesState_withMovies_doesNotShowEmptyMessage() {
        val movies = listOf(fakeMovie(id = 1, title = "Some Movie"))

        composeTestRule.setContent {
            MoviesAppTheme {
                FavoritesContentPreview(
                    state = FavoritesState(favorites = movies, isEmpty = false),
                    onIntent = {}
                )
            }
        }

        composeTestRule.onNodeWithText("No saved movies yet").assertDoesNotExist()
    }

    @Test
    fun favoritesState_topAppBar_isTitleDisplayed() {
        composeTestRule.setContent {
            MoviesAppTheme {
                FavoritesContentPreview(
                    state = FavoritesState(),
                    onIntent = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Favorites").assertIsDisplayed()
    }
}
