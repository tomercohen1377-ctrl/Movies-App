# Implementation Plan

Estimated total effort: **~8 development hours**
Target platform: **Android** (Jetpack Compose + Clean Architecture + MVI)

---

## Requirements Recap

| # | Requirement | Notes |
|---|---|---|
| 1 | 2 bottom navigation tabs: **Home** and **Favorites** | No other tabs |
| 2 | Trailer plays **inside the Movie Details screen at the top** | Embedded player, not external app |
| 3 | Category filter as a **horizontally scrollable chip row** on the Home screen | Material 3 `FilterChip` |
| 4 | Movie cards on Home show the **poster image** | Full-bleed poster card |
| 5 | Cached images expire after **1 day** | Coil DiskCache + OkHttp Cache-Control |
| 6 | **Unit tests for every ViewModel and every UI component** | Not just critical paths |
| 7 | Offline: **scroll cached data freely**; show a network error only when a live API call is needed | No blanket offline banner |

---

## Phase 1 — Project Setup (~1 hour)

### Step 1.1 — Create Android Project
- [ ] New Android Studio project (Empty Activity, min SDK 26, target SDK 34+)
- [ ] Enable Kotlin DSL (`build.gradle.kts`)
- [ ] Configure `local.properties` for TMDB API key (never commit the key)

### Step 1.2 — Add Dependencies
Add to `libs.versions.toml` (version catalog):

```
# Core
compose-bom
compose-navigation
hilt + hilt-navigation-compose

# Network
retrofit
okhttp + logging-interceptor
kotlinx-serialization (or gson)

# Image
coil-compose

# Local
room + room-ktx

# Paging
paging3 + paging-compose

# Video — embedded trailer player inside detail screen
media3-exoplayer
media3-ui
media3-exoplayer-hls        # for HLS streams if needed

# Testing
junit4
mockk
kotlinx-coroutines-test
turbine                     # Flow testing
compose-ui-test             # UI component tests
```

### Step 1.3 — Package Structure

```
com.yourname.moviesapp/
├── data/
│   ├── local/
│   ├── remote/
│   ├── repository/
│   └── mapper/
├── domain/
│   ├── model/
│   ├── repository/
│   └── usecase/
├── presentation/
│   ├── home/               # Home tab (movie list + category filter)
│   ├── moviedetail/        # Detail screen with embedded trailer
│   ├── favorites/          # Favorites tab
│   ├── navigation/         # NavGraph + BottomNavBar
│   └── common/             # Shared composables
└── di/
```

### Step 1.4 — Hilt Setup
- [ ] Annotate `Application` class with `@HiltAndroidApp`
- [ ] Annotate `MainActivity` with `@AndroidEntryPoint`
- [ ] Create `NetworkModule`, `DatabaseModule`, `RepositoryModule`

---

## Phase 2 — Data Layer (~1.5 hours)

### Step 2.1 — TMDB API Service
- [ ] Create `TmdbApiService` (Retrofit interface) with endpoints:
  - `GET /movie/upcoming`
  - `GET /movie/top_rated`
  - `GET /movie/now_playing`
  - `GET /movie/{movie_id}`
  - `GET /movie/{movie_id}/videos`
- [ ] Create `AuthInterceptor` to inject `api_key` query param
- [ ] Create DTO data classes for each response

### Step 2.2 — Domain Models
- [ ] `Movie(id, title, posterPath, backdropPath, overview, releaseDate, voteAverage, voteCount)`
- [ ] `MovieDetail` (extends Movie with `genres`, `runtime`, `tagline`)
- [ ] `VideoResult(key, site, type)` — for embedded trailer
- [ ] `Category` enum: `UPCOMING`, `TOP_RATED`, `NOW_PLAYING`

### Step 2.3 — Mappers
- [ ] `MovieDto → Movie`
- [ ] `MovieDetailDto → MovieDetail`
- [ ] `VideoResultDto → VideoResult`
- [ ] `MovieEntity → Movie` (and reverse)

### Step 2.4 — Room Database
- [ ] `MovieEntity` — cached movie list rows (per category + page)
- [ ] `FavoriteEntity(id, title, posterPath, releaseDate, voteAverage)`
- [ ] `MovieDao` — insert/query cached movies by category
- [ ] `FavoriteDao` with `insert`, `delete`, `observeAll`, `isFavorite(id)` methods
- [ ] `AppDatabase` with version and migration strategy

