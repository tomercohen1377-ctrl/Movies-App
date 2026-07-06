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
        val page = params.key ?: PagingDefaults.STARTING_PAGE_INDEX

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

            val movies = response.results.map { it.toDomain() }

            if (page == PagingDefaults.STARTING_PAGE_INDEX) {
                movieDao.deleteByCategory(category.name)
            }

            movieDao.insertAll(movies.map { it.toEntity(category, page) })

            LoadResult.Page(
                data = movies,
                prevKey = if (page == PagingDefaults.STARTING_PAGE_INDEX) null else page - 1,
                nextKey = if (page >= response.totalPages) null else page + 1
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    private suspend fun loadFromCache(page: Int): LoadResult<Int, Movie> {
        val cached = movieDao.getMoviesByCategory(category.name)
            .filter { it.page == page }
            .map { it.toDomain() }

        return if (cached.isNotEmpty()) {
            val lastCachedPage = movieDao.getLastCachedPage(category.name) ?: PagingDefaults.STARTING_PAGE_INDEX
            LoadResult.Page(
                data = cached,
                prevKey = if (page == PagingDefaults.STARTING_PAGE_INDEX) null else page - 1,
                nextKey = if (page >= lastCachedPage) null else page + 1
            )
        } else {

            LoadResult.Error(NetworkUnavailableException())
        }
    }

    override fun getRefreshKey(state: PagingState<Int, Movie>): Int? {
        return state.anchorPosition?.let { anchor ->
            state.closestPageToPosition(anchor)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchor)?.nextKey?.minus(1)
        }
    }

}
