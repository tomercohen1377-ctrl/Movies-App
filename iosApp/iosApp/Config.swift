import Foundation

/// TMDB API credentials for the iOS target.
///
/// **Security note:** Do NOT commit real credentials to source control.
/// In production, load these from a local `.xcconfig` file that is `.gitignore`d:
///   1. Create `iosApp/Config.xcconfig` (gitignored)
///   2. Add `TMDB_BASE_URL = https://api.themoviedb.org/3/` etc.
///   3. Reference with `$(TMDB_BASE_URL)` in `Info.plist` and read via `Bundle.main.infoDictionary`.
enum TmdbConfig {
    /// e.g. "https://api.themoviedb.org/3/"
    static let baseUrl = "https://api.themoviedb.org/3/"

    /// Your TMDB Read Access Token (v4 auth — long JWT string)
    static let readAccessToken = "YOUR_TMDB_READ_ACCESS_TOKEN_HERE"

    /// Your TMDB account ID (numeric string)
    static let accountId = "YOUR_TMDB_ACCOUNT_ID_HERE"

    /// Your TMDB session ID
    static let sessionId = "YOUR_TMDB_SESSION_ID_HERE"

    /// Set to true during development to enable HTTP request logging
    static let isDebug = true
}
