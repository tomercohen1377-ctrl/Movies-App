package com.tcohen.moviesapp.data.remote.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.tcohen.moviesapp.data.mapper.toDomain
import com.tcohen.moviesapp.data.remote.api.SafeApiCaller
import com.tcohen.moviesapp.data.remote.api.TmdbApiService
import com.tcohen.moviesapp.domain.model.Movie
import com.tcohen.moviesapp.util.ApiError
import com.tcohen.moviesapp.util.NetworkResult
import com.tcohen.moviesapp.util.NetworkUnavailableException

/**
 * [PagingSource] for the free-text movie search.
 *
 * **Online-only by design.** Search results are user-driven, ephemeral, and almost
 * never repeat, so writing them to Room would just churn the database with stale
 * entries that collides with the category-keyed cache. The [SafeApiCaller] block
 * handles the offline check + exception mapping in one place — no
 * `networkMonitor.isCurrentlyOnline()` call here.
 *
 * Errors are surfaced as Paging-typed [LoadResult.Error]s. To preserve the offline
 * distinction the rest of the app uses, we map [ApiError.NO_CONNECTION] back to
 * a typed [NetworkUnavailableException] — UI footers in the app already know
 * how to render a "no internet" footer distinctly from a generic API error.
 */
class SearchPagingSource(
    private val safeApiCaller: SafeApiCaller,
    private val apiService: TmdbApiService,
    private val query: String,
) : PagingSource<Int, Movie>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Movie> {
        val page = params.key ?: PagingDefaults.STARTING_PAGE_INDEX

        val result = safeApiCaller {
            apiService.searchMovies(query = query, page = page)
        }

        return when (result) {
            is NetworkResult.Success -> {
                val response = result.data
                LoadResult.Page(
                    data = response.results.map { it.toDomain() },
                    prevKey = if (page == PagingDefaults.STARTING_PAGE_INDEX) null else page - 1,
                    nextKey = if (page >= response.totalPages) null else page + 1
                )
            }

            is NetworkResult.Error -> LoadResult.Error(toPagingException(result.message))
        }
    }

    /**
     * Translates an [ApiError] message into the [Throwable] flavour Paging 3 expects.
     * Specifically preserves the offline discrimination: a no-connection failure
     * surfaces as [NetworkUnavailableException] so the UI footer renders the
     * "no internet" copy; other failures wrap their message in a generic
     * [Exception] for the standard error view.
     */
    private fun toPagingException(message: String): Throwable =
        if (message == ApiError.NO_CONNECTION.message) {
            NetworkUnavailableException()
        } else {
            Exception(message)
        }

    override fun getRefreshKey(state: PagingState<Int, Movie>): Int? {
        return state.anchorPosition?.let { anchor ->
            state.closestPageToPosition(anchor)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchor)?.nextKey?.minus(1)
        }
    }
}
