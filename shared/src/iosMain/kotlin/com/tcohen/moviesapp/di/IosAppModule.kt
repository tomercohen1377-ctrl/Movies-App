package com.tcohen.moviesapp.di

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.ktor.KtorNetworkFetcherFactory
import com.tcohen.moviesapp.data.remote.TmdbRemoteDataSource
import com.tcohen.moviesapp.data.repository.MovieRepositoryImpl
import com.tcohen.moviesapp.domain.repository.MovieRepository
import com.tcohen.moviesapp.presentation.favorites.FavoritesViewModel
import com.tcohen.moviesapp.presentation.home.HomeViewModel
import com.tcohen.moviesapp.presentation.moviedetail.MovieDetailViewModel
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Creates the iOS Koin app module with the given [config].
 *
 * Provides: Ktor [HttpClient] (Darwin / URLSession), [TmdbRemoteDataSource],
 * Coil [ImageLoader], [MovieRepository], and all three ViewModels.
 */
fun iosAppModule(config: IosAppConfig) = module {

    // ── HTTP client (URLSession via Ktor Darwin engine) ───────────────────────

    single<HttpClient> {
        HttpClient(Darwin) {
            install(DefaultRequest) {
                headers.append("Authorization", "Bearer ${config.tmdbReadAccessToken}")
                contentType(ContentType.Application.Json)
            }
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; coerceInputValues = true })
            }
            if (config.isDebug) {
                install(Logging) {
                    logger = object : Logger {
                        override fun log(message: String) {
                            println("[TMDB_HTTP] $message")
                        }
                    }
                    level = LogLevel.BODY
                }
            }
        }
    }

    // ── Remote data source ────────────────────────────────────────────────────

    single {
        TmdbRemoteDataSource(
            httpClient = get(),
            baseUrl = config.tmdbBaseUrl,
            accountId = config.tmdbAccountId,
            sessionId = config.tmdbSessionId
        )
    }

    // ── Image loader (Coil 3 — Ktor network fetcher for iOS) ─────────────────

    single<ImageLoader> {
        ImageLoader.Builder(PlatformContext.INSTANCE)
            .components { add(KtorNetworkFetcherFactory(httpClient = get())) }
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

    // ── ViewModels ────────────────────────────────────────────���───────────────

    viewModel { HomeViewModel(repository = get(), networkMonitor = get()) }

    viewModel { FavoritesViewModel(repository = get(), networkMonitor = get()) }

    viewModel { (movieId: Int?) -> MovieDetailViewModel(repository = get(), movieId = movieId) }
}
