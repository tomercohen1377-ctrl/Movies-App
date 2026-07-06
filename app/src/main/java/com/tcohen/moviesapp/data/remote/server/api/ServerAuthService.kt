package com.tcohen.moviesapp.data.remote.server.api

import com.tcohen.moviesapp.data.remote.server.dto.ServerTokenResponse
import com.tcohen.moviesapp.data.remote.server.dto.ServerWhoAmIResponse
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

interface ServerAuthService {

    @POST("auth/register")
    suspend fun register(
        @Header(USER_ID_HEADER) userId: String,
        @Header(PASSWORD_HEADER) password: String,
    ): ServerTokenResponse

    @POST("auth/token")
    suspend fun token(
        @Header(USER_ID_HEADER) userId: String,
        @Header(PASSWORD_HEADER) password: String,
    ): ServerTokenResponse

    @GET("auth/whoami")
    suspend fun whoami(): ServerWhoAmIResponse

    companion object {

        const val USER_ID_HEADER = "X-User-Id"

        const val PASSWORD_HEADER = "X-Password"
    }
}
