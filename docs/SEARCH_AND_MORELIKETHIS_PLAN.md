# Implementation Plan — Movie Search & "More Like This"

> **Status:** 🟡 Planned. Two independent features; can be shipped separately.
> Architecturally consistent with the existing app — same Clean Architecture layering, MVI, Paging 3, and testing pyramid.

---

## Goals

| # | Feature | Why |
|---|---|---|
| A | **Movie Search** | The biggest UX gap today. Users have category browse + Favorites but no way to find a specific title. |
| B | **"More Like This"** | Half-finished — TMDB `/{id}/similar` is one extra composable away from a high-value scroll-stopper on the Detail screen. |

Both follow the rules already established in `IMPLEMENTATION_PLAN.md`:
- **Clean Arch + MVI** — every screen has a `*Contract.kt` (sealed `State` / `Intent` / `Effect`), a `*ViewModel.kt`, and a `*Screen.kt`
- **Paging 3** for any list > 20 items, fixed lists for small result sets (≤ ~20)
- **Room cache where it earns its keep** (browse), **in-memory only where it doesn't** (search)
- **`safeApiCall` + `NetworkResult` + `ApiError`** for all new endpoints
- **Hilt** — `RepositoryModule` rebind for the new method
- **Heavy test coverage** — ViewModel unit, PagingSource unit, UI component, flow, journey

---

# Phase A — Movie Search

## A.1 Data Layer

### A.1.1 `TmdbApiService` — new endpoint

Add to `app/src/main/java/com/tcohen/moviesapp/data/remote/api/TmdbApiService.kt`:

```kotlin
@GET("search/movie")
suspend fun searchMovies(
    @Query("query") query: String,
    @Query("page") page: Int = DEFAULT_PAGE,
    @Query("include_adult") includeAdult: Boolean = false,
    @Query("language") language: String = DEFAULT_LANGUAGE
): MovieListResponse
```

`MovieListResponse` is **already** the right shape (paginated `MovieResponse[]`). Zero new DTOs.

### A.1.2 `SearchPagingSource` — new file

**Path:** `app/src/main/java/com/tcohen/moviesapp/data/remote/paging/SearchPagingSource.kt`

Mirrors `MoviePagingSource` structurally but:
- **Online-only** — if offline, return `LoadResult.Error(NetworkUnavailableException())` immediately. Search results aren't worth caching in Room; the most common UX (TMDB / Google) skips caching for queries.
- **No Room writes** — `searchMovies` is user-driven and ephemeral. Reusing the category-keyed `movies` table would risk collisions and stale stale-key entries.
- **Empty-result handling** — TMDB returns `total_pages = 1` with `results = []`. We still emit a `LoadResult.Page` with empty `data` so the UI can render the "no results" state distinctly from an error.

```kotlin
class SearchPagingSource(
    private val apiService: TmdbApiService,
    private val query: String,
    private val networkMonitor: NetworkMonitor
) : PagingSource<Int, Movie>() {
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Movie> {
        if (!networkMonitor.isCurrentlyOnline()) {
            return LoadResult.Error(NetworkUnavailableException())
        }
        val page = params.key ?: PagingDefaults.STARTING_PAGE_INDEX
        return try {
            val response = apiService.searchMovies(query = query, page = page)
            LoadResult.Page(
                data = response.results.map { it.toDomain() },
                prevKey = if (page == PagingDefaults.STARTING_PAGE_INDEX) null else page - 1,
                nextKey = if (page >= response.totalPages) null else page + 1
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, Movie>): Int? = ...
}
```

**Constants** — co-locate tunables inside a `SearchDefaults` private object following the `MovieCardDefaults` / `PagingDefaults` convention:

- `MIN_QUERY_LENGTH = 2` — single-char queries fire a wall of near-duplicates
- `DEBOUNCE_MS = 300L` — visible to grep, never a magic number
- `QUERY_HISTORY_LIMIT = 5`

### A.1.3 `MovieRepository` interface + `MovieRepositoryImpl`

Append to `app/src/main/java/com/tcohen/moviesapp/domain/repository/MovieRepository.kt`:

```kotlin
/** Returns a paginated flow of [Movie]s matching [query].
 *
 * Online-only: no Room cache. Empty query yields an empty flow (the ViewModel
 * gates on MIN_QUERY_LENGTH before invoking this). */
fun searchMovies(query: String): Flow<PagingData<Movie>>
```

