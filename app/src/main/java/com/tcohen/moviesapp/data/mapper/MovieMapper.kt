package com.tcohen.moviesapp.data.mapper

import com.tcohen.moviesapp.data.local.entity.FavoriteEntity
import com.tcohen.moviesapp.data.local.entity.MovieEntity
import com.tcohen.moviesapp.data.remote.dto.GenreDto
import com.tcohen.moviesapp.data.remote.dto.MovieDetailDto
import com.tcohen.moviesapp.data.remote.dto.MovieDto
import com.tcohen.moviesapp.data.remote.dto.VideoDto
import com.tcohen.moviesapp.domain.model.Category
import com.tcohen.moviesapp.domain.model.Genre
import com.tcohen.moviesapp.domain.model.Movie
import com.tcohen.moviesapp.domain.model.MovieDetail
import com.tcohen.moviesapp.domain.model.VideoResult

// DTO → Domain

fun MovieDto.toDomain(): Movie = Movie(
    id = id,
    title = title,
    overview = overview,
    posterPath = posterPath,
    backdropPath = backdropPath,
    releaseDate = releaseDate,
    voteAverage = voteAverage,
    voteCount = voteCount
)

fun MovieDetailDto.toDomain(): MovieDetail = MovieDetail(
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

fun GenreDto.toDomain(): Genre = Genre(id = id, name = name)

fun VideoDto.toDomain(): VideoResult = VideoResult(
    key = key,
    site = site,
    type = type,
    official = official
)

// Entity → Domain

fun MovieEntity.toDomain(): Movie = Movie(
    id = id,
    title = title,
    overview = overview,
    posterPath = posterPath,
    backdropPath = backdropPath,
    releaseDate = releaseDate,
    voteAverage = voteAverage,
    voteCount = voteCount
)

fun FavoriteEntity.toDomain(): Movie = Movie(
    id = id,
    title = title,
    overview = overview,
    posterPath = posterPath,
    backdropPath = backdropPath,
    releaseDate = releaseDate,
    voteAverage = voteAverage,
    voteCount = voteCount
)

// Domain → Entity

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

fun Movie.toEntity(category: Category, page: Int, cachedAt: Long = System.currentTimeMillis()): MovieEntity = MovieEntity(
    id = id,
    title = title,
    overview = overview,
    posterPath = posterPath,
    backdropPath = backdropPath,
    releaseDate = releaseDate,
    voteAverage = voteAverage,
    voteCount = voteCount,
    category = category.name,
    page = page,
    cachedAt = cachedAt
)
