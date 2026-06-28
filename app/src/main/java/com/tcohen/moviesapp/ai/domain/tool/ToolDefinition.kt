package com.tcohen.moviesapp.ai.domain.tool

import kotlinx.serialization.json.JsonElement

/**
 * The full declaration of a tool the model is allowed to invoke.
 *
 * A tool is registered with the [ToolRegistry] behind a stable [name] (snake_case
 * is recommended for compatibility with OpenAI's tool-calling format). The
 * [parameters] list is serialised to a JSON Schema fragment when the request is
 * sent. The [executor] turns the model's [JsonElement] arguments into a [ToolResult].
 *
 * Tools are pure Kotlin — no Android, no Hilt, no networking. Validation belongs to
 * the executor; it must never throw. Invalid arguments → `ToolResult.Error`.
 */
data class ToolDefinition(
    val name: String,
    val description: String,
    val parameters: List<ToolParameter>,
    val executor: (JsonElement) -> ToolResult
) {
    init {
        require(name.isNotBlank()) { "Tool name must not be blank" }
        require(description.isNotBlank()) { "Tool '$name' description must not be blank" }
    }
}

/**
 * Way to access registered tools. Implementations live in the data layer
 * (see `ToolRegistryImpl`). The interface lives in domain so that LLM-facing
 * code only depends on the contract.
 */
interface ToolRegistry {
    /** All tool definitions the model may see in this conversation. */
    fun definitions(): List<ToolDefinition>

    /** Look up a registered tool by the exact [name] the model emitted. */
    fun get(name: String): ToolDefinition?

    /**
     * Execute [args] against the tool named [name]. Returns null if no tool is
     * registered under that name so the agent loop can surface "unknown tool"
     * to the model instead of crashing.
     */
    fun execute(name: String, args: JsonElement): ToolResult? {
        val tool = get(name) ?: return null
        return tool.executor(args)
    }
}
