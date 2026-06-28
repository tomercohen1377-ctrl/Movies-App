package com.tcohen.moviesapp.ai.domain.client

import com.tcohen.moviesapp.ai.domain.model.ChatCompletion
import com.tcohen.moviesapp.ai.domain.model.ChatRequest
import com.tcohen.moviesapp.util.NetworkResult
import kotlinx.coroutines.flow.Flow

/**
 * Transport-agnostic interface the rest of the app uses to talk to "an LLM".
 *
 * Two modes:
 *
 * - [complete] — non-streaming; returns the full completion synchronously. Useful
 *   for short replies (e.g. plot explainer) and for testability. Implementations
 *   read through the response cache; a cached completion skips the network.
 * - [stream] — token-by-token flow of the assistant's text. Each emission is a
 *   [NetworkResult] so a mid-stream error can propagate through the same channel
 *   instead of firing a separate event. The [Flow] completes when the stream
 *   is finished (provider sent `[DONE]` or `finish_reason` is set) or fails with
 *   the last error.
 *
 * Cancellation: callers collect on [kotlinx.coroutines.CoroutineScope] tied to their
 * own scope (e.g. `viewModelScope`). Both modes honour structured cancellation —
 * the underlying HTTP call is aborted by OkHttp / Retrofit on scope cancel.
 *
 * Implementations:
 *
 * - `OpenAiCompatibleLlmClient` — hosted provider (default; Gemini free tier)
 *   via the OpenAI-compatible schema, swap base URL/key for OpenAI/Groq/etc.
 * - `MediaPipeLlmClient` — on-device (added in Phase 5).
 */
interface LlmClient {
    suspend fun complete(request: ChatRequest): NetworkResult<ChatCompletion>
    fun stream(request: ChatRequest): Flow<NetworkResult<String>>
}
