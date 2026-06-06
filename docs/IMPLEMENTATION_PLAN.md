# Implementation Summary

> **Status: ✅ FULLY IMPLEMENTED**
> 188 tests passing — 101 unit tests (JVM) + 87 instrumented tests (device), including 8 real end-to-end journey tests against the live TMDB API.

---

## Requirements

| # | Requirement | Status |
|---|---|---|
| 1 | 2 bottom navigation tabs: **Home** and **Favorites** | ✅ |
| 2 | Trailer plays **inside the Movie Detail screen at the top** | ✅ |
| 3 | Category filter as a **horizontally scrollable chip row** | ✅ |
| 4 | Movie cards on Home show the **poster image** | ✅ |
| 5 | Cached images expire after **1 day** | ✅ |
| 6 | **Unit tests for every ViewModel and every UI component** | ✅ |
| 7 | Offline: **scroll cached data freely**; show error only when live API is needed | ✅ |

---

## What Was Built

### Phase 1 — Project Setup

- Android project with Kotlin DSL, `libs.versions.toml` version catalog, min SDK 26 / target SDK 35
- Hilt DI setup: `HiltAndroidApp`, `@AndroidEntryPoint`, `NetworkModule`, `DatabaseModule`, `RepositoryModule`, `UtilModule`
- TMDB API key and Read Access Token committed directly in `build.gradle.kts` (read-only credentials; no setup needed to run)
- `BuildConfig` fields: `TMDB_API_KEY`, `TMDB_READ_ACCESS_TOKEN`, `TMDB_ACCOUNT_ID`, `TMDB_SESSION_ID`

### Phase 2 — Data Layer

**Network:**
- `TmdbApiService` (Retrofit interface) with 6 endpoints: movie lists (3 categories), detail, videos, and favorites
- `AuthInterceptor` — injects Bearer token into every request
- `safeApiCall` — wraps every API call; catches `HttpException` (parses TMDB `status_message` body) and `IOException`; returns `NetworkResult<T>`
- DTOs: `MovieListResponse`, `MovieResponse`, `MovieDetailsResponse`, `GenreResponse`, `VideoListResponse`, `VideoResponse`, `FavoriteRequest`, `FavoriteResponse`, `TmdbErrorBody`

**Domain Models:** `Movie`, `MovieDetail`, `Genre`, `VideoResult`, `Category` enum (`NOW_PLAYING`, `TOP_RATED`, `UPCOMING`)

**Mappers:** `MovieResponse → Movie`, `MovieDetailsResponse → MovieDetail`, `GenreResponse → Genre`, `VideoResponse → VideoResult`, `Movie → MovieEntity` (and reverse)

**Room (v1):**
- `MovieEntity` — category-keyed movie cache with `page`
- `FavoriteEntity` — persisted favorites with `savedAt` timestamp for offline ordering
- `MovieDao` — insert, query by category+page, delete by category, `getOldestCachedAt`
- `FavoriteDao` — insert, delete, `getFavoritesPaged`, `isFavorite`
- `AppDatabase` — single `@Database` class; KDoc explains the indirect usage pattern

**Paging Sources:**
- `MoviePagingSource` — online: fetches from TMDB + caches to Room; offline: reads from Room; page-1 clears old cache on fresh fetch
- `FavoritesPagingSource` — online: fetches from `GET /account/{id}/favorite/movies`; offline: reads from `favorites` table ordered by `savedAt DESC`
- `PagingDefaults` — shared constants object: `STARTING_PAGE_INDEX = 1`, `PAGE_SIZE = 20`, `PREFETCH_DISTANCE = 5`

**Repository:**
- `MovieRepository` interface + `MovieRepositoryImpl`
- `getMovies(category)` → `Flow<PagingData<Movie>>`
- `getMovieDetail(id)` → `NetworkResult<MovieDetail>`
- `getTrailer(id)` → `NetworkResult<VideoResult?>` (returns `Success(null)` when offline)
- `toggleFavorite(movie)` — optimistic Room update + fire-and-forget `POST /account/{id}/favorite` server sync; emits to `_favoriteChanges: MutableSharedFlow<Unit>`
- `getFavorites()` → `Flow<PagingData<Movie>>`
- `isFavorite(id)` → `Flow<Boolean>`
- `favoriteChanges` → `Flow<Unit>` (SharedFlow; drives pager restarts in FavoritesViewModel)

