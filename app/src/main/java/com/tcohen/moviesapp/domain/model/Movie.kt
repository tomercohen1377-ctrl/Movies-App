package com.tcohen.moviesapp.domain.model

data class Movie(
    val id: Int,
    val title: String,
    val overview: String,
    val posterPath: String?,
    val backdropPath: String?,
    val releaseDate: String,
    val voteAverage: Double,
    val voteCount: Int
)

data class MovieDetail(
    val id: Int,
    val title: String,
    val overview: String,
    val posterPath: String?,
    val backdropPath: String?,
    val releaseDate: String,
    val voteAverage: Double,
    val voteCount: Int,
    val runtime: Int?,
    val tagline: String?,
    val genres: List<Genre>
)

data class Genre(
    val id: Int,
    val name: String
)

data class VideoResult(
    val key: String,
    val site: String,
    val type: String,
    val official: Boolean
)

enum class Category {
    NOW_PLAYING,
    TOP_RATED,
    UPCOMING;
}