### Step 2.5 — Offline-Aware Paging Source
- [ ] `MoviePagingSource(apiService, movieDao, category, isOnline)` — implements `PagingSource<Int, Movie>`
- [ ] **Online path:** fetch from network, cache results to `MovieDao`, return page
- [ ] **Offline path:** read from `MovieDao` for the requested category; on page boundary beyond cache, emit `LoadResult.Error` with a `NetworkUnavailableException`
- [ ] This satisfies requirement #7: cached pages scroll freely; going beyond cache while offline surfaces an error

### Step 2.6 — Repository Implementation
- [ ] `MovieRepositoryImpl` injected with `TmdbApiService`, `MovieDao`, `FavoriteDao`
- [ ] `getMovies(category)` → `Flow<PagingData<Movie>>`
- [ ] `getMovieDetail(id)` → `Flow<MovieDetail>` (network only; throws `NetworkUnavailableException` if offline)
- [ ] `getTrailer(id)` → `VideoResult?` (network only; null if offline)
- [ ] `toggleFavorite(movie)` → insert or delete in Room
- [ ] `getFavorites()` → `Flow<List<Movie>>`
- [ ] `isFavorite(id)` → `Flow<Boolean>`

### Step 2.7 — Network Monitor
- [ ] `NetworkMonitor` utility using `ConnectivityManager.NetworkCallback`
- [ ] Exposes `isOnline: Flow<Boolean>` and a synchronous `isCurrentlyOnline(): Boolean`
- [ ] Injected into `PagingSource` and ViewModels to gate live API calls

---

## Phase 3 — Domain Layer (~0.5 hours)

### Step 3.1 — Repository Interface
- [ ] `MovieRepository` interface matching the implementation contract

### Step 3.2 — Use Cases
- [ ] `GetMoviesUseCase(repo)` → `operator fun invoke(category)`
- [ ] `GetMovieDetailUseCase(repo)`
- [ ] `GetMovieTrailerUseCase(repo)`
- [ ] `ToggleFavoriteUseCase(repo)`
- [ ] `GetFavoritesUseCase(repo)`
- [ ] `IsFavoriteUseCase(repo)`

---

## Phase 4 — Presentation Layer (~3 hours)

### Step 4.1 — Navigation & Bottom Tabs

**Two tabs only:**
| Tab | Icon | Route |
|---|---|---|
| Home | `Icons.Filled.Home` | `home` |
| Favorites | `Icons.Filled.Favorite` | `favorites` |

- [ ] `BottomNavBar` composable with exactly 2 items
- [ ] `AppNavGraph` with nested nav for each tab
- [ ] `MovieDetail` route (`moviedetail/{movieId}`) navigable from both tabs
- [ ] Back-stack correctly restored per tab on re-selection

---

### Step 4.2 — Home Screen (Movie List + Category Filter)

**MVI contract:**
```kotlin
data class HomeState(
    val movies: LazyPagingItems<Movie>?,       // Paging 3
    val selectedCategory: Category,
    val isOffline: Boolean,
    val networkError: String?                  // shown only when live call fails
)

sealed class HomeIntent {
    data class SelectCategory(val category: Category) : HomeIntent()
    data class OpenDetail(val movieId: Int) : HomeIntent()
    object DismissNetworkError : HomeIntent()
}

sealed class HomeEffect {
    data class NavigateToDetail(val movieId: Int) : HomeEffect()
}
```

**UI layout — top to bottom:**
1. **Top App Bar** — app title
2. **Category Dropdown** — expandable selector (see below)
3. **Movie Grid** — `LazyVerticalGrid` of `MovieCard`s

