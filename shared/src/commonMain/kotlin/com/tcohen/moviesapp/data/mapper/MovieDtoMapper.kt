package com.tcohen.moviesapp.data.mapper

import com.tcohen.moviesapp.data.remote.dto.GenreResponse
import com.tcohen.moviesapp.data.remote.dto.MovieDetailsResponse
import com.tcohen.moviesapp.data.remote.dto.MovieResponse
import com.tcohen.moviesapp.data.remote.dto.VideoResponse
import com.tcohen.moviesapp.domain.model.Genre
import com.tcohen.moviesapp.domain.model.Movie
import com.tcohen.moviesapp.domain.model.MovieDetail
import com.tcohen.moviesapp.domain.model.VideoResult

// ── DTO → Domain ───────���──────────────────────────────────────────────────────

fun MovieResponse.toDomain(): Movie = Movie(
    id = id,
    title = title,
    overview = overview,
    posterPath = posterPath,
    backdropPath = backdropPath,
    releaseDate = releaseDate,
    voteAverage = voteAverage,
    voteCount = voteCount
)

fun MovieDetailsResponse.toDomain(): MovieDetail = MovieDetail(
    id = id,
    title = title,
    overview = overview,
    posterPath = posterPath,
    backdropPath = backdropPath,
    releaseDate = releaseDate,
    voteAverage = voteAverage,
    voteCount = voteCount,
    runtime = runtime,
    tagline = tagline,
    genres = genres.map { it.toDomain() }
)

fun GenreResponse.toDomain(): Genre = Genre(id = id, name = name)

fun VideoResponse.toDomain(): VideoResult = VideoResult(
    key = key,
    site = site,
    type = type,
    official = official
)

// ── Domain ────────────────────────────────────────────────────────────────────

/** Converts a [MovieDetail] back to a lightweight [Movie] for favorites operations. */
fun MovieDetail.toMovie(): Movie = Movie(
    id = id,
    title = title,
    overview = overview,
    posterPath = posterPath,
    backdropPath = backdropPath,
    releaseDate = releaseDate,
    voteAverage = voteAverage,
    voteCount = voteCount
)
