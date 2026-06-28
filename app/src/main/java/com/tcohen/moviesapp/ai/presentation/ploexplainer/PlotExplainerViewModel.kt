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

/**
 * MVI ViewModel that streams a short plot summary for the currently
 * displayed movie.
 *
 * Architecture:
 *
 * - **[Prompt + state machine ][PlotExplainerPrompt]** live next to the
 *   state contract in `PlotExplainerContract.kt`.
 * - **Cache-first**: on every [PlotExplainerIntent.Explain] we ask
 *   [responseCache] for an existing entry — on hit we jump straight to
 *   [PlotExplainerState.Done] with zero network traffic. On miss we stream
 *   through [llmClient] and write the completed text back to the cache
 *   before transitioning to [PlotExplainerState.Done], so the next click
 *   on the same movie is free.
 * - **Cancellation**: every intent is funnelled through [streamingJob] so
 *   `Cancel` and a fresh `Explain` cleanly tear down the in-flight
 *   collection without leaking the underlying HTTP request. Bound to
 *   [viewModelScope] so navigating away tears down too.
 */
@HiltViewModel
class PlotExplainerViewModel @Inject constructor(
    private val llmClient: LlmClient,
    private val responseCache: LlmResponseCache,
    private val usageRepository: com.tcohen.moviesapp.ai.data.repository.AiUsageRepositoryImpl
) : ViewModel() {

    private val _state = MutableStateFlow<PlotExplainerState>(PlotExplainerState.Idle)
    val state: StateFlow<PlotExplainerState> = _state.asStateFlow()

    /** Holder for the in-flight streaming coroutine; null when at rest. */
    private var streamingJob: Job? = null

    // ── Public API ────────────────────────────────────────────────────────────

    fun processIntent(intent: PlotExplainerIntent) {
        when (intent) {
            is PlotExplainerIntent.Explain -> explain(intent.title, intent.year, intent.runtimeMinutes)
            PlotExplainerIntent.Cancel -> cancel()
            PlotExplainerIntent.Reset -> reset()
        }
    }

    // ── Intent handlers ───────────────────────────────────────────────────────

    private fun explain(title: String, year: Int?, runtimeMinutes: Int?) {
        // Ignore re-entry while a stream is already running; the user is
        // already seeing a bubble populate.
        if (_state.value is PlotExplainerState.Streaming) return

        streamingJob?.cancel()
        streamingJob = viewModelScope.launch {
            val request = buildRequest(title, year, runtimeMinutes)

            // Cache hit ⇒ jump straight to Done, no network round-trip.
            responseCache.get(request, PlotExplainerPrompt.CACHE_VERSION)?.let { cached ->
                _state.value = PlotExplainerState.Done(cached.text)
                return@launch
            }

            // Phase 2: pre-flight daily-cap check. If the user has burned
            // their daily budget, surface a clear error *before* we hit
            // the provider (which would 429 anyway). Skipped when the
            // answer is already cached above.
            when (val cap = usageRepository.guardUnderDailyCap()) {
                is NetworkResult.Error -> {
                    _state.value = PlotExplainerState.Error(cap.message)
                    return@launch
                }
                is NetworkResult.Success -> Unit
            }

            val completion = runStreaming(request) ?: return@launch

            // Phase 2: record usage. Tokens are estimated from text length
            // until Phase 6 wires real token counts from the LLM provider.
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
        // Cancel from a terminal state is a no-op; from Streaming/Error it
        // returns the UI to Idle.
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

    // ── Streaming pipeline ────────────────────────────────────────────────────

    /**
     * Returns the [ChatCompletion] if the stream finished successfully, or
     * `null` on error (caller short-circuits the post-call bookkeeping).
     */
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

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildRequest(title: String, year: Int?, runtimeMinutes: Int?): ChatRequest {
        val yearStr = year?.toString() ?: PlotExplainerPrompt.CACHE_YEAR_UNKNOWN
        val userPrompt = PlotExplainerPrompt.USER_TEMPLATE.format(
            // Placeholder order matches USER_TEMPLATE — count first, then title, then year.
            PlotExplainerPrompt.DEFAULT_SENTENCE_COUNT,
            title,
            yearStr
        )
        return ChatRequest(
            messages = listOf(
                ChatMessage(ChatRole.SYSTEM, PlotExplainerPrompt.SYSTEM),
                ChatMessage(ChatRole.USER, userPrompt)
            ),
            model = "",  // provider fills with its default
            temperature = PlotExplainerPrompt.TEMPERATURE,
            maxTokens = PlotExplainerPrompt.MAX_TOKENS
        )
    }

    override fun onCleared() {
        super.onCleared()
        streamingJob?.cancel()
    }

    // ── Token-count estimation ─────────────────────────────────────────────
    //
    // Phase 2: we don't have real provider-reported token counts yet
    // (those arrive in Phase 6 when we wire `stream_options.include_usage`).
    // Until then, estimate via the standard "4 chars ≈ 1 token" rule.

    /** Char-length / 4 heuristic; rounded up so even tiny responses count ≥1. */
    private fun estimateOutputTokens(text: String): Int =
        (text.length + OUTPUT_TOKEN_OVERHEAD_CHARS) / OUTPUT_TOKEN_CHARS_PER_TOKEN

    /**
     * Input tokens are the rendered prompt's length. We add a system margin
     * to account for chat-format framing overhead (provider-specific but
     * bounded; ~3 tokens is a safe upper bound for our use).
     */
    private fun estimateInputTokens(request: ChatRequest): Int {
        val promptChars = request.messages.sumOf { it.text.length }
        return (promptChars + INPUT_TOKEN_OVERHEAD_CHARS) / INPUT_TOKEN_CHARS_PER_TOKEN
    }

    private companion object {
        /** Stable name for this feature so usage can be grouped in reports. */
        const val LLM_FEATURE_NAME: String = "plot-explainer"

        /** Fallback model name used when the request didn't override it. */
        const val LLM_MODEL_DEFAULT: String = "gemini-2.5-flash"

        const val OUTPUT_TOKEN_CHARS_PER_TOKEN: Int = 4
        const val OUTPUT_TOKEN_OVERHEAD_CHARS: Int = 4

        const val INPUT_TOKEN_CHARS_PER_TOKEN: Int = 4
        const val INPUT_TOKEN_OVERHEAD_CHARS: Int = 12
    }
}
