package com.tcohen.moviesapp.data.local

import app.cash.sqldelight.db.SqlDriver

/**
 * Platform-specific factory that creates the [SqlDriver] used by [MoviesDatabase].
 *
 * Each platform provides its own `actual` implementation:
 * - **Android** (`androidMain`): [AndroidSqliteDriver]
 * - Future iOS / Desktop targets will add their own `actual` classes here.
 */
expect class DatabaseDriverFactory {
    fun createDriver(): SqlDriver
}
