package com.tcohen.moviesapp.ai.presentation.ploexplainer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tcohen.moviesapp.ai.data.local.cache.LlmResponseCache
import com.tcohen.moviesapp.ai.domain.client.LlmClient
import com.tcohen.moviesapp.ai.domain.model.ChatCompletion
import com.tcohen.moviesapp.ai.domain.model.ChatMessage
import com.tcohen.moviesapp.ai.domain.model.ChatRequest
import com.tcohen.moviesapp.ai.domain.model.ChatRole
import com.tcohen.moviesapp.ai.domain.model.FinishReason
import com.tcohen.moviesapp.util.NetworkResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlotExplainerViewModel @Inject constructor(
    private val llmClient: LlmClient,
    private val responseCache: LlmResponseCache,
    private val usageRepository: com.tcohen.moviesapp.ai.data.repository.AiUsageRepositoryImpl
) : ViewModel() {

    private val _state = MutableStateFlow<PlotExplainerState>(PlotExplainerState.Idle)
    val state: StateFlow<PlotExplainerState> = _state.asStateFlow()

    private var streamingJob: Job? = null

    fun processIntent(intent: PlotExplainerIntent) {
        when (intent) {
            is PlotExplainerIntent.Explain -> explain(intent.title, intent.year, intent.runtimeMinutes)
            PlotExplainerIntent.Cancel -> cancel()
            PlotExplainerIntent.Reset -> reset()
        }
    }

    private fun explain(title: String, year: Int?, runtimeMinutes: Int?) {

        if (_state.value is PlotExplainerState.Streaming) return

        streamingJob?.cancel()
        streamingJob = viewModelScope.launch {
            val request = buildRequest(title, year, runtimeMinutes)

            responseCache.get(request, PlotExplainerPrompt.CACHE_VERSION)?.let { cached ->
                _state.value = PlotExplainerState.Done(cached.text)
                return@launch
            }

            when (val cap = usageRepository.guardUnderDailyCap()) {
                is NetworkResult.Error -> {
                    _state.value = PlotExplainerState.Error(cap.message)
                    return@launch
                }
                is NetworkResult.Success -> Unit
            }

            val completion = runStreaming(request) ?: return@launch

            usageRepository.recordUsage(
                model = request.model.ifEmpty { LLM_MODEL_DEFAULT },
                feature = LLM_FEATURE_NAME,
                inputTokens = estimateInputTokens(request),
                outputTokens = estimateOutputTokens(completion.text)
            )
        }
    }

    private fun cancel() {
        streamingJob?.cancel()
        streamingJob = null

        if (_state.value !is PlotExplainerState.Idle &&
            _state.value !is PlotExplainerState.Done) {
            _state.value = PlotExplainerState.Idle
        }
    }

    private fun reset() {
        streamingJob?.cancel()
        streamingJob = null
        _state.value = PlotExplainerState.Idle
    }

    private suspend fun runStreaming(request: ChatRequest): ChatCompletion? {
        _state.value = PlotExplainerState.Streaming("")
        val collected = StringBuilder()
        var errored: NetworkResult.Error? = null

        llmClient.stream(request).collect { result ->
            when (result) {
                is NetworkResult.Success -> {
                    collected.append(result.data)
                    _state.value = PlotExplainerState.Streaming(collected.toString())
                }
                is NetworkResult.Error -> errored = result
            }
        }

        if (errored != null) {
            _state.value = PlotExplainerState.Error(errored.message)
            return null
        }

        val finalText = collected.toString()
        val completion = ChatCompletion(
            text = finalText,
            toolCalls = emptyList(),
            finishReason = FinishReason.STOP
        )
        responseCache.put(
            request = request,
            promptVersion = PlotExplainerPrompt.CACHE_VERSION,
            completion = completion
        )
        _state.value = PlotExplainerState.Done(finalText)
        return completion
    }

    private fun buildRequest(title: String, year: Int?, runtimeMinutes: Int?): ChatRequest {
        val yearStr = year?.toString() ?: PlotExplainerPrompt.CACHE_YEAR_UNKNOWN
        val userPrompt = PlotExplainerPrompt.USER_TEMPLATE.format(

            PlotExplainerPrompt.DEFAULT_SENTENCE_COUNT,
            title,
            yearStr
        )
        return ChatRequest(
            messages = listOf(
                ChatMessage(ChatRole.SYSTEM, PlotExplainerPrompt.SYSTEM),
                ChatMessage(ChatRole.USER, userPrompt)
            ),
            model = "",
            temperature = PlotExplainerPrompt.TEMPERATURE,
            maxTokens = PlotExplainerPrompt.MAX_TOKENS
        )
    }

    override fun onCleared() {
        super.onCleared()
        streamingJob?.cancel()
    }

    private fun estimateOutputTokens(text: String): Int =
        (text.length + OUTPUT_TOKEN_OVERHEAD_CHARS) / OUTPUT_TOKEN_CHARS_PER_TOKEN

    private fun estimateInputTokens(request: ChatRequest): Int {
        val promptChars = request.messages.sumOf { it.text.length }
        return (promptChars + INPUT_TOKEN_OVERHEAD_CHARS) / INPUT_TOKEN_CHARS_PER_TOKEN
    }

    private companion object {

        const val LLM_FEATURE_NAME: String = "plot-explainer"

        const val LLM_MODEL_DEFAULT: String = "gemini-2.5-flash"

        const val OUTPUT_TOKEN_CHARS_PER_TOKEN: Int = 4
        const val OUTPUT_TOKEN_OVERHEAD_CHARS: Int = 4

        const val INPUT_TOKEN_CHARS_PER_TOKEN: Int = 4
        const val INPUT_TOKEN_OVERHEAD_CHARS: Int = 12
    }
}
