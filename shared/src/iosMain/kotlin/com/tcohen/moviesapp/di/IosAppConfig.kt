package com.tcohen.moviesapp.di

/**
 * App configuration values supplied by the iOS host (Swift) before Koin starts.
 *
 * Pass these from your Swift `iOSApp.init()` — see [Config.swift] in the `iosApp/` module.
 */
data class IosAppConfig(
    val tmdbBaseUrl: String,
    val tmdbReadAccessToken: String,
    val tmdbAccountId: String,
    val tmdbSessionId: String,
    val isDebug: Boolean = false
)
