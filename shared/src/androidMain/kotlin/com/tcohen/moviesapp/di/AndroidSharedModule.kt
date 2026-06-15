package com.tcohen.moviesapp.di

import com.tcohen.moviesapp.data.local.DatabaseDriverFactory
import com.tcohen.moviesapp.data.local.LocalMovieDataSource
import com.tcohen.moviesapp.data.local.db.MoviesDatabase
import com.tcohen.moviesapp.util.NetworkMonitor
import com.tcohen.moviesapp.util.NetworkStatusProvider
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Koin module for Android-platform dependencies that are shared across the app but
 * require Android-specific constructors (Context, AndroidSqliteDriver, etc.).
 *
 * Loaded by [MoviesApplication] alongside the app-level module at startup.
 */
val androidSharedModule = module {

    // ── Database ──────────────────────────────────────────────────────────────

    single {
        val driver = DatabaseDriverFactory(androidContext()).createDriver()
        MoviesDatabase(driver)
    }

    single { LocalMovieDataSource(get()) }

    // ── Network status ────────────────────────────────────────────────────────

    single<NetworkStatusProvider> { NetworkMonitor(androidContext()) }
}
