package com.tcohen.moviesapp.journey

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tcohen.moviesapp.BuildConfig
import com.tcohen.moviesapp.MainActivity
import com.tcohen.moviesapp.data.local.LocalMovieDataSource
import com.tcohen.moviesapp.data.remote.TmdbRemoteDataSource
import com.tcohen.moviesapp.data.remote.dto.FavoriteRequest
import com.tcohen.moviesapp.domain.model.Category
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Full end-to-end journey tests using the **real** TMDB API, the real SQLDelight
 * database, and the real `MovieRepositoryImpl`.
 *
 * Unlike `AppJourneyTest` (which uses an in-memory fake), these tests exercise
 * the complete production stack:
 *
 * - Real HTTP calls to `api.themoviedb.org`
 * - Real OkHttp image cache (Coil loads actual TMDB poster/backdrop images)
 * - Real SQLDelight database (cleared before each test)
 *
 * Because content is dynamic, assertions target **structure** rather than
 * specific movie titles: category chips (hardcoded), the presence of at least
 * N movie-card nodes, the Back button, and the FAB content-description.
 *
 * ## Requirements
 * - The test device must have **internet access**.
 * - `BuildConfig.TMDB_API_KEY` must be a valid TMDB Bearer token.
 *
 * ## Journeys covered
 * 1. Home: category filter chips visible (no network required)
 * 2. Home: real movies load from TMDB (network required)
 * 3. Home → Detail: tapping a movie opens the detail screen
 * 4. Detail → Home: back button returns to the grid
 * 5. Favorites tab: empty state shown before any toggle
 * 6. Detail → toggle favorite → Favorites tab shows the movie
 * 7. Favorites: swipe removes a movie from the list
 */
@RunWith(AndroidJUnit4::class)
class RealAppJourneyTest : KoinComponent {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    /** Injected from Koin — used to clear the SQLDelight local database before/after each test. */
    private val localDataSource: LocalMovieDataSource by inject()

    /** Injected from Koin — used to clean server-side favorites before/after each test. */
    private val remoteDataSource: TmdbRemoteDataSource by inject()

    /** TMDB account-id read from build config — same value the DI graph provides. */
    private val accountId: String get() = BuildConfig.TMDB_ACCOUNT_ID

    /** TMDB session-id (may be blank; Bearer token is used for auth). */
    private val sessionId: String get() = BuildConfig.TMDB_SESSION_ID

    @Before
    fun setUp() {
        runBlocking {
            // 1. Remove any server favorites left by a previous test run so the
            //    favorites paging source starts with a clean slate from the API.
            cleanServerFavorites()
            // 2. Clear local SQLDelight DB so locally-cached data is also wiped.
            clearLocalDatabase()
        }
    }

    @After
    fun tearDown() {
        // Remove any server favorites added during this test so the next test
        // starts with a clean state (mirrors what @Before does, but runs after).
        runBlocking {
            cleanServerFavorites()
            clearLocalDatabase()
        }
    }

    // ── 1. Category chips — no network required ───────────────────────────────

    @Test
    fun homeScreen_categoryChipsAreVisible() {
        // Chips are hardcoded — they appear immediately before any network call.
        waitForText("Now Playing", 10_000)
        pace()

        composeTestRule.onNodeWithText("Now Playing").assertIsDisplayed()
        pace(700)
        composeTestRule.onNodeWithText("Top Rated").assertIsDisplayed()
        pace(700)
        composeTestRule.onNodeWithText("Upcoming").assertIsDisplayed()
        pace()
    }

    // ── 2. Real movies load from the TMDB API ────────────────────────────────

    @Test
    fun homeScreen_loadsRealMoviesFromTmdb() {
        // Wait for the TMDB response — give the network up to 30 s.
        waitForMovieCards(minCount = 4, timeoutMs = 30_000)
        pace()  // let the grid settle with real posters loading

        // At least a healthy number of cards should be in the grid.
        val cardCount = composeTestRule
            .onAllNodes(hasTestTag("movie_card"))
            .fetchSemanticsNodes()
            .size
        assert(cardCount >= 4) { "Expected ≥ 4 movie cards, got $cardCount" }
        pace()
    }

