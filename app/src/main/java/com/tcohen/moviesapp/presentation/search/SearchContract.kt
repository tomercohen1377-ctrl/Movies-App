package com.tcohen.moviesapp.presentation.search

// ── State ────────────────────────────────────────────────────────────────────

/**
 * UI state for the Search screen.
 *
 * @property query Current text in the search input. Drive all filtering off this.
 * @property isOffline True when the device has no connectivity (drives the offline banner).
 * @property hasSearched True after the user typed enough characters to trigger a real
 *   query. Lets the UI distinguish "haven't searched yet" from "search ran with no matches".
 */
data class SearchState(
    val query: String = "",
    val isOffline: Boolean = false,
    val hasSearched: Boolean = false,
)

// ── Intents ───────────────────────────────────────────────────────────────────

sealed interface SearchIntent {
    /** Called on every query-text change. The view-model debounces internally. */
    data class UpdateQuery(val query: String) : SearchIntent

    /** Tap on the trailing close-icon — clears the input and resets the results. */
    data object ClearQuery : SearchIntent

    /** Tap on a history chip — fills the input with that previous query. */
    data class SelectHistory(val query: String) : SearchIntent

    /** Clear all stored history. */
    data object ClearHistory : SearchIntent

    /** Tap on a result card — navigates to the detail screen. */
    data class OpenDetail(val movieId: Int) : SearchIntent
}

// ── Effects (one-shot) ────────────────────────────────────────────────────────

sealed interface SearchEffect {
    data class NavigateToDetail(val movieId: Int) : SearchEffect
}
