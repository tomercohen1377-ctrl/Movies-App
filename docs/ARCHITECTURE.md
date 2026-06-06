# Architecture Overview

## Pattern: Clean Architecture + MVI

The app is structured around **Clean Architecture** layered with **MVI (Model-View-Intent)** as the presentation pattern. This combination enforces strict separation of concerns, makes the codebase testable, and scales well as the feature set grows.

---

## Layers

### 1. Presentation Layer (UI)

- Built entirely with **Jetpack Compose** and **Material 3**.
- Each screen owns a `ViewModel` that exposes state via `StateFlow` and accepts user `Intent`s.
- Navigation is handled by **Compose Navigation** with slide animations defined in `AppNavGraph`.

**MVI Contract per screen:**
```
UiState     — immutable snapshot of what the screen should render
UiIntent    — user actions (e.g. SelectCategory, ToggleFavorite)
UiEffect    — one-shot side effects, used sparingly (e.g. ShowSnackbar)
```

**Movie Detail — sealed interface state:**

`MovieDetailScreen` uses a sealed interface instead of a flat state class, making each UI branch exhaustive and compiler-enforced:

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

The FAB is only rendered inside the `Success` branch — it is structurally impossible to display during loading or error.

---

### 2. Domain Layer

- Pure Kotlin — **no Android dependencies**.
- Contains domain models and the `MovieRepository` interface.
- **No Use Cases** — the repository is sufficiently simple for ViewModels to call directly. Use cases would be introduced if multi-step orchestration was required.

**Domain Models:**

| Model | Fields |
|---|---|
| `Movie` | `id`, `title`, `posterPath`, `backdropPath`, `overview`, `releaseDate`, `voteAverage` |
| `MovieDetail` | All `Movie` fields + `genres`, `runtime`, `tagline` |
| `Genre` | `id`, `name` |
| `VideoResult` | `key`, `site`, `type`, `official`, `publishedAt` |
| `Category` | `NOW_PLAYING`, `TOP_RATED`, `UPCOMING` |

---

### 3. Data Layer

Composed of two data sources, coordinated by `MovieRepositoryImpl`:

#### Remote (Network)

- **Retrofit** + **OkHttp** for HTTP calls.
- `TmdbApiService` defines all endpoints (movie lists, detail, videos, favorites).
- Response types use dedicated `*Response` / `*Request` DTOs mapped to domain models.
- `AuthInterceptor` injects the Bearer token into every request.
- `safeApiCall` wraps every API call — catches `HttpException` and `IOException`, parses the TMDB error body (`TmdbErrorBody`), and returns `NetworkResult<T>`.

#### Local (Database)

- **Room** (v1) for persisting movie cache and favorites.
- Entities: `MovieEntity` (category-keyed movie cache), `FavoriteEntity`.
- DAOs: `MovieDao` (cache read/write/delete), `FavoriteDao` (insert/delete/paged query/isFavorite).

#### Repository Implementation

- `MovieRepositoryImpl` coordinates between remote and local sources.
- **Offline-first for browsing:** serves cached pages from Room when offline; emits `NetworkUnavailableException` only when going beyond cached data or fetching detail/trailer.
- **Favorites:** `toggleFavorite` updates Room immediately (optimistic) then fire-and-forgets a `POST /account/{id}/favorite` call to the server. On success or failure, emits to `_favoriteChanges` SharedFlow.

---

## Navigation

### Bottom Navigation — 2 Tabs

```
┌──────────────────────────────────┐
│            Content               │
│                                  │
├─────────────┬────────────────────┤
│  🏠 Home    │  ❤️ Favorites      │
└─────────────┴────────────────────┘
```

- **Home tab** → `HomeScreen` (movie grid + category chip row)
- **Favorites tab** → `FavoritesScreen` (paginated saved movies grid)
- Both tabs share the `MovieDetail` route; back-stack is managed per tab

**Slide transitions** — detail screen slides in from the right on push, slides back on pop. Home/Favorites screens are stationary.

**Routes:**
```
home          →  moviedetail/{movieId}
favorites     →  moviedetail/{movieId}
```

---

## Screen Layouts

### Home Screen

```
┌──────────────────────────┐
│  [Now Playing][Top Rated]│  ← horizontally scrollable FilterChip row
├──────────────────────────┤
│  ┌──────┐  ┌──────┐      │
│  │Poster│  │Poster│  … ←─── LazyVerticalGrid (via MovieGrid)
│  │      │  │      │      │     (full-bleed poster + gradient + title + rating)
│  └──────┘  └──────┘      │
│  ┌──────┐  ┌──────┐      │
│  │      │  │      │      │
│  └──────┘  └──────┘      │
│  [NetworkErrorFooter]    │  ← only when paging hits offline boundary
└──────────────────────────┘
```

### Movie Detail Screen

