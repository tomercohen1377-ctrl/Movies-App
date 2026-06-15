package com.tcohen.moviesapp

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.tcohen.moviesapp.di.androidSharedModule
import com.tcohen.moviesapp.di.appModule
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class MoviesApplication : Application(), SingletonImageLoader.Factory {

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger(if (BuildConfig.DEBUG) Level.DEBUG else Level.ERROR)
            androidContext(this@MoviesApplication)
            modules(androidSharedModule, appModule)
        }
    }

    /**
     * Returns the Koin-singleton [ImageLoader] as the global Coil 3 image loader.
     * Every [coil3.compose.AsyncImage] in the app shares the same OkHttpClient,
     * disk cache, and memory cache automatically.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader = get()
}
