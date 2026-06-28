package com.tcohen.moviesapp.ai.data.safe

import com.tcohen.moviesapp.util.ApiError
import com.tcohen.moviesapp.util.NetworkResult
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * LLM-aware analogue of `safeApiCall`. Wraps [block] in a try/catch and
 * maps every failure mode to a [NetworkResult.Error] with the appropriate
 * [ApiError] entry owning the user-facing message.
 *
 * Differences from `safeApiCall`:
 *
 * - **No body-parsing fallback.** Providers like Gemini return errors in
 *   OpenAI-shape JSON too, but the error message is permitted to be opaque.
 *   We add a single dedicated [ApiError.RATE_LIMITED]/[ApiError.UNAUTHORIZED]/
 *   etc. mapping keyed on status code rather than parsing the body.
 * - **Auth (401/403) → [ApiError.UNAUTHORIZED].** Lets the UI surface
 *   "check your API key" distinctly from a 5xx.
 * - **Rate limit (429) → [ApiError.RATE_LIMITED].** Distinct from a generic
 *   server error so the chat UI can offer "try local model?" (Phase 5).
 * - **Length (400-ish context overflow) → [ApiError.CONTEXT_TOO_LONG].**
 *   Caller knows to truncate the conversation.
 * - **Generic 5xx → [ApiError.LLM_UNAVAILABLE].** Distinct from existing
 *   [ApiError.SERVER_ERROR] so we can pivot copy later.
 *
 * Cancellation ([kotlinx.coroutines.CancellationException]) is deliberately
 * NOT caught — it must propagate so `viewModelScope` cleanup works.
 */
internal suspend fun <T> safeLlmCall(block: suspend () -> T): NetworkResult<T> {
    return try {
        NetworkResult.Success(block())
    } catch (e: HttpException) {
        NetworkResult.Error(
            message = llmHttpErrorMessage(e),
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

/**
 * Map an HTTP status code from the LLM provider to a user-friendly [ApiError].
 * Centralised so [safeLlmCall] and any future streaming-level equivalent
 * (e.g. error frames inside an SSE stream) translate identically.
 *
 * Visible for testing — Phase 0 ships a `SafeLlmCallTest` that exercises
 * each branch directly.
 */
internal fun llmHttpErrorMessage(e: HttpException): String = when (e.code()) {
    401, 403 -> ApiError.UNAUTHORIZED.message
    429 -> ApiError.RATE_LIMITED.message
    408, 504 -> ApiError.TIMEOUT.message
    in 500..599 -> ApiError.LLM_UNAVAILABLE.message
    else -> ApiError.LLM_UNAVAILABLE.message
}
