# Technical Decisions

This document explains the major technical choices made for the Movies App and the reasoning behind each decision.

---

## Architecture: Clean Architecture + MVI

**Decision:** Use Clean Architecture layered with MVI as the presentation pattern.

**Why:**
- **Clean Architecture** enforces strict separation of concerns: UI knows nothing about networking, domain knows nothing about Android. This makes every layer independently testable and replaceable.
- **MVI** (Model-View-Intent) is the natural fit for Compose because the UI is a pure function of state. A single immutable `UiState` eliminates whole classes of bugs (race conditions, partial updates) that plague MVVM with multiple `LiveData`/`StateFlow` fields.
- This combination is explicitly called out as the expected pattern in the assignment (`Clean Architecture, MVI`).

**Trade-off:** More boilerplate vs. simpler patterns like MVVM. Justified here because the project will be reviewed for production-quality code quality.

---

## UI: Jetpack Compose + Material 3

**Decision:** 100% Jetpack Compose UI with Material 3 design system.

**Why:**
- Compose is the modern, declarative Android UI toolkit — the assignment explicitly requires it.
- Material 3 (Material You) provides well-tested, accessible components out of the box (chips, cards, bottom nav, FAB, snackbars) and follows the [Material Design guidelines](https://m3.material.io/) required by the assignment.
- Compose's animation APIs (`AnimatedVisibility`, `AnimatedContent`, `Crossfade`, shared element transitions) make smooth screen transitions straightforward.

---

## Networking: Retrofit + OkHttp

**Decision:** Retrofit with OkHttp as the HTTP client.

**Why:**
- Industry standard for Android networking. Mature, well-tested, and well-understood by reviewers.
- OkHttp provides a powerful interceptor chain for injecting auth headers, logging, and response caching with a single line of configuration per concern.
- Coroutine support via `suspend` functions is first-class.

**Alternative considered:** Ktor (multiplatform). Rejected because this is an Android-only project and Retrofit is more idiomatic.

---

## Serialization: Gson (or kotlinx.serialization)

**Decision:** Use `kotlinx.serialization` if possible; fall back to Gson.

**Why:**
- `kotlinx.serialization` is the Kotlin-idiomatic approach, fully null-safe, and has compile-time checks.
- If the team already uses Gson or Moshi, that's fine — the choice is trivially swappable since it only affects DTOs.

---

## Image Loading: Coil

**Decision:** Coil (`io.coil-kt:coil-compose`) for image loading and caching.

**Why:**
- Written in Kotlin, Coroutines-native, and has first-class Compose support (`AsyncImage`).
- Built-in memory and disk cache with configurable size and TTL — satisfies the 1-day expiration requirement with a single `DiskCache` configuration.
- Smaller binary footprint than Glide or Picasso.

**Alternative considered:** Glide. Rejected because Coil's Compose integration is cleaner and it's the modern recommendation.

---

## Pagination: Paging 3

**Decision:** Jetpack Paging 3 for infinite scroll.

**Why:**
- First-party Jetpack library designed exactly for this use case.
- `LazyPagingItems` integrates seamlessly into `LazyColumn` / `LazyVerticalGrid` in Compose.
- Handles loading state, error state, and retry logic out of the box.
- `PagingSource` is clean to implement and straightforward to unit test.

---

## Local Storage: Room

**Decision:** Room for the favorites database.

**Why:**
- The definitive Android database library. Compile-time verified SQL, coroutines + Flow support, and clear migration strategy.
- Favorites need to survive app restarts → persistent storage is required (not just in-memory).
- Room's `@Dao` with `Flow<List<T>>` means the Favorites screen reactively updates whenever the DB changes with zero manual polling.

---

## Dependency Injection: Hilt

**Decision:** Hilt for DI.

**Why:**
- Built on top of Dagger but drastically reduces boilerplate.
- First-party Jetpack support; works seamlessly with `ViewModel`, `WorkManager`, and Navigation.
- Standard in the Android ecosystem — reviewers will be familiar with it.

**Alternative considered:** Koin. Rejected because Koin's runtime DI is slower and offers weaker compile-time guarantees compared to Hilt/Dagger.

---

## Async: Kotlin Coroutines + Flow

**Decision:** Coroutines for async work, `Flow` for observable streams.

**Why:**
- Kotlin-native concurrency primitives. Structured concurrency prevents leaks.
- `StateFlow` for UI state, `SharedFlow`/`Channel` for one-shot effects, `Flow` from Room and Paging 3 — all compose cleanly in `ViewModel`.
- No need for RxJava; Coroutines is the modern idiomatic choice.

---

## Video Playback: Embedded Player Inside Movie Details Screen

**Decision:** Play trailers **inline at the top of the Movie Detail screen** using an embedded `WebView` (YouTube iframe) or `Media3 ExoPlayer`, not via a YouTube Intent.

**Why:**
- Requirement explicitly states trailers must play **inside the app** at the **top of the detail screen** — launching an external app would violate this.
- The YouTube iframe embed API (`https://www.youtube.com/embed/{key}?autoplay=1`) is the simplest reliable path: just a `WebView` inside an `AndroidView` composable, no deprecated SDK needed.
- `Media3 ExoPlayer` is the fallback for non-YouTube (MP4/HLS) sources and gives full control over the player UI (play/pause, fullscreen button, progress bar).
- The trailer section is 16:9 and sits at the very top of the screen, above all movie metadata, so the user sees it immediately on open.

**Offline behaviour:**
- If the device is offline or the trailer key fetch returns null, the trailer section gracefully degrades to showing the backdrop image — no crash, no error banner.

**Alternative considered:** YouTube Android Player SDK. Rejected — deprecated since 2023 and requires a Google account sign-in on the device.

---

## API Key Security

**Decision:** Store the TMDB API key in `local.properties`, inject into `BuildConfig` at compile time via `buildConfigField`.

**Why:**
- `local.properties` is gitignored by default — the key never enters version control.
- `BuildConfig` injection is the standard Android approach for environment-specific secrets.
- For a production app, keys should additionally be obfuscated with ProGuard/R8 or fetched from a backend at runtime.

---

## Category Filter: Horizontal Scrollable Chip Row

**Decision:** Use a `LazyRow` of Material 3 `FilterChip`s for category selection.

**Why:**
- All three category options (Upcoming, Top Rated, Now Playing) are visible at a glance — no tap required to discover them.
- `FilterChip` with `selected = true` provides clear visual feedback (filled tint + checkmark) about the active filter, which is better affordance than a dropdown label that shows only the current selection.
- `LazyRow` keeps the layout horizontally scrollable so more categories can be added in the future without a layout redesign.
- Chips sit persistently below the top bar, making the filter always accessible without an extra tap to open a menu.

---

## Offline Strategy: Tiered (Not Blanket Banner)

**Decision:** Allow scrolling through cached data when offline. Only show an error when a live network call is specifically required.

**Why:**
- A blanket "You are offline" overlay that blocks the entire UI is a poor UX. The user likely wants to browse movies they already loaded.
- **Paging 3's `PagingSource`** is the right place to enforce this: pages already in Room are served locally; hitting a page beyond the cache while offline returns `LoadResult.Error`, which Paging renders as a footer-level error — not a full-screen takeover.
- Movie Detail and Trailer fetches are inherently live (we don't cache full detail or video keys) so they surface a proper error state. The user knows why it failed without the app becoming unusable.
- This mirrors how apps like Netflix and Spotify handle offline — content you have is accessible; fresh content requires connectivity.

---

## Unit Tests: Every ViewModel and UI Component

**Decision:** Write unit tests for every ViewModel and every non-trivial UI composable — not just "critical paths".

**Why:**
- Requirement explicitly states this scope.
- ViewModel tests with `Turbine` + `kotlinx-coroutines-test` are fast, deterministic, and catch intent → state regressions immediately.
- Compose UI tests with `compose-ui-test` verify component rendering and interaction without needing a device.
- This discipline also keeps ViewModels lean: if a ViewModel is hard to test, it's a signal the logic is too complex and should be extracted to a use case.

---

## No Magic Numbers or Magic Strings

**Decision:** Every non-obvious literal — numeric or string — must be a named `const val` co-located in the file or class that owns it.

**Why:**
- A bare `20` next to `pageSize =` is meaningless; `PAGE_SIZE = 20` is self-documenting and refactor-safe.
- A bare `"YouTube"` scattered across files creates silent coupling — a typo or API change breaks only the instances you find by accident.
- Co-location (not a central `Constants.kt`) means constants live next to the logic they control, keeping context local. Reviewers can understand a constant without jumping to another file.

**Rules enforced in this project:**
- Constants are `const val` inside the **companion object** of the class that uses them (or directly inside a Kotlin `object` since objects have no companion).
- Constants are `private` unless other classes need to reference them (e.g. test assertions, shared defaults).
- Every constant has a KDoc comment explaining its meaning — not just its value.

**Where each area's constants live:**

| Constant(s) | Owner |
|---|---|
| `QUERY_PARAM_API_KEY` | `AuthInterceptor.Companion` |
| `HEADER_CACHE_CONTROL`, `CACHE_CONTROL_ONE_DAY`, `CONTENT_TYPE_JSON`, `IMAGE_CACHE_MAX_SIZE_BYTES` | `NetworkModule` (top-level `object`) |
| `DEFAULT_LANGUAGE`, `DEFAULT_PAGE` | `TmdbApiService.Companion` |
| `STARTING_PAGE_INDEX` | `MoviePagingSource.Companion` |
| `PAGE_SIZE`, `PREFETCH_DISTANCE`, `VIDEO_SITE_YOUTUBE`, `VIDEO_TYPE_TRAILER` | `MovieRepositoryImpl.Companion` |
| `DEFAULT_MESSAGE` | `NetworkUnavailableException.Companion` |
| `ROUTE`, `LABEL`, `ARG_MOVIE_ID` | `Screen.Home` / `Screen.Favorites` / `Screen.MovieDetail` |
| `DATABASE_NAME` | `AppDatabase.Companion` |

---

## Single-Module vs Multi-Module

**Decision:** Start with a single Gradle module (`:app`), with package-level separation.

**Why:**
- For an 8-hour assignment, the overhead of configuring multi-module Gradle builds doesn't pay off.
- The package structure (`data/`, `domain/`, `presentation/`) mirrors what a multi-module setup would look like, making it trivial to extract modules later.
- This is explicitly called out as a future improvement in the README.
