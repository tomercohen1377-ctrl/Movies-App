package com.tcohen.moviesapp.domain.repository

import app.cash.paging.PagingData
import com.tcohen.moviesapp.domain.model.Category
import com.tcohen.moviesapp.domain.model.Movie
import com.tcohen.moviesapp.domain.model.MovieDetail
import com.tcohen.moviesapp.domain.model.VideoResult
import com.tcohen.moviesapp.util.NetworkResult
import kotlinx.coroutines.flow.Flow

/**
 * Full KMP-compatible repository interface.
 *
 * Combines all movie operations — including paginated list streams — in a single
 * interface that can be used from both [commonMain] state holders and ViewModels.
 *
 * Uses [app.cash.paging.PagingData] (KMP wrapper around AndroidX Paging) so
 * the interface is resolvable in [commonMain]. On Android the CashApp types are
 * type-aliases to the real AndroidX paging types; on other platforms they are
 * independent implementations.
 */
interface MovieRepository {

    // ── Paginated lists ───────────────────────────────────────────────────────

    /** Returns a [Flow] of paginated [Movie] items for the given [category]. */
    fun getMovies(category: Category): Flow<PagingData<Movie>>

    /**
     * Returns a paginated [Flow] of the account's favorites.
     *
     * When online, each page is fetched from `GET /account/{account_id}/favorite/movies`
     * and cached locally. When offline, pages are served from the local cache.
     */
    fun getFavorites(): Flow<PagingData<Movie>>

    // ── Detail ────────────────────────────────────────────────────────────────

    /** Returns full details for a single movie. */
    suspend fun getMovieDetail(movieId: Int): NetworkResult<MovieDetail>

    /**
     * Returns the best YouTube trailer for a movie, or [NetworkResult.Success] with
     * null if none is available / the device is offline.
     */
    suspend fun getTrailer(movieId: Int): NetworkResult<VideoResult?>

    // ── Favorites ─────────────────────────────────────────────────────────────

    /**
     * Adds or removes a movie from the favorites list.
     * Local DB is updated immediately (optimistic); if online the change is
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
