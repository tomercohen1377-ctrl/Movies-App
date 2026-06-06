# Technical Decisions

This document explains the major technical choices made for the Movies App and the reasoning behind each decision.

---

## Architecture: Clean Architecture + MVI

**Decision:** Use Clean Architecture layered with MVI as the presentation pattern.

**Why:**
- **Clean Architecture** enforces strict separation of concerns: UI knows nothing about networking, domain knows nothing about Android. This makes every layer independently testable and replaceable.
- **MVI** (Model-View-Intent) is the natural fit for Compose because the UI is a pure function of state. A single immutable `UiState` eliminates whole classes of bugs (race conditions, partial updates) that plague MVVM with multiple `LiveData`/`StateFlow` fields.

**Trade-off:** More boilerplate vs. simpler patterns like MVVM. Justified here because the project is designed for production-quality code review.

---

## UI: Jetpack Compose + Material 3

**Decision:** 100% Jetpack Compose UI with Material 3 design system.

**Why:**
- Compose is the modern, declarative Android UI toolkit.
- Material 3 (Material You) provides well-tested, accessible components out of the box (chips, cards, bottom nav, FAB) and follows the Material Design guidelines.
- Compose's animation APIs (`AnimatedVisibility`, `AnimatedContent`, `slideInHorizontally`) make smooth screen transitions straightforward.
- `@Preview` annotations on every composable make UI iteration instant without a device.

---

## Networking: Retrofit + OkHttp

**Decision:** Retrofit with OkHttp as the HTTP client.

**Why:**
- Industry standard for Android networking. Mature, well-tested, and well-understood.
- OkHttp provides a powerful interceptor chain for injecting auth headers, logging, and response caching with a single line of configuration per concern.
- Coroutine support via `suspend` functions is first-class.

**Alternative considered:** Ktor (multiplatform). Rejected because this is an Android-only project and Retrofit is more idiomatic.

---

## Serialization: kotlinx.serialization

**Decision:** `kotlinx.serialization` for all JSON parsing.

**Why:**
- Kotlin-idiomatic and fully null-safe — serialization errors surface at compile time via `@Serializable`, not at runtime.
- Integrated with Retrofit via `JakewhartonConverter.asConverterFactory`.
- The `Json { ignoreUnknownKeys = true }` configuration is set once in `NetworkModule` and shared globally.
- `@EncodeDefault` is used on `FavoriteRequest.mediaType` to force serialization of fields with default values (workaround for `encodeDefaults = false` global setting).

**Alternative considered:** Gson. Rejected because it's Java-based and lacks null-safety guarantees.

---

## Error Handling: safeApiCall + NetworkResult

**Decision:** Wrap every Retrofit call in `safeApiCall` which always returns `NetworkResult<T>`, never throws.

**Why:**
- Try/catch at every call site is repetitive and easy to forget. A single wrapper centralises the contract.
- `NetworkResult` is a sealed class with exactly two variants: `Success(data)` and `Error(message, httpCode)`. This is simpler than a three-variant hierarchy (success / http error / network error) — the ViewModel just needs a string to show.
- HTTP errors are decoded from TMDB's JSON error body (`TmdbErrorBody` → `status_message`) using a locally-created `Json` instance inside `safeApiCall`. If parsing fails, `ApiError.SERVER_ERROR.message` is the fallback.
- Connectivity and timeout errors are mapped to `ApiError` enum entries (`NO_CONNECTION`, `TIMEOUT`, `UNEXPECTED`) which own their display strings in one place.

```kotlin
sealed class NetworkResult<out T> {
    data class Success<T>(val data: T) : NetworkResult<T>()
    data class Error(val message: String, val httpCode: Int = 0) : NetworkResult<Nothing>()
}
```

---

## Image Loading: Coil

**Decision:** Coil (`io.coil-kt:coil-compose`) for image loading and caching.

**Why:**
- Written in Kotlin, Coroutines-native, and has first-class Compose support (`AsyncImage`).
- Coil's `DiskCache` provides disk persistence across app kills — critical for offline image serving.
- Smaller binary footprint than Glide or Picasso.

