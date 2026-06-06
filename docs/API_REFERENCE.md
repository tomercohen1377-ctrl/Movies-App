# TMDB API Reference

Base URL: `https://api.themoviedb.org/3`
Image Base URL: `https://image.tmdb.org/t/p/`

Authentication: All requests use a **Bearer token** header injected by `AuthInterceptor`:
```
Authorization: Bearer YOUR_READ_ACCESS_TOKEN
```

> The Bearer token is sufficient for all GET endpoints and the favorites write endpoint (`POST /account/{id}/favorite`). Pass a `TMDB_SESSION_ID` in `build.gradle.kts` only if your account requires v3 session auth for write operations.

---

## Endpoints Used

### 1. Movie Lists

Three category endpoints — all share the same query params and response shape.

#### Now Playing
```
GET /movie/now_playing
```

#### Top Rated
```
GET /movie/top_rated
```

#### Upcoming
```
GET /movie/upcoming
```

**Query params:**

| Param | Type | Default | Description |
|---|---|---|---|
| `language` | string | `en-US` | BCP-47 language tag |
| `page` | int | `1` | Page number (1-based, max ~500) |

**Response (`MovieListResponse`):**
```json
{
  "page": 1,
  "results": [ <MovieResponse> ],
  "total_pages": 42,
  "total_results": 824
}
```

**`MovieResponse` fields used:**

| Field | Type | Notes |
|---|---|---|
| `id` | int | TMDB movie ID |
| `title` | string | Display title |
| `overview` | string | Short description |
| `poster_path` | string? | Path for poster image (see Image URLs) |
| `backdrop_path` | string? | Path for backdrop image |
| `release_date` | string | ISO 8601 (`YYYY-MM-DD`) |
| `vote_average` | double | 0–10 score |

---

### 2. Movie Details

```
GET /movie/{movie_id}
```

| Path param | Type | Description |
|---|---|---|
| `movie_id` | int | The TMDB movie ID |

**Response (`MovieDetailsResponse`) — fields used:**
```json
{
  "id": 12345,
  "title": "Movie Title",
  "overview": "Description...",
  "poster_path": "/abc123.jpg",
  "backdrop_path": "/xyz789.jpg",
  "release_date": "2024-06-15",
  "vote_average": 7.8,
  "runtime": 120,
  "genres": [{ "id": 28, "name": "Action" }],
  "tagline": "The tagline."
}
```

> `runtime` is in minutes. `genres` is deserialized as `List<GenreResponse>`.

---

### 3. Movie Videos (Trailers)

```
GET /movie/{movie_id}/videos
```

**Response (`VideoListResponse`):**
```json
{
  "id": 12345,
  "results": [
    {
      "key": "dQw4w9WgXcQ",
      "site": "YouTube",
      "type": "Trailer",
      "official": true,
      "published_at": "2024-01-01T00:00:00.000Z"
    }
  ]
}
```

> `id` and `name` fields are present in the TMDB response but are not deserialized — they are not used anywhere in the app.

**Trailer selection logic (`MovieRepositoryImpl`):**
1. Filter by `site == "YouTube"` AND `type == "Trailer"`
2. Prefer `official == true`
3. Sort by `publishedAt` descending, take first result
4. Embed URL: `https://www.youtube.com/embed/{key}?autoplay=1`

**Offline behaviour:** `getTrailer()` returns `NetworkResult.Success(null)` immediately when offline. The detail screen then shows the backdrop image instead of the player — no error shown.

---

### 4. Favorites — Get List

```
GET /account/{account_id}/favorite/movies
```

| Query param | Type | Default | Description |
|---|---|---|---|
| `page` | int | `1` | Page number |
| `language` | string | `en-US` | Language for results |

> `{account_id}` is set via the `TMDB_ACCOUNT_ID` build config field. The value `"me"` resolves to the authenticated user when using Bearer token auth.

**Response:** Same shape as movie list endpoints (`MovieListResponse`).

