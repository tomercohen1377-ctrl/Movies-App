package com.tcohen.moviesapp.di

import com.tcohen.moviesapp.BuildConfig
import com.tcohen.moviesapp.ai.data.local.cache.InMemoryLlmResponseCache
import com.tcohen.moviesapp.ai.data.local.cache.LlmResponseCache
import com.tcohen.moviesapp.ai.data.remote.client.OpenAiCompatibleLlmClient
import com.tcohen.moviesapp.ai.data.remote.interceptor.LlmAuthInterceptor
import com.tcohen.moviesapp.ai.data.repository.SystemTimeProvider
import com.tcohen.moviesapp.ai.data.repository.TimeProvider
import com.tcohen.moviesapp.ai.domain.client.LlmClient
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LlmModule {

    @Provides
    @Singleton
    fun provideLlmAuthInterceptor(): LlmAuthInterceptor =
        LlmAuthInterceptor(apiKey = BuildConfig.GEMINI_API_KEY)

    @Provides
    @Singleton
    @Named("llmBaseUrl")
    fun provideLlmBaseUrl(): String = BuildConfig.LLM_BASE_URL

    @Provides
    @Singleton
    @Named("llmModel")
    fun provideDefaultModel(): String = BuildConfig.LLM_DEFAULT_MODEL

    @Provides
    @Singleton
    fun provideLlmResponseCache(): LlmResponseCache = InMemoryLlmResponseCache()

    @Provides
    @Singleton
    fun provideTimeProvider(): TimeProvider = SystemTimeProvider
}

@Module
@InstallIn(SingletonComponent::class)
abstract class LlmBindingsModule {

    @Binds
    @Singleton
    abstract fun bindLlmClient(impl: OpenAiCompatibleLlmClient): LlmClient
}
