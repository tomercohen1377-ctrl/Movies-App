package com.tcohen.moviesapp.di

import android.content.Context
import androidx.room.Room
import com.tcohen.moviesapp.data.local.AppDatabase
import com.tcohen.moviesapp.ai.data.local.dao.AiUsageDao
import com.tcohen.moviesapp.data.local.dao.MovieDao
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
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        )
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideMovieDao(database: AppDatabase): MovieDao = database.movieDao()

    @Provides
    fun provideAiUsageDao(database: AppDatabase): AiUsageDao = database.aiUsageDao()
}
