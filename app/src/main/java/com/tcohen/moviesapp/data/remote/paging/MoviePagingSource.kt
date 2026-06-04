package com.tcohen.moviesapp.data.remote.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.tcohen.moviesapp.data.local.dao.MovieDao
import com.tcohen.moviesapp.data.mapper.toDomain
import com.tcohen.moviesapp.data.mapper.toEntity
import com.tcohen.moviesapp.data.remote.api.TmdbApiService
import com.tcohen.moviesapp.domain.model.Category
import com.tcohen.moviesapp.domain.model.Movie
import com.tcohen.moviesapp.util.NetworkMonitor
import com.tcohen.moviesapp.util.NetworkUnavailableException

class MoviePagingSource(
    private val apiService: TmdbApiService,
    private val movieDao: MovieDao,
    private val category: Category,
    private val networkMonitor: NetworkMonitor
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
            val response = when (category) {
                Category.UPCOMING -> apiService.getUpcomingMovies(page)
                Category.TOP_RATED -> apiService.getTopRatedMovies(page)
                Category.NOW_PLAYING -> apiService.getNowPlayingMovies(page)
            }

            val now = System.currentTimeMillis()
            val movies = response.results.map { it.toDomain() }

            // On the first page of a fresh online fetch, clear the stale cache so expired
            // entries don't linger alongside the new data.
            if (page == STARTING_PAGE_INDEX) {
                movieDao.deleteByCategory(category.name)
            }

            // Cache to Room so offline browsing works later.
            movieDao.insertAll(movies.map { it.toEntity(category, page, cachedAt = now) })

            LoadResult.Page(
                data = movies,
                prevKey = if (page == STARTING_PAGE_INDEX) null else page - 1,
                nextKey = if (page >= response.totalPages) null else page + 1
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    private suspend fun loadFromCache(page: Int): LoadResult<Int, Movie> {
        // Refuse to serve data that is older than CACHE_EXPIRY_MS.
        // getOldestCachedAt returns null when there is nothing cached yet.
        val oldestCachedAt = movieDao.getOldestCachedAt(category.name) ?: 0L
        val cacheAge = System.currentTimeMillis() - oldestCachedAt
        if (cacheAge > CACHE_EXPIRY_MS) {
            // Cache is expired (or never populated). Wipe stale rows and surface an error
            // so Paging shows the "offline / retry" state rather than stale content.
            movieDao.deleteByCategory(category.name)
            return LoadResult.Error(NetworkUnavailableException())
        }

        val cached = movieDao.getMoviesByCategory(category.name)
            .filter { it.page == page }
            .map { it.toDomain() }

        return if (cached.isNotEmpty()) {
            val lastCachedPage = movieDao.getLastCachedPage(category.name) ?: STARTING_PAGE_INDEX
            LoadResult.Page(
                data = cached,
                prevKey = if (page == STARTING_PAGE_INDEX) null else page - 1,
                nextKey = if (page >= lastCachedPage) null else page + 1
            )
        } else {
            // Beyond cached pages while offline — surface a targeted error
            LoadResult.Error(NetworkUnavailableException())
        }
    }

    override fun getRefreshKey(state: PagingState<Int, Movie>): Int? {
        return state.anchorPosition?.let { anchor ->
            state.closestPageToPosition(anchor)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchor)?.nextKey?.minus(1)
        }
    }

    companion object {
        /** Index of the first page when starting a fresh paging session. TMDB uses 1-based page numbers. */
        const val STARTING_PAGE_INDEX = 1

        /** Cached movie lists older than this are considered stale and will not be served offline. */
        const val CACHE_EXPIRY_MS = 24 * 60 * 60 * 1000L // 1 day
    }
}
