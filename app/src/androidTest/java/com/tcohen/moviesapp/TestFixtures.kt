package com.tcohen.moviesapp

import com.tcohen.moviesapp.domain.model.Category
import com.tcohen.moviesapp.domain.model.Genre
import com.tcohen.moviesapp.domain.model.Movie
import com.tcohen.moviesapp.domain.model.MovieDetail

fun fakeMovie(
    id: Int = 1,
    title: String = "Test Movie $id",
    posterPath: String? = null,     // null → no network needed in UI tests
    backdropPath: String? = null,
    voteAverage: Double = 7.5,
    releaseDate: String = "2024-01-01"
) = Movie(
    id = id,
    title = title,
    overview = "Overview $id",
    posterPath = posterPath,
    backdropPath = backdropPath,
    releaseDate = releaseDate,
    voteAverage = voteAverage,
    voteCount = 1000
)

fun fakeMovieDetail(
    id: Int = 1,
    title: String = "Test Movie Detail $id",
    genres: List<Genre> = listOf(Genre(28, "Action"), Genre(12, "Adventure")),
    runtime: Int? = 120,
    tagline: String? = "Just a tagline"
) = MovieDetail(
    id = id,
    title = title,
    overview = "Overview $id",
    posterPath = null,
    backdropPath = null,
    releaseDate = "2024-05-01",
    voteAverage = 8.2,
    voteCount = 5000,
    runtime = runtime,
    tagline = tagline,
    genres = genres
)
