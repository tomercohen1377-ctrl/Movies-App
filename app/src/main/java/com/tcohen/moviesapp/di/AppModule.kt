package com.tcohen.moviesapp.di

import android.util.Log
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.tcohen.moviesapp.BuildConfig
import com.tcohen.moviesapp.data.remote.TmdbRemoteDataSource
import com.tcohen.moviesapp.data.repository.MovieRepositoryImpl
import com.tcohen.moviesapp.domain.repository.MovieRepository
import com.tcohen.moviesapp.presentation.favorites.FavoritesViewModel
import com.tcohen.moviesapp.presentation.home.HomeViewModel
import com.tcohen.moviesapp.presentation.moviedetail.MovieDetailViewModel
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
import okio.Path.Companion.toPath
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * App-level Koin module.
 *
 * Provides: HttpClient, TmdbRemoteDataSource, ImageLoader, MovieRepository,
 * and all three ViewModels. Platform dependencies (database, network status)
 * come from [androidSharedModule].
 */
val appModule = module {

    // ── HTTP client ───────────────────────────────────────────────────────────

    single<HttpClient> {
        HttpClient(OkHttp) {
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
                        Log.d("TMDB_HTTP", message)
                    }
                }
                level = if (BuildConfig.DEBUG) LogLevel.BODY else LogLevel.NONE
            }
        }
    }

    // ── Remote data source ────────────────────────────────────────────────────

    single {
        TmdbRemoteDataSource(
            httpClient = get(),
            baseUrl = BuildConfig.TMDB_BASE_URL,
            accountId = BuildConfig.TMDB_ACCOUNT_ID,
            sessionId = BuildConfig.TMDB_SESSION_ID
        )
    }

    // ── Image loader ──────────────────────────────────────────────────────────

    single<ImageLoader> {
        val context = androidContext()
        val imageOkHttpClient = OkHttpClient.Builder()
            .addNetworkInterceptor { chain ->
                chain.proceed(chain.request()).newBuilder()
                    .header("Cache-Control", "max-age=86400")
                    .build()
            }
            .build()

        ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = imageOkHttpClient))
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache").absolutePath.toPath())
                    .maxSizeBytes(100L * 1024 * 1024)
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

    // ── Repository ────────────────────────────────────────────────────────────

    single<MovieRepository> {
        MovieRepositoryImpl(
            remoteDataSource = get(),
            localDataSource = get(),
            networkStatusProvider = get()
        )
    }

    // ── ViewModels ────────────────────────────────────────────────────────────

    viewModel { HomeViewModel(repository = get(), networkMonitor = get()) }

    viewModel { FavoritesViewModel(repository = get(), networkMonitor = get()) }

    // MovieDetailViewModel receives movieId as a Koin parameter (passed from AppNavGraph)
    viewModel { (movieId: Int?) -> MovieDetailViewModel(repository = get(), movieId = movieId) }
}
