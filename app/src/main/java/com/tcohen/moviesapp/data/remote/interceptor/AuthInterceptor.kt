package com.tcohen.moviesapp.data.remote.interceptor

import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/**
 * Appends the TMDB API key to every outgoing request as a query parameter.
 */
class AuthInterceptor @Inject constructor(private val apiKey: String) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val url = originalRequest.url.newBuilder()
            .addQueryParameter(QUERY_PARAM_API_KEY, apiKey)
            .build()
        return chain.proceed(originalRequest.newBuilder().url(url).build())
    }

    companion object {
        /** TMDB query parameter name that carries the API key on every request. */
        const val QUERY_PARAM_API_KEY = "api_key"
    }
}
