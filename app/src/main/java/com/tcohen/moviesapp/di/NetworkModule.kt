package com.tcohen.moviesapp.di

import android.content.Context
import android.util.Log
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.tcohen.moviesapp.BuildConfig
import com.tcohen.moviesapp.data.remote.api.TmdbApiService
import com.tcohen.moviesapp.data.remote.interceptor.AuthInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun provideAuthInterceptor(): AuthInterceptor = AuthInterceptor(BuildConfig.TMDB_READ_ACCESS_TOKEN)

    @Provides
    @Singleton
    fun provideOkHttpClient(authInterceptor: AuthInterceptor): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(buildLoggingInterceptor())
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, json: Json): Retrofit {
        val contentType = CONTENT_TYPE_JSON.toMediaType()
        return Retrofit.Builder()
            .baseUrl(BuildConfig.TMDB_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    @Provides
    @Singleton
    fun provideTmdbApiService(retrofit: Retrofit): TmdbApiService =
        retrofit.create(TmdbApiService::class.java)

    /** TMDB account ID used for the favorites endpoints. Defaults to "me" (Bearer token resolves it). */
    @Provides
    @Singleton
    @Named("tmdbAccountId")
    fun provideTmdbAccountId(): String = BuildConfig.TMDB_ACCOUNT_ID

    /**
     * TMDB v3 session ID for write operations (add/remove favorite).
     * Empty string means write-ops are skipped; only GET favorites uses the Bearer token.
     */
    @Provides
    @Singleton
    @Named("tmdbSessionId")
    fun provideTmdbSessionId(): String = BuildConfig.TMDB_SESSION_ID

    /**
     * Provides a Coil [ImageLoader] with persistent image caching:
     *
     * - Uses a **dedicated** [OkHttpClient] (separate from Retrofit's) so API JSON responses
     *   are never accidentally cached — Room handles API data caching.
     * - A network interceptor stamps every response with a 1-day `max-age`, so Coil treats
     *   images as fresh for 24 hours before re-fetching from TMDB's CDN.
     * - **Coil's own `DiskCache`** (100 MB) stores the raw image bytes on disk. Coil checks
     *   the disk cache before making any network request, so cached images load instantly
     *   offline as long as the entry is within the 7-day TTL.
     * - Memory cache holds the most-recently-used decoded bitmaps (25 % of available RAM)
     *   for instant display without disk I/O during the current session.
     */
    @Provides
    @Singleton
    fun provideImageLoader(
        @ApplicationContext context: Context,
        authInterceptor: AuthInterceptor
    ): ImageLoader {
        val imageOkHttpClient = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            // Network interceptor (runs only on live requests).
            // Stamps every fresh response with a 1-day TTL so Coil's DiskCache keeps
            // images for 24 hours before re-fetching from TMDB's CDN.
            .addNetworkInterceptor { chain ->
                chain.proceed(chain.request()).newBuilder()
                    .header(HEADER_CACHE_CONTROL, CACHE_CONTROL_IMAGES)
                    .build()
            }
            .build()

        return ImageLoader.Builder(context)
            .okHttpClient(imageOkHttpClient)
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(IMAGE_CACHE_MAX_SIZE_BYTES)
                    .build()
            }
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizePercent(0.25)
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

/**
 * Builds an [HttpLoggingInterceptor] that:
 * - Logs to Logcat under the tag **TMDB_HTTP** (easy to filter)
 * - Uses [HttpLoggingInterceptor.Level.BODY] in debug builds → prints the full
 *   request line, headers, body AND the full response code, headers, body.
 * - Is completely silent ([HttpLoggingInterceptor.Level.NONE]) in release builds
 *   so no sensitive tokens or payloads appear in production logs.
 *
 * Example Logcat output for a request:
 * ```
 * D  TMDB_HTTP  --> GET https://api.themoviedb.org/3/movie/popular?page=1
 * D  TMDB_HTTP  Authorization: Bearer eyJ...
 * D  TMDB_HTTP  --> END GET
 * D  TMDB_HTTP  <-- 200 OK https://api.themoviedb.org/3/movie/popular?page=1 (342ms)
 * D  TMDB_HTTP  {"page":1,"results":[...]}
 * D  TMDB_HTTP  <-- END HTTP (3421-byte body)
 * ```
 */
private fun buildLoggingInterceptor(): HttpLoggingInterceptor =
    HttpLoggingInterceptor { message ->
        Log.d(NetworkModule.LOG_TAG, message)
    }.apply {
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BODY
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }
