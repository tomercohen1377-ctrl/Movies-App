package com.tcohen.moviesapp

import com.tcohen.moviesapp.data.local.entity.MovieEntity
import com.tcohen.moviesapp.data.remote.dto.GenreResponse
import com.tcohen.moviesapp.data.remote.dto.MovieDetailsResponse
import com.tcohen.moviesapp.data.remote.dto.MovieResponse
import com.tcohen.moviesapp.data.remote.dto.MovieListResponse
import com.tcohen.moviesapp.data.remote.dto.VideoResponse
import com.tcohen.moviesapp.data.remote.dto.VideoListResponse
import com.tcohen.moviesapp.data.remote.server.dto.ServerFavoriteDto
import com.tcohen.moviesapp.data.remote.server.dto.ServerIsFavoriteResponse
import com.tcohen.moviesapp.domain.model.Category
import com.tcohen.moviesapp.domain.model.Genre
import com.tcohen.moviesapp.domain.model.Movie
import com.tcohen.moviesapp.domain.model.MovieDetail
import com.tcohen.moviesapp.domain.model.VideoResult
import retrofit2.Response

fun fakeMovie(id: Int = 1, voteAverage: Double = 7.5) = Movie(
    id = id,
    title = "Test Movie $id",
    overview = "Overview $id",
    posterPath = "/poster$id.jpg",
    backdropPath = "/backdrop$id.jpg",
    releaseDate = "2024-01-01",
    voteAverage = voteAverage,
    voteCount = 1000,
)

fun fakeMovieDto(id: Int = 1, voteAverage: Double = 7.5) = MovieResponse(
    id = id,
    title = "Test Movie $id",
    overview = "Overview $id",
    posterPath = "/poster$id.jpg",
    backdropPath = "/backdrop$id.jpg",
    releaseDate = "2024-01-01",
    voteAverage = voteAverage,
    voteCount = 1000,
)

fun fakeMovieDetail(id: Int = 1) = MovieDetail(
    id = id,
    title = "Test Movie Detail $id",
    overview = "Test Movie Detail Overview $id",
    posterPath = "/poster$id.jpg",
    backdropPath = "/backdrop$id.jpg",
    releaseDate = "2024-01-01",
    voteAverage = 7.5,
    voteCount = 1000,
    runtime = 120,
    tagline = "A tagline",
    genres = listOf(Genre(28, "Action")),
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
    genres = listOf(GenreResponse(28, "Action")),
)

fun fakeMovieListResponse(
    page: Int = 1,
    totalPages: Int = 1,
    results: List<MovieResponse> = emptyList(),
) = MovieListResponse(
    page = page,
    totalPages = totalPages,
    results = results,
    totalResults = results.size,
)

fun fakeMovieEntity(id: Int = 1, category: Category = Category.NOW_PLAYING, page: Int = 1) = MovieEntity(
    id = id,
    title = "Test Movie $id",
    overview = "Overview $id",
    posterPath = "/poster$id.jpg",
    backdropPath = "/backdrop$id.jpg",
    releaseDate = "2024-01-01",
    voteAverage = 7.5,
    voteCount = 1000,
    category = category.name,
    page = page,
)

fun fakeVideoListResponse(
    trailerKey: String? = null,
    id: Int = 1,
): VideoListResponse = VideoListResponse(
    id = id,
    results = listOfNotNull(
        trailerKey?.let {
            VideoResponse(
                key = it,
                site = "YouTube",
                type = "Trailer",
                official = true,
                publishedAt = "2024-01-01T00:00:00.000Z",
            )
        }
    )
)

fun fakeServerFavoriteDto(id: Int = 1, savedAt: Long = 1L) =
    ServerFavoriteDto(movieId = id, savedAt = savedAt)

fun fakeIsFavoriteResponse(isFavorite: Boolean): Response<ServerIsFavoriteResponse> =
    Response.success(ServerIsFavoriteResponse(isFavorite = isFavorite))

fun fakeVideoResult(key: String = "abc123"): VideoResult = VideoResult(
    key = key,
    site = "YouTube",
    type = "Trailer",
    official = true,
)
