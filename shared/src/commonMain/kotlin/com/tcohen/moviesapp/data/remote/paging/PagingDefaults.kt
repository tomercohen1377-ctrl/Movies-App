package com.tcohen.moviesapp.data.remote.paging

/**
 * Shared paging constants used across all [app.cash.paging.PagingSource] implementations.
 */
object PagingDefaults {
    /** TMDB page index starts at 1. */
    const val STARTING_PAGE_INDEX = 1

    /** Number of movies per page returned by TMDB. */
    const val PAGE_SIZE = 20

    /** How many items ahead of the viewport to prefetch. */
    const val PREFETCH_DISTANCE = 5
}
