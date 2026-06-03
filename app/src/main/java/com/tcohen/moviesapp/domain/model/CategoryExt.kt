package com.tcohen.moviesapp.domain.model

/** Human-readable label shown in the category filter chip row. */
val Category.displayName: String
    get() = when (this) {
        Category.UPCOMING -> "Upcoming"
        Category.TOP_RATED -> "Top Rated"
        Category.NOW_PLAYING -> "Now Playing"
    }
