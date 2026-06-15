package com.tcohen.moviesapp.domain.repository

import com.tcohen.moviesapp.domain.model.Movie
import com.tcohen.moviesapp.domain.model.MovieDetail
import com.tcohen.moviesapp.domain.model.VideoResult
import com.tcohen.moviesapp.util.NetworkResult
import kotlinx.coroutines.flow.Flow

/**
 * Platform-agnostic subset of the movie repository.
 *
 * Contains all operations that do NOT depend on Android Paging (`PagingData`).
 * Shared code (state holders, use cases) should depend on this interface.
 *
 * The full Android [MovieRepository] extends this and adds paginated list methods.
 */
interface MovieRepositoryBase {

    /** Returns full details for a single movie. */
    suspend fun getMovieDetail(movieId: Int): NetworkResult<MovieDetail>

    /**
     * Returns the best YouTube trailer for a movie, or [NetworkResult.Success] with
     * null if none is available / the device is offline.
     */
    suspend fun getTrailer(movieId: Int): NetworkResult<VideoResult?>

    /**
     * Adds or removes a movie from the favorites list.
     * Local Room is updated immediately (optimistic); if online the change is
     * also pushed to the TMDB server.
     */
    suspend fun toggleFavorite(movie: Movie)

    /** Observes whether a specific movie is currently favorited (local, always fast). */
    fun observeIsFavorite(movieId: Int): Flow<Boolean>

    /** Suspending check for whether a movie is favorited — for one-shot reads. */
    suspend fun isFavorite(movieId: Int): Boolean

    /**
     * Emits [Unit] whenever a favorite is added or removed via [toggleFavorite].
     * Used to restart paging flows after any favorite change.
     */
    val favoriteChanges: Flow<Unit>
}
