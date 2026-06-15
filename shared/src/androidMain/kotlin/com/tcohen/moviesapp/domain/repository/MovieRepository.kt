package com.tcohen.moviesapp.domain.repository

import androidx.paging.PagingData
import com.tcohen.moviesapp.domain.model.Category
import com.tcohen.moviesapp.domain.model.Movie
import kotlinx.coroutines.flow.Flow

/**
 * Full Android repository interface.
 *
 * Extends [MovieRepositoryBase] with Android-Paging–specific operations.
 * Shared-code state holders depend on [MovieRepositoryBase]; only Android
 * ViewModels and paging sources need the full [MovieRepository].
 */
interface MovieRepository : MovieRepositoryBase {

    /** Returns a [Flow] of paginated [Movie] items for the given [category]. */
    fun getMovies(category: Category): Flow<PagingData<Movie>>

    /**
     * Returns a paginated [Flow] of the account's favorites.
     *
     * When online, each page is fetched from `GET /account/{account_id}/favorite/movies`
     * and cached locally. When offline, pages are served from the local cache.
     */
    fun getFavorites(): Flow<PagingData<Movie>>
}
