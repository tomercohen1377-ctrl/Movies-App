package com.tcohen.moviesapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tcohen.moviesapp.data.local.entity.FavoriteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(favorite: FavoriteEntity)

    /**
     * Bulk insert. Used by [FavoritesPagingSource] to mirror the server's full
     * favorites list into Room on every successful online page fetch, so:
     *  - `MovieRepository.observeIsFavorite(id)` is consistent with what's shown
     *    in the Favorites grid (fixes the Detail-screen FAB on a fresh install).
     *  - the offline cache path is populated for the same set of movies.
     *
     * `REPLACE` strategy means re-syncs are idempotent — re-fetching the same page
     * simply overwrites the same rows.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(favorites: List<FavoriteEntity>)

    @Query("DELETE FROM favorites WHERE id = :movieId")
    suspend fun deleteById(movieId: Int)

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE id = :movieId)")
    fun observeIsFavorite(movieId: Int): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE id = :movieId)")
    suspend fun isFavorite(movieId: Int): Boolean

    @Query("SELECT * FROM favorites ORDER BY savedAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getFavoritesPaged(limit: Int, offset: Int): List<FavoriteEntity>
}