Implementation in `MovieRepositoryImpl.kt` — straightforward `Pager(config, factory = { SearchPagingSource(...) })`. The factory can capture the latest query by closure since `Pager` rebuilds on `flatMapLatest`.

## A.2 Domain Layer

No use cases (project convention). The ViewModel talks to the repository directly.

## A.3 Presentation Layer

### A.3.1 `SearchContract.kt`

```kotlin
data class SearchState(
    val query: String = "",
    val isOffline: Boolean = false,
    val hasSearched: Boolean = false,  // true after the first non-empty query
)

sealed interface SearchIntent {
    data class UpdateQuery(val query: String) : SearchIntent
    data object ClearQuery : SearchIntent
    data object ClearHistory : SearchIntent
    data object OpenHistory : SearchIntent
    data class OpenDetail(val movieId: Int) : SearchIntent
}

sealed interface SearchEffect {
    data class NavigateToDetail(val movieId: Int) : SearchEffect
}
```

### A.3.2 `SearchViewModel.kt`

Core shape — exposes `results: Flow<PagingData<Movie>>` driven by a debounced query flow:

```kotlin
private val _query = MutableStateFlow("")
private val _state = MutableStateFlow(SearchState())

private val debouncedQuery: Flow<String> = _query
    .debounce { if (it.length < SearchDefaults.MIN_QUERY_LENGTH) 0L else SearchDefaults.DEBOUNCE_MS }
    .distinctUntilChanged()

@OptIn(ExperimentalCoroutinesApi::class)
val results: Flow<PagingData<Movie>> = debouncedQuery
    .flatMapLatest { q ->
        _state.update { it.copy(hasSearched = q.length >= SearchDefaults.MIN_QUERY_LENGTH) }
        if (q.length < SearchDefaults.MIN_QUERY_LENGTH) flowOf(PagingData.empty())
        else repository.searchMovies(q)
    }
    .cachedIn(viewModelScope)
```

Also wires `NetworkMonitor.isOnline` to `SearchState.isOffline`, and `SearchHistoryRepository` for last 5 queries.

### A.3.3 `SearchHistoryRepository` (small stretch)

**Path:** `app/src/main/java/com/tcohen/moviesapp/data/local/SearchHistoryRepository.kt`

Wraps a tiny DataStore-less implementation first: `SharedPreferences` (`search_history` file, comma-joined). Holds at most `QUERY_HISTORY_LIMIT` unique entries, MRU-ordered. Easy to swap to DataStore later.

### A.3.4 `SearchScreen.kt`

UI sections, top-to-bottom:
1. **Search bar** — `OutlinedTextField` with leading `Search` icon and trailing `Close` icon (visible when text is non-empty). TextInput IME action = `Search` (no-op when input ends with `Search` key — TMDB endpoint is hit on every debounced change, not on submit, so we keep the standard "as you type" behavior).
2. **History chip row** — `LazyRow` of `AssistChip`s, shown only when `query.isEmpty() && history.isNotEmpty()`. Tapping fills the query.
3. **Body** — `when (loadState.refresh)`:
   - Loading → centered `CircularProgressIndicator`
   - Error → `ErrorView` with friendly "Search requires an internet connection" message (we know the typical `NetworkUnavailableException` flavour)
   - Empty *and* hasSearched → inline illustration: "No matches for "{query}""
   - Empty *and* not hasSearched → prompt: "Type to search for a movie"
   - Has items → `MovieGrid(movies = pagingItems) { … MovieCard … }` — **reuse exactly** the existing `MovieGrid` + `MovieCard` composables (already have `testTag("movie_card")`)
4. **Offline banner** — same `OfflineBanner` used by Home/Favorites.

## A.4 Navigation

**Recommendation:** Add **Search as a 3rd bottom-nav tab**, not a top-bar icon. Reasons:
- Discoverability: search is a top-level verb, not an action
- Consistency with Home/Favorites
- Slide-in-from-right only makes sense for *stacked* screens (Detail pushes onto Search, and we already have that animation)

Implementation:

