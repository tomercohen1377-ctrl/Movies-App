package com.tcohen.moviesapp.di

import coil3.SingletonImageLoader
import org.koin.core.context.startKoin

/**
 * Initialises Koin for the iOS app.
 *
 * Must be called **before** [MainViewController] is created.
 * Typically called from Swift in `iOSApp.init()`:
 *
 * ```swift
 * KoinHelperKt.doInitKoin(config: IosAppConfig(
 *     tmdbBaseUrl: TmdbConfig.baseUrl,
 *     tmdbReadAccessToken: TmdbConfig.readAccessToken,
 *     tmdbAccountId: TmdbConfig.accountId,
 *     tmdbSessionId: TmdbConfig.sessionId,
 *     isDebug: true
 * ))
 * ```
 */
fun initKoin(config: IosAppConfig) {
    val koinApp = startKoin {
        modules(iosSharedModule, iosAppModule(config))
    }

    // Register Coil's singleton ImageLoader from the Koin graph so AsyncImage works everywhere.
    SingletonImageLoader.setSafe {
        koinApp.koin.get()
    }
}
