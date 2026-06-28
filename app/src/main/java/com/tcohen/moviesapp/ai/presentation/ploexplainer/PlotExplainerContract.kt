package com.tcohen.moviesapp.ai.presentation.ploexplainer

/**
 * Sealed UI state for the **plot explainer** sub-screen embedded inside
 * `MovieDetailScreen`.
 *
 * Mirrors the pattern used by `MovieDetailUiState` — four mutually-exclusive
 * branches so a `when (state)` in the composable is exhaustive and the
 * compiler enforces that no UI is rendered in the wrong state.
 *
 * - [Idle] — initial state and after a [PlotExplainerIntent.Reset] / [PlotExplainerIntent.Cancel].
 *   UI shows the *Explain plot* trigger.
 * - [Streaming] — model is producing tokens; [text] is the accumulated response
 *   so far (so the bubble grows visibly).
 * - [Done] — terminal success; [text] is the final cached plot summary.
 * - [Error] — non-recoverable failure (auth, rate limit, etc.); UI shows the
 *   error message plus a *Retry* button that re-issues the same request.
 */
sealed interface PlotExplainerState {
    data object Idle : PlotExplainerState
    data class Streaming(val text: String) : PlotExplainerState
    data class Done(val text: String) : PlotExplainerState
    data class Error(val message: String) : PlotExplainerState
}

/**
 * User-initiated actions dispatched via
 * `viewModel.processIntent(...)` from `PlotExplainerSection`.
 *
 * - [Explain] carries the movie metadata needed to build the prompt —
 *   the section gets these from the surrounding `MovieDetailUiState.Success`.
 * - [Cancel] aborts the in-flight `stream()` collection and returns to [PlotExplainerState.Idle].
 * - [Reset] is the equivalent of "clear and start over" — used from the
 *   terminal [PlotExplainerState.Done] branch's "Explain another" affordance.
 */
sealed interface PlotExplainerIntent {
    data class Explain(
        val title: String,
        val year: Int?,
        val runtimeMinutes: Int?,
    ) : PlotExplainerIntent

    data object Cancel : PlotExplainerIntent
    data object Reset : PlotExplainerIntent
}

/**
 * Static prompt template and tunables for the plot-explainer feature.
 *
 * Co-located with the contract because the prompt and the state shape evolve
 * together — and because bumping [CACHE_VERSION] is the only knob that
 * invalidates every cached completion for this feature (see
 * `docs/LLM_SETUP.md`).
 */
object PlotExplainerPrompt {

    /** System prompt — sets tone, language, and the no-spoiler constraint. */
    const val SYSTEM: String =
        "You are a concise movie plot narrator. Avoid spoilers. Use plain English."

    /**
     * User template — three printf-style placeholders:
     *  1. movie title (quoted)
     *  2. year string — `[CACHE_YEAR_UNKNOWN]` when no year is known
     *  3. target sentence count
     *
     * Explicit "do not cut off" + minimum sentence length defends against
     * the provider stopping mid-stream after just a few tokens.
     */
    const val USER_TEMPLATE: String =
        "Write a concrete %d-sentence plot summary of the movie \"%s\" " +
            "(released %s). Each sentence must be at least 12 words long. " +
            "Do not cut off — finish the summary before responding."

    /** Cache bucketing version. Bump on semantic prompt change. */
    const val CACHE_VERSION: String = "plot-v1"

    /** Low temperature — keeps the response deterministic and on-topic. */
    const val TEMPERATURE: Float = 0.4f

    /**
     * Hard cap on generated tokens for a single plot summary.
     *
     * Gemini's OpenAI-compat layer interprets this aggressively — pushing
     * the model to fully complete 3+ sentences requires headroom above the
     * ~50-80 tokens a summary actually needs.
     */
    const val MAX_TOKENS: Int = 1000

    /** Default number of sentences we ask the model for. */
    const val DEFAULT_SENTENCE_COUNT: Int = 3

    /** Substituted into the year placeholder when the movie has no year. */
    const val CACHE_YEAR_UNKNOWN: String = "year unknown"
}
