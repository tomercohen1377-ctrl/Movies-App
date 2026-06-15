# Movies App — Kotlin Multiplatform + Compose Multiplatform

A production-quality **KMP + CMP** app that browses movies using [The Movie Database (TMDB) API](https://www.themoviedb.org/documentation/api).
Built with Clean Architecture, MVI, Compose Multiplatform, Ktor, SQLDelight, and Koin — targeting **Android and iOS** from a single shared codebase.

> Originally an Android-only app; fully migrated to KMP/CMP. See [`docs/KMP_CMP_MIGRATION_GUIDE.md`](docs/KMP_CMP_MIGRATION_GUIDE.md) for the complete migration log.

---

## Screenshots

<p float="left">
  <img src="assets/home.png" width="300" />
  <img src="assets/details.png" width="300" />
  <img src="assets/favorites.png" width="300" />
  <img src="assets/offline.png" width="300" />
</p>

---

## Table of Contents

- [Features](#features)
- [Architecture](#architecture)
- [Tech Stack](#tech-stack)
- [Project Structure](#project-structure)
- [Getting Started](#getting-started)
- [Caching Strategy](#caching-strategy)
- [Offline Behaviour](#offline-behaviour)
- [Error Handling](#error-handling)
- [Testing](#testing)
- [iOS Limitations](#ios-limitations)

---

## Features

| Feature | Detail |
|---|---|
| **2-tab bottom navigation** | Home (movie grid) + Favorites |
| **Category filter chips** | Now Playing · Top Rated · Upcoming — defaults to Now Playing |
| **Infinite scroll / Paging** | 20 items per page, prefetches the next page automatically |
| **Full-bleed poster cards** | 2:3 aspect-ratio cards with gradient scrim, rating badge, and swipe-to-remove on Favorites |
| **Movie detail screen** | YouTube trailer at the top, then metadata: title, year, runtime, rating, genre chips, tagline, overview |
| **Slide navigation animation** | Detail screen slides in from the right; slides back out on back press |
| **Favorites — server sync** | Add/remove favorites syncs to your TMDB account via `POST /account/{id}/favorite` |
| **Favorites — paginated** | Favorites grid is driven by CashApp Paging; online pages from TMDB, offline from SQLDelight |
| **Favorites — live updates** | Grid refreshes whenever a movie is toggled on any screen |
| **Swipe to remove favorites** | Swipe a card left to remove; disabled while offline |
| **Offline banner** | Animated banner on both Home and Favorites when connectivity is lost |
| **Image caching — 1-day TTL** | Coil 3 DiskCache (100 MB); images served from disk up to 24 hours |
| **Typed error handling** | `NetworkResult<T>` sealed class; HTTP errors use TMDB's `status_message`; connectivity errors mapped to `ApiError` enum |

---

## Architecture

```
UI Layer        Compose Multiplatform screens + MVI (State / Intent / Effect)
    ↕
Domain Layer    Repository interface + domain models  — commonMain
    ↕
Data Layer      Ktor + SQLDelight + CashApp Paging    — commonMain
                Platform drivers (SQLite, network)    — androidMain / iosMain
```

### MVI Pattern

Each screen has a `Contract` file defining:
- **State** — immutable snapshot collected as `StateFlow`
- **Intent** — user actions dispatched via `processIntent()`
- **Effect** — one-shot navigation or side-effect events via `Channel`

A **`StateHolder`** in `commonMain` owns all the business logic. The `ViewModel` (also in `commonMain`, injected by Koin) wraps it and adds the paging `Flow`.

### Movie Detail — Sealed UI State

```kotlin
sealed interface MovieDetailUiState {
    data object Loading : MovieDetailUiState
    data class Error(val message: String) : MovieDetailUiState
    data class Success(
        val movie: MovieDetail,
        val trailerKey: String?,
        val isFavorite: Boolean
    ) : MovieDetailUiState
}
```

The FAB is only rendered inside the `Success` branch — impossible to show during loading or error.

### KMP `expect/actual` Surface

| Symbol | commonMain | androidMain | iosMain |
|--------|------------|-------------|---------|
| `MoviesAppTheme` | `expect fun` | Dynamic color + WindowCompat | Static Material 3 colors |
| `DatabaseDriverFactory` | `expect class` | `AndroidSqliteDriver` | `NativeSqliteDriver` |
| `TrailerPlayerSection` | `expect fun` | YouTube `AndroidView` | WKWebView placeholder |
| `currentTimeMillis` | `expect fun` | `System.currentTimeMillis()` | `NSDate` epoch |
| `NetworkStatusProvider` | interface | `NetworkMonitor` (ConnectivityManager) | `IosNetworkStatusProvider` (NWPathMonitor) |

---

## Tech Stack

| Layer | Library / Tool |
|---|---|
| **UI** | Compose Multiplatform 1.7.3 + Material 3 |
| **State management** | MVI — `StateHolder` + `ViewModel` + `StateFlow` + `Channel` |
| **DI** | Koin 4.0.0 (KMP-compatible) |
| **Navigation** | JetBrains KMP Navigation Compose (`org.jetbrains.androidx.navigation`) |
| **Networking** | Ktor 2.3.12 (OkHttp engine on Android, Darwin/URLSession on iOS) |
| **Image loading** | Coil 3.1.0 (OkHttp fetcher on Android, Ktor fetcher on iOS) |
| **Local DB** | SQLDelight 2.0.2 (Android SQLite driver · iOS Native driver) |
| **Paging** | CashApp Multiplatform Paging 3.3.0 |
| **Video** | YouTube Android Player library (Android) · WKWebView placeholder (iOS) |
| **Async** | Kotlin Coroutines + Flow |
| **ViewModel** | JetBrains lifecycle-viewmodel KMP (`org.jetbrains.androidx.lifecycle`) |
| **Unit testing** | JUnit 4 + MockK + Turbine + `paging-testing` (`:app`) · `kotlin.test` (`:shared`) |
| **Build** | Kotlin 2.1.20 · AGP 8.8.2 · Gradle 9.4.1 |

---

## Project Structure

```
Movies-App/
├── app/                        ← Android entry point — 3 files only
│   └── src/main/
│       ├── MainActivity.kt         @ComponentActivity, sets content
│       ├── MoviesApplication.kt    startKoin + Coil SingletonImageLoader
│       └── di/AppModule.kt         Koin module: HttpClient, ImageLoader, Repository, VMs
│
├── shared/                     ← KMP + CMP module (everything else lives here)
│   └── src/
│       ├── commonMain/         54 files — truly cross-platform
│       │   ├── data/
│       │   │   ├── local/          LocalMovieDataSource (SQLDelight queries)
│       │   │   ├── remote/         TmdbRemoteDataSource (Ktor), SafeApiCall,
│       │   │   │                   DTOs (6 files), MoviePagingSource, FavoritesPagingSource
│       │   │   ├── repository/     MovieRepository (interface) + MovieRepositoryImpl
│       │   │   └── mapper/         MovieDtoMapper (DTO → domain)
│       │   ├── domain/model/       Movie, MovieDetail, Genre, VideoResult, Category
│       │   ├── presentation/
│       │   │   ├── common/         MovieCard, MovieGrid, MoviePosterImage, RatingBadge,
│       │   │   │                   CategoryFilterRow, NetworkErrorFooter, OfflineBanner,
│       │   │   │                   ErrorView, TrailerPlayerSection (expect)
│       │   │   ├── home/           HomeScreen, HomeViewModel, HomeStateHolder, HomeContract
│       │   │   ├── moviedetail/    MovieDetailScreen, MovieDetailContent, MovieMetadata,
│       │   │   │                   MovieDetailViewModel, MovieDetailStateHolder, MovieDetailContract
│       │   │   ├── favorites/      FavoritesScreen, FavoritesComponents,
│       │   │   │                   FavoritesViewModel, FavoritesStateHolder, FavoritesContract
│       │   │   ├── navigation/     AppNavGraph, Screen, BottomNavBar, BottomNavItem
│       │   │   └── theme/          Theme (expect MoviesAppTheme), Type
│       │   └── util/               NetworkResult, ApiError, TmdbImageUrl,
│       │                           NetworkStatusProvider (interface), NetworkUnavailableException,
│       │                           PagingDefaults, Platform (expect currentTimeMillis)
│       │
│       ├── androidMain/        6 Android-specific actuals
│       │   ├── di/             AndroidSharedModule (Koin androidContext)
│       │   ├── data/local/     DatabaseDriverFactory (AndroidSqliteDriver)
│       │   ├── util/           NetworkMonitor (ConnectivityManager)
│       │   └── presentation/
│       │       ├── common/     TrailerPlayerSection (YouTubePlayerView via AndroidView)
│       │       └── theme/      Theme (dynamic color + WindowCompat)
│       │
│       └── iosMain/            10 iOS-specific files
│           ├── di/             IosAppConfig, IosSharedModule, IosAppModule, KoinHelper
│           ├── data/local/     DatabaseDriverFactory (NativeSqliteDriver)
│           ├── util/           IosNetworkStatusProvider (NWPathMonitor)
│           └── presentation/
│               ├── common/     TrailerPlayerSection (WKWebView placeholder)
│               ├── theme/      Theme (static M3 colors)
│               └──             MainViewController (ComposeUIViewController)
│
└── iosApp/                     Xcode project (open on Mac with Xcode 15+)
    ├── iosApp.xcodeproj/
    └── iosApp/
        ├── iOSApp.swift         @main — calls KoinHelper.initKoin()
        ├── ContentView.swift    UIViewControllerRepresentable → MainViewController
        ├── Config.swift         TMDB credentials placeholder
        └── Info.plist
```

---

## Getting Started

### Prerequisites

- Android Studio Meerkat or later
- Android SDK 26+ (minSdk) / targets SDK 35
- For iOS: Mac with Xcode 15+ and a physical device or simulator

### Android

```bash
git clone <repo-url>
cd Movies-App
```

> **No setup required.** The TMDB API key and Read Access Token are committed in `app/build.gradle.kts`. Both are read-only credentials.

Open in Android Studio and click **Run**, or:

```bash
./gradlew :app:assembleDebug
```

### iOS (Mac only)

**Step 1** — Fill in your TMDB credentials in `iosApp/iosApp/Config.swift`:

```swift
enum TmdbConfig {
    static let apiKey        = "your_api_key"
    static let readToken     = "your_read_access_token"
    static let accountId     = "your_account_id"   // optional, for favorites sync
    static let sessionId     = ""                   // optional, for v3 session auth
}
```

**Step 2** — Open the Xcode project and press ▶:

```bash
open iosApp/iosApp.xcodeproj
```

The "Compile Kotlin" script phase in Xcode automatically builds the shared KMP framework before each run.

### Optional: TMDB Account Sync

To enable server-side favorites on Android, fill in the build config fields in `app/build.gradle.kts`:

```kotlin
buildConfigField("String", "TMDB_ACCOUNT_ID", "\"your_account_id\"")
buildConfigField("String", "TMDB_SESSION_ID", "\"your_session_id\"")
```

Without these, favorites are saved locally in SQLDelight with a best-effort server sync using the Bearer token.

---

## Caching Strategy

### Image Caching

Images use a **two-tier Coil 3 cache** with a 1-day expiry:

| Tier | Storage | TTL | Purpose |
|---|---|---|---|
| Memory cache | RAM (25% of available) | Process lifetime | Instant display for recently seen posters |
| Disk cache (`image_cache/`, 100 MB) | Internal storage | 24 hours | Survives app kills and restarts |

On Android, Coil uses a dedicated `OkHttpClient` with a custom `NetworkFetcher` that stamps responses with `Cache-Control: max-age=86400`. On iOS, Coil uses the Ktor Darwin fetcher with equivalent caching.

### Movie List Caching (SQLDelight)

`MoviePagingSource` caches pages in the `Movie` SQLDelight table:

- **Online (page 1):** old cached rows for the category are cleared first, then fresh data is inserted.
- **Online (page N > 1):** rows are appended to the existing cache.
- **Offline:** pages already cached are served freely; hitting an uncached page returns `LoadResult.Error(NetworkUnavailableException)`, which renders as a `NetworkErrorFooter` at the bottom — not a full-screen takeover.

### Favorites Caching (SQLDelight + Server)

Favorites are stored in the `Favorite` SQLDelight table and synced to TMDB:

- **Online:** `FavoritesPagingSource` fetches from `GET /account/{id}/favorite/movies` and caches to SQLDelight.
- **Offline:** falls back to the `Favorite` table ordered by `savedAt DESC`.

---

## Offline Behaviour

| Scenario | Behaviour |
|---|---|
| Scrolling within cached pages | Served from SQLDelight — no network needed |
| Scrolling past cached pages | `NetworkErrorFooter` at the bottom; list above remains scrollable |
| Opening movie detail while offline | Full-screen `ErrorView` with Retry |
| Trailer while offline | Backdrop image shown — no crash |
| Images while offline | Served from Coil DiskCache (if fetched within 24 hours) |
| Favorites while offline | SQLDelight cache served; swipe-to-remove disabled; offline banner shown |
| Returning online | Paging retries automatically |

---

## Error Handling

All API calls go through `safeApiCall` (Ktor-based), which maps every failure to `NetworkResult<T>`:

```kotlin
sealed class NetworkResult<out T> {
    data class Success<T>(val data: T) : NetworkResult<T>()
    data class Error(val message: String, val httpCode: Int = 0) : NetworkResult<Nothing>()
}
```

- **HTTP errors:** TMDB error body (`{"status_message": "..."}`) is parsed; `ApiError.SERVER_ERROR` is the fallback.
- **Connectivity errors:** mapped to `ApiError` enum entries (`NO_CONNECTION`, `TIMEOUT`, `UNEXPECTED`).
- **Paging errors:** `LoadResult.Error(e)` propagates to `LoadStateError`; the UI renders `NetworkErrorFooter` (inline) or `ErrorView` (full-screen).

---

## Testing

### Unit Tests (run on JVM — no device needed)

```bash
./gradlew :app:testDebugUnitTest :shared:testDebugUnitTest
```

| Module | Suite | What's covered |
|---|---|---|
| `:app` | `HomeViewModelTest` | Category selection, offline state, network error, paging flow |
| `:app` | `MovieDetailViewModelTest` | All UI states (Loading/Success/Error), trailer degradation, favorite toggle |
| `:app` | `FavoritesViewModelTest` | Offline state, remove favorite, paging flow |
| `:app` | `MoviePagingSourceTest` | Online/offline paths, page keys, error cases |
| `:app` | `FavoritesPagingSourceTest` | Online/offline paths, offset pagination |
| `:app` | `MovieRepositoryImplTest` | `getMovieDetail`, `getTrailer`, `toggleFavorite`, server sync |
| `:shared` | `MovieDtoMapperTest` | DTO → domain mapping |
| `:shared` | `TmdbImageUrlTest` | `poster()`, `posterLarge()`, `backdrop()` — null and non-null |
| `:shared` | `CategoryExtTest` | `displayName` for all categories |
| `:shared` | `ApiErrorTest` | All enum entries, unique messages |

All `:shared` tests use `kotlin.test` and run on the JVM Android target (and can run on iOS targets when compiled for that platform).

### End-to-End Journey Test (live TMDB API — device required)

```bash
./gradlew connectedDebugAndroidTest
```

`RealAppJourneyTest` launches `MainActivity` with the **real** `MovieRepositoryImpl`, real SQLDelight database, and real TMDB network calls. Each test cleans up after itself (clears the local DB and un-favorites any movies added during the test).

| Journey | What's validated |
|---|---|
| `homeScreen_loadsRealMoviesFromTmdb` | Movie cards appear from a live API response |
| `homeScreen_categoryChips_visible` | All 3 category chips are displayed |
| `homeScreen_tapMovieCard_opensDetailScreen` | Tapping a card navigates to detail |
| `detailScreen_navigateBack_returnsToHome` | Back button returns to the grid |
| `favoritesTab_showsEmptyState_initially` | Empty state shown on first launch |
| `toggleFavorite_movieAppearsInFavoritesTab` | FAB toggle adds a movie to Favorites |
| `swipeLeft_removesMovieFromFavoritesList` | Swipe-to-dismiss removes the card |
| `favoritesTab_showsOnlyFavoritedMovies` | Only explicitly favorited movies appear |

---

## iOS Limitations

| Feature | Android | iOS |
|---------|---------|-----|
| **Trailers** | YouTube embedded player | WKWebView placeholder — see migration guide for full SFSafariViewController implementation |
| **Dynamic theme** | Android 12+ Material You | Static Material 3 color scheme |
| **Build platform** | Windows / Mac / Linux | Mac with Xcode 15+ only |

---

## Migration Notes

This project was migrated from Android-only to KMP + CMP. The full phase-by-phase log, technology replacement table, and all `expect/actual` design decisions are documented in:

**[`docs/KMP_CMP_MIGRATION_GUIDE.md`](docs/KMP_CMP_MIGRATION_GUIDE.md)**
