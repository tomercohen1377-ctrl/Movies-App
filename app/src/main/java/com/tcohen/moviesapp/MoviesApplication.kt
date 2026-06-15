package com.tcohen.moviesapp

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MoviesApplication : Application(), SingletonImageLoader.Factory {

    // Injected by Hilt — the singleton ImageLoader configured in NetworkModule
    @Inject lateinit var imageLoader: ImageLoader

    /**
     * Registers our Hilt-provided [ImageLoader] as the global Coil 3 singleton.
     * This means every [coil3.compose.AsyncImage] in the app uses the same
     * OkHttpClient, disk cache, and memory cache automatically.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader = imageLoader
}