1. `Screen.Search` entry in `Screen.kt` (`route = "search"`)
2. Add to `bottomNav` set in `BottomNavBar.kt` (new tab between Home and Favorites, Material 3 icon: `Icons.Filled.Search`)
3. Add `composable(Screen.Search.route) { SearchScreen(...) }` to `AppNavGraph.kt`
4. Detail navigation from Search uses the **same** `Screen.MovieDetail.createRoute(...)` and the same slide animations — zero new transitions needed
5. Update `showBottomBar` set to include `"search"`

`AppNavGraph` becomes:

```kotlin
composable(Screen.Home.route) { HomeScreen(onNavigateToDetail = ...) }
composable(Screen.Search.route) { SearchScreen(onNavigateToDetail = ...) }
composable(Screen.Favorites.route) { FavoritesScreen(onNavigateToDetail = ...) }
composable(Screen.MovieDetail.routeWithArgs, …) { MovieDetailScreen(onNavigateBack = ...) }
```

No changes to the Search icon semantics in `Screen.kt` — `Screen.Search` is a leaf route.

> **Alternative considered:** a search icon in the Home top bar that pushes a full-screen Search screen. Cleaner for the bottom nav, but adds an animation variant. Bottom nav wins on simplicity.

## A.5 Tests

### Unit (JVM, ~12 new tests)

| File | Tests | Covers |
|---|---|---|
| `SearchViewModelTest` | 6 | debounce timing, MIN_QUERY_LENGTH gating, distinctUntilChanged (no duplicate flows on `UpdateQuery("ab"); UpdateQuery("ab")`), offline state, single OpenDetail effect, history added on submit |
| `SearchPagingSourceTest` | 4 | online success, online empty results page, `NetworkUnavailableException` when offline, HTTP error propagation |
| `SearchHistoryRepositoryTest` | 4 | MRU ordering, dedup, max-length truncation, clear |

### UI component (device, ~6 new tests)

| File | Covers |
|---|---|
| `SearchScreenTest` | empty prompt state, typing-into-input state, history chips, no-results message, results grid (with dummy `Pager`), offline banner shown when isOffline |
| `SearchDebounceTest` | Type 3 chars in 100ms → confirm only **one** repository call fires (Turbine-replay of `results` collector) |

### Journey (device, live TMDB — consider adding)

| Journey | Validates |
|---|---|
| `SearchTab_navigate_opensEmptyState` | Tap Search tab → empty prompt state |
| `SearchTab_typeQuery_loadsRealResults` | Type "Dune" → real TMDB results appear in grid |
| `SearchTab_tapResult_opensDetailScreen` | Tap a result → Detail screen slides in |

Add as new methods inside `RealAppJourneyTest` (extend `@Before`/`@After` to wipe Room + server favorites; search is read-only so no cleanup needed there).

## A.6 Files Touched

```
NEW:
  app/src/main/java/com/tcohen/moviesapp/data/remote/paging/SearchPagingSource.kt
  app/src/main/java/com/tcohen/moviesapp/data/local/SearchHistoryRepository.kt
  app/src/main/java/com/tcohen/moviesapp/presentation/search/SearchContract.kt
  app/src/main/java/com/tcohen/moviesapp/presentation/search/SearchViewModel.kt
  app/src/main/java/com/tcohen/moviesapp/presentation/search/SearchScreen.kt
  app/src/test/java/com/tcohen/moviesapp/presentation/search/SearchViewModelTest.kt
  app/src/test/java/com/tcohen/moviesapp/data/remote/paging/SearchPagingSourceTest.kt
  app/src/test/java/com/tcohen/moviesapp/data/local/SearchHistoryRepositoryTest.kt
  app/src/androidTest/java/com/tcohen/moviesapp/presentation/search/SearchScreenTest.kt
  app/src/androidTest/java/com/tcohen/moviesapp/presentation/search/SearchDebounceTest.kt

MODIFIED:
  app/src/main/java/com/tcohen/moviesapp/data/remote/api/TmdbApiService.kt          (+1 endpoint)
  app/src/main/java/com/tcohen/moviesapp/domain/repository/MovieRepository.kt         (+1 method)
  app/src/main/java/com/tcohen/moviesapp/data/repository/MovieRepositoryImpl.kt        (+1 implementation)
  app/src/main/java/com/tcohen/moviesapp/presentation/navigation/Screen.kt            (+Search entry)
  app/src/main/java/com/tcohen/moviesapp/presentation/navigation/BottomNavBar.kt      (+1 tab)
  app/src/main/java/com/tcohen/moviesapp/presentation/navigation/AppNavGraph.kt       (+1 composable + showBottomBar update)
  app/src/androidTest/java/com/tcohen/moviesapp/RealAppJourneyTest.kt                (+2-3 journeys)
```

