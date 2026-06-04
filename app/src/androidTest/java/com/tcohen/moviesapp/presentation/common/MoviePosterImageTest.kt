package com.tcohen.moviesapp.presentation.common

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tcohen.moviesapp.presentation.theme.MoviesAppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MoviePosterImageTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ── Rendering ─────────────────────────────────────────────────────────────

    @Test
    fun moviePosterImage_isDisplayed() {
        composeTestRule.setContent {
            MoviesAppTheme {
                MoviePosterImage(
                    imageUrl = null,
                    contentDescription = "Test poster",
                    modifier = Modifier
                        .width(120.dp)
                        .aspectRatio(2f / 3f)
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription("Test poster")
            .assertIsDisplayed()
    }

    @Test
    fun moviePosterImage_nullContentDescription_doesNotCrash() {
        composeTestRule.setContent {
            MoviesAppTheme {
                MoviePosterImage(
                    imageUrl = null,
                    contentDescription = null,
                    modifier = Modifier
                        .width(80.dp)
                        .aspectRatio(2f / 3f)
                )
            }
        }

        // No crash — empty/null content description renders without issue
        composeTestRule.onNodeWithContentDescription("").assertDoesNotExist()
    }

    @Test
    fun moviePosterImage_nullUrl_showsPlaceholderBackground() {
        composeTestRule.setContent {
            MoviesAppTheme {
                MoviePosterImage(
                    imageUrl = null,
                    contentDescription = "Placeholder poster",
                    modifier = Modifier
                        .width(120.dp)
                        .aspectRatio(2f / 3f)
                )
            }
        }

        // The node is still present even when no image loads (shows surface-variant background)
        composeTestRule
            .onNodeWithContentDescription("Placeholder poster")
            .assertIsDisplayed()
    }
}