**Network Monitor:** `NetworkMonitor` using `ConnectivityManager.NetworkCallback`; exposes `isOnline: Flow<Boolean>` and `isCurrentlyOnline(): Boolean`

### Phase 3 — Domain Layer

- `MovieRepository` interface
- No use cases — ViewModels call the repository directly (sufficient complexity level for this project)
- `CategoryExt.kt` — `displayName` extension on `Category` enum; `toApiParam()` for endpoint path segments

### Phase 4 — Presentation Layer

**Navigation:**
- `Screen` sealed class with `Home`, `Favorites`, `MovieDetail(movieId)` routes
- `AppNavGraph` with slide-in/slide-out animations (detail slides right; pop slides back)
- `BottomNavBar` — 2 tabs (Home + Favorites)

**Home Screen:**
- `HomeContract` — `HomeState(selectedCategory, isOffline)`, `HomeIntent`, `HomeEffect`
- `HomeViewModel` — manages category selection + network status observation
- `HomeScreen` — `CategoryFilterRow` (defaults to `NOW_PLAYING`) + `MovieGrid` + `OfflineBanner`

**Movie Detail Screen (3 files):**
- `MovieDetailContract` — sealed `MovieDetailUiState`: `Loading`, `Error(message)`, `Success(movie, trailerKey, isFavorite)`. No `NavigateBack` intent/effect — back button calls `onNavigateBack` directly.
- `MovieDetailViewModel` — loads detail + trailer in parallel via `async`; isFavorite from Room `Flow`; all state via sealed interface
- `MovieDetailScreen` — `when(uiState)` drives the whole screen; FAB only in `Success` branch
- `MovieDetailContent` (separate file) — scrollable body (trailer + metadata + FAB spacer)
- `MovieMetadata` (separate file) — poster thumbnail, title, year, runtime, rating, genre chips, tagline, overview

**Favorites Screen (2 files):**
- `FavoritesContract` — `FavoritesState(isOffline)`, `FavoritesIntent`, `FavoritesEffect`
- `FavoritesViewModel` — drives `favoritesFlow` via `favoriteChanges.flatMapLatest`; exposes network status
- `FavoritesScreen` — offline banner + `when(loadState)` for loading/error/content/empty
- `FavoritesComponents` — `FavoritesGrid` (swipe-to-dismiss, disabled when offline) + `EmptyFavoritesState`

**Shared Components:**
- `MovieCard` — full-bleed poster, gradient scrim, title, `RatingBadge`, `testTag("movie_card")`
- `MovieGrid` — shared paging grid used by both Home and Favorites; owns append footer
- `MoviePosterImage` — `AsyncImage` with crossfade, placeholder, error drawable
- `CategoryFilterRow` — `LazyRow` of Material 3 `FilterChip`s
- `RatingBadge` — star icon + formatted score
- `TrailerPlayerSection` — YouTube embed via `WebView` or backdrop fallback
- `NetworkErrorFooter` — inline paging append error + Retry
- `ErrorView` — full-screen error + Retry
- `OfflineBanner` — animated `AnimatedVisibility` banner; shared by Home and Favorites

### Phase 5 — Image Caching

