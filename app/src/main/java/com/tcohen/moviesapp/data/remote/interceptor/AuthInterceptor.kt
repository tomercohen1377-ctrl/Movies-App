package com.tcohen.moviesapp.data.remote.interceptor

import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/**
 * Attaches a TMDB Bearer token to every outgoing request via the
 * `Authorization` header — the modern, recommended auth method for TMDB v3.
 */
class AuthInterceptor @Inject constructor(private val readAccessToken: String) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header(HEADER_AUTHORIZATION, "Bearer $readAccessToken")
            .build()
        return chain.proceed(request)
    }

    companion object {
        const val HEADER_AUTHORIZATION = "Authorization"
    }
}
