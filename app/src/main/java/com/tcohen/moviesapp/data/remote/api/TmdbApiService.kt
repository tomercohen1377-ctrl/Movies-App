package com.tcohen.moviesapp.data.remote.api

import com.tcohen.moviesapp.data.remote.dto.MovieDetailDto
import com.tcohen.moviesapp.data.remote.dto.MovieListResponseDto
import com.tcohen.moviesapp.data.remote.dto.VideoListResponseDto
import retrofit2.http.GET
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

    companion object {
        /** BCP-47 language tag sent on every TMDB API request. */
        const val DEFAULT_LANGUAGE = "en-US"

        /** Default page number for paginated endpoints (TMDB uses 1-based pages). */
        const val DEFAULT_PAGE = 1
    }
}