---

# Phase B — "More Like This"

This is smaller and rides on existing infrastructure. Two-stage plan: ship a TMDB version first (Phase B.1), with an optional AI-enhanced version on top (Phase B.stretch) since the empty `ai/presentation/morelikethis` + `ai/data/embedding` directories suggest you started that direction.

## B.1 Data Layer — TMDB-powered

### B.1.1 `TmdbApiService` endpoint

Add one endpoint:

```kotlin
@GET("movie/{movie_id}/similar")
suspend fun getSimilarMovies(
    @Path("movie_id") movieId: Int,
    @Query("page") page: Int = 1,
    @Query("language") language: String = DEFAULT_LANGUAGE
): MovieListResponse
```

Reuses the existing `MovieListResponse` DTO. No new types.

> Note: TMDB returns the first page of `/{id}/similar` as part of every detail load in some clients, but we keep it lazy — separate request, fired only when the section is inflated. (Reason: reduces TTFD on the detail screen and lets the AI mode in B.stretch drop in cleanly without changing the call site.)

### B.1.2 Constants

```kotlin
// MoreLikeThisDefaults.kt (package-level object, like PagingDefaults)
const val SIMILAR_PAGE_SIZE_CAP = 20     // TMDB returns up to 20 on page 1
const val DETAIL_NETWORK_TIMEOUT_MS = 8_000L  // give up earlier than default Retrofit timeout
```

## B.2 Presentation — embedded section on Detail screen

Mirror the **`PlotExplainerSection` pattern** exactly. The Detail screen already hosts an AI sub-feature with its own ViewModel + sealed state; we replicate that.

### B.2.1 `MoreLikeThisContract.kt`

```kotlin
sealed interface MoreLikeThisState {
    data object Idle : MoreLikeThisState            // before first load or after Clear
    data object Loading : MoreLikeThisState
    data class Success(val movies: List<Movie>) : MoreLikeThisState
    data object Empty : MoreLikeThisState              // TMDB returned 0 — banner hidden
    data class Error(val message: String) : MoreLikeThisState  // shown but non-blocking
}

sealed interface MoreLikeThisIntent {
    data class Load(val movieId: Int) : MoreLikeThisIntent
    data class OpenDetail(val movieId: Int) : MoreLikeThisIntent
}
```

### B.2.2 `MoreLikeThisViewModel.kt`

```kotlin
@HiltViewModel
class MoreLikeThisViewModel @Inject constructor(
    private val repository: MovieRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val _state = MutableStateFlow<MoreLikeThisState>(MoreLikeThisState.Idle)
    val state: StateFlow<MoreLikeThisState> = _state.asStateFlow()
    private val _effects = Channel<MoreLikeThisEffect>(Channel.BUFFERED)
    val effects: Flow<MoreLikeThisEffect> = _effects.receiveAsFlow()

    fun processIntent(intent: MoreLikeThisIntent) {
        when (intent) {
            is MoreLikeThisIntent.Load -> load(intent.movieId)
            is MoreLikeThisIntent.OpenDetail -> viewModelScope.launch {
                _effects.send(MoreLikeThisEffect.NavigateToDetail(intent.movieId))
            }
        }
    }

    private fun load(movieId: Int) {
        _state.value = MoreLikeThisState.Loading
        viewModelScope.launch {
            _state.value = when (val r = repository.getSimilarMovies(movieId)) {
                is NetworkResult.Success -> if (r.data.isEmpty()) MoreLikeThisState.Empty
                                            else MoreLikeThisState.Success(r.data.take(MoreLikeThisDefaults.SIMILAR_PAGE_SIZE_CAP))
                is NetworkResult.Error  -> MoreLikeThisState.Error(r.message)
            }
        }
    }
}
```

`@HiltViewModel` *and* scoped to the parent `MovieDetailScreen` via `hiltViewModel<MoreLikeThisViewModel>(viewModelStoreOwner = ...)` or — simpler — instantiated locally as a `viewModel()` inside `MoreLikeThisSection`. The latter is cleaner since the section is destroyed when the Detail screen pops.

