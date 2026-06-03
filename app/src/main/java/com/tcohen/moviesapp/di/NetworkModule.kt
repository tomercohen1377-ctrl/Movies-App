package com.tcohen.moviesapp.di

import android.content.Context
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
            .addNetworkInterceptor { chain ->
                // Enforce 1-day cache TTL on every network response
                chain.proceed(chain.request()).newBuilder()
                    .header(HEADER_CACHE_CONTROL, CACHE_CONTROL_ONE_DAY)
                    .build()
            }
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = if (BuildConfig.DEBUG) {
                        HttpLoggingInterceptor.Level.BODY
                    } else {
                        HttpLoggingInterceptor.Level.NONE
                    }
                }
            )
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

    /**
     * Provides a Coil [ImageLoader] that:
     * - Shares the same [OkHttpClient] as Retrofit (unified cache budget + auth headers)
     * - Stores images on disk for up to 1 day (enforced by OkHttp's Cache-Control interceptor)
     * - Uses a 100 MB disk cache and a 25% memory cache
     */
    @Provides
    @Singleton
    fun provideImageLoader(
        @ApplicationContext context: Context,
        okHttpClient: OkHttpClient
    ): ImageLoader = ImageLoader.Builder(context)
        .okHttpClient(okHttpClient)
        .memoryCache {
            MemoryCache.Builder(context)
                .maxSizePercent(0.25)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(context.cacheDir.resolve("image_cache"))
                .maxSizeBytes(IMAGE_CACHE_MAX_SIZE_BYTES)
                .build()
        }
        .crossfade(true)
        .build()

    const val HEADER_CACHE_CONTROL = "Cache-Control"
    const val CACHE_CONTROL_ONE_DAY = "max-age=86400, must-revalidate"
    const val CONTENT_TYPE_JSON = "application/json"
    const val IMAGE_CACHE_MAX_SIZE_BYTES = 100L * 1024 * 1024
}