Used by `FavoritesPagingSource` when online. Results are also written to the `favorites` Room table for offline access.

---

### 5. Favorites — Add / Remove

```
POST /account/{account_id}/favorite
```

**Request body (`FavoriteRequest`):**
```json
{
  "media_type": "movie",
  "media_id": 12345,
  "favorite": true
}
```

> `media_type` is always `"movie"`. It must always be serialized even though it has a default value — `@EncodeDefault` is applied to the field to override `kotlinx.serialization`'s `encodeDefaults = false` global setting.

Set `"favorite": false` to remove the movie from favorites.

**Response (`FavoriteResponse`):**
```json
{
  "status_code": 1,
  "status_message": "The item/record was created successfully.",
  "success": true
}
```

Used by `MovieRepositoryImpl.toggleFavorite()` — the call is fire-and-forget; local Room state is updated optimistically before the server call.

---

## Image URLs

Construct image URLs by combining base URL + size + path:

```
https://image.tmdb.org/t/p/{size}{path}
```

### Sizes Used in This App

| Size | Dimensions | Use case | Helper |
|---|---|---|---|
| `w342` | 342px wide | Movie list card poster | `TmdbImageUrl.poster(path)` |
| `w500` | 500px wide | Detail screen poster thumbnail | `TmdbImageUrl.posterLarge(path)` |
| `w780` | 780px wide | Detail screen backdrop | `TmdbImageUrl.backdrop(path)` |

All helpers return `null` when `path` is null — `AsyncImage` gracefully shows the error drawable in that case.

**Example:**
```
https://image.tmdb.org/t/p/w342/oYuLEt3zVCKq57qu2F8dT7NIa6f.jpg
```

**Caching:** Coil fetches images using a dedicated `OkHttpClient`. A network interceptor stamps each response with `Cache-Control: max-age=86400`. Coil's `DiskCache` (100 MB) stores the result for 24 hours. Subsequent requests — including when offline — are served from disk within that window.

---

## Pagination

All list endpoints use 1-based page numbers. `PagingDefaults` (shared constants object) defines:

| Constant | Value | Purpose |
|---|---|---|
| `STARTING_PAGE_INDEX` | `1` | First page for any fresh load |
| `PAGE_SIZE` | `20` | Items per page (also matches TMDB's default) |
| `PREFETCH_DISTANCE` | `5` | Items before end to trigger next page prefetch |

**`PagingSource.getRefreshKey`** returns the page the user was last viewing, so pager restarts resume from that position rather than page 1.

---

## Error Handling

All API calls go through `safeApiCall`:

```kotlin
suspend fun <T> safeApiCall(block: suspend () -> T): NetworkResult<T>
```

| Exception type | `NetworkResult` produced |
|---|---|
| `HttpException` (4xx/5xx) | `Error(statusMessage, httpCode)` — `statusMessage` from TMDB's `TmdbErrorBody` JSON |
| `SocketTimeoutException` | `Error(ApiError.TIMEOUT.message)` |
| `IOException` / `UnknownHostException` | `Error(ApiError.NO_CONNECTION.message)` |
| Any other `Exception` | `Error(ApiError.UNEXPECTED.message)` |

**`TmdbErrorBody` JSON shape:**
```json
{
  "status_code": 7,
  "status_message": "Invalid API key: You must be granted a valid key.",
  "success": false
}
```

The `status_message` field is used as the user-facing error string. If parsing fails (malformed body), `ApiError.SERVER_ERROR.message` is the fallback.

---

## Rate Limits

TMDB imposes approximately **50 requests per second** per IP. For a standard mobile app this is never a concern.

---

## Useful Links

- [TMDB API Docs](https://developer.themoviedb.org/docs)
- [Register for an API Key](https://www.themoviedb.org/settings/api)
- [Image Configuration Endpoint](https://developer.themoviedb.org/reference/configuration-details) — for dynamic size resolution
- [Favorites API](https://developer.themoviedb.org/reference/account-add-favorite) — full request/response spec
