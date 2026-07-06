package com.tcohen.moviesapp.util

sealed class NetworkResult<out T> {
    data class Success<out T>(val data: T) : NetworkResult<T>()
    data class Error(val message: String, val httpCode: Int = 0) : NetworkResult<Nothing>()
}
