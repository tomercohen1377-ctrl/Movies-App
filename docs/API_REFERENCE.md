# TMDB API Reference

Base URL: `https://api.themoviedb.org/3`
Image Base URL: `https://image.tmdb.org/t/p/`

Authentication: All requests require either:
- Query param: `?api_key=YOUR_KEY`
- Bearer header: `Authorization: Bearer YOUR_READ_ACCESS_TOKEN`

> Recommendation: Use `Bearer` token auth via `AuthInterceptor` — it's the modern approach.

---

## Endpoints Used

### 1. Movie Lists

#### Upcoming Movies
```
GET /movie/upcoming
```
Query params:
| Param | Type | Default | Description |
|---|---|---|---|
| `language` | string | `en-US` | Language for results |
| `page` | int | `1` | Page number (max ~500) |
| `region` | string | — | ISO 3166-1 region filter |

Response:
```json
{
  "page": 1,
  "results": [ <MovieResult> ],
  "total_pages": 42,
  "total_results": 824
}
```

---

#### Top Rated Movies
```
GET /movie/top_rated
```
Same query params as above.

---

#### Now Playing Movies
```
GET /movie/now_playing
```
Same query params as above.

---

### 2. Movie Details

```
GET /movie/{movie_id}
```
Path params:
| Param | Type | Description |
|---|---|---|
| `movie_id` | int | The TMDB movie ID |

Response includes:
```json
{
  "id": 12345,
  "title": "Movie Title",
  "overview": "Description...",
  "poster_path": "/abc123.jpg",
  "backdrop_path": "/xyz789.jpg",
  "release_date": "2024-06-15",
  "vote_average": 7.8,
  "vote_count": 1234,
  "runtime": 120,
  "genres": [{ "id": 28, "name": "Action" }],
  "tagline": "The tagline."
}
```

---

### 3. Movie Videos (Trailers)

```
GET /movie/{movie_id}/videos
```

Response:
```json
{
  "id": 12345,
  "results": [
    {
      "key": "dQw4w9WgXcQ",
      "name": "Official Trailer",
      "site": "YouTube",
      "type": "Trailer",
      "official": true,
      "published_at": "2024-01-01T00:00:00.000Z"
    }
  ]
}
```

**Trailer selection logic:**
1. Filter by `site == "YouTube"` AND `type == "Trailer"`
2. Prefer `official == true`
3. Sort by `published_at` descending, take first result
4. YouTube watch URL: `https://www.youtube.com/watch?v={key}`
5. YouTube thumbnail: `https://img.youtube.com/vi/{key}/0.jpg`

---

## Image URLs

Construct image URLs by combining base URL + size + path:

```
https://image.tmdb.org/t/p/{size}{poster_path}
```

### Common Sizes

| Size | Dimensions | Use Case |
|---|---|---|
| `w154` | 154px wide | Small thumbnails |
| `w342` | 342px wide | Movie list cards |
| `w500` | 500px wide | Detail screen poster |
| `w780` | 780px wide | Backdrop (detail header) |
| `original` | Full resolution | High-res display |

**Example:**
```
https://image.tmdb.org/t/p/w342/abc123.jpg
```

---

## Pagination

All list endpoints return paginated results. Use Paging 3 `PagingSource` for seamless infinite scroll:

```kotlin
override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Movie> {
    val page = params.key ?: 1
    return try {
        val response = api.getMovies(category = category, page = page)
        LoadResult.Page(
            data = response.results.map { it.toDomain() },
            prevKey = if (page == 1) null else page - 1,
            nextKey = if (page >= response.totalPages) null else page + 1
        )
    } catch (e: Exception) {
        LoadResult.Error(e)
    }
}
```

---

## Error Handling

| HTTP Status | Meaning | Handling |
|---|---|---|
| `401` | Invalid API key | Show auth error, log |
| `404` | Movie not found | Show not found state |
| `429` | Rate limit exceeded | Retry with backoff |
| `5xx` | Server error | Show generic error + retry |
| Network error | No connection | Show offline state |

---

## Rate Limits

TMDB imposes a rate limit of approximately **50 requests per second** per IP. For a standard mobile app this is never a concern, but good to know.

---

## Useful Links

- [TMDB API Docs](https://developer.themoviedb.org/docs)
- [Register for an API Key](https://www.themoviedb.org/settings/api)
- [Image Configuration Endpoint](https://developer.themoviedb.org/reference/configuration-details) — for dynamic size resolution
