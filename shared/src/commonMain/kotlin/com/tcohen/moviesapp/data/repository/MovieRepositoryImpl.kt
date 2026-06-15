package com.tcohen.moviesapp.data.repository

import app.cash.paging.Pager
import app.cash.paging.PagingConfig
import app.cash.paging.PagingData
import com.tcohen.moviesapp.data.local.LocalMovieDataSource
import com.tcohen.moviesapp.data.mapper.toDomain
import com.tcohen.moviesapp.data.remote.TmdbRemoteDataSource
import com.tcohen.moviesapp.data.remote.api.safeApiCall
import com.tcohen.moviesapp.data.remote.dto.VideoResponse
import com.tcohen.moviesapp.data.remote.paging.FavoritesPagingSource
import com.tcohen.moviesapp.data.remote.paging.MoviePagingSource
import com.tcohen.moviesapp.data.remote.paging.PagingDefaults
import com.tcohen.moviesapp.domain.model.Category
import com.tcohen.moviesapp.domain.model.Movie
import com.tcohen.moviesapp.domain.model.MovieDetail
import com.tcohen.moviesapp.domain.model.VideoResult
import com.tcohen.moviesapp.domain.repository.MovieRepository
import com.tcohen.moviesapp.util.NetworkResult
import com.tcohen.moviesapp.util.NetworkStatusProvider
import com.tcohen.moviesapp.util.currentTimeMillis
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class MovieRepositoryImpl(
    private val remoteDataSource: TmdbRemoteDataSource,
    private val localDataSource: LocalMovieDataSource,
    private val networkStatusProvider: NetworkStatusProvider
) : MovieRepository {

    // Emits Unit whenever a favorite is added or removed — used to restart the paging flow.
    private val _favoriteChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val favoriteChanges: Flow<Unit> = _favoriteChanges.asSharedFlow()

    override fun getMovies(category: Category): Flow<PagingData<Movie>> {
        return Pager(
            config = PagingConfig(
                pageSize = PagingDefaults.PAGE_SIZE,
                enablePlaceholders = false,
                prefetchDistance = PagingDefaults.PREFETCH_DISTANCE
            ),
            pagingSourceFactory = {
                MoviePagingSource(
                    remoteDataSource      = remoteDataSource,
                    localDataSource       = localDataSource,
                    category              = category,
                    networkStatusProvider = networkStatusProvider
                )
            }
        ).flow
    }

    override suspend fun getMovieDetail(movieId: Int): NetworkResult<MovieDetail> =
        safeApiCall { remoteDataSource.getMovieDetails(movieId).toDomain() }

    override suspend fun getTrailer(movieId: Int): NetworkResult<VideoResult?> {
        // Offline fast-path — no point attempting a network call with no connectivity.
        if (!networkStatusProvider.isCurrentlyOnline()) return NetworkResult.Success(null)
        return safeApiCall {
            remoteDataSource.getMovieVideos(movieId).results
                .filter { it.site == VIDEO_SITE_YOUTUBE && it.type == VIDEO_TYPE_TRAILER }
                .sortedWith(
                    compareByDescending<VideoResponse> { it.official }
                        .thenByDescending { it.publishedAt }
                )
                .firstOrNull()
                ?.toDomain()
        }
    }

    /**
     * Toggles the favorite state for [movie].
     *
     * **Local update** happens immediately (optimistic UI).
     *
     * **Server sync**: `POST /account/{account_id}/favorite` with `favorite = true/false`.
     *
     * Server failures are silently ignored — local state is never rolled back.
     */
    override suspend fun toggleFavorite(movie: Movie) {
        val isCurrentlyFavorite = localDataSource.isFavorite(movie.id)

        // 1. Update local immediately for instant UI feedback.
        if (isCurrentlyFavorite) {
            localDataSource.deleteFavoriteById(movie.id)
        } else {
            localDataSource.insertFavorite(movie, currentTimeMillis())
        }

        // 2. Best-effort server sync.
        if (networkStatusProvider.isCurrentlyOnline()) {
            try {
                remoteDataSource.markFavorite(
                    mediaId  = movie.id,
                    favorite = !isCurrentlyFavorite
                )
            } catch (_: Exception) {
                // Server sync failure — local state already updated, nothing to undo.
            }
        }

        // Notify observers so any active favorites pager restarts and shows the change.
        _favoriteChanges.tryEmit(Unit)
    }

    /**
     * Returns a paginated [Flow] of the account's favorites.
     *
     * Online: pages are fetched from `GET /account/{account_id}/favorite/movies`
     * and written into the local SQLDelight cache.
     * Offline: pages are read from the SQLDelight cache (offset-based pagination).
     */
    override fun getFavorites(): Flow<PagingData<Movie>> {
        return Pager(
            config = PagingConfig(
                pageSize = PagingDefaults.PAGE_SIZE,
                enablePlaceholders = false,
                prefetchDistance = PagingDefaults.PREFETCH_DISTANCE
            ),
            pagingSourceFactory = {
                FavoritesPagingSource(
                    remoteDataSource      = remoteDataSource,
                    localDataSource       = localDataSource,
                    networkStatusProvider = networkStatusProvider
                )
            }
        ).flow
    }

    override fun observeIsFavorite(movieId: Int): Flow<Boolean> =
        localDataSource.observeIsFavorite(movieId)

    override suspend fun isFavorite(movieId: Int) = localDataSource.isFavorite(movieId)

    companion object {
        private const val VIDEO_SITE_YOUTUBE = "YouTube"
        private const val VIDEO_TYPE_TRAILER = "Trailer"
    }
}
