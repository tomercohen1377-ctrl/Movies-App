package com.tcohen.moviesapp.di

import android.util.Log
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.tcohen.moviesapp.BuildConfig
import com.tcohen.moviesapp.data.remote.server.ServerDefaults
import com.tcohen.moviesapp.data.remote.server.api.ServerApiService
import com.tcohen.moviesapp.data.remote.server.api.ServerAuthService
import com.tcohen.moviesapp.data.remote.server.interceptor.ServerBearerInterceptor
import com.tcohen.moviesapp.data.remote.server.interceptor.ServerTokenAuthenticator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ServerNetworkModule {

    @Provides
    @Singleton
    @ServerOkHttpClient
    fun provideServerOkHttpClient(
        bearerInterceptor: ServerBearerInterceptor,
        authenticator: ServerTokenAuthenticator,
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(bearerInterceptor)
            .addInterceptor(serverLoggingInterceptor())
            .authenticator(authenticator)
            .connectTimeout(ServerDefaults.SERVER_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(ServerDefaults.SERVER_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .writeTimeout(ServerDefaults.SERVER_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideServerApiService(
        @ServerOkHttpClient okHttpClient: OkHttpClient,
        json: Json,
    ): ServerApiService = buildServerRetrofit(okHttpClient, json)
        .create(ServerApiService::class.java)

    @Provides
    @Singleton
    fun provideServerAuthService(
        @ServerOkHttpClient okHttpClient: OkHttpClient,
        json: Json,
    ): ServerAuthService = buildServerRetrofit(okHttpClient, json)
        .create(ServerAuthService::class.java)

    private fun buildServerRetrofit(
        okHttpClient: OkHttpClient,
        json: Json,
    ): Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.SERVER_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory(CONTENT_TYPE_JSON.toMediaType()))
        .build()

    private fun serverLoggingInterceptor(): okhttp3.Interceptor =
        okhttp3.Interceptor { chain ->
            val redacted = redactBearer(chain.request())
            val response = chain.proceed(redacted)
            Log.d(LOG_TAG, "--> ${redacted.method} ${redacted.url}")
            response
        }

    private fun redactBearer(request: okhttp3.Request): okhttp3.Request {
        val authHeader = request.header(ServerDefaults.AUTH_HEADER)
        if (authHeader.isNullOrEmpty()) return request
        if (!authHeader.startsWith(ServerDefaults.BEARER_PREFIX)) return request
        return request.newBuilder()
            .header(ServerDefaults.AUTH_HEADER, ServerDefaults.AUTH_HEADER_REDACTED_VALUE)
            .build()
    }

    private const val CONTENT_TYPE_JSON = "application/json"
    private const val LOG_TAG = "SERVER_HTTP"
}

@Retention(AnnotationRetention.BINARY)
@javax.inject.Qualifier
annotation class ServerOkHttpClient