### B.2.3 `MoreLikeThisSection.kt`

A horizontally-scrolling `LazyRow` of poster-only cards with:
- Section heading "More like this" with the existing `MoviesAppTheme` typography styles
- Each poster is a compact version of `MovieCard` (smaller width, no rating scrim — name + year under poster). Could be a new shared component `SimilarMoviePosterCard` to avoid bloating `MovieCard` with variants.
- Tapping a card dispatches `MoreLikeThisIntent.OpenDetail(id)` → `MoreLikeThisEffect.NavigateToDetail(id)` → navigated via the surrounding `MovieDetailScreen`'s `navController`

State-driven rendering:
- `Idle` / `Loading` → "More like this" header + 5 placeholder boxes (same `surfaceVariant` background as `HomeScreen`)
- `Success` → header + `LazyRow` of posters
- `Empty` → header omitted entirely (no awkward empty section)
- `Error` → header + small inline message + Retry button (NOT a full-screen takeover — the detail page should never be hijacked by a subsection failure)

## B.3 Wire into Detail screen

Three small edits in `MovieDetailContent.kt`:

```kotlin
val releaseYear = uiState.movie.releaseDate.take(RELEASE_YEAR_CHAR_COUNT).toIntOrNull()

Column(modifier = modifier.verticalScroll(rememberScrollState())) {
    // … trailer/backdrop, MovieMetadata, PlotExplainerSection …
    MoreLikeThisSection(
        movieId = uiState.movie.id,
        onMovieClicked = { /* bubble navigation up to parent MovieDetailScreen */ },
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(88.dp))
}
```

`onMovieClicked` should not navigate directly — `MovieDetailScreen` (the parent) owns navigation, since it's the only thing with a `NavController` reference. Pass `onNavigateToDetail` down through `MovieDetailContent` (add it as a new param alongside `onPlayerReady`).

Bonus: because we now have two `NetworkResult`-driven sub-fetches on the detail screen (trailer + similar), parallelize them all in `MovieDetailViewModel.loadDetail()` using a single `coroutineScope { async { ... }; async { ... } }` block — already partly done; just add the `similarMovies` async.

Actually **better**: keep the `MoreLikeThisViewModel` self-contained and trigger its load via a `LaunchedEffect(uiState.movie.id) { viewModel.processIntent(MoreLikeThisIntent.Load(it)) }` inside `MoreLikeThisSection`. This keeps `MovieDetailViewModel` clean of the new feature and mirrors how AI's `PlotExplainerSection` is decoupled.

## B.4 Tests

### Unit (~5 new tests)

| File | Tests | Covers |
|---|---|---|
| `MoreLikeThisViewModelTest` | 4 | Load→Loading→Success, empty results → Empty, error → Error + Retry button, OpenDetail effect |
| `MovieRepositoryImplTest` (+1 test) | `getSimilarMovies` wired correctly, success + error round-trip |

### UI component (~3 new tests)

| File | Covers |
|---|---|
| `MoreLikeThisSectionTest` | Renders header + LazyRow for `Success`, header hidden for `Empty`, error+retry for `Error`, placeholder boxes for `Loading` |
| `MovieDetailContentTest` (+1 test) | After the section lands in `MovieDetailContent`, assert that mock `MoreLikeThisState.Success` causes 5+ cards to appear |

### No new journey test required — the feature is local to the detail screen; existing `detailScreen_*` journeys give coverage of the parent flow.

## B.5 Files Touched

