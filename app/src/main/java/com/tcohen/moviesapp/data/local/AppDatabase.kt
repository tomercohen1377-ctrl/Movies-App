package com.tcohen.moviesapp.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.tcohen.moviesapp.data.local.dao.FavoriteDao
import com.tcohen.moviesapp.data.local.dao.MovieDao
import com.tcohen.moviesapp.data.local.entity.FavoriteEntity
import com.tcohen.moviesapp.data.local.entity.MovieEntity

/**
 * Root Room database for the app.
 *
 * Although [AppDatabase] is never referenced directly in feature code, it is the
 * foundation of the entire local persistence layer. [DatabaseModule] uses it to:
 *  - Create (or open) the SQLite file on disk under [DATABASE_NAME]
 *  - Vend [MovieDao] and [FavoriteDao] as Hilt-injected singletons
 *
 * Tables:
 *  - [MovieEntity]    — paginated movie list cache per [Category], cleared on refresh
 *  - [FavoriteEntity] — user's favorited movies, synced best-effort with the TMDB API
 */
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
