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

**Example (Movie List):**
```
MovieListState   → data class holding list, selected category, loading/error flags
MovieListIntent  → LoadMovies, SelectCategory(category), RefreshMovies, OpenDetail(id)
MovieListEffect  → NavigateToDetail(id), ShowSnackbar(message)
```

---

### 2. Domain Layer

- Pure Kotlin — **no Android dependencies**.
- Contains:
  - **Domain Models**: Plain Kotlin data classes (e.g. `Movie`, `MovieDetail`, `Category`).
  - **Repository Interfaces**: Contracts the data layer must fulfill.
  - **Use Cases**: Single-responsibility classes that encapsulate one piece of business logic.

**Use Cases:**
| Use Case | Responsibility |
|---|---|
| `GetMoviesUseCase` | Fetch paginated movie list by category |
| `GetMovieDetailUseCase` | Fetch full details for a single movie |
| `GetMovieTrailerUseCase` | Fetch trailer video key |
| `ToggleFavoriteUseCase` | Add/remove a movie from favorites |
| `GetFavoritesUseCase` | Observe the favorites list from local DB |
| `SearchMoviesUseCase` | (Bonus) Search movies by query |

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
- Entities: `MovieEntity`, `FavoriteEntity`.
- DAOs: `MovieDao`, `FavoriteDao`.

#### Repository Implementation
- `MovieRepositoryImpl` coordinates between remote and local sources.
- Implements an **offline-first** strategy where applicable: serve cached data first, then refresh from network.

---

## Data Flow Diagram

```
User Action (Intent)
        │
        ▼
   ViewModel
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
```

---

## Dependency Injection

**Hilt** manages all dependencies. Modules are organized by layer:

| Module | Provides |
|---|---|
| `NetworkModule` | `Retrofit`, `OkHttpClient`, `TmdbApiService` |
| `DatabaseModule` | `AppDatabase`, DAOs |
| `RepositoryModule` | Repository interface → implementation bindings |

---

## Navigation

A single `NavGraph` defined in `AppNavGraph.kt` handles all routes.

**Routes:**
```
MovieList  →  MovieDetail(movieId)
                    ↑
Favorites  →  (same MovieDetail route)
```

Bottom navigation bar switches between `MovieList` and `Favorites` tabs.

---

## State Management

- ViewModels use `StateFlow` for UI state (hot, always has a value).
- Side effects use `Channel` → exposed as `Flow` to avoid re-delivery on recomposition.
- Paging state is handled by **Paging 3**'s `LazyPagingItems` in Compose.

---

## Threading Model

| Operation | Dispatcher |
|---|---|
| Network calls | `Dispatchers.IO` |
| DB reads/writes | `Dispatchers.IO` |
| Business logic | `Dispatchers.Default` (or main-safe via Room/Retrofit) |
| UI updates | `Dispatchers.Main` (via `StateFlow`) |

---

## Module Graph (High Level)

```
:app
  ���── :presentation
  │     ├── movielist
  │     ├── moviedetail
  │     └── favorites
  ├── :domain
  └── :data
        ├── remote
        └── local
```

> Note: The app currently lives in a single module for time-to-market. Splitting into separate Gradle modules is a clear next step.
