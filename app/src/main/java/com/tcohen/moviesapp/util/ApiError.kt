package com.tcohen.moviesapp.util

/**
 * Fallback error messages for cases where the server cannot provide one
 * (connectivity failures, unknown HTTP codes, unexpected exceptions).
 *
 * The first four entries were originally added for the TMDB API. Phase 0
 * adds four LLM-specific entries that share the same ownership rules:
 * every entry owns its user-facing string in one place so the UI layer
 * only ever reads `ApiError.X.message`.
 */
enum class ApiError(val message: String) {
    // ── TMDB / generic — Phase 0 stable ───────────────────────────────────────
    SERVER_ERROR("Server error"),
    NO_CONNECTION("No internet connection"),
    TIMEOUT("Connection timed out — check your internet and retry"),
    UNEXPECTED("An unexpected error occurred"),

    // ── LLM-specific — added in Phase 0 of the AI implementation plan ──────────
    /** Provider rejected our credentials (401/403). User should check their API key. */
    UNAUTHORIZED("Couldn't authenticate with the AI provider — check your API key"),

    /** Provider throttled us (429). Phase 5 surfaces the local-model fallback here. */
    RATE_LIMITED("Too many requests — please slow down and retry"),

    /** Provider is responding 5xx or otherwise refusing job. */
    LLM_UNAVAILABLE("The AI service is unavailable right now, please retry shortly"),

    /** Provider refused the request because the conversation is too long. */
    CONTEXT_TOO_LONG("This conversation got too long — start a new chat to continue")
}
