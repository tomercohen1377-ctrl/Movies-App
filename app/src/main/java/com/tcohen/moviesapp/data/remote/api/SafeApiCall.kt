package com.tcohen.moviesapp.data.remote.api

import com.tcohen.moviesapp.data.remote.dto.TmdbErrorBody
import com.tcohen.moviesapp.util.ApiError
import com.tcohen.moviesapp.util.NetworkResult
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

suspend fun <T> safeApiCall(block: suspend () -> T): NetworkResult<T> {
    return try {
        NetworkResult.Success(block())
    } catch (e: HttpException) {
        val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        val tmdbMessage = e.response()?.errorBody()?.string()
            ?.let { body -> runCatching { json.decodeFromString<TmdbErrorBody>(body) }.getOrNull() }
            ?.statusMessage
            ?.ifEmpty { null }
        NetworkResult.Error(
            message = tmdbMessage ?: ApiError.SERVER_ERROR.message,
            httpCode = e.code()
        )
    } catch (_: SocketTimeoutException) {
        NetworkResult.Error(ApiError.TIMEOUT.message)
    } catch (_: UnknownHostException) {
        NetworkResult.Error(ApiError.NO_CONNECTION.message)
    } catch (_: IOException) {
        NetworkResult.Error(ApiError.NO_CONNECTION.message)
    } catch (_: Exception) {
        NetworkResult.Error(ApiError.UNEXPECTED.message)
    }
}
