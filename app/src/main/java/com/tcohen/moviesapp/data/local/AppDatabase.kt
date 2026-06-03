package com.tcohen.moviesapp.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.tcohen.moviesapp.data.local.dao.FavoriteDao
import com.tcohen.moviesapp.data.local.dao.MovieDao
import com.tcohen.moviesapp.data.local.entity.FavoriteEntity
import com.tcohen.moviesapp.data.local.entity.MovieEntity

@Database(
    entities = [MovieEntity::class, FavoriteEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun movieDao(): MovieDao
    abstract fun favoriteDao(): FavoriteDao

    companion object {
        const val DATABASE_NAME = "movies_db"
    }
}
