package com.tcohen.moviesapp.di

import android.content.Context
import android.util.Log
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import okio.Path.Companion.toPath
import com.tcohen.moviesapp.BuildConfig
import com.tcohen.moviesapp.data.remote.TmdbRemoteDataSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /**
     * Ktor [HttpClient] backed by the OkHttp engine.
     *
     * Auth is attached via [DefaultRequest] so every request automatically
     * carries the TMDB Bearer token — no interceptor class needed.
     */
    @Provides
    @Singleton
    fun provideHttpClient(): HttpClient = HttpClient(OkHttp) {
        install(DefaultRequest) {
            headers.append("Authorization", "Bearer ${BuildConfig.TMDB_READ_ACCESS_TOKEN}")
            contentType(ContentType.Application.Json)
        }
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; coerceInputValues = true })
        }
        install(Logging) {
            logger = object : Logger {
                override fun log(message: String) {
                    Log.d(LOG_TAG, message)
                }
            }
            level = if (BuildConfig.DEBUG) LogLevel.BODY else LogLevel.NONE
        }
    }

    /**
     * Shared multiplatform data source that translates TMDB API calls
     * into [com.tcohen.moviesapp.data.remote.dto] DTOs.
     */
    @Provides
    @Singleton
    fun provideTmdbRemoteDataSource(httpClient: HttpClient): TmdbRemoteDataSource =
        TmdbRemoteDataSource(
            httpClient = httpClient,
            baseUrl = BuildConfig.TMDB_BASE_URL,
            accountId = BuildConfig.TMDB_ACCOUNT_ID,
            sessionId = BuildConfig.TMDB_SESSION_ID
        )

    /**
     * Provides a Coil 3 [ImageLoader] with persistent image caching.
     *
     * Uses a dedicated [OkHttpClient] (separate from the Ktor client) so API
     * JSON responses are never accidentally cached. Images from TMDB's CDN are
     * stamped with a 1-day `max-age` and stored in a 100 MB disk cache.
     */
    @Provides
    @Singleton
    fun provideImageLoader(@ApplicationContext context: Context): ImageLoader {
        val imageOkHttpClient = OkHttpClient.Builder()
            .addNetworkInterceptor { chain ->
                chain.proceed(chain.request()).newBuilder()
                    .header(HEADER_CACHE_CONTROL, CACHE_CONTROL_IMAGES)
                    .build()
            }
            .build()

        return ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = imageOkHttpClient))
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache").absolutePath.toPath())
                    .maxSizeBytes(IMAGE_CACHE_MAX_SIZE_BYTES)
                    .build()
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.25)
                    .build()
            }
            .crossfade(true)
            .build()
    }

    const val HEADER_CACHE_CONTROL = "Cache-Control"

    /** 1-day TTL for image responses (86 400 seconds = 60 × 60 × 24). */
    const val CACHE_CONTROL_IMAGES = "max-age=86400"

    const val CONTENT_TYPE_JSON = "application/json"
    const val IMAGE_CACHE_MAX_SIZE_BYTES = 100L * 1024 * 1024

    /**
     * Logcat tag used for all HTTP traffic logs.
     * Filter in Logcat with: tag:TMDB_HTTP
     */
    const val LOG_TAG = "TMDB_HTTP"
}
