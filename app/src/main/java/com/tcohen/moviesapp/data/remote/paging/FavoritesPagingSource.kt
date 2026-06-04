package com.tcohen.moviesapp.data.remote.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.tcohen.moviesapp.data.local.dao.FavoriteDao
import com.tcohen.moviesapp.data.mapper.toDomain
import com.tcohen.moviesapp.data.remote.api.TmdbApiService
import com.tcohen.moviesapp.domain.model.Movie
import com.tcohen.moviesapp.util.NetworkMonitor

/**
 * [PagingSource] for the favorites list.
 *
 * **Online**: fetches pages directly from `GET /account/{account_id}/favorite/movies`.
 * Does **not** write results back to Room to avoid triggering an infinite reload loop
 * (any Room write would re-invalidate this source via the ViewModel's refresh trigger).
 *
 * **Offline**: reads from the local Room favorites table using an offset-based approach
 * (`savedAt DESC`, page size = [FAVORITES_PAGE_SIZE]). The cache is populated whenever
 * the user toggles a favorite (add or remove) via `MovieRepository.toggleFavorite`.
 */
class FavoritesPagingSource(
    private val apiService: TmdbApiService,
    private val favoriteDao: FavoriteDao,
    private val networkMonitor: NetworkMonitor,
    private val accountId: String,
    private val sessionId: String
) : PagingSource<Int, Movie>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Movie> {
        val page = params.key ?: STARTING_PAGE_INDEX
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
            val movies = response.results.map { dto ->
                Movie(
                    id = dto.id,
                    title = dto.title,
                    overview = dto.overview,
                    posterPath = dto.posterPath,
                    backdropPath = dto.backdropPath,
                    releaseDate = dto.releaseDate,
                    voteAverage = dto.voteAverage,
                    voteCount = dto.voteCount
                )
            }

            LoadResult.Page(
                data = movies,
                prevKey = if (page == STARTING_PAGE_INDEX) null else page - 1,
                nextKey = if (page >= response.totalPages) null else page + 1
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    /**
     * Serves cached favorites from Room using offset-based pagination.
     *
     * Fetches [FAVORITES_PAGE_SIZE] + 1 rows; the extra row is used to determine
     * whether a next page exists without a separate `COUNT(*)` query.
     */
    private suspend fun loadFromCache(page: Int): LoadResult<Int, Movie> {
        val offset = (page - STARTING_PAGE_INDEX) * FAVORITES_PAGE_SIZE
        val rows = favoriteDao.getFavoritesPaged(
            limit = FAVORITES_PAGE_SIZE + 1,
            offset = offset
        )
        val hasNextPage = rows.size > FAVORITES_PAGE_SIZE
        val pageItems = if (hasNextPage) rows.dropLast(1) else rows

        return LoadResult.Page(
            data = pageItems.map { it.toDomain() },
            prevKey = if (page == STARTING_PAGE_INDEX) null else page - 1,
            nextKey = if (hasNextPage) page + 1 else null
        )
    }

    override fun getRefreshKey(state: PagingState<Int, Movie>): Int? {
        return state.anchorPosition?.let { anchor ->
            state.closestPageToPosition(anchor)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchor)?.nextKey?.minus(1)
        }
    }

    companion object {
        /** TMDB uses 1-based page numbers. */
        const val STARTING_PAGE_INDEX = 1

        /**
         * Page size used for both server requests and the offline Room cache.
         * Matches TMDB's default of 20 items per page.
         */
        const val FAVORITES_PAGE_SIZE = 20
    }
}
