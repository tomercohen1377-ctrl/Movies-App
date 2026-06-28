package com.tcohen.moviesapp.ai.domain.tool

/**
 * A single typed parameter of a tool call.
 *
 * Mirrors the subset of JSON-Schema we actually need for tool calling.
 * Validation is best-effort at execution time — the LLM may omit or mistype fields.
 *
 * @property name        parameter name (must match the JSON object key the model emits)
 * @property type        one of [ToolParameterType]
 * @property description natural-language description shown to the model in the tool schema
 * @property required    whether the model must supply a value
 */
data class ToolParameter(
    val name: String,
    val type: ToolParameterType,
    val description: String,
    val required: Boolean = false
)

/** Supported JSON value types used by [ToolParameter]. */
enum class ToolParameterType {
    STRING,
    INTEGER,
    NUMBER,
    BOOLEAN,
    ARRAY,
    OBJECT
}

/**
 * Result of executing a tool. Always returned to the model as a [ChatRole.TOOL] message.
 *
 * [Success] carries the text the model will see and may include a structured payload
 * surfaced as [structured]; [Error] carries a short human-readable message so the
 * model can self-correct next round (e.g. a non-existent movie id).
 */
sealed interface ToolResult {
    data class Success(
        val text: String,
        val structured: String? = null
    ) : ToolResult

    data class Error(val message: String) : ToolResult
}