```
NEW:
  app/src/main/java/com/tcohen/moviesapp/data/remote/MoreLikeThisDefaults.kt
  app/src/main/java/com/tcohen/moviesapp/presentation/moviedetail/morelikethis/MoreLikeThisContract.kt
  app/src/main/java/com/tcohen/moviesapp/presentation/moviedetail/morelikethis/MoreLikeThisViewModel.kt
  app/src/main/java/com/tcohen/moviesapp/presentation/moviedetail/morelikethis/MoreLikeThisSection.kt
  app/src/main/java/com/tcohen/moviesapp/presentation/common/SimilarMoviePosterCard.kt
  app/src/test/java/com/tcohen/moviesapp/presentation/moviedetail/morelikethis/MoreLikeThisViewModelTest.kt
  app/src/androidTest/java/com/tcohen/moviesapp/presentation/moviedetail/morelikethis/MoreLikeThisSectionTest.kt

MODIFIED:
  app/src/main/java/com/tcohen/moviesapp/data/remote/api/TmdbApiService.kt            (+1 endpoint)
  app/src/main/java/com/tcohen/moviesapp/domain/repository/MovieRepository.kt           (+1 method)
  app/src/main/java/com/tcohen/moviesapp/data/repository/MovieRepositoryImpl.kt          (+1 implementation)
  app/src/main/java/com/tcohen/moviesapp/presentation/moviedetail/MovieDetailContent.kt (+section, +onNavigateToDetail param)
  app/src/androidTest/java/com/tcohen/moviesapp/presentation/moviedetail/MovieDetailContentTest.kt (+1 test)
```

---

# Phase B.Stretch — AI-enhanced "More Like This" (optional)

> Uses the existing `LlmClient` + cache. Builds on Phase B; do not start until B.1 + B.2 are green.

Approach:
- Use Gemini (`LlmClient` already wired in `di/LlmModule.kt`) to embed the source movie's `title + overview + genres` as a semantic key
- Fetch TMDB's `/{id}/similar` and `/{id}/recommendations`
- Score each candidate by **embedding cosine similarity** to the source (cheap, run on-device)
- Caveat: generating embeddings requires either an additional `text-embedding-004` model or a local embedding — explore after B.1 lands
- DB layer: cache `(movieId, rankedMovieIds, timestamp)` in Room; reuse `MoreLikeThisState.Success(movies)` if cache hit and source movie unchanged

This step is **explicitly a stretch** because it touches the LLM quota, the cache key strategy, and the embedding model setup. The TMDB-only Phase B.1 already produces a high-value UX win on its own.

---

# Execution Order

Recommended shipping order — each phase compiles, tests, and ships independently.

| Step | Phase | Reason |
|---|---|---|
| 1 | **A.1 — A.3** (Search data + domain + presentation) | Standalone feature; biggest UX win |
| 2 | **A.4 (Navigation)** | Adds bottom-nav tab; expands test surface for journeys |
| 3 | **A.5 (Tests)** | ViewModel + paging source unit tests first, then UI |
| 4 | **B.1 — B.3** (More like this) | Independent feature; orthogonal to Search |
| 5 | **B.4** (Tests) | UI heavy on this one |
| 6 | **Update README** | New feature row, new screenshot |
| 7 | **(Optional) B.Stretch** | AI-enhanced later, after B.1 + B.2 land |

> **Recommendation:** ship A and B in a single PR if the test suite stays green; otherwise A → release → B → release. Don't combine B.Stretch with B.1 — separate concerns and quota.

---

# Risk Register

| Risk | Mitigation |
|---|---|
| Empty `search/movie` queries return huge irrelevant lists | Gate on `MIN_QUERY_LENGTH = 2`; show "type to search" when below threshold |
| Debounced query changes mid-flight cancel flow correctly | Already use `flatMapLatest`; covered by `SearchViewModelTest` |
| TMDB rate-limits `/{id}/similar` aggressively | Lazy-load only when section inflates; cache result per session via `savedStateHandle` |
| 3-tab bottom nav changes tab order for existing users | Default tab stays Home — no behavioral change |
| New Search UI test tag conflicts with existing `movie_card` testTag | Use `search_bar`, `search_result_grid`, `search_empty_view` |
| `MoreLikeThisSection` runs accidentally while offline | ViewModel short-circuits to `Error("Search requires an internet connection")` when `!isOnline`; UI shows retry rather than taking over the screen |
| Home/Favorites users see a new tab appear | Documented in README; the bottom bar's `saveState = true` keeps them intact |

---

# Expected Test-Count Impact

| Suite | Current | +After A | +After B | +After B.Stretch |
|---|---|---|---|---|
| Unit (JVM) | 101 | +14 | +5 | +3 |
| UI component (device) | 79 | +8 | +3 | +2 |
| Journey (device) | 8 | +3 | 0 | 0 |
| **Total** | **188** | **213** | **221** | **226** |

All still JVM- and instrument-runnable on the existing test infrastructure — no new tools needed.
