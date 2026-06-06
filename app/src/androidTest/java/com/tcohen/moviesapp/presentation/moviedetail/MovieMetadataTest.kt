package com.tcohen.moviesapp.presentation.moviedetail

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tcohen.moviesapp.domain.model.Genre
import com.tcohen.moviesapp.fakeMovieDetail
import com.tcohen.moviesapp.presentation.theme.MoviesAppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MovieMetadataTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ── Title ─────────────────────────────────────────────────────────────────

    @Test
    fun movieMetadata_displaysTitle() {
        composeTestRule.setContent {
            MoviesAppTheme {
                MovieMetadata(movie = fakeMovieDetail(title = "Interstellar"))
            }
        }

        composeTestRule.onNodeWithText("Interstellar").assertIsDisplayed()
    }

    // ── Release year ──────────────────────────────────────────────────────────

    @Test
    fun movieMetadata_displaysReleaseYear() {
        val movie = fakeMovieDetail().copy(releaseDate = "2023-11-22")
        composeTestRule.setContent {
            MoviesAppTheme {
                MovieMetadata(movie = movie)
            }
        }

        // Only the first 4 chars (year) are shown
        composeTestRule.onNodeWithText("2023").assertIsDisplayed()
    }

    // ── Runtime ───────────────────────────────────────────────────────────────

    @Test
    fun movieMetadata_displaysRuntimeWithSuffix() {
        val movie = fakeMovieDetail(runtime = 148)
        composeTestRule.setContent {
            MoviesAppTheme {
                MovieMetadata(movie = movie)
            }
        }

        composeTestRule.onNodeWithText("148m").assertIsDisplayed()
    }

    @Test
    fun movieMetadata_runtimeNotShownWhenNull() {
        val movie = fakeMovieDetail(runtime = null)
        composeTestRule.setContent {
            MoviesAppTheme {
                MovieMetadata(movie = movie)
            }
        }

        composeTestRule.onNodeWithText("m", substring = true).assertDoesNotExist()
    }

    // ── Genre chips ───────────────────────────────────────────────────────────

    @Test
    fun movieMetadata_displaysGenreChips() {
        val movie = fakeMovieDetail(
            genres = listOf(Genre(878, "Science Fiction"), Genre(28, "Action"))
        )
        composeTestRule.setContent {
            MoviesAppTheme {
                MovieMetadata(movie = movie)
            }
        }

        composeTestRule.onNodeWithText("Science Fiction").assertIsDisplayed()
        composeTestRule.onNodeWithText("Action").assertIsDisplayed()
    }

    @Test
    fun movieMetadata_noGenreChipsWhenEmpty() {
        val movie = fakeMovieDetail(genres = emptyList())
        composeTestRule.setContent {
            MoviesAppTheme {
                MovieMetadata(movie = movie)
            }
        }

        // Should not crash and genre-specific text won't exist
        composeTestRule.onNodeWithText("Action").assertDoesNotExist()
    }

    // ── Tagline ───────────────────────────────────────────────────────────────

    @Test
    fun movieMetadata_displaysTaglineInQuotes() {
        val movie = fakeMovieDetail(tagline = "Long live the fighters.")
        composeTestRule.setContent {
            MoviesAppTheme {
                MovieMetadata(movie = movie)
            }
        }

        composeTestRule.onNodeWithText("\"Long live the fighters.\"").assertIsDisplayed()
    }

    @Test
    fun movieMetadata_taglineNotShownWhenNull() {
        val movie = fakeMovieDetail(tagline = null)
        composeTestRule.setContent {
            MoviesAppTheme {
                MovieMetadata(movie = movie)
            }
        }

        composeTestRule.onNodeWithText("\"", substring = true).assertDoesNotExist()
    }

    @Test
    fun movieMetadata_taglineNotShownWhenBlank() {
        val movie = fakeMovieDetail(tagline = "   ")
        composeTestRule.setContent {
            MoviesAppTheme {
                MovieMetadata(movie = movie)
            }
        }

        composeTestRule.onNodeWithText("\"", substring = true).assertDoesNotExist()
    }

    // ── Overview ──────────────────────────────────────────────────────────────

    @Test
    fun movieMetadata_displaysOverview() {
        val movie = fakeMovieDetail().copy(overview = "A story about space exploration.")
        composeTestRule.setContent {
            MoviesAppTheme {
                MovieMetadata(movie = movie)
            }
        }

        composeTestRule.onNodeWithText("A story about space exploration.").assertIsDisplayed()
    }

    // ── Rating ────────────────────────────────────────────────────────────────

    @Test
    fun movieMetadata_displaysRatingBadge() {
        val movie = fakeMovieDetail().copy(voteAverage = 9.1)
        composeTestRule.setContent {
            MoviesAppTheme {
                MovieMetadata(movie = movie)
            }
        }

        composeTestRule.onNodeWithText("9.1").assertIsDisplayed()
    }
}


