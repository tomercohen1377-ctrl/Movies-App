package com.tcohen.moviesapp.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.tcohen.moviesapp.ai.data.local.dao.AiUsageDao
import com.tcohen.moviesapp.ai.data.local.entity.AiUsageEntity
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
 *  - Vend [MovieDao], [FavoriteDao], and [AiUsageDao] as Hilt-injected singletons
 *
 * Tables:
 *  - [MovieEntity]    — paginated movie list cache per [com.tcohen.moviesapp.domain.model.Category], cleared on refresh
 *  - [FavoriteEntity] — user's favorited movies, synced best-effort with the TMDB API
 *  - [AiUsageEntity]  — per-call LLM token ledger (Phase 2+) — drives daily-cap enforcement
 *
 * Version history:
 *  - **v1** — TMDB project launch. movies + favorites tables.
 *  - **v2** — Phase 2. Adds ai_usage table; existing tables are NOT modified.
 */
@Database(
    entities = [MovieEntity::class, FavoriteEntity::class, AiUsageEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun movieDao(): MovieDao
    abstract fun favoriteDao(): FavoriteDao

    /** Phase 2: token-usage ledger. */
    abstract fun aiUsageDao(): AiUsageDao

    companion object {
        const val DATABASE_NAME = "movies_db"

        /**
         * v1 → v2: creates the ai_usage ledger + index on timestamp.
         * Does NOT touch the existing movies / favorites tables, so
         * user favorites survive the upgrade cleanly.
         */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS ai_usage (
                        id              INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        model           TEXT NOT NULL,
                        feature         TEXT NOT NULL,
                        input_tokens    INTEGER NOT NULL,
                        output_tokens   INTEGER NOT NULL,
                        timestamp       INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_ai_usage_timestamp ON ai_usage(timestamp)"
                )
            }
        }
    }
}
