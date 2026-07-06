package com.tcohen.moviesapp.data.remote.server.api

import com.tcohen.moviesapp.data.remote.server.dto.ServerAddFavoriteResponse
import com.tcohen.moviesapp.data.remote.server.dto.ServerFavoriteDto
import com.tcohen.moviesapp.data.remote.server.dto.ServerIsFavoriteResponse
import retrofit2.Response
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface ServerApiService {

    @GET(FAVORITES_PATH)
    suspend fun getFavorites(@Path("userId") userId: String): List<ServerFavoriteDto>

    @GET(FAVORITES_PATH + "/{movieId}")
    suspend fun isFavorite(
        @Path("userId") userId: String,
        @Path("movieId") movieId: Int,
    ): Response<ServerIsFavoriteResponse>

    @POST(FAVORITES_PATH + "/{movieId}")
    suspend fun addFavorite(
        @Path("userId") userId: String,
        @Path("movieId") movieId: Int,
    ): ServerAddFavoriteResponse

    @DELETE(FAVORITES_PATH + "/{movieId}")
    suspend fun removeFavorite(
        @Path("userId") userId: String,
        @Path("movieId") movieId: Int,
    ): Response<Unit>

    companion object {

        const val FAVORITES_PATH = "users/{userId}/favorites"
    }
}
