package com.tcohen.moviesapp.ai.data.remote.interceptor

import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Attaches a Bearer token to every outgoing LLM request.
 *
 * - Provider default: Google Gemini (OpenAI-compatible schema). The token is the
 *   `GEMINI_API_KEY` placed in `BuildConfig` by `app/build.gradle.kts`.
 * - Swap providers = swap base URL + key in [com.tcohen.moviesapp.di.LlmModule];
 *   this interceptor is unchanged.
 */
@Singleton
class LlmAuthInterceptor @Inject constructor(
    val apiKey: String
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header(HEADER_AUTHORIZATION, "Bearer $apiKey")
            .build()
        return chain.proceed(request)
    }

    companion object {
        const val HEADER_AUTHORIZATION = "Authorization"
    }
}
