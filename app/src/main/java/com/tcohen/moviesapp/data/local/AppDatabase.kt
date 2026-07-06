package com.tcohen.moviesapp.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.tcohen.moviesapp.ai.data.local.dao.AiUsageDao
import com.tcohen.moviesapp.ai.data.local.entity.AiUsageEntity
import com.tcohen.moviesapp.data.local.dao.MovieDao
import com.tcohen.moviesapp.data.local.entity.MovieEntity

@Database(
    entities = [MovieEntity::class, AiUsageEntity::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun movieDao(): MovieDao

    abstract fun aiUsageDao(): AiUsageDao

    companion object {
        const val DATABASE_NAME = "movies_db"

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
