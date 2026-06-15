package com.tcohen.moviesapp.di

import com.tcohen.moviesapp.data.local.DatabaseDriverFactory
import com.tcohen.moviesapp.data.local.LocalMovieDataSource
import com.tcohen.moviesapp.data.local.db.MoviesDatabase
import com.tcohen.moviesapp.util.IosNetworkStatusProvider
import com.tcohen.moviesapp.util.NetworkStatusProvider
import org.koin.dsl.module

/**
 * Koin module for iOS-platform dependencies:
 * - SQLDelight database (NativeSqliteDriver)
 * - [LocalMovieDataSource]
 * - [NetworkStatusProvider] backed by Apple's NWPathMonitor
 */
val iosSharedModule = module {

    // ── Database ──────────────────────────────────────────────────────────────

    single {
        val driver = DatabaseDriverFactory().createDriver()
        MoviesDatabase(driver)
    }

    single { LocalMovieDataSource(get()) }

    // ── Network status ─────────���──────────────────────────────────────────────

    single<NetworkStatusProvider> { IosNetworkStatusProvider() }
}
