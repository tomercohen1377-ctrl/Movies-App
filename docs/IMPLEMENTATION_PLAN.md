# Implementation Plan

Estimated total effort: **~8 development hours**
Target platform: **Android** (Jetpack Compose + Clean Architecture + MVI)

---

## Phase 1 — Project Setup (~ 1 hour)

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
gson (or kotlinx-serialization)

# Image
coil-compose

# Local
room + room-ktx

# Paging
paging3 + paging-compose

# Video (Bonus)
youtube-android-player OR media3-exoplayer

# Testing
junit4, mockk, coroutines-test, turbine
```

### Step 1.3 — Package Structure
Create empty packages matching the architecture:
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
│   ├── movielist/
│   ├── moviedetail/
│   ├── favorites/
│   ├── navigation/
│   └── common/
└── di/
```

### Step 1.4 — Hilt Setup
- [ ] Annotate `Application` class with `@HiltAndroidApp`
- [ ] Annotate `MainActivity` with `@AndroidEntryPoint`
- [ ] Create `NetworkModule`, `DatabaseModule`, `RepositoryModule`

---

## Phase 2 — Data Layer (~ 1.5 hours)

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
- [ ] `VideoResult(key, site, type)` — for trailer
- [ ] `Category` enum: `UPCOMING`, `TOP_RATED`, `NOW_PLAYING`

### Step 2.3 — Mappers
- [ ] `MovieDto → Movie`
- [ ] `MovieDetailDto → MovieDetail`
- [ ] `VideoResultDto → VideoResult`
- [ ] `MovieEntity → Movie` (and reverse)

### Step 2.4 — Room Database
- [ ] `FavoriteEntity(id, title, posterPath, releaseDate, voteAverage)`
- [ ] `FavoriteDao` with `insert`, `delete`, `observeAll`, `isFavorite(id)` methods
- [ ] `AppDatabase` with version and migration strategy

### Step 2.5 — Paging Source
- [ ] `MoviePagingSource(apiService, category)` — implements `PagingSource<Int, Movie>`
- [ ] Handles page 1..n, returns `LoadResult.Page` or `LoadResult.Error`

### Step 2.6 — Repository Implementation
- [ ] `MovieRepositoryImpl` injected with `TmdbApiService` + `FavoriteDao`
- [ ] `getMovies(category)` → returns `Flow<PagingData<Movie>>`
- [ ] `getMovieDetail(id)` → returns `Flow<MovieDetail>`
- [ ] `getTrailer(id)` → returns `VideoResult?`
- [ ] `toggleFavorite(movie)` → insert or delete in Room
- [ ] `getFavorites()` → `Flow<List<Movie>>`
- [ ] `isFavorite(id)` → `Flow<Boolean>`

---

## Phase 3 — Domain Layer (~ 0.5 hours)

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

## Phase 4 — Presentation Layer (~ 3 hours)

### Step 4.1 — Navigation
- [ ] Define `Screen` sealed class with routes: `MovieList`, `MovieDetail(id)`, `Favorites`
- [ ] `AppNavGraph` composable wiring all screens
- [ ] `BottomNavBar` with icons for Movie List and Favorites tabs

### Step 4.2 — Movie List Screen
**MVI contract:**
```kotlin
data class MovieListState(
    val movies: LazyPagingItems<Movie>?,   // via Paging 3
    val selectedCategory: Category,
    val isLoading: Boolean,
    val error: String?
)

sealed class MovieListIntent {
    data class SelectCategory(val category: Category) : MovieListIntent()
    data class OpenDetail(val movieId: Int) : MovieListIntent()
}
```

**UI components:**
- [ ] `CategoryFilterRow` — horizontal scrollable chip row (Upcoming / Top Rated / Now Playing)
- [ ] `MovieGrid` or `MovieList` — `LazyVerticalGrid` / `LazyColumn` with `PagingItems`
- [ ] `MovieCard` — poster image (Coil), title overlay, rating badge
- [ ] Loading shimmer placeholder
- [ ] Error state with retry button
- [ ] Empty state illustration

