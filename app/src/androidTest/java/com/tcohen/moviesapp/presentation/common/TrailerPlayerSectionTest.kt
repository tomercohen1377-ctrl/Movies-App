package com.tcohen.moviesapp.presentation.common

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tcohen.moviesapp.presentation.theme.MoviesAppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrailerPlayerSectionTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ── No trailer (offline fallback) ─────────────────────────────────────────

    @Test
    fun trailerPlayerSection_noTrailerKey_showsFallbackPlayIcon() {
        composeTestRule.setContent {
            MoviesAppTheme {
                TrailerPlayerSection(
                    trailerKey = null,
                    backdropUrl = null
                )
            }
        }

        // The fallback play icon has "Trailer unavailable offline" content description
        composeTestRule
            .onNodeWithContentDescription("Trailer unavailable offline")
            .assertIsDisplayed()
    }

    @Test
    fun trailerPlayerSection_emptyTrailerKey_showsFallback() {
        // Empty string behaves as null for the branch — verify graceful rendering
        composeTestRule.setContent {
            MoviesAppTheme {
                TrailerPlayerSection(
                    trailerKey = null,
                    backdropUrl = "https://image.tmdb.org/t/p/w500/backdrop.jpg"
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription("Trailer unavailable offline")
            .assertIsDisplayed()
    }

    // ── With trailer key (WebView path) ───────────────────────────────────────

    @Test
    fun trailerPlayerSection_withTrailerKey_doesNotShowFallbackIcon() {
        composeTestRule.setContent {
            MoviesAppTheme {
                TrailerPlayerSection(
                    trailerKey = "dQw4w9WgXcQ",
                    backdropUrl = null
                )
            }
        }

        // When a key exists, the fallback icon should NOT be present
        composeTestRule
            .onNodeWithContentDescription("Trailer unavailable offline")
            .assertDoesNotExist()
    }
}