**Category Chip Row (requirement #3):**
- [ ] `CategoryFilterRow` — a `LazyRow` of Material 3 `FilterChip` composables
- [ ] One chip per category: Upcoming, Top Rated, Now Playing
- [ ] Selected chip renders with `selected = true` (filled tint + checkmark icon)
- [ ] Tapping a chip fires `SelectCategory` intent → updates `selectedCategory` and invalidates paging
- [ ] Row is horizontally scrollable so additional categories can be added later without layout changes

**Movie Card (requirement #4):**
- [ ] `MovieCard(movie, onClick)` — fixed aspect-ratio card
- [ ] Full-bleed poster image via `AsyncImage` (Coil) with `ContentScale.Crop`
- [ ] Gradient scrim at the bottom for text readability
- [ ] Movie title + rating badge overlaid on the scrim
- [ ] Ripple tap effect → triggers `OpenDetail` intent

**Network error handling (requirement #7):**
- [ ] When `LazyPagingItems` emits `LoadState.Error` with `NetworkUnavailableException` → show inline error footer with "No internet. Showing cached results." and a Retry button
- [ ] Cached pages already in the list continue to be scrollable — no full-screen error
- [ ] If no cached data at all → full-screen empty/error state

---

### Step 4.3 — Movie Detail Screen (with embedded trailer)

**MVI contract:**
```kotlin
data class MovieDetailState(
    val movie: MovieDetail?,
    val isFavorite: Boolean,
    val trailerKey: String?,          // YouTube video key
    val isTrailerLoading: Boolean,
    val isPlayerReady: Boolean,       // ExoPlayer/WebView ready to render
    val isLoading: Boolean,
    val error: String?
)

sealed class MovieDetailIntent {
    object LoadDetail : MovieDetailIntent()
    object ToggleFavorite : MovieDetailIntent()
    object NavigateBack : MovieDetailIntent()
}

sealed class MovieDetailEffect {
    object NavigateBack : MovieDetailEffect()
    data class ShowError(val message: String) : MovieDetailEffect()
}
```

**UI layout — top to bottom (requirement #2):**
1. **Trailer Player (top of screen)**
   - [ ] `TrailerPlayerSection` composable — appears at the very top, 16:9 aspect ratio
   - [ ] If `trailerKey != null` → embed an `AndroidView` hosting a `WebView` loading `https://www.youtube.com/embed/{key}?autoplay=1` OR a `PlayerView` backed by `ExoPlayer` + HLS
   - [ ] While `isTrailerLoading == true` → show a poster image with a centered play icon overlay
   - [ ] If `trailerKey == null` (no trailer or offline) → show backdrop image as fallback
2. **Content below trailer** (scrollable `Column` inside `LazyColumn`):
   - [ ] Poster thumbnail + title + release year + runtime
   - [ ] Star rating + vote count
   - [ ] Genre chips (horizontal `FlowRow`)
   - [ ] Tagline (italic, muted)
   - [ ] Overview text
   - [ ] Favorite button (heart FAB — animated fill/unfill)
3. **Back button** — top-left arrow over the player

**Transition animation:**
- [ ] Shared element transition on poster image from `MovieCard` → detail screen (or `Crossfade` if shared element is unavailable on the target API level)

---

### Step 4.4 — Favorites Screen

**MVI contract:**
```kotlin
data class FavoritesState(
    val favorites: List<Movie>,
    val isEmpty: Boolean
)

sealed class FavoritesIntent {
    data class OpenDetail(val movieId: Int) : FavoritesIntent()
    data class RemoveFavorite(val movieId: Int) : FavoritesIntent()
}
```

**UI components:**
- [ ] `LazyVerticalGrid` using the same `MovieCard` component as the Home screen
- [ ] Swipe-to-remove with `SwipeToDismissBox`
- [ ] Empty state illustration + "No saved movies yet" copy + CTA button to Home tab

---

### Step 4.5 — Shared UI Components

| Component | Description |
|---|---|
| `MovieCard(movie, onClick)` | Full-bleed poster card with gradient + title + rating |
| `MoviePosterImage(url, modifier)` | `AsyncImage` with crossfade, placeholder, and error drawable |
| `RatingBadge(rating)` | Star icon + formatted score |
| `CategoryFilterRow(selected, onSelect)` | Horizontal `LazyRow` of Material 3 `FilterChip`s |
| `TrailerPlayerSection(trailerKey, backdropUrl)` | Embedded trailer or backdrop fallback |
| `NetworkErrorFooter(onRetry)` | Inline error shown in paging footer |
| `ErrorView(message, onRetry)` | Full-screen error state |
| `LoadingIndicator` | Centered `CircularProgressIndicator` |
| `ShimmerBox(modifier)` | Skeleton shimmer placeholder |

---

## Phase 5 — Image Caching (requirement #5) (~0.25 hours)

- [ ] Configure Coil's `ImageLoader` in `di/NetworkModule`:
  ```kotlin
  ImageLoader.Builder(context)
      .diskCache {
          DiskCache.Builder()
              .directory(cacheDir.resolve("image_cache"))
              .maxSizeBytes(100 * 1024 * 1024) // 100 MB
              .build()
      }
      .diskCachePolicy(CachePolicy.ENABLED)
      .memoryCachePolicy(CachePolicy.ENABLED)
      .build()
  ```
- [ ] Enforce **1-day expiration** via OkHttp network interceptor:
  ```kotlin
  .addNetworkInterceptor { chain ->
      chain.proceed(chain.request()).newBuilder()
          .header("Cache-Control", "max-age=86400, must-revalidate")
          .build()
  }
  ```
- [ ] Provide the same `OkHttpClient` to both Retrofit and Coil so both share the same disk cache budget

---

## Phase 6 — Unit Tests (requirement #6) (~1.5 hours)

> **Rule:** Every ViewModel and every non-trivial UI component must have a test. No exceptions.

### ViewModels
- [ ] `HomeViewModelTest`
  - `selectCategory` → state reflects new category, paging invalidated
  - `toggleCategoryDropdown` → `isCategoryDropdownExpanded` flips
  - `dismissNetworkError` → `networkError` cleared
- [ ] `MovieDetailViewModelTest`
  - initial load → `isLoading = true`, then `movie != null`
  - `toggleFavorite` → `isFavorite` toggles
  - network error on detail load → `error` set in state
  - offline + no trailer → `trailerKey = null`
- [ ] `FavoritesViewModelTest`
  - favorites list updates reactively
  - `removeFavorite` → item removed from state

### Use Cases
- [ ] `GetMoviesUseCaseTest` — delegates to repository with correct category
- [ ] `GetMovieDetailUseCaseTest` — returns correct domain model
- [ ] `ToggleFavoriteUseCaseTest` — calls insert/delete based on current state
- [ ] `GetFavoritesUseCaseTest` — emits updated list on DB change

### Data Layer
- [ ] `MoviePagingSourceTest`
  - online: returns correct `LoadResult.Page` with `nextKey`
  - last page: `nextKey == null`
  - offline beyond cache: returns `LoadResult.Error(NetworkUnavailableException)`
- [ ] `MovieRepositoryImplTest` — mock `TmdbApiService` + `FavoriteDao`, verify mappers called

### UI Components
- [ ] `MovieCardTest` — renders title, poster; click triggers `onClick`
- [ ] `CategoryFilterRowTest` — correct chip is shown as selected; tapping a chip fires `onSelect`
- [ ] `TrailerPlayerSectionTest` — shows backdrop when `trailerKey == null`; shows player container when key present
- [ ] `NetworkErrorFooterTest` — visible on error state; retry button calls `onRetry`
- [ ] `FavoritesScreenTest` — empty state visible when list is empty; grid visible when non-empty
- [ ] `RatingBadgeTest` — displays formatted score

---

## Phase 7 — Offline Handling (requirement #7) (~0.5 hours)

### Strategy Summary
> The app distinguishes between two offline scenarios:
> - **Cached data available** → user scrolls freely, no error shown
> - **Live API required (detail/trailer/beyond cached pages)** → targeted network error, not a blanket banner

- [ ] `NetworkMonitor` injects `isOnline` into `PagingSource`
- [ ] `MoviePagingSource` serves Room cache for already-loaded pages even when offline
- [ ] When offline paging hits a page not in cache → `LoadResult.Error` → `NetworkErrorFooter` shown below the list
- [ ] `MovieDetailViewModel` checks `isOnline` before fetching; if offline → sets `error = "No internet connection"` in state → `ErrorView` shown
- [ ] `GetMovieTrailerUseCase` returns `null` gracefully when offline (no crash, no error banner — detail screen simply shows backdrop)
- [ ] No full-screen "You are offline" overlay — existing cached content always remains visible and scrollable

---

## Phase 8 — Polish & Submission (~0.5 hours)

- [ ] Run on a physical device and an emulator (API 26 + API 34)
- [ ] Fix visual regressions and animation jank
- [ ] Verify 1-day cache expiry with Charles Proxy or offline toggle
- [ ] Final commit, push to private GitHub repo
- [ ] Share repo link / add `@talezion` as collaborator

---

## Commit Strategy

```
feat: project setup + dependency config
feat: data layer - Retrofit, Room, DTOs, mappers
feat: offline-aware paging source + NetworkMonitor
feat: domain layer - models + use cases
feat: navigation - 2-tab bottom nav (Home + Favorites)
feat: home screen - movie grid + expandable category dropdown
feat: movie detail - embedded trailer at top + favorites toggle
feat: favorites screen - grid + swipe to remove
feat: image caching with 1-day expiration
feat: offline handling - cached scroll + targeted network errors
test: unit tests - viewmodels, use cases, paging, UI components
docs: final README + architecture docs
```
