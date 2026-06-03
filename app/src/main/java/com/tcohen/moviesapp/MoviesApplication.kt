package com.tcohen.moviesapp

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MoviesApplication : Application(), ImageLoaderFactory {

    // Injected by Hilt — the singleton ImageLoader configured in NetworkModule
    @Inject lateinit var imageLoader: ImageLoader

    /**
     * Registers our Hilt-provided [ImageLoader] as the global Coil singleton.
     * This means every [coil.compose.AsyncImage] in the app uses the same
     * OkHttpClient, disk cache, and memory cache automatically.
     */
    override fun newImageLoader(): ImageLoader = imageLoader
}