```
┌──────────────────────────┐
│  ◀  [Trailer Player]     │  ← 16:9 embedded YouTube player AT THE TOP
│     (or backdrop image)  │     android-youtube-player WebView
├──────────────────────────┤
│  Poster | Title          │  ← MovieMetadata composable
│         | Year  Runtime  │
│         | ★ 7.8          │
│  [Action] [Drama]        │  ← Genre chips
│  "Tagline here"          │
│  Overview paragraph...   │
│                           │
│              [❤️ Save]   │  ← Animated FAB (only in Success state)
└──────────────────────────┘
```

### Favorites Screen

```
┌──────────────────────────┐
│  [Offline Banner]        │  ← animated, shown only when offline
├──────────────────────────┤
│  ┌──────┐  ┌──────┐      │
│  │ ←swipe│  │Poster│  ←─── SwipeToDismissBox wraps each MovieCard
│  │ removes│  │      │      │     swipe disabled when offline
│  └──────┘  └──────┘      │
│        ── or ──           │
│  💙 No saved movies yet   │  ← EmptyFavoritesState
└──────────────────────────┘
```

---

## Shared Components

| Component | File | Description |
|---|---|---|
| `MovieCard` | `common/MovieCard.kt` | Full-bleed poster card with gradient, title, rating. Tagged with `testTag("movie_card")` |
| `MovieGrid` | `common/MovieGrid.kt` | Shared paging grid used by both Home and Favorites. Owns the append footer. |
| `MoviePosterImage` | `common/MoviePosterImage.kt` | `AsyncImage` with crossfade, placeholder, and error drawable |
| `RatingBadge` | `common/RatingBadge.kt` | Star icon + formatted score |
| `CategoryFilterRow` | `common/CategoryFilterRow.kt` | Horizontal `LazyRow` of Material 3 `FilterChip`s |
| `TrailerPlayerSection` | `common/TrailerPlayerSection.kt` | YouTube embed or backdrop fallback |
| `NetworkErrorFooter` | `common/NetworkErrorFooter.kt` | Inline footer shown at the paging append boundary |
| `ErrorView` | `common/ErrorView.kt` | Full-screen error state with retry |
| `OfflineBanner` | `common/OfflineBanner.kt` | Animated offline banner, shared by Home and Favorites |

---

## Paging Architecture

Three `PagingSource` implementations:

| Class | Source (online) | Source (offline) |
|---|---|---|
| `MoviePagingSource` | `GET /movie/{category}` | `movies` Room table (by category) |
| `FavoritesPagingSource` | `GET /account/{id}/favorite/movies` | `favorites` Room table ordered by `savedAt DESC` |

Shared constants in `PagingDefaults` (package-level `object`):
```kotlin
STARTING_PAGE_INDEX = 1
PAGE_SIZE           = 20
PREFETCH_DISTANCE   = 5
```

---

## Favorites Refresh Strategy

`FavoritesViewModel` uses a `SharedFlow`-based trigger to avoid an infinite invalidation loop:

1. `toggleFavorite()` in `MovieRepositoryImpl` emits to `_favoriteChanges: MutableSharedFlow<Unit>`
2. `FavoritesViewModel.favoritesFlow` listens: `repository.favoriteChanges.onStart { emit(Unit) }.flatMapLatest { repository.getFavorites() }.cachedIn(viewModelScope)`
3. Any toggle from any screen (detail FAB, favorites swipe) emits → `flatMapLatest` cancels the old pager, creates a new one → grid reloads

This avoids the Room `InvalidationTracker` approach (which caused an infinite reload loop because the paging source was writing to the same table it was listening to).

---

## Error Handling

All API calls go through `safeApiCall`:

```kotlin
sealed class NetworkResult<out T> {
    data class Success<T>(val data: T) : NetworkResult<T>()
    data class Error(val message: String, val httpCode: Int = 0) : NetworkResult<Nothing>()
}
```

- **HTTP errors:** TMDB error body (`{"status_message": "..."}`) is parsed locally inside `safeApiCall` using a `Json` instance created inline. The `statusMessage` is used as the user-facing string; `ApiError.SERVER_ERROR.message` is the fallback if parsing fails.
- **Connectivity errors:** mapped to `ApiError` enum entries (`NO_CONNECTION`, `TIMEOUT`, `UNEXPECTED`).
- **Paging errors:** `LoadResult.Error(e)` → Paging 3 renders `NetworkErrorFooter` (inline, append state) or `ErrorView` (full-screen, refresh state).

---

## Offline Handling Strategy

| Scenario | Behaviour |
|---|---|
| Scrolling cached movie pages | Served from Room — no network needed |
| Scrolling past cached pages while offline | `NetworkErrorFooter` inline at the bottom |
| Opening movie detail while offline | Full-screen `ErrorView` with Retry |
| Trailer fetch while offline | `null` trailer key → backdrop image shown, no error |
| Images while offline | Served from Coil `DiskCache` (1-day TTL) |
| Favorites while offline | Room cache served; swipe disabled; offline banner shown |

---

## Image Caching

