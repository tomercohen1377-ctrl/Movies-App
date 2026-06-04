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

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE id = :movieId)")
    fun observeIsFavorite(movieId: Int): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE id = :movieId)")
    suspend fun isFavorite(movieId: Int): Boolean

    @Query("SELECT * FROM favorites ORDER BY savedAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getFavoritesPaged(limit: Int, offset: Int): List<FavoriteEntity>
}