**Key configuration:**
- A **dedicated `OkHttpClient`** is given to Coil (separate from Retrofit's). This prevents `Cache-Control` headers stamped on image responses from leaking into the API client's cache.
- A network interceptor stamps every image response with `Cache-Control: max-age=86400` (1 day). Coil's `DiskCache` honours this TTL.
- `diskCachePolicy = ENABLED` (the default) — Coil owns disk storage; no OkHttp `Cache` object is used for images.
- **Offline serving:** within the 24-hour TTL, images are served from disk with zero network calls.

**Alternative considered:** Glide. Rejected because Coil's Compose integration is cleaner and it's the modern recommendation.

---

## Pagination: Paging 3

**Decision:** Jetpack Paging 3 for infinite scroll on both Home and Favorites.

**Why:**
- First-party Jetpack library designed exactly for this use case.
- `LazyPagingItems` integrates seamlessly into `LazyVerticalGrid` in Compose.
- Handles loading state, error state, and retry logic out of the box.
- `PagingSource` is clean to implement and straightforward to unit test.

**Shared constants:** `PagingDefaults` is a package-level `internal object` that owns `STARTING_PAGE_INDEX = 1`, `PAGE_SIZE = 20`, and `PREFETCH_DISTANCE = 5`. Previously these were duplicated inside `MoviePagingSource`, `FavoritesPagingSource`, and `MovieRepositoryImpl.Companion`.

**`getRefreshKey`:** Returns the page number the user was last viewing, so a pager restart resumes from that position rather than page 1.

---

## Local Storage: Room

**Decision:** Room (v1) for the movie list cache and favorites.

**Why:**
- The definitive Android database library. Compile-time verified SQL, coroutines + Flow support.
- Movie list pages are cached in the `movies` table so they can be served offline. Stale rows are cleared on page-1 network fetch to prevent ghost entries.
- Favorites are stored in the `favorites` table with a `savedAt` timestamp used for offline ordering (`savedAt DESC`).
- `AppDatabase` is never referenced in feature code directly — Hilt injects the DAOs. A KDoc comment in the class explains this indirect usage pattern.

---

## Dependency Injection: Hilt

**Decision:** Hilt for DI.

**Why:**
- Built on top of Dagger but drastically reduces boilerplate.
- First-party Jetpack support; works seamlessly with `ViewModel`, `WorkManager`, and Navigation.
- `@Named` qualifiers disambiguate multiple `String` bindings (`tmdbAccountId`, `tmdbSessionId`).
- `@TestInstallIn` / `@UninstallModules` + `@HiltAndroidTest` allow the production DI graph to be partially overridden in instrumented tests without modifying any production code.

---

## Async: Kotlin Coroutines + Flow

**Decision:** Coroutines for async work, `Flow` for observable streams.

**Why:**
- Kotlin-native concurrency primitives. Structured concurrency prevents leaks.
- `StateFlow` for UI state, `Channel` for one-shot effects, `SharedFlow` for the `favoriteChanges` invalidation signal, `Flow` from Room and Paging 3 — all compose cleanly in `ViewModel`.
- `async { ... }.await()` is used in `MovieDetailViewModel` to fetch detail and trailer **in parallel**, halving the perceived loading time vs. sequential `await()` calls.

---

## Favorites: SharedFlow Invalidation

**Decision:** A `MutableSharedFlow<Unit>` named `favoriteChanges` drives pager restarts in `FavoritesViewModel` instead of Room's `InvalidationTracker`.

**Why:**
- `InvalidationTracker.addObserver()` asserts it's called off the main thread. Paging 3's `pagingSourceFactory` lambda runs on the main thread during `Pager` subscription — causing a crash.
- A write-triggered `SharedFlow` has no threading constraints and is simpler to reason about.
- `FavoritesViewModel` subscribes via `favoriteChanges.onStart { emit(Unit) }.flatMapLatest { getFavorites() }` — the initial `emit` fires the first load; any subsequent toggle from **any screen** restarts the pager.

---

## Video Playback: Embedded WebView (YouTube iframe)

**Decision:** Play trailers inline at the top of the Movie Detail screen using an embedded `WebView` (YouTube iframe).

**Why:**
- The detail screen must show the trailer inside the app, not launch an external YouTube app.
- The YouTube iframe embed API (`https://www.youtube.com/embed/{key}?autoplay=1`) is the simplest reliable path: a `WebView` inside an `AndroidView` composable, no deprecated SDK.
- **Offline behaviour:** if offline or the trailer key fetch returns null, the trailer section gracefully degrades to the backdrop image — no crash, no error banner.

**Rejected:**
- YouTube Android Player SDK — deprecated since 2023, requires a Google account sign-in on the device.
- Launching a YouTube Intent — violates the "inside the app" requirement.

---

## Category Filter: Horizontal Scrollable Chip Row

**Decision:** Use a `LazyRow` of Material 3 `FilterChip`s for category selection.

**Why:**
- All three category options (Now Playing · Top Rated · Upcoming) are visible at a glance.
- `FilterChip` with `selected = true` provides clear visual feedback (filled tint + checkmark) about the active filter.
- **Tab order:** `NOW_PLAYING` is first (leftmost, default selection) because it shows the most time-relevant content and is most likely what a user wants on first open.

---

## Offline Strategy: Tiered (Not Blanket Banner)

**Decision:** Allow scrolling through cached data when offline. Only show an error when a live network call is specifically required.

**Why:**
- A blanket "You are offline" overlay that blocks the entire UI is poor UX. The user likely wants to browse movies they already loaded.
- **Paging 3's `PagingSource`** is the right place to enforce this: pages already in Room are served locally; hitting a page beyond the cache while offline returns `LoadResult.Error`, which Paging renders as a footer-level error — not a full-screen takeover.
- Movie detail and trailer fetches are inherently live (not cached in Room) so they surface a proper error state.
- **Offline banner:** a lightweight `AnimatedVisibility` banner (not an overlay) appears at the top of Home and Favorites screens when connectivity is lost. This informs without blocking.
- **Swipe-to-remove:** disabled on the Favorites screen when offline (`enableDismissFromEndToStart = !isOffline`). Allowing a remove while offline would desync from the server and confuse the user.

---

## API Key Security

**Decision:** Store the TMDB API key in `BuildConfig` fields committed in `build.gradle.kts`.

**Why:**
- For a demo/assignment project these are read-only public-API credentials. No git history compromise is possible.
- For a production app, keys should be gitignored in `local.properties`, further obfuscated with R8, or fetched from a backend at runtime.

---

## Testing Strategy

**Decision:** Three-tier test pyramid: unit → component → end-to-end journey.

| Tier | Location | Count | Speed |
|---|---|---|---|
| Unit (JVM) | `src/test/` | 101 | ~5s total |
| UI component (device) | `src/androidTest/` | 79 | ~90s total |
| Journey / E2E (device, real API) | `src/androidTest/journey/` | 8 | ~3min total |

**Journey test infrastructure:**
- `HiltTestRunner` — custom `AndroidJUnitRunner` that boots `HiltTestApplication` instead of the production `MoviesApplication`.
- `@HiltAndroidTest` with **no module overrides** — the real production DI graph is used end-to-end.
- `@Before` + `@After` clear both Room (`db.clearAllTables()`) and the TMDB server (`markFavorite(favorite = false)` for all currently-favorited movies) so tests are fully isolated.
- Tests are paced with `Thread.sleep()` pauses so a human observer can follow each action on-screen.

---

## No Magic Numbers or Magic Strings

**Decision:** Every non-obvious literal must be a named `const val` co-located with the class that owns it.

**Where each area's constants live:**

| Constant(s) | Owner |
|---|---|
| `QUERY_PARAM_API_KEY`, `HEADER_AUTHORIZATION` | `AuthInterceptor` |
| `HEADER_CACHE_CONTROL`, `CACHE_CONTROL_ONE_DAY`, `IMAGE_CACHE_MAX_SIZE_BYTES` | `NetworkModule` companion |
| `DEFAULT_LANGUAGE`, `DEFAULT_PAGE` | `TmdbApiService.Companion` |
| `STARTING_PAGE_INDEX`, `PAGE_SIZE`, `PREFETCH_DISTANCE` | `PagingDefaults` (package-level `internal object`) |
| `VIDEO_SITE_YOUTUBE`, `VIDEO_TYPE_TRAILER` | `MovieRepositoryImpl.Companion` |
| `DEFAULT_MESSAGE` | `NetworkUnavailableException.Companion` |
| `DATABASE_NAME` | `AppDatabase.Companion` |
| `BACK_BUTTON_SCRIM_ALPHA`, `FAB_COLOR_ANIMATION_MS`, `RELEASE_YEAR_CHAR_COUNT` | `DetailScreenDefaults` (private object in `MovieDetailScreen.kt`) |
| `ASPECT_RATIO`, `SCRIM_HEIGHT_FRACTION`, `SCRIM_GRADIENT_ALPHA` | `MovieCardDefaults` (private object in `MovieCard.kt`) |

---

## Single-Module vs Multi-Module

**Decision:** Single Gradle module (`:app`) with package-level separation.

**Why:**
- For a demo project, the overhead of configuring multi-module Gradle builds doesn't pay off.
- The package structure (`data/`, `domain/`, `presentation/`) mirrors what a multi-module setup would look like, making it trivial to extract modules later as the team grows.
