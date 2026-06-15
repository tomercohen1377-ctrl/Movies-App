package com.tcohen.moviesapp.data.remote.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.tcohen.moviesapp.data.local.LocalMovieDataSource
import com.tcohen.moviesapp.data.mapper.toDomain
import com.tcohen.moviesapp.data.remote.TmdbRemoteDataSource
import com.tcohen.moviesapp.domain.model.Movie
import com.tcohen.moviesapp.util.NetworkMonitor

/**
 * [PagingSource] for the favorites list.
 *
 * **Online**: fetches pages directly from `GET /account/{account_id}/favorite/movies`
 * via [TmdbRemoteDataSource].
 *
 * **Offline**: reads from the local SQLDelight favorites cache using offset-based
 * pagination. The cache is populated whenever the user toggles a favorite via
 * `MovieRepository.toggleFavorite`.
 */
class FavoritesPagingSource(
    private val remoteDataSource: TmdbRemoteDataSource,
    private val localDataSource: LocalMovieDataSource,
    private val networkMonitor: NetworkMonitor
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
            val response = remoteDataSource.getFavoriteMovies(page = page)
            val movies = response.results.map { dto -> dto.toDomain() }

            LoadResult.Page(
                data    = movies,
                prevKey = if (page == PagingDefaults.STARTING_PAGE_INDEX) null else page - 1,
                nextKey = if (page >= response.totalPages) null else page + 1
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    /**
     * Serves cached favorites from SQLDelight using offset-based pagination.
     *
     * Fetches [PagingDefaults.PAGE_SIZE] + 1 rows; the extra row is used to
     * determine whether a next page exists without a separate `COUNT(*)` query.
     */
    private suspend fun loadFromCache(page: Int): LoadResult<Int, Movie> {
        val offset    = (page - PagingDefaults.STARTING_PAGE_INDEX) * PagingDefaults.PAGE_SIZE
        val rows      = localDataSource.getFavoritesPaged(
            limit  = PagingDefaults.PAGE_SIZE + 1,
            offset = offset
        )
        val hasNextPage = rows.size > PagingDefaults.PAGE_SIZE
        val pageItems   = if (hasNextPage) rows.dropLast(1) else rows

        return LoadResult.Page(
            data    = pageItems,
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
