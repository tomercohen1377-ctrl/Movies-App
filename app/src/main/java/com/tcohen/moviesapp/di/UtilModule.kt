package com.tcohen.moviesapp.di

import android.content.Context
import android.content.SharedPreferences
import com.tcohen.moviesapp.data.user.UserIdPrefs
import com.tcohen.moviesapp.data.user.UserIdProvider
import com.tcohen.moviesapp.util.NetworkMonitor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object UtilModule {

    @Provides
    @Singleton
    fun provideNetworkMonitor(@ApplicationContext context: Context): NetworkMonitor {
        return NetworkMonitor(context)
    }

    @Provides
    @Singleton
    @UserIdPrefs
    fun provideUserIdPrefs(@ApplicationContext context: Context): SharedPreferences {
        return context.getSharedPreferences(
            UserIdProvider.USER_ID_PREFS_FILE,
            Context.MODE_PRIVATE
        )
    }
}