### Step 4.3 — Movie Detail Screen
**MVI contract:**
```kotlin
data class MovieDetailState(
    val movie: MovieDetail?,
    val isFavorite: Boolean,
    val trailerKey: String?,
    val isLoading: Boolean,
    val error: String?
)

sealed class MovieDetailIntent {
    object ToggleFavorite : MovieDetailIntent()
    object PlayTrailer : MovieDetailIntent()
    object NavigateBack : MovieDetailIntent()
}
```

**UI components:**
- [ ] Shared element / fade transition from list card
- [ ] Backdrop image with parallax header
- [ ] Poster + title + release year + rating
- [ ] Genre chips
- [ ] Overview text (expandable)
- [ ] Favorite toggle button (heart icon, animated fill)
- [ ] "Play Trailer" button (Bonus)

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
- [ ] `LazyVerticalGrid` mirroring the movie list
- [ ] Swipe-to-remove gesture
- [ ] Empty state with CTA to browse movies

### Step 4.5 — Shared UI Components
- [ ] `MoviePosterImage(url, modifier)` — Coil + crossfade + placeholder
- [ ] `RatingBadge(rating)` — star icon + score
- [ ] `ErrorView(message, onRetry)`
- [ ] `LoadingIndicator`
- [ ] `ShimmerBox` (skeleton loading)

---

## Phase 5 — Image Caching (~ 0.25 hours)

- [ ] Configure Coil's `ImageLoader` in `di/NetworkModule`:
  ```kotlin
  ImageLoader.Builder(context)
      .diskCache {
          DiskCache.Builder()
              .directory(cacheDir.resolve("image_cache"))
              .maxSizeBytes(100 * 1024 * 1024) // 100MB
              .build()
      }
      .diskCachePolicy(CachePolicy.ENABLED)
      .memoryCachePolicy(CachePolicy.ENABLED)
      .build()
  ```
- [ ] OkHttp cache-control header to enforce 1-day expiration:
  ```kotlin
  .addNetworkInterceptor { chain ->
      chain.proceed(chain.request()).newBuilder()
          .header("Cache-Control", "max-age=86400")
          .build()
  }
  ```

---

## Phase 6 — Bonus Features (~ 1 hour)

### Step 6.1 — Trailer Playback
- [ ] Fetch YouTube video key via `/movie/{id}/videos` endpoint
- [ ] Open with YouTube Intent OR embed `YouTubePlayerView` / `ExoPlayer`

### Step 6.2 — Offline Handling
- [ ] Detect network state with `ConnectivityManager` / `NetworkCallback`
- [ ] Show a `SnackBar` or `Banner` when offline
- [ ] Display cached data with an "Offline" indicator
- [ ] Disable paginated loading gracefully when offline

### Step 6.3 — Unit Tests
Priority files to test:
- [ ] `MoviePagingSource` — verify page loading and error handling
- [ ] `GetMoviesUseCase` — verify it delegates correctly to the repository
- [ ] `MovieListViewModel` — test intent → state transitions using `Turbine`
- [ ] `MovieRepositoryImpl` — mock API + DAO and verify mapping

---

## Phase 7 — Polish & Submission (~ 0.75 hours)

- [ ] Run on a physical device and an emulator
- [ ] Fix any visual regressions
- [ ] Add `TMDB_API_KEY` instructions to `README.md`
- [ ] Final commit, push, share GitHub link

---

## Commit Strategy

Use incremental, descriptive commits aligned to each phase:

```
feat: initial project setup + dependency config
feat: data layer - Retrofit + Room + DTOs + mappers
feat: domain layer - models + use cases
feat: movie list screen with paging + category filter
feat: movie detail screen with favorites
feat: favorites tab
feat: image caching + pagination
feat: trailer playback (bonus)
feat: offline handling (bonus)
test: unit tests for critical components (bonus)
docs: final README + architecture docs
```