- Coil `ImageLoader` with **dedicated `OkHttpClient`** (separate from Retrofit's)
- `DiskCache` (100 MB, `image_cache/` directory) — persists across app kills
- Memory cache (25% of available RAM) — process lifetime
- Network interceptor stamps responses with `Cache-Control: max-age=86400` (1-day TTL)
- `diskCachePolicy = ENABLED` (default) — no OkHttp cache layer; Coil owns disk storage
- Offline image serving works automatically within the TTL window

### Phase 6 — Testing

**101 unit tests (JVM):**

| File | Tests | Coverage |
|---|---|---|
| `HomeViewModelTest` | 9 | Category selection, offline state, paging |
| `MovieDetailViewModelTest` | 12 | All sealed states, trailer degradation, HTTP errors, favorites |
| `FavoritesViewModelTest` | 4 | Offline state, remove, paging |
| `MoviePagingSourceTest` | 10 | Online/offline paths, page keys, errors |
| `FavoritesPagingSourceTest` | 11 | Online/offline, offset pagination, next-page detection |
| `MovieRepositoryImplTest` | 13 | `getMovieDetail`, `getTrailer`, `toggleFavorite` |
| `MovieMapperTest` | 12 | DTO → domain, domain → entity, round-trips |
| `TmdbImageUrlTest` | 9 | `poster()`, `posterLarge()`, `backdrop()` |
| `CategoryExtTest` | 9 | `displayName`, ordering, uniqueness |
| `ApiErrorTest` | 7 | All enum entries, `NetworkResult.Error` round-trip |

**79 UI component tests (device):**

All shared composables, plus flow/state transition tests for Home, Detail, and Favorites screens.

**8 end-to-end journey tests (device, live TMDB API):**

`RealAppJourneyTest` launches the full `MainActivity` via `@HiltAndroidTest` with the real production stack.

| Journey | What's validated |
|---|---|
| `homeScreen_loadsRealMoviesFromTmdb` | Cards appear from live API |
| `homeScreen_categoryChips_visible` | All 3 filter chips shown |
| `homeScreen_tapMovieCard_opensDetailScreen` | Navigation to detail |
| `detailScreen_navigateBack_returnsToHome` | Back button returns to grid |
| `favoritesTab_showsEmptyState_initially` | Empty state on clean launch |
| `toggleFavorite_movieAppearsInFavoritesTab` | FAB adds movie to Favorites |
| `swipeLeft_removesMovieFromFavoritesList` | Swipe removes from Favorites grid |
| `favoritesTab_showsOnlyFavoritedMovies` | Non-favorited movies don't appear |

`@Before` clears Room + server favorites; `@After` repeats cleanup for isolation.

### Phase 7 — Offline Handling

- `MoviePagingSource` serves Room-cached pages offline; emits `NetworkUnavailableException` only at the boundary
- `FavoritesPagingSource` falls back to `favorites` table ordered by `savedAt DESC`
- `getTrailer()` fast-paths to `NetworkResult.Success(null)` when offline — trailer section shows backdrop, no error
- `getMovieDetail()` returns `NetworkResult.Error(ApiError.NO_CONNECTION.message)` when offline
- Offline banners on Home and Favorites driven by `NetworkMonitor.isOnline` flow
- Swipe-to-remove disabled when offline

### Phase 8 — Polish

- Slide animations on detail screen push/pop
- Filter chip order: `NOW_PLAYING` first (most relevant for users)
- `MovieCard` carries `testTag("movie_card")` for both component and journey tests
- All magic numbers replaced with named `const val` constants
- No unused files, functions, or members
- KDoc on all public and internal APIs
- `@Preview` on every composable

---

## Coding Conventions

### No Magic Numbers or Magic Strings

Every non-obvious literal is a named `const val` co-located with the class that owns it:

| Constant(s) | Owner |
|---|---|
| `QUERY_PARAM_API_KEY`, `HEADER_AUTHORIZATION` | `AuthInterceptor` |
| `HEADER_CACHE_CONTROL`, `CACHE_CONTROL_ONE_DAY`, `IMAGE_CACHE_MAX_SIZE_BYTES` | `NetworkModule` companion |
| `DEFAULT_LANGUAGE`, `DEFAULT_PAGE` | `TmdbApiService.Companion` |
| `STARTING_PAGE_INDEX`, `PAGE_SIZE`, `PREFETCH_DISTANCE` | `PagingDefaults` (package-level object) |
| `VIDEO_SITE_YOUTUBE`, `VIDEO_TYPE_TRAILER` | `MovieRepositoryImpl.Companion` |
| `DEFAULT_MESSAGE` | `NetworkUnavailableException.Companion` |
| `DATABASE_NAME` | `AppDatabase.Companion` |
| `BACK_BUTTON_SCRIM_ALPHA`, `FAB_COLOR_ANIMATION_MS`, `RELEASE_YEAR_CHAR_COUNT` | `DetailScreenDefaults` (private object) |
| `ASPECT_RATIO`, `SCRIM_HEIGHT_FRACTION`, `SCRIM_GRADIENT_ALPHA` | `MovieCardDefaults` (private object) |

### Other Conventions

- All public and internal functions/properties have KDoc
- No function longer than ~30 lines
- No raw `TODO()` in committed code
- Constants are `private` unless other modules legitimately reference them
