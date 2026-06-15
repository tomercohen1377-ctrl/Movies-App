# KMP + Compose Multiplatform Migration Guide

## Status: ✅ BUILD SUCCESSFUL · Android runs · iOS ready (needs Mac + Xcode)

---

## What Was Done

This document summarises the complete migration of **Movies-App** from an Android-only app
into a **Kotlin Multiplatform + Compose Multiplatform** project targeting **Android and iOS**.

### Starting point (before migration)
- Single `:app` Android module, ~60 Kotlin files
- Jetpack Compose (AndroidX only)
- Hilt for DI
- Retrofit + OkHttp for networking
- Room for local database
- AndroidX Paging 3
- Coil 2 for image loading
- AndroidX Navigation Compose
- AndroidX ViewModel (Hilt-injected)

### End result (after migration)
- `:app` reduced to **3 files** (Android host only)
- `:shared` KMP module with **54 commonMain files** (truly cross-platform)
- **6 androidMain actuals** for Android-specific platform APIs
- **10 iosMain files** including DI, theme, entry point, and platform actuals
- `iosApp/` Xcode project ready to open on a Mac

---

## Final Module Structure

```
Movies-App/
├── app/                        ← Android entry point — 3 files only
│   └── src/main/
│       ├── MainActivity.kt
│       ├── MoviesApplication.kt
│       └── di/AppModule.kt
│
├── shared/                     ← KMP + CMP module
│   └── src/
│       ├── commonMain/         ← 54 files — everything shared
│       │   ├── data/
│       │   │   ├── local/      LocalMovieDataSource (SQLDelight)
│       │   │   ├── mapper/     MovieDtoMapper
│       │   │   ├── remote/     TmdbRemoteDataSource (Ktor), SafeApiCall,
│       │   │   │               DTOs (6 files), paging sources (2 files)
│       │   │   └── repository/ MovieRepository (interface), MovieRepositoryImpl
│       │   ├── domain/
│       │   │   └── model/      Movie, MovieDetail, Genre, VideoResult, Category, CategoryExt
│       │   ├── presentation/
│       │   │   ├── common/     MovieCard, MovieGrid, MoviePosterImage, RatingBadge,
│       │   │   │               OfflineBanner, ErrorView, NetworkErrorFooter,
│       │   │   │               CategoryFilterRow, TrailerPlayerSection (expect)
│       │   │   ├── favorites/  FavoritesContract, FavoritesStateHolder,
│       │   │   │               FavoritesViewModel, FavoritesScreen, FavoritesComponents
│       │   │   ├── home/       HomeContract, HomeStateHolder, HomeViewModel, HomeScreen
│       │   │   ├── moviedetail/ MovieDetailContract, MovieDetailStateHolder,
│       │   │   │                MovieDetailViewModel, MovieDetailScreen,
│       │   │   │                MovieDetailContent, MovieMetadata
│       │   │   ├── navigation/ AppNavGraph, Screen, BottomNavBar, BottomNavItem
│       │   │   └── theme/      Theme (expect), Type
│       │   └── util/           NetworkResult, ApiError, TmdbImageUrl,
│       │                       NetworkStatusProvider (interface), NetworkUnavailableException,
│       │                       PagingDefaults, Platform (expect currentTimeMillis)
│       │
│       ├── androidMain/        ← 6 Android-specific actuals
│       │   ├── di/AndroidSharedModule.kt  (Koin androidContext wiring)
│       │   ├── data/local/DatabaseDriverFactory.kt  (AndroidSqliteDriver)
│       │   ├── util/NetworkMonitor.kt     (ConnectivityManager actual)
│       │   ├── presentation/
│       │   │   ├── common/TrailerPlayerSection.kt  (YouTubePlayerView via AndroidView)
│       │   ��   └── theme/Theme.kt         (dynamic color + WindowCompat actual)
│       │   └── util/Platform.kt           (System.currentTimeMillis actual)
│       │
│       └── iosMain/            ← 10 iOS-specific files
│           ├── di/
│           │   ├── IosAppConfig.kt        (credentials data class for Swift → Kotlin)
│           │   ├── IosSharedModule.kt     (Koin: DB + network status)
│           │   ├── IosAppModule.kt        (Koin: Ktor Darwin + Coil + repo + VMs)
│           │   └── KoinHelper.kt          (initKoin() called from Swift @main)
│           ├── data/local/DatabaseDriverFactory.kt  (NativeSqliteDriver actual)
│           ├── presentation/
│           │   ├── common/TrailerPlayerSection.kt   (WKWebView placeholder actual)
│           │   └── theme/Theme.kt         (static M3 colors actual)
│           ├── util/
│           │   ├── IosNetworkStatusProvider.kt  (NWPathMonitor)
│           │   └── Platform.kt            (NSDate actual)
│           └── MainViewController.kt      (ComposeUIViewController entry point)
│
└── iosApp/                     ← Xcode project (open on Mac to build)
    ├── iosApp.xcodeproj/
    │   └── project.pbxproj
    └── iosApp/
        ├── iOSApp.swift         (@main — calls KoinHelper.initKoin())
        ├── ContentView.swift    (UIViewControllerRepresentable → MainViewController)
        ├── Config.swift         (TmdbConfig — fill in your API credentials)
        └── Info.plist
```

