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
     * Returns a paginated flow of movies matching the free-text [query].
     *
     * Online-only — searches are user-driven and ephemeral, so they don't write
     * to the Room category cache. If the device is offline, the inner Paging source
     * emits a [com.tcohen.moviesapp.util.NetworkUnavailableException] so the UI
     * can render a "search requires internet" footer.
     *
     * The [Repository][com.tcohen.moviesapp.data.repository.MovieRepositoryImpl]
     * short-circuits empty or below-threshold queries by emitting [PagingData.empty].
     */
    fun searchMovies(query: String): Flow<PagingData<Movie>>

    /**
     * Returns up to ~20 movies "similar" to [movieId], per TMDB's `/{id}/similar`
     * endpoint. Capped at MOST_SIMILAR_MOVIES.
     */
    suspend fun getSimilarMovies(movieId: Int): NetworkResult<List<Movie>>

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
