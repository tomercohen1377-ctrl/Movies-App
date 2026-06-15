package com.tcohen.moviesapp.util

/**
 * Returns the current time in milliseconds since the Unix epoch.
 *
 * Implemented per-platform:
 * - **Android/JVM**: `System.currentTimeMillis()`
 * - **iOS**: `NSDate().timeIntervalSince1970 * 1000`
 */
expect fun currentTimeMillis(): Long
