package com.tcohen.moviesapp.ai.domain.client

import com.tcohen.moviesapp.ai.domain.model.ChatCompletion
import com.tcohen.moviesapp.ai.domain.model.ChatRequest
import com.tcohen.moviesapp.util.NetworkResult
import kotlinx.coroutines.flow.Flow

interface LlmClient {
    suspend fun complete(request: ChatRequest): NetworkResult<ChatCompletion>
    fun stream(request: ChatRequest): Flow<NetworkResult<String>>
}