    @Test
    fun homeScreen_chipSwitch_loadsTopRatedMovies() {
        waitForMovieCards(minCount = 1, timeoutMs = 30_000)
        pace()

        composeTestRule.onNodeWithText("Top Rated").performClick()
        pace()  // observe the chip change and the new page load

        // After switching, at least some cards should still appear.
        waitForMovieCards(minCount = 1, timeoutMs = 20_000)
        composeTestRule.onNodeWithText("Top Rated").assertIsDisplayed()
        pace()
    }

    // ── 3. Home → Detail ─────────────────────────────────────────────────────

    @Test
    fun tappingFirstMovie_navigatesToDetailScreen() {
        waitForMovieCards(minCount = 1, timeoutMs = 30_000)
        pace()

        // Tap whatever the first card is (real TMDB data — title is dynamic).
        composeTestRule.onAllNodes(hasTestTag("movie_card"))[0]
            .performScrollTo()
            .performClick()
        pace()  // slide-in animation

        // The Back button appears only when we're on the detail screen.
        waitForContentDescription("Back", timeoutMs = 20_000)
        composeTestRule.onNodeWithContentDescription("Back").assertIsDisplayed()
        pace()

        // The FAB should appear (heart icon — either filled or outlined).
        waitForAnyContentDescription(
            listOf("Add to favorites", "Remove from favorites"),
            timeoutMs = 10_000
        )
        pace()
    }

    // ── 4. Detail → Back ─────────────────────────────────────────────────────

    @Test
    fun detailScreen_backButton_returnsToHomeGrid() {
        waitForMovieCards(minCount = 1, timeoutMs = 30_000)
        composeTestRule.onAllNodes(hasTestTag("movie_card"))[0]
            .performScrollTo()
            .performClick()
        pace()
        waitForContentDescription("Back", timeoutMs = 20_000)
        pace()  // observe the detail screen

        composeTestRule.onNodeWithContentDescription("Back").performClick()
        pace()  // watch the back-slide animation

        // Category chips appear again once we're back on Home.
        waitForText("Now Playing", timeoutMs = 10_000)
        composeTestRule.onNodeWithText("Now Playing").assertIsDisplayed()
        pace()
    }

    // ── 5. Favorites tab — empty state ───────────────────────────────────────

    @Test
    fun favoritesTab_showsEmptyState_initially() {
        // Bottom navigation "Favorites" tab is always visible.
        waitForText("Favorites", timeoutMs = 10_000)
        pace()

        composeTestRule.onNodeWithText("Favorites").performClick()
        pace()  // tab switch animation

        // Empty state: "No saved movies yet" headline.
        waitForText("No saved movies yet", timeoutMs = 15_000)
        composeTestRule.onNodeWithText("No saved movies yet").assertIsDisplayed()
        pace()
    }

    // ── 6. Toggle favorite → check in Favorites tab ──────────────────────────

    @Test
    fun toggleFavorite_movieAppearsInFavoritesTab() {
        // Open first movie.
        waitForMovieCards(minCount = 1, timeoutMs = 30_000)
        pace()
        composeTestRule.onAllNodes(hasTestTag("movie_card"))[0]
            .performScrollTo()
            .performClick()
        pace()

        // Wait for the FAB.
        waitForContentDescription("Add to favorites", timeoutMs = 20_000)
        pace()  // observe detail with unfilled heart

        // Tap "Add to favorites".
        composeTestRule.onNodeWithContentDescription("Add to favorites").performClick()
        pace()  // heart fills with color animation

        // Confirm the toggle: FAB description flips to "Remove from favorites".
        waitForContentDescription("Remove from favorites", timeoutMs = 10_000)
        composeTestRule.onNodeWithContentDescription("Remove from favorites").assertIsDisplayed()
        pace()

        // Go back and switch to Favorites tab.
        composeTestRule.onNodeWithContentDescription("Back").performClick()
        pace()
        waitForText("Favorites", timeoutMs = 10_000)
        composeTestRule.onNodeWithText("Favorites").performClick()
        pace()  // tab switch

        // At least one movie card must appear in the favorites grid.
        waitForMovieCards(minCount = 1, timeoutMs = 15_000)
        composeTestRule.onAllNodes(hasTestTag("movie_card"))[0].assertIsDisplayed()
        pace()
    }

    // ── 7. Swipe to remove in Favorites ──────────────────────────────────────

