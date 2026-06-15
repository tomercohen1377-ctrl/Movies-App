package com.tcohen.moviesapp.data.remote

import com.tcohen.moviesapp.data.remote.dto.FavoriteRequest
import com.tcohen.moviesapp.data.remote.dto.FavoriteResponse
import com.tcohen.moviesapp.data.remote.dto.MovieDetailsResponse
import com.tcohen.moviesapp.data.remote.dto.MovieListResponse
import com.tcohen.moviesapp.data.remote.dto.VideoListResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

/**
 * Multiplatform remote data source for the TMDB API.
 *
 * Replaces the Retrofit [TmdbApiService] with a Ktor-based implementation that
 * can run on Android, Desktop, and iOS. The [httpClient] is provided by the
 * platform (Android: OkHttp engine, iOS: Darwin engine, Desktop: CIO/OkHttp).
 *
 * Auth is handled at the [HttpClient] level via the `defaultRequest` plugin
 * (Bearer token attached to every request), so individual methods here do not
 * need to add auth headers manually.
 */
class TmdbRemoteDataSource(
    private val httpClient: HttpClient,
    private val baseUrl: String,
    private val accountId: String,
    private val sessionId: String
) {
    private val base get() = baseUrl.trimEnd('/')

    suspend fun getUpcomingMovies(
        page: Int,
        language: String = DEFAULT_LANGUAGE
    ): MovieListResponse =
        httpClient.get("$base/movie/upcoming") {
            parameter("page", page)
            parameter("language", language)
        }.body()

    suspend fun getTopRatedMovies(
        page: Int,
        language: String = DEFAULT_LANGUAGE
    ): MovieListResponse =
        httpClient.get("$base/movie/top_rated") {
            parameter("page", page)
            parameter("language", language)
        }.body()

    suspend fun getNowPlayingMovies(
        page: Int,
        language: String = DEFAULT_LANGUAGE
    ): MovieListResponse =
        httpClient.get("$base/movie/now_playing") {
            parameter("page", page)
            parameter("language", language)
        }.body()

    suspend fun getMovieDetails(
        movieId: Int,
        language: String = DEFAULT_LANGUAGE
    ): MovieDetailsResponse =
        httpClient.get("$base/movie/$movieId") {
            parameter("language", language)
        }.body()

    suspend fun getMovieVideos(
        movieId: Int,
        language: String = DEFAULT_LANGUAGE
    ): VideoListResponse =
        httpClient.get("$base/movie/$movieId/videos") {
            parameter("language", language)
        }.body()

    suspend fun markFavorite(mediaId: Int, favorite: Boolean): FavoriteResponse {
        val sessionIdOrNull = sessionId.takeIf { it.isNotEmpty() }
        return httpClient.post("$base/account/$accountId/favorite") {
            sessionIdOrNull?.let { parameter("session_id", it) }
            contentType(ContentType.Application.Json)
            setBody(FavoriteRequest(mediaId = mediaId, favorite = favorite))
        }.body()
    }

    suspend fun getFavoriteMovies(
        page: Int,
        language: String = DEFAULT_LANGUAGE
    ): MovieListResponse {
        val sessionIdOrNull = sessionId.takeIf { it.isNotEmpty() }
        return httpClient.get("$base/account/$accountId/favorite/movies") {
            sessionIdOrNull?.let { parameter("session_id", it) }
            parameter("page", page)
            parameter("language", language)
        }.body()
    }

    companion object {
        /** BCP-47 language tag sent on every TMDB API request. */
        const val DEFAULT_LANGUAGE = "en-US"
    }
}