---

## Technology Replacements

| Layer | Before (Android-only) | After (KMP/CMP) |
|-------|-----------------------|-----------------|
| **UI** | Jetpack Compose (AndroidX BOM 2024.11) | Compose Multiplatform 1.7.3 |
| **Navigation** | `androidx.navigation:navigation-compose` | `org.jetbrains.androidx.navigation:navigation-compose` |
| **DI** | Hilt 2.52 | Koin 4.0.0 |
| **Networking** | Retrofit 2.11 + OkHttp 4.12 | Ktor 2.3.12 (OkHttp engine Android, Darwin engine iOS) |
| **Local DB** | Room 2.6.1 | SQLDelight 2.0.2 |
| **Paging** | AndroidX Paging 3.3.2 | CashApp Multiplatform Paging 3.3.0-alpha02-0.5.1 |
| **Image loading** | Coil 2.7 | Coil 3.1.0 (OkHttp fetcher Android, Ktor fetcher iOS) |
| **Theme** | Android dynamic color | `expect/actual MoviesAppTheme` |
| **ViewModel** | `@HiltViewModel` (Android-only) | JetBrains `lifecycle-viewmodel` KMP (in commonMain, Koin-injected) |
| **Build tools** | AGP 8.7.0 / Gradle 8.9 | AGP 8.8.2 / Gradle 9.4.1 |
| **Kotlin** | 2.0.21 | 2.1.20 |

---

## Key Design Decisions

### Why the `androidx.*` import names look unchanged
JetBrains publishes their KMP ports under the **same `androidx.*` package names** but different
Maven group IDs. When Gradle resolves `commonMain` dependencies it uses the KMP artifacts:

| Import in code | Gradle group (in commonMain) |
|----------------|------------------------------|
| `androidx.lifecycle.ViewModel` | `org.jetbrains.androidx.lifecycle:lifecycle-viewmodel` |
| `androidx.navigation.compose.*` | `org.jetbrains.androidx.navigation:navigation-compose` |
| `androidx.compose.runtime.*` | `org.jetbrains.compose.runtime` (via CMP plugin) |
| `androidx.compose.material3.*` | `org.jetbrains.compose.material3` (via CMP plugin) |

These are **not** Android-SDK dependencies. They compile to iOS and other KMP targets.

### expect/actual surface

| Symbol | commonMain | androidMain | iosMain |
|--------|------------|-------------|---------|
| `MoviesAppTheme` | `expect fun` | Dynamic color + WindowCompat | Static M3 dark/light |
| `defaultDarkTheme` | `expect fun` | `isSystemInDarkTheme()` | `isSystemInDarkTheme()` |
| `DatabaseDriverFactory` | `expect class` | `AndroidSqliteDriver` | `NativeSqliteDriver` |
| `TrailerPlayerSection` | `expect fun` | YouTube `AndroidView` | WKWebView placeholder |
| `currentTimeMillis` | `expect fun` | `System.currentTimeMillis()` | `NSDate` epoch |

### Navigation argument parsing
JetBrains KMP navigation stores all route path segments as **strings** by default.
Use `getString("key")?.toIntOrNull()` not `getInt("key")` to avoid silent `0` returns:

```kotlin
// ✅ Correct — KMP-safe
val movieId = backStackEntry.arguments?.getString(Screen.MovieDetail.ARG_MOVIE_ID)?.toIntOrNull()

// ❌ Wrong — getInt returns 0 when argument is stored as String
val movieId = backStackEntry.arguments?.getInt(Screen.MovieDetail.ARG_MOVIE_ID)
```

---

## Migration Phase Log

