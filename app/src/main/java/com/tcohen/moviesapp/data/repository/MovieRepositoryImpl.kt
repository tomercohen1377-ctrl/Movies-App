package com.tcohen.moviesapp.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.tcohen.moviesapp.data.local.dao.FavoriteDao
import com.tcohen.moviesapp.data.local.dao.MovieDao
import com.tcohen.moviesapp.data.mapper.toDomain
import com.tcohen.moviesapp.data.mapper.toFavoriteEntity
import com.tcohen.moviesapp.data.remote.api.TmdbApiService
import com.tcohen.moviesapp.data.remote.api.safeApiCall
import com.tcohen.moviesapp.data.remote.dto.FavoriteRequest
import com.tcohen.moviesapp.data.remote.dto.VideoResponse
import com.tcohen.moviesapp.data.remote.paging.FavoritesPagingSource
import com.tcohen.moviesapp.data.remote.paging.MoviePagingSource
import com.tcohen.moviesapp.data.remote.paging.PagingDefaults
import com.tcohen.moviesapp.domain.model.Category
import com.tcohen.moviesapp.domain.model.Movie
import com.tcohen.moviesapp.domain.model.MovieDetail
import com.tcohen.moviesapp.domain.model.VideoResult
import com.tcohen.moviesapp.domain.repository.MovieRepository
import com.tcohen.moviesapp.util.NetworkMonitor
import com.tcohen.moviesapp.util.NetworkResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Named

class MovieRepositoryImpl @Inject constructor(
    private val apiService: TmdbApiService,
    private val movieDao: MovieDao,
    private val favoriteDao: FavoriteDao,
    private val networkMonitor: NetworkMonitor,
    @Named("tmdbAccountId") private val accountId: String,
    @Named("tmdbSessionId") private val sessionId: String
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
                    apiService = apiService,
                    movieDao = movieDao,
                    category = category,
                    networkMonitor = networkMonitor
                )
            }
        ).flow
    }

    override suspend fun getMovieDetail(movieId: Int): NetworkResult<MovieDetail> =
        safeApiCall { apiService.getMovieDetails(movieId).toDomain() }

    override suspend fun getTrailer(movieId: Int): NetworkResult<VideoResult?> {
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
        val isCurrentlyFavorite = favoriteDao.isFavorite(movie.id)

        // 1. Update local immediately for instant UI feedback.
        if (isCurrentlyFavorite) {
            favoriteDao.deleteById(movie.id)
        } else {
            favoriteDao.insert(movie.toFavoriteEntity())
        }

        // 2. Best-effort server sync.
        // Mirrors the GET approach: always attempt when online, passing session_id only when
        // configured (null otherwise). A 401 or any other failure is silently swallowed —
        // local state is already updated and is never rolled back.
        if (networkMonitor.isCurrentlyOnline() && accountId.isNotEmpty()) {
            val sessionIdOrNull = sessionId.takeIf { it.isNotEmpty() }
            try {
                apiService.markFavorite(
                    accountId = accountId,
                    sessionId = sessionIdOrNull,
                    body = FavoriteRequest(mediaId = movie.id, favorite = !isCurrentlyFavorite)
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
     * and written into the local Room cache.
     * Offline: pages are read from the Room cache (offset-based pagination).
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
                    apiService = apiService,
                    favoriteDao = favoriteDao,
                    networkMonitor = networkMonitor,
                    accountId = accountId,
                    sessionId = sessionId
                )
            }
        ).flow
    }

    override fun observeIsFavorite(movieId: Int): Flow<Boolean> {
        return favoriteDao.observeIsFavorite(movieId)
    }

    override suspend fun isFavorite(movieId: Int) = favoriteDao.isFavorite(movieId)

    companion object {
        /** Video hosting site identifier used to filter YouTube trailers. */
        private const val VIDEO_SITE_YOUTUBE = "YouTube"

        /** Video type used to identify trailers in the TMDB videos response. */
        private const val VIDEO_TYPE_TRAILER = "Trailer"
    }
}
