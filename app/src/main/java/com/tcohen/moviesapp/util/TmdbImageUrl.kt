package com.tcohen.moviesapp.util

object TmdbImageUrl {
    private const val BASE = "https://image.tmdb.org/t/p/"

    fun poster(path: String?): String? = path?.let { "${BASE}w342$it" }

    fun posterLarge(path: String?): String? = path?.let { "${BASE}w500$it" }

    fun backdrop(path: String?): String? = path?.let { "${BASE}w780$it" }
}
