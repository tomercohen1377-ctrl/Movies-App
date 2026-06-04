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

    @Query("DELETE FROM favorites WHERE id = :movieId")
    suspend fun deleteById(movieId: Int)

    @Query("SELECT * FROM favorites ORDER BY savedAt DESC")
    fun observeAll(): Flow<List<FavoriteEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE id = :movieId)")
    fun isFavorite(movieId: Int): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE id = :movieId)")
    suspend fun isFavoriteOnce(movieId: Int): Boolean

    /** Returns all stored favorite movie IDs (used for server-sync diffing). */
    @Query("SELECT id FROM favorites")
    suspend fun getAllIds(): List<Int>

    /**
     * Returns a page of favorites ordered by `savedAt` descending.
     *
     * Fetching [limit] + 1 items lets the caller check if a next page exists
     * without a separate COUNT query.
     */
    @Query("SELECT * FROM favorites ORDER BY savedAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getFavoritesPaged(limit: Int, offset: Int): List<FavoriteEntity>
}
