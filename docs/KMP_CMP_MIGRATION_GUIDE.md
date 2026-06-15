# KMP + Compose Multiplatform Migration Guide

## Status: ✅ COMPLETE (Android) · 🛠️ iOS Ready (needs Mac to build)

---

## Goal

Convert the Movies-App from an Android-only Hilt/Retrofit/Room/AndroidX app into a
**Kotlin Multiplatform + Compose Multiplatform** project that targets **Android and iOS**
from a single shared codebase.

---

## Final Module Structure

```
Movies-App/
├── app/                    ← Android entry point (3 files only)
│   └── src/main/
│       ├── MainActivity.kt
│       ├── MoviesApplication.kt
│       └── di/AppModule.kt
│
├── shared/                 ← KMP + CMP shared module
│   └── src/
│       ├── commonMain/     ← 53+ files — everything shared
│       │   ├── data/       domain models, DTOs, Ktor, SQLDelight, paging, repository
│       │   ├── presentation/  ViewModels, state holders, contracts, ALL screens, navigation
│       │   └── util/       NetworkResult, ApiError, TmdbImageUrl, etc.
│       ├── androidMain/    ← 6 platform-specific actuals
│       │   ├── DatabaseDriverFactory.kt  (AndroidSqliteDriver)
│       │   ├── AndroidSharedModule.kt    (Koin + androidContext)
│       │   ├── TrailerPlayerSection.kt   (YouTubePlayerView via AndroidView)
│       │   ├── Theme.kt                  (actual — dynamic color + status bar)
│       │   ├── NetworkMonitor.kt         (actual — ConnectivityManager)
│       │   └── Platform.kt              (actual — System.currentTimeMillis)
│       └── iosMain/        ← iOS platform-specific actuals + entry point
│           ├── DatabaseDriverFactory.kt  (NativeSqliteDriver)
│           ├── Theme.kt                  (actual — static M3 colors)
│           ├── TrailerPlayerSection.kt   (actual — WKWebView placeholder)
│           ├── NetworkMonitor.kt         (IosNetworkStatusProvider — NWPathMonitor)
│           ├── Platform.kt              (actual — NSDate)
│           ├── MainViewController.kt    (ComposeUIViewController entry point)
│           └── di/
│               ├── IosAppConfig.kt      (credentials data class)
│               ├── IosSharedModule.kt   (Koin: DB + network status)
│               ├── IosAppModule.kt      (Koin: Ktor Darwin + Coil + repo + VMs)
│               └── KoinHelper.kt        (initKoin() called from Swift)
│
└── iosApp/                 ← Xcode project (open on Mac to build)
    ├── iosApp.xcodeproj/
    │   └── project.pbxproj
    └── iosApp/
        ├── iOSApp.swift     (@main — calls KoinHelper.initKoin())
        ├── ContentView.swift (UIViewControllerRepresentable wrapping MainViewController)
        ├── Config.swift      (TmdbConfig — API credentials placeholder)
        └── Info.plist
```

---

## Key Technology Replacements

| Layer | Before (Android-only) | After (KMP/CMP) |
|-------|-----------------------|-----------------|
| **UI** | Jetpack Compose (AndroidX) | Compose Multiplatform 1.7.3 |
| **Navigation** | AndroidX Navigation Compose | JetBrains KMP Navigation Compose |
| **DI** | Hilt | Koin 4.0.0 |
| **Networking** | Retrofit + OkHttp | Ktor (OkHttp engine on Android, Darwin on iOS) |
| **Local DB** | Room | SQLDelight 2.0.2 |
| **Paging** | AndroidX Paging 3 | CashApp Multiplatform Paging 3.3.0 |
| **Image loading** | Coil 2 | Coil 3 (OkHttp fetcher on Android, Ktor fetcher on iOS) |
| **Theme** | Android dynamic color | `expect/actual MoviesAppTheme` |
| **ViewModel** | AndroidX ViewModel (Hilt-injected) | JetBrains lifecycle-viewmodel (Koin-injected, in commonMain) |

