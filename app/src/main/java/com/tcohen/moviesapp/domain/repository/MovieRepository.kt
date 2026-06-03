package com.tcohen.moviesapp.domain.repository

import androidx.paging.PagingData
import com.tcohen.moviesapp.domain.model.Category
import com.tcohen.moviesapp.domain.model.Movie
import com.tcohen.moviesapp.domain.model.MovieDetail
import com.tcohen.moviesapp.domain.model.VideoResult
import kotlinx.coroutines.flow.Flow

interface MovieRepository {

    /** Returns a [Flow] of paginated [Movie] items for the given [category]. */
    fun getMovies(category: Category): Flow<PagingData<Movie>>

    /** Returns full details for a single movie. Throws if offline and not cached. */
    suspend fun getMovieDetail(movieId: Int): MovieDetail

    /**
     * Returns the best YouTube trailer key for a movie, or null if none exists
     * or the device is offline.
     */
    suspend fun getTrailer(movieId: Int): VideoResult?

    /** Adds or removes a movie from the favorites list. */
    suspend fun toggleFavorite(movie: Movie)

    /** Observes the full favorites list from local DB. */
    fun getFavorites(): Flow<List<Movie>>

    /** Observes whether a specific movie is currently favorited. */
    fun isFavorite(movieId: Int): Flow<Boolean>
}
