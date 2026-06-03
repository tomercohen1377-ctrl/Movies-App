# Architecture Overview

## Pattern: Clean Architecture + MVI

The app is structured around **Clean Architecture** layered with **MVI (Model-View-Intent)** as the presentation pattern. This combination enforces strict separation of concerns, makes the codebase testable, and scales well as the feature set grows.

---

## Layers

### 1. Presentation Layer (UI)

- Built entirely with **Jetpack Compose** and **Material 3**.
- Each screen owns a `ViewModel` that exposes a single `StateFlow<UiState>` and accepts user `Intent`s.
- Navigation is handled by **Compose Navigation** with a central `NavGraph`.

**MVI Contract per screen:**
```
UiState     — immutable snapshot of what the screen should render
UiIntent    — user actions (e.g. LoadMovies, SelectCategory, ToggleFavorite)
UiEffect    — one-shot side effects (e.g. NavigateToDetail, ShowError)
```

---

### 2. Domain Layer

- Pure Kotlin — **no Android dependencies**.
- Contains:
  - **Domain Models**: Plain Kotlin data classes (`Movie`, `MovieDetail`, `VideoResult`, `Category`).
  - **Repository Interfaces**: Contracts the data layer must fulfill.
  - **Use Cases**: Single-responsibility classes that encapsulate one piece of business logic.

**Use Cases:**
| Use Case | Responsibility |
|---|---|
| `GetMoviesUseCase` | Fetch paginated movie list by category |
| `GetMovieDetailUseCase` | Fetch full details for a single movie |
| `GetMovieTrailerUseCase` | Fetch trailer video key (returns null if offline) |
| `ToggleFavoriteUseCase` | Add/remove a movie from favorites |
| `GetFavoritesUseCase` | Observe the favorites list from local DB |
| `IsFavoriteUseCase` | Observe whether a single movie is favorited |

---

### 3. Data Layer

Composed of two data sources, coordinated by a Repository implementation:

#### Remote (Network)
- **Retrofit** + **OkHttp** for HTTP calls.
- A single `TmdbApiService` interface defines all endpoints.
- Response DTOs are mapped to domain models via dedicated mapper classes.
- An `AuthInterceptor` injects the API key into every request.

#### Local (Database)
- **Room** database for persisting favorites and cached movie data.
- Entities: `MovieEntity` (category-keyed movie cache), `FavoriteEntity`.
- DAOs: `MovieDao` (cache read/write), `FavoriteDao`.

#### Repository Implementation
- `MovieRepositoryImpl` coordinates between remote and local sources.
- **Offline-first for browsing:** serves cached pages from Room when offline; triggers a targeted `NetworkUnavailableException` only when going beyond cached data or fetching detail/trailer.

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
- **Favorites tab** → `FavoritesScreen` (saved movies grid)
- Both tabs share the `MovieDetail` route; back-stack is managed per tab

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
│  Top App Bar              │
├──────────────────────────┤
│  [Upcoming][Top Rated]…  │  ← horizontally scrollable FilterChip row
├──────────────────────────┤
│  ┌──────┐  ┌──────┐      │
│  │Poster│  │Poster│  … ←─── LazyVerticalGrid of MovieCards
│  │      │  │      │      │     (full-bleed poster + gradient + title)
│  └──────┘  └──────┘      │
│  ┌──────┐  ┌──────┐      │
│  │      │  │      │      │
│  └──────┘  └──────┘      │
│                           │
│  [Network Error Footer]  │  ← only when paging hits offline boundary
└──────────────────────────┘
```

### Movie Detail Screen
```
┌──────────────────────────┐
│  ◀  [Trailer Player]     │  ← 16:9 embedded player AT THE TOP
│     (or backdrop image)  │     ExoPlayer WebView / YouTube embed
├──────────────────────────┤
│  Poster | Title          │
│         | Year  Runtime  │
│         | ★ 7.8  (1.2k)  │
├──────────────────────────┤
│  [Action] [Drama]        │  ← Genre chips
├──────────────────────────┤
│  "Tagline here"          │
├──────────────────────────┤
│  Overview paragraph...   │
│                           │
│              [❤️ Save]   │  ← Animated FAB
└──────────────────────────┘
```

---

## Offline Handling Strategy

The app uses a **tiered offline model** — not a blanket offline banner:

| Scenario | Behaviour |
|---|---|
| Browsing cached pages | Scrolls freely, no error shown |
| Paging beyond cached data while offline | `NetworkErrorFooter` in list — retry button visible |
| Opening movie detail while offline | Full `ErrorView` with "No internet connection" message |
| Trailer fetch while offline | `trailerKey = null` → backdrop image shown, no error banner |
| App comes back online | Paging retries automatically; detail screen shows retry |

`NetworkMonitor` (backed by `ConnectivityManager.NetworkCallback`) exposes `isOnline: Flow<Boolean>` and `isCurrentlyOnline(): Boolean`. It is injected into `MoviePagingSource` and all ViewModels.

---

## Data Flow Diagram

```
User Action (Intent)
        │
        ▼
   ViewModel
   ├── reads NetworkMonitor.isOnline
   │
        │  calls
        ▼
   Use Case
        │  calls
        ▼
  Repository Interface
        │  implemented by
        ▼
  Repository Impl
   ┌────┴────┐
   ▼         ▼