- **Library:** Coil `AsyncImage` with a **dedicated `OkHttpClient`** (separate from Retrofit's API client)
- **Two-tier cache:**
  - Memory cache — 25% of available RAM; process lifetime
  - Disk cache (`image_cache/`, 100 MB) — persists across kills; 1-day TTL
- **TTL enforcement:** a network interceptor stamps every image response with `Cache-Control: max-age=86400`. Coil's `DiskCache` respects this header and re-fetches after 24 hours.
- `diskCachePolicy = ENABLED` (default) — Coil writes to and reads from its own disk cache without interference from OkHttp's cache layer.

---

## Testing Strategy

| Test type | Tools | What's covered |
|---|---|---|
| ViewModel unit tests | MockK, Turbine, `kotlinx-coroutines-test` | All ViewModels — every intent/state transition |
| Paging unit tests | MockK, `paging-testing` | `MoviePagingSource`, `FavoritesPagingSource` |
| Repository unit tests | MockK | `MovieRepositoryImpl` — detail, trailer, toggle |
| Mapper unit tests | JUnit | DTO → domain, entity round-trips |
| Utility unit tests | JUnit | `TmdbImageUrl`, `CategoryExt`, `ApiError` |
| UI component tests | `compose-ui-test` | All shared composables, flow/state tests |
| E2E journey tests | Hilt + `@HiltAndroidTest` + live TMDB API | Full screen flows: home → detail → back, favorites toggle, swipe |

---

## Dependency Injection

**Hilt** manages all dependencies. Modules are organized by concern:

| Module | Provides |
|---|---|
| `NetworkModule` | `Retrofit`, API `OkHttpClient`, `TmdbApiService`, Coil `ImageLoader` with dedicated `OkHttpClient`, `@Named` TMDB account/session strings |
| `DatabaseModule` | `AppDatabase`, `MovieDao`, `FavoriteDao` |
| `RepositoryModule` | `MovieRepository` interface → `MovieRepositoryImpl` binding |
| `UtilModule` | `NetworkMonitor` |

**For instrumented tests:** `HiltTestRunner` boots `HiltTestApplication`. Tests annotated with `@HiltAndroidTest` get full Hilt injection with the real production modules — no module overrides needed (the journey tests test the real stack).

---

## State Management

- ViewModels use `StateFlow` for UI state (hot, always has a current value).
- One-shot effects use `Channel` → `receiveAsFlow()` where applicable.
- Paging state is handled by Paging 3's `LazyPagingItems` in Compose.
- `NetworkMonitor.isOnline` flows into `HomeState.isOffline` and `FavoritesState.isOffline` reactively.
- `observeIsFavorite(movieId)` is a Room `Flow<Boolean>` used in `MovieDetailViewModel` to track the FAB state for a single movie.

---

## Threading Model

| Operation | Dispatcher |
|---|---|
| Network calls | `Dispatchers.IO` (Retrofit) |
| DB reads/writes | `Dispatchers.IO` (Room) |
| PagingSource.load() | Background thread managed by Paging 3 |
| ViewModel logic | main-safe (Room + Retrofit handle their own threading) |
| UI updates | `Dispatchers.Main` (via `StateFlow`) |

---

## Constants Strategy

Every non-obvious literal is a named `const val` co-located with the class that owns it.

| Constant(s) | Owner |
|---|---|
| `QUERY_PARAM_API_KEY`, `HEADER_AUTHORIZATION` | `AuthInterceptor` |
| `HEADER_CACHE_CONTROL`, `CACHE_CONTROL_ONE_DAY`, `IMAGE_CACHE_MAX_SIZE_BYTES` | `NetworkModule` companion |
| `DEFAULT_LANGUAGE`, `DEFAULT_PAGE` | `TmdbApiService.Companion` |
| `STARTING_PAGE_INDEX`, `PAGE_SIZE`, `PREFETCH_DISTANCE` | `PagingDefaults` (package-level object) |
| `VIDEO_SITE_YOUTUBE`, `VIDEO_TYPE_TRAILER` | `MovieRepositoryImpl.Companion` |
| `DEFAULT_MESSAGE` | `NetworkUnavailableException.Companion` |
| `DATABASE_NAME` | `AppDatabase.Companion` |
| `BACK_BUTTON_SCRIM_ALPHA`, `FAB_COLOR_ANIMATION_MS`, `RELEASE_YEAR_CHAR_COUNT` | `DetailScreenDefaults` (private object in `MovieDetailScreen.kt`) |
| `ASPECT_RATIO`, `SCRIM_HEIGHT_FRACTION`, `SCRIM_GRADIENT_ALPHA` | `MovieCardDefaults` (private object in `MovieCard.kt`) |

---

## Module Graph (High Level)

```
:app
  ├── presentation/
  │     ├── home/
  │     ├── moviedetail/
  │     ├── favorites/
  │     ├── common/
  │     ├── navigation/
  │     └── theme/
  ├── domain/
  │     ├── model/
  │     └── repository/
  ├── data/
  │     ├── local/
  │     ├── remote/
  │     ├── repository/
  │     └── mapper/
  ├── di/
  └── util/
```

> The app lives in a single Gradle module (`:app`) for simplicity. The package structure mirrors a multi-module layout, making extraction straightforward as a future step.
