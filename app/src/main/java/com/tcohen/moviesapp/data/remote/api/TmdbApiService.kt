package com.tcohen.moviesapp.data.remote.api

import com.tcohen.moviesapp.data.remote.dto.FavoriteRequestDto
import com.tcohen.moviesapp.data.remote.dto.FavoriteResponseDto
import com.tcohen.moviesapp.data.remote.dto.MovieDetailDto
import com.tcohen.moviesapp.data.remote.dto.MovieListResponseDto
import com.tcohen.moviesapp.data.remote.dto.RemoveFromListRequestDto
import com.tcohen.moviesapp.data.remote.dto.VideoListResponseDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface TmdbApiService {

    @GET("movie/upcoming")
    suspend fun getUpcomingMovies(
        @Query("page") page: Int = DEFAULT_PAGE,
        @Query("language") language: String = DEFAULT_LANGUAGE
    ): MovieListResponseDto

    @GET("movie/top_rated")
    suspend fun getTopRatedMovies(
        @Query("page") page: Int = DEFAULT_PAGE,
        @Query("language") language: String = DEFAULT_LANGUAGE
    ): MovieListResponseDto

    @GET("movie/now_playing")
    suspend fun getNowPlayingMovies(
        @Query("page") page: Int = DEFAULT_PAGE,
        @Query("language") language: String = DEFAULT_LANGUAGE
    ): MovieListResponseDto

    @GET("movie/{movie_id}")
    suspend fun getMovieDetail(
        @Path("movie_id") movieId: Int,
        @Query("language") language: String = DEFAULT_LANGUAGE
    ): MovieDetailDto

    @GET("movie/{movie_id}/videos")
    suspend fun getMovieVideos(
        @Path("movie_id") movieId: Int,
        @Query("language") language: String = DEFAULT_LANGUAGE
    ): VideoListResponseDto

    // ── Account / Favorites ───────────────────────────────────────────────────

    /**
     * Add or remove a movie from the account's TMDB favorites list.
     *
     * `POST /account/{account_id}/favorite`
     *
     * Pass [sessionId] for v3 session auth (required for write access).
     * The Bearer token added by `AuthInterceptor` is sufficient for accounts
     * that use v4 user-authenticated tokens.
     */
    @POST("account/{account_id}/favorite")
    suspend fun markFavorite(
        @Path("account_id") accountId: String,
        @Query("session_id") sessionId: String? = null,
        @Body body: FavoriteRequestDto
    ): FavoriteResponseDto

    /**
     * Fetch the paginated list of movies the account has marked as favorites.
     *
     * `GET /account/{account_id}/favorite/movies`
     */
    @GET("account/{account_id}/favorite/movies")
    suspend fun getFavoriteMovies(
        @Path("account_id") accountId: String,
        @Query("session_id") sessionId: String? = null,
        @Query("page") page: Int = DEFAULT_PAGE,
        @Query("language") language: String = DEFAULT_LANGUAGE
    ): MovieListResponseDto

    /**
     * Remove a movie from a TMDB list (e.g. the account's favorites list).
     *
     * `POST /list/{list_id}/remove_item`
     *
     * Requires [sessionId] for v3 authentication.
     */
    @POST("list/{list_id}/remove_item")
    suspend fun removeFromList(
        @Path("list_id") listId: String,
        @Query("session_id") sessionId: String? = null,
        @Body body: RemoveFromListRequestDto
    ): FavoriteResponseDto

    companion object {
        /** BCP-47 language tag sent on every TMDB API request. */
        const val DEFAULT_LANGUAGE = "en-US"

        /** Default page number for paginated endpoints (TMDB uses 1-based pages). */
        const val DEFAULT_PAGE = 1
    }
}