Remote      Local
(Retrofit)  (Room)
  ↑           │
  │  cache    │ serve cache
  └───────────┘
```

---

## Image Caching

- **Library:** Coil `AsyncImage`
- **Disk cache:** 100 MB, stored in `cacheDir/image_cache`
- **Expiration:** 1-day enforced via OkHttp `Cache-Control: max-age=86400, must-revalidate` response header interceptor
- Same `OkHttpClient` instance is shared between Retrofit and Coil's `ImageLoader` to unify the cache budget

---

## Testing Strategy

> Every ViewModel and every non-trivial UI component has a dedicated test class.

| Test type | Tools | Coverage target |
|---|---|---|
| ViewModel unit tests | MockK, Turbine, `kotlinx-coroutines-test` | All ViewModels — every intent/state transition |
| Use case unit tests | MockK | All use cases |
| Paging unit tests | MockK | `MoviePagingSource` — online, last page, offline |
| Repository unit tests | MockK | `MovieRepositoryImpl` |
| UI component tests | `compose-ui-test`, `TestRule` | All shared composables |

---

## Dependency Injection

**Hilt** manages all dependencies. Modules are organized by layer:

| Module | Provides |
|---|---|
| `NetworkModule` | `Retrofit`, `OkHttpClient`, `TmdbApiService`, Coil `ImageLoader` |
| `DatabaseModule` | `AppDatabase`, `MovieDao`, `FavoriteDao` |
| `RepositoryModule` | Repository interface → implementation bindings |
| `UtilModule` | `NetworkMonitor` |

---

## State Management

- ViewModels use `StateFlow` for UI state (hot, always has a value).
- Side effects use `Channel` → exposed as `Flow` to avoid re-delivery on recomposition.
- Paging state is handled by **Paging 3**'s `LazyPagingItems` in Compose.
- `NetworkMonitor.isOnline` flows into ViewModel state so the UI reflects connectivity reactively.

---

## Threading Model

| Operation | Dispatcher |
|---|---|
| Network calls | `Dispatchers.IO` |
| DB reads/writes | `Dispatchers.IO` |
| Business logic | main-safe (Room + Retrofit are main-safe by default) |
| UI updates | `Dispatchers.Main` (via `StateFlow`) |

---

## Module Graph (High Level)

```
:app
  ├── :presentation
  │     ├── home
  │     ├── moviedetail
  │     └── favorites
  ├── :domain
  └── :data
        ├── remote
        └── local
```

> The app lives in a single Gradle module for time-to-market. The package structure mirrors a multi-module layout, making extraction straightforward as a future step.
