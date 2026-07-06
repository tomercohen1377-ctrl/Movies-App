package com.tcohen.moviesapp.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.tcohen.moviesapp.data.auth.AuthStore
import com.tcohen.moviesapp.data.local.dao.MovieDao
import com.tcohen.moviesapp.data.mapper.toDomain
import com.tcohen.moviesapp.data.remote.api.TmdbApiService
import com.tcohen.moviesapp.data.remote.api.safeApiCall
import com.tcohen.moviesapp.data.remote.dto.VideoResponse
import com.tcohen.moviesapp.data.remote.paging.MoviePagingSource
import com.tcohen.moviesapp.data.remote.paging.PagingDefaults
import com.tcohen.moviesapp.data.remote.server.api.ServerApiService
import com.tcohen.moviesapp.data.remote.server.api.safeServerApiCall
import com.tcohen.moviesapp.data.remote.server.dto.ServerIsFavoriteResponse
import com.tcohen.moviesapp.domain.model.Category
import com.tcohen.moviesapp.domain.model.Movie
import com.tcohen.moviesapp.domain.model.MovieDetail
import com.tcohen.moviesapp.domain.model.VideoResult
import com.tcohen.moviesapp.domain.repository.MovieRepository
import com.tcohen.moviesapp.util.NetworkMonitor
import com.tcohen.moviesapp.util.NetworkResult
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class MovieRepositoryImpl @Inject constructor(
    private val apiService: TmdbApiService,
    private val movieDao: MovieDao,
    private val networkMonitor: NetworkMonitor,
    private val serverApiService: ServerApiService,
    private val authStore: AuthStore,
) : MovieRepository {

    override fun getMovies(category: Category): Flow<PagingData<Movie>> = Pager(
        config = PagingConfig(
            pageSize = PagingDefaults.PAGE_SIZE,
            enablePlaceholders = false,
            prefetchDistance = PagingDefaults.PREFETCH_DISTANCE
        ),
        pagingSourceFactory = {
            MoviePagingSource(
                apiService = apiService,
                movieDao = movieDao,
                category = category,
                networkMonitor = networkMonitor
            )
        }
    ).flow

    override suspend fun getMovieDetail(movieId: Int): NetworkResult<MovieDetail> =
        safeApiCall { apiService.getMovieDetails(movieId).toDomain() }

    override suspend fun getTrailer(movieId: Int): NetworkResult<VideoResult?> {
        if (!networkMonitor.isCurrentlyOnline()) return NetworkResult.Success(null)
        return safeApiCall {
            apiService.getMovieVideos(movieId).results
                .filter { it.site == VIDEO_SITE_YOUTUBE && it.type == VIDEO_TYPE_TRAILER }
                .sortedWith(
                    compareByDescending<VideoResponse> { it.official }
                        .thenByDescending { it.publishedAt }
                )
                .firstOrNull()
                ?.toDomain()
        }
    }

    override suspend fun getFavorites(): NetworkResult<List<Movie>> {
        val userId = resolveUserId() ?: return NetworkResult.Error("Not signed in", httpCode = 0)
        return when (val response = safeServerApiCall(networkMonitor) {
            serverApiService.getFavorites(userId)
        }) {
            is NetworkResult.Error -> response
            is NetworkResult.Success -> NetworkResult.Success(
                response.data.mapNotNull { movieDao.findById(it.movieId)?.toDomain() }
            )
        }
    }

    override suspend fun addFavorite(movie: Movie): NetworkResult<Unit> {
        val userId = resolveUserId() ?: return NetworkResult.Error("Not signed in", httpCode = 0)
        return safeServerApiCall(networkMonitor) { serverApiService.addFavorite(userId, movie.id) }
            .toUnitOrError()
    }

    override suspend fun removeFavorite(movie: Movie): NetworkResult<Unit> {
        val userId = resolveUserId() ?: return NetworkResult.Error("Not signed in", httpCode = 0)
        return safeServerApiCall(networkMonitor) { serverApiService.removeFavorite(userId, movie.id) }
            .toUnitOrError()
    }

    override suspend fun isFavorite(movieId: Int): NetworkResult<Boolean> {
        val userId = resolveUserId() ?: return NetworkResult.Error("Not signed in", httpCode = 0)
        return when (val response = safeServerApiCall(networkMonitor) {
            serverApiService.isFavorite(userId, movieId)
        }) {
            is NetworkResult.Error -> response
            is NetworkResult.Success -> when {
                response.data.code() == 404 -> NetworkResult.Success(data = false)
                response.data.isSuccessful -> NetworkResult.Success(
                    data = response.data.body()?.isFavorite ?: false,
                )
                else -> NetworkResult.Error(
                    message = response.data.message().ifEmpty { "Failed" },
                    httpCode = response.data.code(),
                )
            }
        }
    }

    private fun resolveUserId(): String? = authStore.readSnapshotBlocking()?.userId

    private fun <T> NetworkResult<T>.toUnitOrError(): NetworkResult<Unit> = when (this) {
        is NetworkResult.Error -> NetworkResult.Error(message, httpCode)
        is NetworkResult.Success -> NetworkResult.Success(Unit)
    }

    companion object {

        private const val VIDEO_SITE_YOUTUBE = "YouTube"

        private const val VIDEO_TYPE_TRAILER = "Trailer"
    }
}
