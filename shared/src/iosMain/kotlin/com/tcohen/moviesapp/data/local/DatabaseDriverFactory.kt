package com.tcohen.moviesapp.data.local

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.tcohen.moviesapp.data.local.db.MoviesDatabase

/**
 * iOS actual implementation of [DatabaseDriverFactory].
 *
 * Uses SQLDelight's [NativeSqliteDriver] which writes the SQLite database
 * to the app's standard Documents directory on iOS/macOS.
 */
actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver {
        return NativeSqliteDriver(MoviesDatabase.Schema, "movies.db")
    }
}
