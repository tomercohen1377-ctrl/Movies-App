package com.tcohen.moviesapp.data.remote.api

import com.tcohen.moviesapp.data.remote.dto.MovieDetailsResponse
import com.tcohen.moviesapp.data.remote.dto.MovieListResponse
import com.tcohen.moviesapp.data.remote.dto.VideoListResponse
import com.tcohen.moviesapp.data.remote.paging.PagingDefaults
import retrofit2.http.GET
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

    companion object {

        const val DEFAULT_LANGUAGE = "en-US"

        const val DEFAULT_PAGE = PagingDefaults.STARTING_PAGE_INDEX
    }
}
