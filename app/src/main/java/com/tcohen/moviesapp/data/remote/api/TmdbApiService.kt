package com.tcohen.moviesapp.data.remote.api

import com.tcohen.moviesapp.data.remote.dto.FavoriteRequest
import com.tcohen.moviesapp.data.remote.dto.FavoriteResponse
import com.tcohen.moviesapp.data.remote.dto.MovieDetailsResponse
import com.tcohen.moviesapp.data.remote.dto.MovieListResponse
import com.tcohen.moviesapp.data.remote.dto.VideoListResponse
import com.tcohen.moviesapp.data.remote.paging.PagingDefaults
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
    ): MovieListResponse

    @GET("movie/top_rated")
    suspend fun getTopRatedMovies(
        @Query("page") page: Int = DEFAULT_PAGE,
        @Query("language") language: String = DEFAULT_LANGUAGE
    ): MovieListResponse

    @GET("movie/now_playing")
    suspend fun getNowPlayingMovies(
        @Query("page") page: Int = DEFAULT_PAGE,
        @Query("language") language: String = DEFAULT_LANGUAGE
    ): MovieListResponse

    @GET("movie/{movie_id}")
    suspend fun getMovieDetails(
        @Path("movie_id") movieId: Int,
        @Query("language") language: String = DEFAULT_LANGUAGE
    ): MovieDetailsResponse

    @GET("movie/{movie_id}/videos")
    suspend fun getMovieVideos(
        @Path("movie_id") movieId: Int,
        @Query("language") language: String = DEFAULT_LANGUAGE
    ): VideoListResponse

    @POST("account/{account_id}/favorite")
    suspend fun markFavorite(
        @Path("account_id") accountId: String,
        @Query("session_id") sessionId: String? = null,
        @Body body: FavoriteRequest
    ): FavoriteResponse

    @GET("account/{account_id}/favorite/movies")
    suspend fun getFavoriteMovies(
        @Path("account_id") accountId: String,
        @Query("session_id") sessionId: String? = null,
        @Query("page") page: Int = DEFAULT_PAGE,
        @Query("language") language: String = DEFAULT_LANGUAGE
    ): MovieListResponse


    companion object {
        /** BCP-47 language tag sent on every TMDB API request. */
        const val DEFAULT_LANGUAGE = "en-US"

        /** Default page number for paginated endpoints — see [PagingDefaults.STARTING_PAGE_INDEX]. */
        const val DEFAULT_PAGE = PagingDefaults.STARTING_PAGE_INDEX
    }
}
