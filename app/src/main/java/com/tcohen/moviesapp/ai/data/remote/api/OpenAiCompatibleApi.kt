package com.tcohen.moviesapp.ai.data.remote.api

import com.tcohen.moviesapp.ai.data.remote.dto.ChatCompletionRequestDto
import com.tcohen.moviesapp.ai.data.remote.dto.ChatCompletionResponseDto
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * Retrofit interface for the subset of OpenAI-compatible chat-completion endpoints
 * we actually call.
 *
 * The streaming variant is intentionally absent here — streaming is implemented
 * directly against OkHttp in `OpenAiCompatibleLlmClient` because the SSE wire
 * format and per-chunk parsing has different correctness requirements than
 * a Retrofit `Response<ResponseBody>` return, and we want full control of
 * chunk boundaries and cancellation.
 */
internal interface OpenAiCompatibleApi {

    /**
     * Non-streaming completion. Returns the fully-formed assistant reply.
     *
     * Authentication is handled by [com.tcohen.moviesapp.ai.data.remote.interceptor.LlmAuthInterceptor]
     * on the dedicated LLM `OkHttpClient`; we do not pass it as a header here.
     */
    @POST("chat/completions")
    suspend fun createChatCompletion(
        @Body request: ChatCompletionRequestDto
    ): ChatCompletionResponseDto

    /**
     * Reserved for future embed-aware calls. Not used in Phase 0; here so the
     * interface spotlights a stable base URL when Phase 3 adds embedding.
     */
    @Suppress("unused")
    @POST("embeddings")
    suspend fun createEmbeddings(
        @Body body: EmbeddingsRequestDto,
        @Header("Accept") accept: String = "application/json"
    ): EmbeddingsResponseDto
}

/** Placeholder embed DTOs kept here so Phase 3 has a one-line landing pad. */
internal data class EmbeddingsRequestDto(val model: String, val input: List<String>)
internal data class EmbeddingsResponseDto(val data: List<EmbeddingDatumDto>)
internal data class EmbeddingDatumDto(val embedding: List<Float>, val index: Int)
