package com.tcohen.moviesapp.ai.data.remote.dto

import com.tcohen.moviesapp.ai.domain.model.ChatMessage
import com.tcohen.moviesapp.ai.domain.model.ChatRequest
import com.tcohen.moviesapp.ai.domain.model.ChatRole
import com.tcohen.moviesapp.ai.domain.model.ToolInvocation
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class ChatCompletionRequestDto(
    val model: String,
    val messages: List<ChatMessageDto>,
    val temperature: Float? = null,
    @SerialName("max_tokens") val maxTokens: Int? = null,
    val stream: Boolean = false,
    val tools: List<ToolSchemaDto>? = null
)

@Serializable
internal data class ChatMessageDto(
    val role: String,
    val content: String? = null,
    @SerialName("tool_calls") val toolCalls: List<ToolCallDto>? = null,
    @SerialName("tool_call_id") val toolCallId: String? = null
)

@Serializable
internal data class ToolCallDto(
    val id: String,
    val type: String = "function",
    val function: ToolCallFunctionDto
)

@Serializable
internal data class ToolCallFunctionDto(
    val name: String,

    val arguments: String
)

@Serializable
internal data class ToolSchemaDto(
    val type: String = "function",
    val function: ToolFunctionSchemaDto
)

@Serializable
internal data class ToolFunctionSchemaDto(
    val name: String,
    val description: String,
    val parameters: ToolParametersSchemaDto
)

@Serializable
internal data class ToolParametersSchemaDto(
    val type: String = "object",
    val properties: Map<String, ToolParameterSchemaDto>,
    val required: List<String> = emptyList()
)

@Serializable
internal data class ToolParameterSchemaDto(
    val type: String,
    val description: String? = null
)

@Serializable
internal data class ChatCompletionResponseDto(
    val id: String? = null,
    val model: String? = null,
    val choices: List<ChoiceDto>
)

@Serializable
internal data class ChoiceDto(
    val index: Int = 0,
    val message: AssistantMessageDto,
    @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
internal data class AssistantMessageDto(
    val role: String? = null,
    val content: String? = null,
    @SerialName("tool_calls") val toolCalls: List<ToolCallDto>? = null
)

@Serializable
internal data class ChatCompletionChunkDto(
    val id: String? = null,
    val model: String? = null,
    val choices: List<ChunkChoiceDto>
)

@Serializable
internal data class ChunkChoiceDto(
    val index: Int = 0,
    val delta: AssistantDeltaDto,
    @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
internal data class AssistantDeltaDto(
    val role: String? = null,
    val content: String? = null,
    @SerialName("tool_calls") val toolCalls: List<ToolCallDto>? = null
)

internal fun ChatMessage.toDto(): ChatMessageDto = when (role) {
    ChatRole.TOOL -> ChatMessageDto(
        role = "tool",
        content = text,
        toolCalls = null,
        toolCallId = toolCallId
    )
    ChatRole.ASSISTANT -> ChatMessageDto(
        role = "assistant",
        content = text.ifEmpty { null },
        toolCalls = if (toolCalls.isEmpty()) null else toolCalls.map { it.toDto() },
        toolCallId = null
    )
    else -> ChatMessageDto(
        role = role.name.lowercase(),
        content = text,
        toolCalls = null,
        toolCallId = null
    )
}

internal fun ToolInvocation.toDto(): ToolCallDto = ToolCallDto(
    id = id,
    type = "function",
    function = ToolCallFunctionDto(name = toolName, arguments = rawArgs)
)

internal fun ChatRequest.toDto(stream: Boolean): ChatCompletionRequestDto = ChatCompletionRequestDto(
    model = model,
    messages = messages.map { it.toDto() },
    temperature = temperature,
    maxTokens = maxTokens,
    stream = stream,
    tools = null
)
