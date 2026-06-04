package com.tcohen.moviesapp.domain.repository

import androidx.paging.PagingData
import com.tcohen.moviesapp.domain.model.Category
import com.tcohen.moviesapp.domain.model.Movie
import com.tcohen.moviesapp.domain.model.MovieDetail
import com.tcohen.moviesapp.domain.model.VideoResult
import com.tcohen.moviesapp.util.NetworkResult
import kotlinx.coroutines.flow.Flow

interface MovieRepository {

    /** Returns a [Flow] of paginated [Movie] items for the given [category]. */
    fun getMovies(category: Category): Flow<PagingData<Movie>>

    /** Returns full details for a single movie. */
    suspend fun getMovieDetail(movieId: Int): NetworkResult<MovieDetail>

    /**
     * Returns the best YouTube trailer for a movie, or [NetworkResult.Success] with
     * null if none is available / device is offline.
     */
    suspend fun getTrailer(movieId: Int): NetworkResult<VideoResult?>

    /**
     * Adds or removes a movie from the favorites list.
     *
     * Local Room is updated immediately (optimistic). If online and credentials
     * are configured, the change is also pushed to the TMDB server:
     * - **Add**: `POST /account/{account_id}/favorite` with `favorite = true`
     * - **Remove**: `POST /list/{list_id}/remove_item` when a list ID is configured,
     *   otherwise falls back to `POST /account/{account_id}/favorite` with `favorite = false`
     */
    suspend fun toggleFavorite(movie: Movie)

    /**
     * Returns a paginated [Flow] of the account's favorites.
     *
     * When online, each page is fetched from `GET /account/{account_id}/favorite/movies`
     * and cached in Room. When offline, pages are served from the Room cache.
     */
    fun getFavorites(): Flow<PagingData<Movie>>

    /** Observes whether a specific movie is currently favorited (local, always fast). */
    fun observeIsFavorite(movieId: Int): Flow<Boolean>

    suspend fun isFavorite(movieId: Int): Boolean

    /**
     * Emits [Unit] whenever a favorite is added or removed via [toggleFavorite].
     * Used by the favorites ViewModel to restart the paging flow after any change,
     * whether the change originated from the favorites screen or the detail screen.
     */
    val favoriteChanges: Flow<Unit>
}
