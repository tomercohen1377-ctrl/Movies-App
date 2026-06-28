package com.tcohen.moviesapp.di

import com.tcohen.moviesapp.BuildConfig
import com.tcohen.moviesapp.ai.data.local.cache.InMemoryLlmResponseCache
import com.tcohen.moviesapp.ai.data.local.cache.LlmResponseCache
import com.tcohen.moviesapp.ai.data.remote.client.OpenAiCompatibleLlmClient
import com.tcohen.moviesapp.ai.data.remote.interceptor.LlmAuthInterceptor
import com.tcohen.moviesapp.ai.domain.client.LlmClient
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

/**
 * Hilt module wiring the LLM layer.
 *
 * Swap providers by replacing the two `@Named` strings below — every code path
 * above (chat, plot explainer, semantic recs) consumes `LlmClient` and is
 * unaware of the underlying provider.
 *
 * - **Hosted (Phase 0 default):** Google Gemini `gemini-2.0-flash` (free tier)
 *   via the OpenAI-compatible schema at `…/v1beta/openai/`. Drop the same
 *   client on Groq, OpenRouter, OpenAI — only the base URL and key change.
 * - **Local (Phase 5):** `MediaPipeLlmClient` will be added as an alternate
 *   `LlmClient` binding behind a `Settings` toggle; the binding for
 *   [LlmClient] flips via a `MutableStateFlow<LlmProvider>` in Phase 5.
 */
@Module
@InstallIn(SingletonComponent::class)
object LlmModule {

    @Provides
    @Singleton
    fun provideLlmAuthInterceptor(): LlmAuthInterceptor =
        LlmAuthInterceptor(apiKey = BuildConfig.GEMINI_API_KEY)

    /** OpenAI-compatible base URL. Defaults to Gemini's OpenAI-shaped endpoint. */
    @Provides
    @Singleton
    @Named("llmBaseUrl")
    fun provideLlmBaseUrl(): String = BuildConfig.LLM_BASE_URL

    /** Default model identifier. Per-request overrides via [com.tcohen.moviesapp.ai.domain.model.ChatRequest.model]. */
    @Provides
    @Singleton
    @Named("llmModel")
    fun provideDefaultModel(): String = BuildConfig.LLM_DEFAULT_MODEL

    @Provides
    @Singleton
    fun provideLlmResponseCache(): LlmResponseCache = InMemoryLlmResponseCache()
}

/**
 * Binds the [OpenAiCompatibleLlmClient] as the concrete implementation of
 * [LlmClient]. Phase 5 will introduce a decorator (`SwitchingLlmClient`) that
 * delegates to either this hosted client or an on-device client based on a
 * user setting.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class LlmBindingsModule {

    @Binds
    @Singleton
    abstract fun bindLlmClient(impl: OpenAiCompatibleLlmClient): LlmClient
}