---

## What Remains Platform-Specific (expect/actual)

| Symbol | commonMain | androidMain actual | iosMain actual |
|--------|------------|-------------------|----------------|
| `MoviesAppTheme` | `expect fun` | Dynamic color + WindowCompat | Static M3 colors |
| `defaultDarkTheme` | `expect fun` | `isSystemInDarkTheme()` | `isSystemInDarkTheme()` |
| `DatabaseDriverFactory` | `expect class` | `AndroidSqliteDriver` | `NativeSqliteDriver` |
| `TrailerPlayerSection` | `expect fun` | YouTube `AndroidView` | WKWebView placeholder |
| `currentTimeMillis` | `expect fun` | `System.currentTimeMillis()` | `NSDate` |

---

## How to Build

### Android
Open in Android Studio and run normally, or:
```
./gradlew :app:assembleDebug
```

### iOS (requires a Mac with Xcode 15+)

**Step 1 — Fill in your TMDB credentials in `iosApp/iosApp/Config.swift`**

**Step 2 — Build the shared KMP framework**
```bash
./gradlew :shared:assembleSharedReleaseXCFramework
# or for debug simulator:
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
```

**Step 3 — Open the Xcode project and run**
```bash
open iosApp/iosApp.xcodeproj
```
Select a simulator or device and press ▶.

The "Compile Kotlin (shared framework)" script build phase in Xcode automatically calls
`./gradlew :shared:embedAndSignAppleFrameworkForXcode` before each build, so Step 2 is
only needed for the first build or after major Gradle changes.

---

## iOS — Known Limitations

| Feature | Android | iOS |
|---------|---------|-----|
| **Trailers** | YouTube player (full) | WKWebView placeholder — open YouTube URL via `SafariServices` |
| **Dynamic colors** | Android 12+ | Not applicable — uses static M3 scheme |
| **Offline paging** | Room + Paging cache | SQLDelight + CashApp Paging |

---

## Adding the iOS `TrailerPlayerSection` (Future Work)

The `iosMain/TrailerPlayerSection.kt` is currently a placeholder composable.
To play YouTube videos on iOS:

```kotlin
// iosMain/TrailerPlayerSection.kt
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

## Phase Log

| Phase | Description | Status |
|-------|-------------|--------|
| 0 | Git branch + project backup | ✅ |
| 1 | Create `:shared` KMP module, Gradle setup | ✅ |
| 2 | Move domain models to `commonMain` | ✅ |
| 3 | Move DTOs + pure mapper to `commonMain` | ✅ |
| 4 | Retrofit → Ktor, move networking to `commonMain` | ✅ |
| 5 | Move UI contracts + state holders to `commonMain` | ✅ |
| 6 | Room → SQLDelight, move local DB to `commonMain` | ✅ |
| 7 | Coil 2 → Coil 3, add CMP plugin, move pure UI to `commonMain` | ✅ |
| 8 | Move screens + navigation to `shared/androidMain` | ✅ |
| 9 | `Theme.kt` → `shared/androidMain` | ✅ |
| 10 | Hilt → Koin, move ViewModels, paging, repository to `shared` | ✅ |
| 11 | Move all remaining presentation code to `commonMain` via KMP Navigation | ✅ |
| 12 | Add iOS targets + `iosMain` actuals (DB, network, theme) | ✅ |
| 13 | iOS DI (`IosSharedModule`, `IosAppModule`, `KoinHelper`) | ✅ |
| 14 | `MainViewController` + `iosApp/` Xcode project | ✅ |

---

## Dependency Versions (as of migration completion)

```toml
kotlin = "2.1.20"
composeMultiplatform = "1.7.3"
koin = "4.0.0"
ktor = "2.3.12"
sqldelight = "2.0.2"
coil3 = "3.1.0"
cashappPaging = "3.3.0-alpha02-0.5.1"
jetbrainsLifecycle = "2.8.4"
jetbrainsNavigation = "2.8.0-alpha10"
agp = "8.7.0"
```
