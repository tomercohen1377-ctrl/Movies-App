package com.tcohen.moviesapp.di

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.tcohen.moviesapp.BuildConfig
import com.tcohen.moviesapp.data.remote.api.TmdbApiService
import com.tcohen.moviesapp.data.remote.interceptor.AuthInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
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
    fun provideAuthInterceptor(): AuthInterceptor = AuthInterceptor(BuildConfig.TMDB_API_KEY)

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
    fun provideTmdbApiService(retrofit: Retrofit): TmdbApiService {
        return retrofit.create(TmdbApiService::class.java)
    }

    // -------------------------------------------------------------------------
    // Constants — directly in the object since Kotlin objects have no companion
    // -------------------------------------------------------------------------

    /** OkHttp header name for HTTP cache control directives. */
    const val HEADER_CACHE_CONTROL = "Cache-Control"

    /**
     * Instructs caches to store responses for exactly one day (86 400 s)
     * and revalidate afterwards. Applied to every network response.
     */
    const val CACHE_CONTROL_ONE_DAY = "max-age=86400, must-revalidate"

    /** MIME type used for JSON request/response bodies with Retrofit. */
    const val CONTENT_TYPE_JSON = "application/json"

    /**
     * Maximum disk-cache budget for Coil's image cache (100 MB).
     * Long to avoid silent Int overflow when computing byte sizes.
     */
    const val IMAGE_CACHE_MAX_SIZE_BYTES = 100L * 1024 * 1024
}