| Phase | What changed | Key files |
|-------|-------------|-----------|
| **1** | Add `:shared` KMP module, Gradle + version catalog setup | `settings.gradle.kts`, `shared/build.gradle.kts`, `libs.versions.toml` |
| **2** | Move domain models to `commonMain` | `MovieModels.kt`, `CategoryExt.kt`, `NetworkResult.kt`, `ApiError.kt`, `TmdbImageUrl.kt` |
| **3** | Move DTOs + DTO mapper to `commonMain` | 6 DTO files, `MovieDtoMapper.kt`, `NetworkUnavailableException.kt` |
| **4** | **Retrofit → Ktor** | `TmdbRemoteDataSource.kt`, `SafeApiCall.kt`, `NetworkModule` → `AppModule` |
| **5** | Move UI contracts + state holders to `commonMain` | `*Contract.kt`, `*StateHolder.kt`, `NetworkStatusProvider.kt`, `MovieRepositoryBase.kt` |
| **6** | **Room → SQLDelight** | `.sq` schema files, `LocalMovieDataSource.kt`, `DatabaseDriverFactory` expect/actual, `DatabaseModule` → Koin |
| **7** | **Coil 2 → Coil 3**, add CMP plugin, move pure UI components | `MoviePosterImage.kt`, `CategoryFilterRow.kt`, `NetworkErrorFooter.kt`, `Type.kt` |
| **8** | Move screens + navigation to `shared/androidMain` | All 3 screens, `AppNavGraph.kt`, `BottomNavBar.kt`, `TrailerPlayerSection.kt` |
| **9** | Move theme to `shared/androidMain` | `Theme.kt`, `Type.kt` |
| **10** | **Hilt → Koin** | Removed 4 Hilt `@Module` classes, added `AppModule.kt`, removed `@HiltViewModel`/`@Inject` |
| **11** | Move ViewModels, paging sources, repository to `shared` (partly `commonMain`) | `HomeViewModel.kt`, `FavoritesViewModel.kt`, `MovieDetailViewModel.kt`, `MoviePagingSource.kt`, `MovieRepositoryImpl.kt` |
| **12** | **AndroidX Paging → CashApp Multiplatform Paging**, move everything to `commonMain` | All screens, navigation, ViewModels, paging sources now in `commonMain` |
| **13** | Add iOS targets, `iosMain` actuals, Xcode project | `IosAppConfig`, `IosSharedModule`, `IosAppModule`, `KoinHelper`, `MainViewController`, `iosApp/` |
| **14** | Bug fixes + build cleanup | `"%.1f".format` → KMP-safe rounding, proguard-rules.pro rewrite, `AndroidManifest.xml` in `:shared`, SQLDelight migration verifier disabled, AGP 8.7→8.8.2 |

---

## Build Instructions

### Android (works on Windows/Mac/Linux)

```bash
./gradlew :app:assembleDebug
# or run unit tests:
./gradlew :app:testDebugUnitTest :shared:testDebugUnitTest
```

### iOS (requires Mac with Xcode 15+)

**Step 1** — Fill in your TMDB credentials in `iosApp/iosApp/Config.swift`:
```swift
enum TmdbConfig {
    static let apiKey        = "your_api_key_here"
    static let readToken     = "your_read_access_token_here"
    static let accountId     = "your_account_id_here"
    static let sessionId     = "your_session_id_here"
}
```

**Step 2** — Open the Xcode project and run:
```bash
open iosApp/iosApp.xcodeproj
```
Select a simulator or device and press ▶.

The "Compile Kotlin" script phase in Xcode automatically calls
`./gradlew :shared:embedAndSignAppleFrameworkForXcode` before each build.

---

## iOS Known Limitations

| Feature | Android | iOS |
|---------|---------|-----|
| **Trailers** | YouTube player (full embedded) | WKWebView placeholder — see below for full implementation |
| **Dynamic theme** | Android 12+ Material You | Static Material 3 color scheme |
| **Offline badge** | ConnectivityManager | NWPathMonitor |

### iOS full trailer implementation (future work)

Replace `iosMain/TrailerPlayerSection.kt` with:

```kotlin
@Composable
actual fun TrailerPlayerSection(trailerKey: String, modifier: Modifier) {
    val urlString = "https://www.youtube.com/watch?v=$trailerKey"
    val url = NSURL.URLWithString(urlString)!!
    UIKitView(
        factory = {
            val config = SFSafariViewControllerConfiguration()
            SFSafariViewController(URL = url, configuration = config).view
        },
        modifier = modifier
    )
}
```
Add `SafariServices` framework to the Xcode target's "Frameworks and Libraries".

---

## Dependency Versions

```toml
kotlin                = "2.1.20"
agp                   = "8.8.2"
composeMultiplatform  = "1.7.3"
koin                  = "4.0.0"
ktor                  = "2.3.12"
sqldelight            = "2.0.2"
coil3                 = "3.1.0"
cashappPaging         = "3.3.0-alpha02-0.5.1"
jetbrainsLifecycle    = "2.8.4"
jetbrainsNavigation   = "2.8.0-alpha10"
```

---

## Stale Catalog Entries (safe to remove in a future cleanup)

The following entries in `libs.versions.toml` are no longer used but were kept to avoid
breaking any tooling that might reference them:

- `retrofit`, `okhttp`, `retrofitKotlinxSerialization` — replaced by Ktor
- `room = "2.6.1"`, `androidx-room-*` — replaced by SQLDelight
- `androidx-media3-*` — replaced by YouTube Player
- `coil = "2.7.0"`, `coil-compose` — replaced by Coil 3
- `androidx-navigation-compose` (AndroidX version) — replaced by `jetbrains-navigation-compose`
- `androidx-lifecycle-viewmodel-compose` (AndroidX version) — replaced by `jetbrains-lifecycle-viewmodel-compose`
