# Movies App

A production-quality Android application that displays movies using [The Movie Database (TMDB) API](https://www.themoviedb.org/documentation/api). Built showcasing Clean Architecture, MVI, Jetpack Compose, Paging 3, and thorough test coverage.

---

## Table of Contents

- [Features](#features)
- [Architecture](#architecture)
- [Tech Stack](#tech-stack)
- [Project Structure](#project-structure)
- [Getting Started](#getting-started)
- [Caching Strategy](#caching-strategy)
- [Offline Behaviour](#offline-behaviour)
- [Testing](#testing)
- [Limitations](#limitations)

---

## Features

| Feature | Detail |
|---|---|
| **2-tab bottom navigation** | Home (movie grid) + Favorites |
| **Category filter chips** | Now Playing · Top Rated · Upcoming — defaults to Now Playing |
| **Infinite scroll / Paging 3** | Loads 20 items per page, prefetches next page automatically |
| **Full-bleed poster cards** | 2:3 aspect-ratio cards with gradient scrim and rating badge |
| **Movie detail screen** | YouTube trailer at the top, then title, year, runtime, rating, genres, overview |
| **Slide navigation animation** | Detail screen slides in from the right; slides back out on back press |
| **Favorites — server sync** | Add/remove favorites syncs to your TMDB account via `POST /account/{id}/favorite` |
| **Favorites — paginated** | Favorites grid is driven by Paging 3; online pages come from TMDB, offline from Room |
| **Favorites — live updates** | The favorites grid refreshes automatically whenever a movie is added or removed from **any screen** (detail FAB or favorites swipe) |
| **Swipe to remove** | Swipe a favorite card left to remove it; animated exit |
| **Image caching — 1-day TTL** | Dedicated OkHttp `Cache` (100 MB) with `Cache-Control: max-age=86400`; images served from disk for up to 24 hours |
| **Movie list caching — 1-day TTL** | Paged movie lists cached in Room; stale entries (> 1 day old) are purged and re-fetched on next load |
| **Offline browsing** | Cached pages served from Room; `NetworkErrorFooter` appears only when paging exceeds the cache boundary |

---

## Architecture

```
UI Layer  (Compose + MVI — State / Intent / Effect)
    ↕
Domain Layer  (repository interfaces + domain models)
    ↕
Data Layer  (Retrofit + Room + Paging 3 + Coil)
```

### MVI Pattern

Each screen has a `Contract` file defining three types:
- **State** — immutable snapshot of what the screen should display (collected as `StateFlow`)
- **Intent** — user actions dispatched via `processIntent()`
- **Effect** — one-shot navigation or side-effect events (sent via a `Channel`)

---

## Tech Stack

| Layer | Library / Tool |
|---|---|
| UI | Jetpack Compose + Material 3 |
| State management | MVI (ViewModel + `StateFlow` + `Channel`) |
| DI | Hilt |
| Navigation | Compose Navigation with slide animations |
| Networking | Retrofit 2 + OkHttp 4 + Kotlin Serialization |
| Image loading | Coil 2 with dedicated `OkHttpClient` + OkHttp disk cache |
| Local DB | Room |
| Paging | Paging 3 (`PagingSource` → `LazyPagingItems`) |
| Video | YouTube Android Player library |
| Async | Kotlin Coroutines + Flow |
| Unit testing | JUnit 4 + MockK + Turbine + `paging-testing` |
| UI testing | Compose UI Test (`createComposeRule`) |

---

## Project Structure

```
app/src/main/java/com/tcohen/moviesapp/
├── data/
│   ├── local/              # Room DB (v1), DAOs (MovieDao, FavoriteDao), entities
│   ├── remote/             # Retrofit API service, DTOs, AuthInterceptor
│   │   ├── api/            # TmdbApiService
│   │   ├── dto/            # MovieDto, FavoriteRequestDto, RemoveFromListRequestDto, …
│   │   ├── interceptor/    # AuthInterceptor (Bearer token)
│   │   └── paging/         # MoviePagingSource, FavoritesPagingSource
│   ├── repository/         # MovieRepositoryImpl
│   └── mapper/             # DTO ↔ Domain ↔ Entity mappers
├── domain/
│   ├── model/              # Movie, MovieDetail, Genre, VideoResult, Category
│   └── repository/         # MovieRepository interface
├── presentation/
│   ├── home/               # HomeScreen, HomeViewModel, HomeContract
│   ├── moviedetail/        # MovieDetailScreen, MovieDetailViewModel, MovieDetailContract
│   ├── favorites/          # FavoritesScreen, FavoritesViewModel, FavoritesContract
│   ├── common/             # MovieCard, MoviePosterImage, CategoryFilterRow, RatingBadge, …
│   ├── navigation/         # AppNavGraph (with slide transitions), BottomNavBar, Screen
│   └── theme/              # MoviesAppTheme, Typography
├── di/                     # Hilt modules (NetworkModule, DatabaseModule, RepositoryModule, UtilModule)
└── util/                   # NetworkMonitor, TmdbImageUrl, NetworkUnavailableException
```

---

## Getting Started

### Prerequisites

- Android Studio Meerkat or later
- Android SDK 26+ (minSdk) / targets SDK 35
- Internet connection (TMDB API calls)

### Clone and Run

```bash
git clone <repo-url>
cd Movies-App
```

> **No setup required.** The TMDB API key and Read Access Token are committed directly in `app/build.gradle.kts` so the app works out of the box. Both are **read-only** credentials scoped to TMDB public data reads.

Open in Android Studio and click **Run** on any device or emulator running API 26+.

### Optional: TMDB Account Sync

To enable server-side favorites sync, fill in the build config fields in `app/build.gradle.kts`:

```kotlin
buildConfigField("String", "TMDB_ACCOUNT_ID", "\"your_account_id\"")
buildConfigField("String", "TMDB_SESSION_ID", "\"your_session_id\"")  // v3 session
```

Without these, favorites are still fully functional — changes are saved locally in Room and a best-effort sync is attempted using the Bearer token.

---

## Caching Strategy

### Image Caching

Images use a **two-tier cache** with a hard 1-day expiry:

| Tier | Storage | TTL | Purpose |
|---|---|---|---|
| Memory cache | RAM (25% of available) | Process lifetime | Instant display; no I/O for already-seen posters |
| Disk cache | `cache/image_cache/` (100 MB) | 24 hours | Survives app kills and device restarts |

Coil uses a **dedicated `OkHttpClient`** (separate from Retrofit's API client). A network interceptor writes `Cache-Control: max-age=86400, must-revalidate` on every image response, and OkHttp's built-in `Cache` enforces it. After 24 hours OkHttp re-validates with TMDB's CDN.

### Movie List Caching (Room)

Each `MovieEntity` row stores a `cachedAt` timestamp. In `MoviePagingSource`:

- **Online**: page 1 of a fresh fetch **clears the old cache** for that category before inserting new rows, ensuring no stale entries linger.
- **Offline**: before serving from Room, the oldest `cachedAt` for the category is checked. If it exceeds **24 hours**, the stale cache is deleted and a `NetworkUnavailableException` is surfaced so the UI shows the retry state rather than outdated content.

---

## Offline Behaviour

| Scenario | Behaviour |
|---|---|
| Scrolling within cached pages (< 1 day old) | Served from Room — no network required, no error shown |
| Cache older than 1 day | Stale data purged; offline banner + retry shown |
| Scrolling past cached pages | `NetworkErrorFooter` at the bottom; list above remains scrollable |
| Opening movie detail while offline | Full-screen `ErrorView` with Retry button |
| Trailer when offline | Backdrop image shown with a faded play icon — no crash, no error banner |
| Favorites while offline | Room cache served (populated from any previous online session or local toggles) |
| Returning online | Retry automatically restores live data |

---

## Testing

### Unit tests (run on JVM — no device needed)

```bash
./gradlew testDebugUnitTest
```

| Suite | Tests |
|---|---|
| `HomeViewModelTest` | 9 |
| `MovieDetailViewModelTest` | 9 |
| `FavoritesViewModelTest` | 3 |
| `MoviePagingSourceTest` | 10 |
| `MovieRepositoryImplTest` | 13 |
| `MovieMapperTest` | 12 |

### UI component tests (run on device/emulator)

```bash
./gradlew connectedDebugAndroidTest
```

Tests: `MovieCardTest`, `CategoryFilterRowTest`, `RatingBadgeTest`, `NetworkErrorFooterTest`, `TrailerPlayerSectionTest`, `FavoritesScreenTest`

---

## Limitations

- Trailer playback requires internet (YouTube player); no offline trailer support.
- Movie detail and trailers are always fetched live (not cached in Room).
- Favorites server sync requires a TMDB v3 session ID for write operations; GET favorites works with the Bearer token alone.
