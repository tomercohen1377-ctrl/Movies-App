package com.tcohen.moviesapp

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.tcohen.moviesapp.data.auth.AuthRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class MoviesApplication : Application(), ImageLoaderFactory {

    @Inject lateinit var imageLoader: ImageLoader
    @Inject lateinit var authRepository: AuthRepository

    private val appScope by lazy {
        CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    override fun onCreate() {
        super.onCreate()
        bootstrapAuth()
    }

    private fun bootstrapAuth() {
        appScope.launch {
            runCatching {
                authRepository.ensureValidToken()
            }
        }
    }

    override fun newImageLoader(): ImageLoader = imageLoader
}
