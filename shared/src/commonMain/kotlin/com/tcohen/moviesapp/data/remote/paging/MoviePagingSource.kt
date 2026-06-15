package com.tcohen.moviesapp.data.remote.paging

import app.cash.paging.PagingSource
import app.cash.paging.PagingState
import com.tcohen.moviesapp.data.local.LocalMovieDataSource
import com.tcohen.moviesapp.data.mapper.toDomain
import com.tcohen.moviesapp.data.remote.TmdbRemoteDataSource
import com.tcohen.moviesapp.domain.model.Category
import com.tcohen.moviesapp.domain.model.Movie
import com.tcohen.moviesapp.util.NetworkStatusProvider
import com.tcohen.moviesapp.util.NetworkUnavailableException

class MoviePagingSource(
    private val remoteDataSource: TmdbRemoteDataSource,
    private val localDataSource: LocalMovieDataSource,
    private val category: Category,
    private val networkStatusProvider: NetworkStatusProvider
) : PagingSource<Int, Movie>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Movie> {
        val page = params.key ?: PagingDefaults.STARTING_PAGE_INDEX

        return if (networkStatusProvider.isCurrentlyOnline()) {
            loadFromNetwork(page)
        } else {
            loadFromCache(page)
        }
    }

    private suspend fun loadFromNetwork(page: Int): LoadResult<Int, Movie> {
        return try {
            val response = when (category) {
                Category.UPCOMING    -> remoteDataSource.getUpcomingMovies(page)
                Category.TOP_RATED   -> remoteDataSource.getTopRatedMovies(page)
                Category.NOW_PLAYING -> remoteDataSource.getNowPlayingMovies(page)
            }

            val movies = response.results.map { it.toDomain() }

            // On page 1, clear the old cache before inserting fresh data.
            if (page == PagingDefaults.STARTING_PAGE_INDEX) {
                localDataSource.deleteByCategory(category.name)
            }

            // Cache to SQLDelight so offline browsing works later.
            localDataSource.insertMovies(movies, category.name, page)

            LoadResult.Page(
                data    = movies,
                prevKey = if (page == PagingDefaults.STARTING_PAGE_INDEX) null else page - 1,
                nextKey = if (page >= response.totalPages) null else page + 1
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    private suspend fun loadFromCache(page: Int): LoadResult<Int, Movie> {
        val cached = localDataSource.getMoviesByCategory(category.name)

        // SQLDelight returns all cached rows for the category; we slice the requested page.
        val pageSize   = PagingDefaults.PAGE_SIZE
        val fromIndex  = (page - PagingDefaults.STARTING_PAGE_INDEX) * pageSize
        val pageSlice  = if (fromIndex < cached.size)
            cached.subList(fromIndex, minOf(fromIndex + pageSize, cached.size))
        else emptyList()

        return if (pageSlice.isNotEmpty() || (page == PagingDefaults.STARTING_PAGE_INDEX && cached.isNotEmpty())) {
            // Re-use all rows for page 1 if slice arithmetic puts us in the first window.
            val data = if (pageSlice.isEmpty() && page == PagingDefaults.STARTING_PAGE_INDEX)
                cached.take(pageSize) else pageSlice
            val lastCachedPage = getLastCachedPage(cached.size)
            LoadResult.Page(
                data    = data,
                prevKey = if (page == PagingDefaults.STARTING_PAGE_INDEX) null else page - 1,
                nextKey = if (page >= lastCachedPage) null else page + 1
            )
        } else {
            // No cached data for this page while offline.
            LoadResult.Error(NetworkUnavailableException())
        }
    }

    private fun getLastCachedPage(totalCachedRows: Int): Int {
        return if (totalCachedRows == 0) PagingDefaults.STARTING_PAGE_INDEX
        else PagingDefaults.STARTING_PAGE_INDEX +
            (totalCachedRows - 1) / PagingDefaults.PAGE_SIZE
    }

    override fun getRefreshKey(state: PagingState<Int, Movie>): Int? {
        return state.anchorPosition?.let { anchor ->
            state.closestPageToPosition(anchor)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchor)?.nextKey?.minus(1)
        }
    }
}
