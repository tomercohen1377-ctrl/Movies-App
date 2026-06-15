import SwiftUI
import shared

@main
struct iOSApp: App {

    init() {
        // Initialize Koin with TMDB credentials before any Compose UI is created.
        // Edit Config.swift to set your real credentials.
        KoinHelperKt.doInitKoin(
            config: IosAppConfig(
                tmdbBaseUrl: TmdbConfig.baseUrl,
                tmdbReadAccessToken: TmdbConfig.readAccessToken,
                tmdbAccountId: TmdbConfig.accountId,
                tmdbSessionId: TmdbConfig.sessionId,
                isDebug: TmdbConfig.isDebug
            )
        )
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
