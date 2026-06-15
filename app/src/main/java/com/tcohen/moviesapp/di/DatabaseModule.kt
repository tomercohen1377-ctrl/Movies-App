package com.tcohen.moviesapp.di

import android.content.Context
import com.tcohen.moviesapp.data.local.DatabaseDriverFactory
import com.tcohen.moviesapp.data.local.LocalMovieDataSource
import com.tcohen.moviesapp.data.local.db.MoviesDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideMoviesDatabase(@ApplicationContext context: Context): MoviesDatabase {
        val driver = DatabaseDriverFactory(context).createDriver()
        return MoviesDatabase(driver)
    }

    @Provides
    @Singleton
    fun provideLocalMovieDataSource(database: MoviesDatabase): LocalMovieDataSource {
        return LocalMovieDataSource(database)
    }
}
