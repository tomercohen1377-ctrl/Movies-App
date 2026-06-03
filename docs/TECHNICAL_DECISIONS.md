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

## Video Playback: YouTube Intent + ExoPlayer (Bonus)

**Decision:** Launch YouTube app via Intent as primary path; fall back to embedded ExoPlayer for MP4 trailers.

**Why:**
- TMDB video results are YouTube keys. The simplest, most reliable approach is to launch the YouTube app.
- Embedding requires the YouTube Android Player API (deprecated) or a WebView workaround.
- ExoPlayer (Media3) handles non-YouTube sources and provides full control over playback UI.

---

## API Key Security

**Decision:** Store the TMDB API key in `local.properties`, inject into `BuildConfig` at compile time via `buildConfigField`.

**Why:**
- `local.properties` is gitignored by default — the key never enters version control.
- `BuildConfig` injection is the standard Android approach for environment-specific secrets.
- For a production app, keys should additionally be obfuscated with ProGuard/R8 or fetched from a backend at runtime.

---

## Single-Module vs Multi-Module

**Decision:** Start with a single Gradle module (`:app`), with package-level separation.

**Why:**
- For an 8-hour assignment, the overhead of configuring multi-module Gradle builds doesn't pay off.
- The package structure (`data/`, `domain/`, `presentation/`) mirrors what a multi-module setup would look like, making it trivial to extract modules later.
- This is explicitly called out as a future improvement in the README.
