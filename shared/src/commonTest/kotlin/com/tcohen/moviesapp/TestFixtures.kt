package com.tcohen.moviesapp

import com.tcohen.moviesapp.data.remote.dto.GenreResponse
import com.tcohen.moviesapp.data.remote.dto.MovieDetailsResponse
import com.tcohen.moviesapp.data.remote.dto.MovieListResponse
import com.tcohen.moviesapp.data.remote.dto.MovieResponse
import com.tcohen.moviesapp.data.remote.dto.VideoListResponse
import com.tcohen.moviesapp.data.remote.dto.VideoResponse
import com.tcohen.moviesapp.domain.model.Genre
import com.tcohen.moviesapp.domain.model.Movie
import com.tcohen.moviesapp.domain.model.MovieDetail
import com.tcohen.moviesapp.domain.model.VideoResult

// ── Domain models ─────────────────────────────────────────────────────────────

fun fakeMovie(
    id: Int = 1,
    title: String = "Test Movie $id",
    overview: String = "Overview for movie $id",
    posterPath: String? = "/poster$id.jpg",
    backdropPath: String? = "/backdrop$id.jpg",
    releaseDate: String = "2024-01-01",
    voteAverage: Double = 7.5,
    voteCount: Int = 1000
) = Movie(
    id = id,
    title = title,
    overview = overview,
    posterPath = posterPath,
    backdropPath = backdropPath,
    releaseDate = releaseDate,
    voteAverage = voteAverage,
    voteCount = voteCount
)

fun fakeMovieDetail(
    id: Int = 1,
    title: String = "Test Movie Detail $id",
    genres: List<Genre> = listOf(Genre(28, "Action")),
    runtime: Int? = 120,
    tagline: String? = "A tagline"
) = MovieDetail(
    id = id,
    title = title,
    overview = "Overview for movie $id",
    posterPath = "/poster$id.jpg",
    backdropPath = "/backdrop$id.jpg",
    releaseDate = "2024-01-01",
    voteAverage = 7.5,
    voteCount = 1000,
    runtime = runtime,
    tagline = tagline,
    genres = genres
)

fun fakeVideoResult(key: String = "dQw4w9WgXcQ") = VideoResult(
    key = key,
    site = "YouTube",
    type = "Trailer",
    official = true
)

// ── DTOs ──────────────────────────────────────────────────────────────────────

fun fakeMovieDto(id: Int = 1) = MovieResponse(
    id = id,
    title = "Test Movie $id",
    overview = "Overview $id",
    posterPath = "/poster$id.jpg",
    backdropPath = "/backdrop$id.jpg",
    releaseDate = "2024-01-01",
    voteAverage = 7.5,
    voteCount = 1000
)

fun fakeMovieDetailDto(id: Int = 1) = MovieDetailsResponse(
    id = id,
    title = "Test Movie $id",
    overview = "Overview $id",
    posterPath = "/poster$id.jpg",
    backdropPath = "/backdrop$id.jpg",
    releaseDate = "2024-01-01",
    voteAverage = 7.5,
    voteCount = 1000,
    runtime = 120,
    tagline = "A tagline",
    genres = listOf(GenreResponse(28, "Action"))
)

fun fakeMovieListResponse(
    page: Int = 1,
    totalPages: Int = 5,
    count: Int = 20
) = MovieListResponse(
    page = page,
    results = (1..count).map { fakeMovieDto(it + (page - 1) * count) },
    totalPages = totalPages,
    totalResults = totalPages * count
)

fun fakeVideoListResponse(
    movieId: Int = 1,
    trailerKey: String? = "abc123"
) = VideoListResponse(
    id = movieId,
    results = if (trailerKey != null) listOf(
        VideoResponse(
            key = trailerKey,
            site = "YouTube",
            type = "Trailer",
            official = true,
            publishedAt = "2024-01-01T00:00:00.000Z"
        )
    ) else emptyList()
)
