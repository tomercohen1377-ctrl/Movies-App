package com.tcohen.moviesapp.ai.data.remote.client

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.tcohen.moviesapp.ai.data.local.cache.LlmResponseCache
import com.tcohen.moviesapp.ai.data.remote.api.OpenAiCompatibleApi
import com.tcohen.moviesapp.ai.data.remote.dto.ChatCompletionChunkDto
import com.tcohen.moviesapp.ai.data.remote.dto.ChatCompletionRequestDto
import com.tcohen.moviesapp.ai.data.remote.dto.ChatCompletionResponseDto
import com.tcohen.moviesapp.ai.data.remote.dto.toDto
import com.tcohen.moviesapp.ai.data.remote.interceptor.LlmAuthInterceptor
import com.tcohen.moviesapp.ai.data.remote.streaming.SseStreamParser
import com.tcohen.moviesapp.ai.data.safe.safeLlmCall
import com.tcohen.moviesapp.ai.domain.client.LlmClient
import com.tcohen.moviesapp.ai.domain.model.ChatCompletion
import com.tcohen.moviesapp.ai.domain.model.ChatRequest
import com.tcohen.moviesapp.ai.domain.model.FinishReason
import com.tcohen.moviesapp.ai.domain.model.ToolInvocation
import com.tcohen.moviesapp.util.ApiError
import com.tcohen.moviesapp.util.NetworkResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class OpenAiCompatibleLlmClient @Inject constructor(
    private val authInterceptor: LlmAuthInterceptor,
    private val json: Json,
    @Named("llmBaseUrl") private val baseUrl: String,
    @Named("llmModel") private val defaultModel: String,
    private val cache: LlmResponseCache
) : LlmClient {

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .build()
    }

    private val api: OpenAiCompatibleApi by lazy {
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(httpClient)
            .addConverterFactory(json.asConverterFactory(JSON_MEDIA_TYPE))
            .build()
            .create(OpenAiCompatibleApi::class.java)
    }

    override suspend fun complete(request: ChatRequest): NetworkResult<ChatCompletion> {

        val versionedRequest = request.copy(model = request.model.ifEmpty { defaultModel })
        cache.get(versionedRequest, PROMPT_VERSION_DEFAULT)?.let { cached ->
            return NetworkResult.Success(cached)
        }

        val result = safeLlmCall {
            api.createChatCompletion(versionedRequest.toDto(stream = false)).toDomain()
        }
        if (result is NetworkResult.Success) {
            cache.put(versionedRequest, PROMPT_VERSION_DEFAULT, result.data)
        }
        return result
    }

    override fun stream(request: ChatRequest): Flow<NetworkResult<String>> = flow {
        val versionedRequest = request.copy(model = request.model.ifEmpty { defaultModel })
        val httpRequest = buildStreamingRequest(versionedRequest)
        val call: Call = httpClient.newCall(httpRequest)

        val cancellationHook = currentCoroutineContext()[Job]
            ?.invokeOnCompletion { if (it != null) call.cancel() }

        try {
            val response = call.execute()
            if (!response.isSuccessful) {
                logErrorResponse(response, "stream")
                emit(streamingErrorMessage(response.code))
                return@flow
            }
            val body = response.body ?: run {
                emit(NetworkResult.Error(ApiError.LLM_UNAVAILABLE.message))
                return@flow
            }

            var chunkCount = 0
            var firstDelta: String? = null
            var lastFinishReason: String? = null
            var emptyContentCount = 0

            val parser = SseStreamParser { payload ->
                chunkCount++
                val parsed = parseDeltaForSummary(payload)
                when {
                    parsed.delta != null && firstDelta == null -> firstDelta = parsed.delta.take(SSE_LOG_PAYLOAD_LIMIT)
                    parsed.delta == null -> emptyContentCount++
                    else -> Unit
                }
                if (parsed.finishReason != null) lastFinishReason = parsed.finishReason
                parsed.delta
            }
            parser.stream(body)
                .map { delta -> NetworkResult.Success(delta) }
                .collect { emit(it) }

            android.util.Log.i(
                LOG_TAG_LLM_HTTP,
                "[stream] chunks=$chunkCount emptyContent=$emptyContentCount " +
                    "finishReason=${lastFinishReason ?: "(none)"} " +
                    "firstDelta=\"$firstDelta\""
            )
        } catch (_: java.io.IOException) {
            emit(NetworkResult.Error(ApiError.NO_CONNECTION.message))
        } finally {
            cancellationHook?.dispose()
            runCatching { call.cancel() }
        }
    }.flowOn(Dispatchers.IO)

    private fun logErrorResponse(response: okhttp3.Response, source: String) {
        val msg = response.message
        val body = runCatching { response.peekBody(PEEK_BODY_BYTE_LIMIT).string() }.getOrNull()
        android.util.Log.w(
            LOG_TAG_LLM_HTTP,
            "<-- ${response.code} $msg [$source] ${body?.take(PEEK_BODY_LOG_LIMIT) ?: "(no body)"}"
        )
    }

    private fun buildStreamingRequest(request: ChatRequest): Request {
        val dto = request.toDto(stream = true)
        val bodyJson = json.encodeToString(ChatCompletionRequestDto.serializer(), dto)
        val httpBody = bodyJson.toRequestBody(JSON_MEDIA_TYPE)
        val url = baseUrl.trimEnd(URL_PATH_SEPARATOR) + CHAT_COMPLETIONS_PATH
        return Request.Builder()
            .url(url)
            .header(AUTHORIZATION_HEADER, "$BEARER_TOKEN_PREFIX${authInterceptor.apiKey}")
            .header(ACCEPT_HEADER, EVENT_STREAM_MEDIA_TYPE.toString())
            .post(httpBody)
            .build()
    }

    private fun parseDelta(payload: String): String? {
        val chunk = runCatching {
            json.decodeFromString(ChatCompletionChunkDto.serializer(), payload)
        }.getOrNull() ?: return null

        val deltaContent = chunk.choices.firstOrNull()?.delta?.content ?: return null
        return deltaContent.takeIf { it.isNotEmpty() }
    }

    private fun parseDeltaForSummary(payload: String): ParsedChunk {
        val chunk = runCatching {
            json.decodeFromString(ChatCompletionChunkDto.serializer(), payload)
        }.getOrNull() ?: return ParsedChunk(null, null)

        val delta = chunk.choices.firstOrNull()?.delta?.content?.takeIf { it.isNotEmpty() }
        val finishReason = chunk.choices.firstOrNull()?.finishReason
        return ParsedChunk(delta, finishReason)
    }

    private data class ParsedChunk(val delta: String?, val finishReason: String?)

    private fun streamingErrorMessage(code: Int): NetworkResult.Error = when (code) {
        HTTP_STATUS_UNAUTHORIZED, HTTP_STATUS_FORBIDDEN ->
            NetworkResult.Error(ApiError.UNAUTHORIZED.message, httpCode = code)
        HTTP_STATUS_RATE_LIMITED ->
            NetworkResult.Error(ApiError.RATE_LIMITED.message, httpCode = code)
        in HTTP_STATUS_SERVER_ERROR_MIN..HTTP_STATUS_SERVER_ERROR_MAX ->
            NetworkResult.Error(ApiError.LLM_UNAVAILABLE.message, httpCode = code)
        else ->
            NetworkResult.Error(ApiError.LLM_UNAVAILABLE.message, httpCode = code)
    }

    companion object {

        private const val CONTENT_TYPE_JSON: String = "application/json"
        private const val CONTENT_TYPE_EVENT_STREAM: String = "text/event-stream"
        private const val HEADER_AUTHORIZATION: String = "Authorization"
        private const val HEADER_ACCEPT: String = "Accept"

        const val LOG_TAG_LLM_HTTP: String = "LLM_HTTP"

        private const val PEEK_BODY_BYTE_LIMIT: Long = 8L * 1024

        private const val PEEK_BODY_LOG_LIMIT: Int = 500

        private const val SSE_LOG_PAYLOAD_LIMIT: Int = 80

        private val JSON_MEDIA_TYPE: MediaType = CONTENT_TYPE_JSON.toMediaType()
        private val EVENT_STREAM_MEDIA_TYPE: MediaType = CONTENT_TYPE_EVENT_STREAM.toMediaType()

        const val AUTHORIZATION_HEADER: String = HEADER_AUTHORIZATION

        const val ACCEPT_HEADER: String = HEADER_ACCEPT

        const val BEARER_TOKEN_PREFIX: String = "Bearer "
        const val CHAT_COMPLETIONS_PATH: String = "/chat/completions"
        const val URL_PATH_SEPARATOR: Char = '/'

        const val HTTP_STATUS_UNAUTHORIZED: Int = 401
        const val HTTP_STATUS_FORBIDDEN: Int = 403
        const val HTTP_STATUS_RATE_LIMITED: Int = 429
        const val HTTP_STATUS_SERVER_ERROR_MIN: Int = 500
        const val HTTP_STATUS_SERVER_ERROR_MAX: Int = 599

        const val FINISH_REASON_STOP: String = "stop"
        const val FINISH_REASON_TOOL_CALLS: String = "tool_calls"
        const val FINISH_REASON_LENGTH: String = "length"
        const val FINISH_REASON_CONTENT_FILTER: String = "content_filter"

        const val PROMPT_VERSION_DEFAULT: String = "phase0-v1"
    }
}

private fun ChatCompletionResponseDto.toDomain(): ChatCompletion {
    val choice = choices.firstOrNull()
        ?: error("Chat completion response contained no choices")
    val message = choice.message
    val text = message.content.orEmpty()
    val toolCalls = (message.toolCalls ?: emptyList()).map { it.toDomain() }
    val finish = when (choice.finishReason) {
        OpenAiCompatibleLlmClient.FINISH_REASON_STOP -> FinishReason.STOP
        OpenAiCompatibleLlmClient.FINISH_REASON_TOOL_CALLS -> FinishReason.TOOL_CALLS
        OpenAiCompatibleLlmClient.FINISH_REASON_LENGTH -> FinishReason.LENGTH
        OpenAiCompatibleLlmClient.FINISH_REASON_CONTENT_FILTER -> FinishReason.CONTENT_FILTER
        null -> FinishReason.STOP
        else -> FinishReason.UNEXPECTED
    }
    return ChatCompletion(text = text, toolCalls = toolCalls, finishReason = finish)
}

private fun com.tcohen.moviesapp.ai.data.remote.dto.ToolCallDto.toDomain(): ToolInvocation =
    ToolInvocation(id = id, toolName = function.name, rawArgs = function.arguments)
