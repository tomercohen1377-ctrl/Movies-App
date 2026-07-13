package com.tcohen.moviesapp.data.remote.paging

/**
 * Tunables for movie search (query-stream behaviour, etc.).
 *
 * Co-located per the project convention of naming constants under their owner —
 * see [PagingDefaults] for paging constants used by every list, and
 * [com.tcohen.moviesapp.presentation.moviedetail.morelikethis.MoreLikeThisDefaults]
 * for the "More Like This" section.
 */
internal object SearchDefaults {
    /**
     * Minimum characters before triggering a search.
     *
     * TMDB's `/search/movie` returns massive result sets for single-character queries
     * (effectively "any title starting with *a*"), which is unhelpful on a phone screen
     * and burns API quota. Two characters is the threshold used by Android's own
     * search affordances.
     */
    const val MIN_QUERY_LENGTH = 2

    /**
     * Debounce window for keystrokes. 300ms balances the "feels instant" UX with
     * "don't fire a request for each keystroke during typing".
     */
    const val DEBOUNCE_MS = 300L

    /**
     * How many recent search terms we keep in [com.tcohen.moviesapp.data.local.SearchHistoryRepository].
     * Older entries are evicted FIFO. Five is the common UX default for type-ahead chooser rows.
     */
    const val HISTORY_LIMIT = 5
}
