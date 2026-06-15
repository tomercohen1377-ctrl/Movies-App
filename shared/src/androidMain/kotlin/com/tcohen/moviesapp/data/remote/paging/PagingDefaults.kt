package com.tcohen.moviesapp.data.remote.paging

/**
 * Shared paging constants used across [MoviePagingSource], [FavoritesPagingSource],
 * and the [Pager] configured in the repository.
 *
 * TMDB uses **1-based** page numbers for all paginated endpoints.
 */
internal object PagingDefaults {
    /** First page number. TMDB pages start at 1, not 0. */
    const val STARTING_PAGE_INDEX = 1

    /** Number of items loaded per page. Matches TMDB's default of 20 items per page. */
    const val PAGE_SIZE = 20

    /** How many items before the end of the list trigger the next page load. */
    const val PREFETCH_DISTANCE = 5
}
