package com.tcohen.moviesapp.ai.presentation.ploexplainer

sealed interface PlotExplainerState {
    data object Idle : PlotExplainerState
    data class Streaming(val text: String) : PlotExplainerState
    data class Done(val text: String) : PlotExplainerState
    data class Error(val message: String) : PlotExplainerState
}

sealed interface PlotExplainerIntent {
    data class Explain(
        val title: String,
        val year: Int?,
        val runtimeMinutes: Int?,
    ) : PlotExplainerIntent

    data object Cancel : PlotExplainerIntent
    data object Reset : PlotExplainerIntent
}

object PlotExplainerPrompt {

    const val SYSTEM: String =
        "You are a concise movie plot narrator. Avoid spoilers. Use plain English."

    const val USER_TEMPLATE: String =
        "Write a concrete %d-sentence plot summary of the movie \"%s\" " +
            "(released %s). Each sentence must be at least 12 words long. " +
            "Do not cut off — finish the summary before responding."

    const val CACHE_VERSION: String = "plot-v1"

    const val TEMPERATURE: Float = 0.4f

    const val MAX_TOKENS: Int = 1000

    const val DEFAULT_SENTENCE_COUNT: Int = 3

    const val CACHE_YEAR_UNKNOWN: String = "year unknown"
}
