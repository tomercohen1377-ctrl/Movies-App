package com.tcohen.moviesapp.ai.data.remote.api

import com.tcohen.moviesapp.ai.data.remote.dto.ChatCompletionRequestDto
import com.tcohen.moviesapp.ai.data.remote.dto.ChatCompletionResponseDto
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

internal interface OpenAiCompatibleApi {

    @POST("chat/completions")
    suspend fun createChatCompletion(
        @Body request: ChatCompletionRequestDto
    ): ChatCompletionResponseDto

    @Suppress("unused")
    @POST("embeddings")
    suspend fun createEmbeddings(
        @Body body: EmbeddingsRequestDto,
        @Header("Accept") accept: String = "application/json"
    ): EmbeddingsResponseDto
}

internal data class EmbeddingsRequestDto(val model: String, val input: List<String>)
internal data class EmbeddingsResponseDto(val data: List<EmbeddingDatumDto>)
internal data class EmbeddingDatumDto(val embedding: List<Float>, val index: Int)
