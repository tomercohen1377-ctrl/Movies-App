package com.tcohen.moviesapp.ai.domain.model

enum class ChatRole {
    SYSTEM,
    USER,
    ASSISTANT,
    TOOL
}

data class ChatMessage(
    val role: ChatRole,
    val text: String,
    val toolCalls: List<ToolInvocation> = emptyList(),
    val toolCallId: String? = null
)

data class ToolInvocation(
    val id: String,
    val toolName: String,
    val rawArgs: String
)

data class ChatRequest(
    val messages: List<ChatMessage>,
    val model: String,
    val temperature: Float = DEFAULT_TEMPERATURE,
    val maxTokens: Int? = null,
    val tools: List<String> = emptyList()
)

data class ChatCompletion(
    val text: String,
    val toolCalls: List<ToolInvocation> = emptyList(),
    val finishReason: FinishReason
)

enum class FinishReason {

    STOP,

    TOOL_CALLS,

    LENGTH,

    CONTENT_FILTER,

    UNEXPECTED
}

const val DEFAULT_TEMPERATURE: Float = 0.7f