    @Test
    fun swipeLeft_removesMovieFromFavoritesList() {
        // Favourite the first movie.
        waitForMovieCards(minCount = 1, timeoutMs = 30_000)
        composeTestRule.onAllNodes(hasTestTag("movie_card"))[0]
            .performScrollTo()
            .performClick()
        pace()
        waitForContentDescription("Add to favorites", timeoutMs = 20_000)
        composeTestRule.onNodeWithContentDescription("Add to favorites").performClick()
        pace()
        waitForContentDescription("Remove from favorites", timeoutMs = 10_000)
        pace()

        composeTestRule.onNodeWithContentDescription("Back").performClick()
        pace()

        // Switch to Favorites.
        waitForText("Favorites", timeoutMs = 10_000)
        composeTestRule.onNodeWithText("Favorites").performClick()
        pace()
        waitForMovieCards(minCount = 1, timeoutMs = 15_000)
        pace()  // observe the favorited movie

        // Swipe the card away.
        composeTestRule.onAllNodes(hasTestTag("movie_card"))[0]
            .performScrollTo()
            .performTouchInput { swipeLeft() }
        pace()  // watch the card fly off screen
        composeTestRule.waitForIdle()

        // Empty state returns.
        waitForText("No saved movies yet", timeoutMs = 15_000)
        composeTestRule.onNodeWithText("No saved movies yet").assertIsDisplayed()
        pace()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Clears all locally cached data from SQLDelight.
     * Replaces Room's `AppDatabase.clearAllTables()`.
     */
    private suspend fun clearLocalDatabase() {
        // Clear movie cache for every category
        Category.values().forEach { category ->
            localDataSource.deleteByCategory(category.name)
        }
        // Clear all local favorites (fetch and delete in batches of 50)
        while (true) {
            val batch = localDataSource.getFavoritesPaged(limit = 50, offset = 0)
            if (batch.isEmpty()) break
            batch.forEach { movie -> localDataSource.deleteFavoriteById(movie.id) }
        }
    }

    /**
     * Fetches all currently-favorited movies from the TMDB server and removes
     * each one, leaving the server account in a clean (no favorites) state.
     *
     * Called in both `@Before` and `@After` to guard against stale server state
     * from a previous test run and to clean up after the current test.
     * Errors are swallowed — if the cleanup fails, the test should still run.
     */
    private suspend fun cleanServerFavorites() {
        try {
            val page1 = remoteDataSource.getFavoriteMovies(page = 1)
            page1.results.forEach { dto ->
                try {
                    remoteDataSource.markFavorite(mediaId = dto.id, favorite = false)
                } catch (_: Exception) { /* best-effort */ }
            }
        } catch (_: Exception) { /* best-effort */ }
    }

    /**
     * Inserts a [Thread.sleep] pause so a human watching the device can see
     * the current state before the next action fires.
     *
     * @param ms Pause duration in milliseconds. Default is 1 500 ms.
     */
    private fun pace(ms: Long = 1_500L) = Thread.sleep(ms)

    /**
     * Waits until at least [minCount] nodes tagged with `"movie_card"` appear.
     * The TMDB network call can take several seconds on a real device.
     */
    private fun waitForMovieCards(minCount: Int = 1, timeoutMs: Long = 20_000) {
        composeTestRule.waitUntil(timeoutMillis = timeoutMs) {
            composeTestRule.onAllNodes(hasTestTag("movie_card"))
                .fetchSemanticsNodes().size >= minCount
        }
    }

    /**
     * Waits until a node with exact [text] appears in the semantic tree.
     */
    private fun waitForText(text: String, timeoutMs: Long = 8_000) {
        composeTestRule.waitUntil(timeoutMillis = timeoutMs) {
            composeTestRule.onAllNodes(hasText(text, substring = false))
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    /**
     * Waits until a node with [contentDesc] appears (for FAB and Back button checks).
     */
    private fun waitForContentDescription(contentDesc: String, timeoutMs: Long = 8_000) {
        composeTestRule.waitUntil(timeoutMillis = timeoutMs) {
            composeTestRule.onAllNodes(hasContentDescription(contentDesc))
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    /**
     * Waits until a node matching ANY of the given content descriptions appears.
     * Used for the FAB which can be in either "Add" or "Remove" state.
     */
    private fun waitForAnyContentDescription(descriptions: List<String>, timeoutMs: Long = 8_000) {
        composeTestRule.waitUntil(timeoutMillis = timeoutMs) {
            descriptions.any { desc ->
                composeTestRule.onAllNodes(hasContentDescription(desc))
                    .fetchSemanticsNodes().isNotEmpty()
            }
        }
    }
}
