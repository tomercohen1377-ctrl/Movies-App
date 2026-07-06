package com.tcohen.moviesapp.util

enum class ApiError(val message: String) {

    SERVER_ERROR("Server error"),
    NO_CONNECTION("No internet connection"),
    TIMEOUT("Connection timed out — check your internet and retry"),
    UNEXPECTED("An unexpected error occurred"),

    UNAUTHORIZED("Couldn't authenticate with the AI provider — check your API key"),

    RATE_LIMITED("Too many requests — please slow down and retry"),

    LLM_UNAVAILABLE("The AI service is unavailable right now, please retry shortly"),

    CONTEXT_TOO_LONG("This conversation got too long — start a new chat to continue")
}
