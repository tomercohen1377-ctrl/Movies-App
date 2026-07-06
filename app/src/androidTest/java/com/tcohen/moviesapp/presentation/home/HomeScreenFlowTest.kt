package com.tcohen.moviesapp.presentation.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tcohen.moviesapp.domain.model.Category
import com.tcohen.moviesapp.fakeMovie
import com.tcohen.moviesapp.presentation.common.CategoryFilterRow
import com.tcohen.moviesapp.presentation.common.MovieCard
import com.tcohen.moviesapp.presentation.common.MovieGrid
import com.tcohen.moviesapp.presentation.common.OfflineBanner
import com.tcohen.moviesapp.presentation.theme.MoviesAppTheme
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeScreenFlowTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun homeFlow_allCategoryChipsAreDisplayed() {
        composeTestRule.setContent {
            MoviesAppTheme {
                CategoryFilterRow(
                    selectedCategory = Category.NOW_PLAYING,
                    onCategorySelected = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Now Playing").assertIsDisplayed()
        composeTestRule.onNodeWithText("Top Rated").assertIsDisplayed()
        composeTestRule.onNodeWithText("Upcoming").assertIsDisplayed()
    }

    @Test
    fun homeFlow_defaultSelectedChipIsNowPlaying() {
        composeTestRule.setContent {
            MoviesAppTheme {
                CategoryFilterRow(
                    selectedCategory = Category.NOW_PLAYING,
                    onCategorySelected = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Now Playing").assertIsSelected()
    }

    @Test
    fun homeFlow_selectingCategoryChipInvokesCallback() {
        var selected: Category? = null
        composeTestRule.setContent {
            MoviesAppTheme {
                CategoryFilterRow(
                    selectedCategory = Category.NOW_PLAYING,
                    onCategorySelected = { selected = it }
                )
            }
        }

        composeTestRule.onNodeWithText("Top Rated").performClick()
        assertEquals(Category.TOP_RATED, selected)
    }

    @Test
    fun homeFlow_selectingUpcomingInvokesCallback() {
        var selected: Category? = null
        composeTestRule.setContent {
            MoviesAppTheme {
                CategoryFilterRow(
                    selectedCategory = Category.NOW_PLAYING,
                    onCategorySelected = { selected = it }
                )
            }
        }

        composeTestRule.onNodeWithText("Upcoming").performClick()
        assertEquals(Category.UPCOMING, selected)
    }

    @Test
    fun homeFlow_movieCardsDisplayTitles() {
        val movies = listOf(fakeMovie(title = "Oppenheimer"), fakeMovie(title = "Barbie"))
        composeTestRule.setContent {
            MoviesAppTheme {
                val pagingItems = flowOf(PagingData.from(movies)).collectAsLazyPagingItems()
                MovieGrid(movies = pagingItems) { movie ->
                    if (movie != null) MovieCard(movie = movie, onClick = {})
                }
            }
        }

        composeTestRule.onNodeWithText("Oppenheimer").assertIsDisplayed()
        composeTestRule.onNodeWithText("Barbie").assertIsDisplayed()
    }

    @Test
    fun homeFlow_clickingMovieCardInvokesCallback() {
        val movie = fakeMovie(title = "Dune")
        var clicked = false
        composeTestRule.setContent {
            MoviesAppTheme {
                val pagingItems = flowOf(PagingData.from(listOf(movie))).collectAsLazyPagingItems()
                MovieGrid(movies = pagingItems) { item ->
                    if (item != null) MovieCard(movie = item, onClick = { clicked = true })
                }
            }
        }

        composeTestRule.onNodeWithText("Dune").performClick()
        assertTrue(clicked)
    }

    @Test
    fun homeFlow_offlineBannerVisibleWhenOffline() {
        composeTestRule.setContent {
            MoviesAppTheme {
                OfflineBanner(isOffline = true)
            }
        }

        composeTestRule.onNodeWithText("You're offline").assertIsDisplayed()
    }

    @Test
    fun homeFlow_offlineBannerHiddenWhenOnline() {
        composeTestRule.setContent {
            MoviesAppTheme {
                OfflineBanner(isOffline = false)
            }
        }

        composeTestRule.onNodeWithText("You're offline").assertDoesNotExist()
    }
}
