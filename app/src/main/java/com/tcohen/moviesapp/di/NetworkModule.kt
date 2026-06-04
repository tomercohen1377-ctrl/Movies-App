package com.tcohen.moviesapp.di

import android.content.Context
import android.util.Log
import coil.ImageLoader
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
import okhttp3.Cache
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
     * Numeric TMDB list ID used for `POST /list/{list_id}/remove_item`.
     * When empty, removal falls back to `POST /account/{account_id}/favorite` with `favorite=false`.
     */
    @Provides
    @Singleton
    @Named("tmdbFavoritesListId")
    fun provideTmdbFavoritesListId(): String = BuildConfig.TMDB_FAVORITES_LIST_ID

    /**
     * Provides a Coil [ImageLoader] with proper 1-day image caching:
     *
     * - Uses a **dedicated** [OkHttpClient] (separate from Retrofit's) so API JSON responses
     *   are never accidentally cached through OkHttp — that is handled by Room.
     * - Configures an OkHttp [Cache] (100 MB on disk). OkHttp respects the
     *   `Cache-Control: max-age=86400` header added by the network interceptor, so images
     *   are served from disk for up to 1 day before being re-fetched from TMDB's CDN.
     * - Memory cache holds the most-recently-used decoded bitmaps (25 % of available RAM)
     *   for instant display without any disk I/O.
     * - `diskCachePolicy = DISABLED` on the Coil level tells Coil to delegate all disk
     *   caching to OkHttp, avoiding double-caching of the raw bytes.
     */
    @Provides
    @Singleton
    fun provideImageLoader(
        @ApplicationContext context: Context,
        authInterceptor: AuthInterceptor
    ): ImageLoader {
        val imageHttpCache = Cache(
            directory = context.cacheDir.resolve("image_cache"),
            maxSize = IMAGE_CACHE_MAX_SIZE_BYTES
        )

        val imageOkHttpClient = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addNetworkInterceptor { chain ->
                // Force a 1-day TTL on every image response so OkHttp's disk cache
                // keeps the file for exactly 24 hours before re-validating with the CDN.
                chain.proceed(chain.request()).newBuilder()
                    .header(HEADER_CACHE_CONTROL, CACHE_CONTROL_ONE_DAY)
                    .build()
            }
            .cache(imageHttpCache)
            .build()

        return ImageLoader.Builder(context)
            .okHttpClient(imageOkHttpClient)
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizePercent(0.25)
                    .build()
            }
            // No Coil-level disk cache — OkHttp's Cache (above) owns disk storage.
            // This prevents the same bytes from being written to disk twice.
            .diskCachePolicy(coil.request.CachePolicy.DISABLED)
            .crossfade(true)
            .build()
    }

    const val HEADER_CACHE_CONTROL = "Cache-Control"
    const val CACHE_CONTROL_ONE_DAY = "max-age=86400, must-revalidate"
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
