package com.tcohen.moviesapp.data.remote.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.tcohen.moviesapp.data.local.dao.FavoriteDao
import com.tcohen.moviesapp.data.mapper.toDomain
import com.tcohen.moviesapp.data.mapper.toFavoriteEntity
import com.tcohen.moviesapp.data.remote.api.TmdbApiService
import com.tcohen.moviesapp.domain.model.Movie
import com.tcohen.moviesapp.util.NetworkMonitor

/**
 * [PagingSource] for the favorites list.
 *
 * **Online**: fetches pages from `GET /account/{account_id}/favorite/movies` and
 * **mirrors them into Room** via [FavoriteDao.insertAll]. The Room write-through is
 * essential — without it, `MovieRepository.observeIsFavorite(id)` would lie about
 * server-side favorites on a fresh install (Room is empty, but the Favorites grid
 * is showing the server's truth via the API call), so the Detail screen FAB would
 * not reflect the user's actual favorites. Insertions use `REPLACE` strategy so
 * re-syncs are idempotent — re-fetching page N simply overwrites the same rows.
 *
 * We intentionally do **not** trigger [MovieRepository]'s `favoriteChanges` flow
 * from this write-through: that signal is reserved for user-initiated toggles so
 * the [com.tcohen.moviesapp.presentation.favorites.FavoritesViewModel] pager
 * doesn't ping-pong on every successful sync.
 *
 * **Offline**: reads from the local Room favorites table using an offset-based
 * approach (`savedAt DESC`, page size = [PagingDefaults.PAGE_SIZE]). The cache is
 * populated by both user toggles (`MovieRepository.toggleFavorite`) AND by the
 * online sync path here — the two write paths converge into the same Room table.
 *
 * **Note on the offline check inside [load]:** As in [MoviePagingSource], the
 * `networkMonitor.isCurrentlyOnline()` call is a **data-source routing decision**,
 * not a duplicate of [com.tcohen.moviesapp.data.remote.api.SafeApiCaller]'s guard.
 * Server errors on the online path must surface to the UI — silently falling back
 * to Room for every API failure would hide real 5xx issues.
 */
class FavoritesPagingSource(
    private val apiService: TmdbApiService,
    private val favoriteDao: FavoriteDao,
    private val networkMonitor: NetworkMonitor,
    private val accountId: String,
    private val sessionId: String
) : PagingSource<Int, Movie>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Movie> {
        val page = params.key ?: PagingDefaults.STARTING_PAGE_INDEX
        return if (networkMonitor.isCurrentlyOnline()) {
            loadFromNetwork(page)
        } else {
            loadFromCache(page)
        }
    }

    private suspend fun loadFromNetwork(page: Int): LoadResult<Int, Movie> {
        return try {
            val response = apiService.getFavoriteMovies(
                accountId = accountId,
                sessionId = sessionId.takeIf { it.isNotEmpty() },
                page = page
            )
            val movies = response.results.map { dto -> dto.toDomain() }

            // Mirror into Room so `observeIsFavorite(id)` sees server-side favorites.
            // REPLACE conflict strategy → re-syncs are idempotent.
            favoriteDao.insertAll(movies.map { it.toFavoriteEntity() })

            LoadResult.Page(
                data = movies,
                prevKey = if (page == PagingDefaults.STARTING_PAGE_INDEX) null else page - 1,
                nextKey = if (page >= response.totalPages) null else page + 1
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    /**
     * Serves cached favorites from Room using offset-based pagination.
     *
     * Fetches [PagingDefaults.PAGE_SIZE] + 1 rows; the extra row is used to determine
     * whether a next page exists without a separate `COUNT(*)` query.
     */
    private suspend fun loadFromCache(page: Int): LoadResult<Int, Movie> {
        val offset = (page - PagingDefaults.STARTING_PAGE_INDEX) * PagingDefaults.PAGE_SIZE
        val rows = favoriteDao.getFavoritesPaged(
            limit = PagingDefaults.PAGE_SIZE + 1,
            offset = offset
        )
        val hasNextPage = rows.size > PagingDefaults.PAGE_SIZE
        val pageItems = if (hasNextPage) rows.dropLast(1) else rows

        return LoadResult.Page(
            data = pageItems.map { it.toDomain() },
            prevKey = if (page == PagingDefaults.STARTING_PAGE_INDEX) null else page - 1,
            nextKey = if (hasNextPage) page + 1 else null
        )
    }

    override fun getRefreshKey(state: PagingState<Int, Movie>): Int? {
        return state.anchorPosition?.let { anchor ->
            state.closestPageToPosition(anchor)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchor)?.nextKey?.minus(1)
        }
    }

}
