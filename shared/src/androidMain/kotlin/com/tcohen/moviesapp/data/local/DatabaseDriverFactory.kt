package com.tcohen.moviesapp.data.local

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.tcohen.moviesapp.data.local.db.MoviesDatabase

/**
 * Android implementation of [DatabaseDriverFactory].
 *
 * Uses [AndroidSqliteDriver] which opens (or creates) the SQLite file at the given name.
 */
actual class DatabaseDriverFactory(private val context: Context) {
    actual fun createDriver(): SqlDriver =
        AndroidSqliteDriver(MoviesDatabase.Schema, context, "movies_db")
}
