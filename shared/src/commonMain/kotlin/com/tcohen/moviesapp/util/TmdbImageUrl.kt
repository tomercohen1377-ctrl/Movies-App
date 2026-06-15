package com.tcohen.moviesapp.util

/**
 * Builds TMDB image URLs from raw path strings returned by the API.
 *
 * Docs: https://developer.themoviedb.org/docs/image-basics
 */
object TmdbImageUrl {
    private const val BASE = "https://image.tmdb.org/t/p/"

    /** w342 — suitable for movie list cards */
    fun poster(path: String?): String? = path?.let { "${BASE}w342$it" }

    /** w500 — suitable for the detail screen poster thumbnail */
    fun posterLarge(path: String?): String? = path?.let { "${BASE}w500$it" }

    /** w780 — suitable for the detail screen backdrop / trailer fallback */
    fun backdrop(path: String?): String? = path?.let { "${BASE}w780$it" }
}
