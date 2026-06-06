package com.tcohen.moviesapp

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

/**
 * Custom test runner that replaces [MoviesApplication] with [HiltTestApplication]
 * so that `@HiltAndroidTest` journey tests can inject real ViewModels with
 * fake repository bindings — without touching any real network or database.
 *
 * All existing component tests (which don't use @HiltAndroidTest) continue to
 * work unchanged because HiltTestApplication is backward-compatible with the
 * standard AndroidJUnitRunner.
 */
class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(
        cl: ClassLoader?,
        name: String?,
        context: Context?
    ): Application = super.newApplication(cl, HiltTestApplication::class.java.name, context)
}
