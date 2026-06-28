package com.tcohen.moviesapp.ai.domain.model

/**
 * Role of a participant in a chat conversation.
 *
 * The four values map 1:1 to OpenAI-compatible chat completion roles. [SYSTEM] is the
 * developer-set system prompt; [USER] is the human (or the chat turn's owning app); [ASSISTANT]
 * is the model's reply; [TOOL] is the structured result of invoking a registered tool.
 */
enum class ChatRole {
    SYSTEM,
    USER,
    ASSISTANT,
    TOOL
}

/**
 * A single message in a chat conversation.
 *
 * @property role   who produced this message
 * @property text   the message contents; plain text for [SYSTEM]/[USER]/[ASSISTANT]/[TOOL]
 *                  for OpenAI-compatible APIs. Empty string allowed (used for assistant
 *                  messages that consist only of tool calls in some adapter schemas).
 * @property toolCalls       any tool invocations issued by the model on this turn
 *                           (only meaningful for [ChatRole.ASSISTANT])
 * @property toolCallId      set when [role] is [ChatRole.TOOL]; identifies which tool
 *                           call this message is a result of
 */
data class ChatMessage(
    val role: ChatRole,
    val text: String,
    val toolCalls: List<ToolInvocation> = emptyList(),
    val toolCallId: String? = null
)

/**
 * A single tool invocation in an assistant message.
 *
 * @property id        the call id assigned by the model; round-tripped into the corresponding
 *                     tool message so the model can refer to it
 * @property toolName  which registered tool should be executed
 * @property rawArgs   JSON-encoded arguments object as the model produced them
 *                     (parsed at execution time, never trusted blindly)
 */
data class ToolInvocation(
    val id: String,
    val toolName: String,
    val rawArgs: String
)

/**
 * A complete chat completion request.
 *
 * @property messages   chronological ordered list of [ChatMessage]s
 * @property model      model identifier used for this request (free-form; provider-dependent)
 * @property temperature sampling temperature, 0.0–2.0; clamped by the provider
 * @property maxTokens  optional cap on tokens generated for the response
 * @property tools      optional list of tool names that the model may invoke this turn
 *                      (looked up against the [ToolRegistry] by [LlmClient.complete])
 */
data class ChatRequest(
    val messages: List<ChatMessage>,
    val model: String,
    val temperature: Float = DEFAULT_TEMPERATURE,
    val maxTokens: Int? = null,
    val tools: List<String> = emptyList()
)

/**
 * The model's structured reply to a [ChatRequest].
 *
 * The raw text is always present; [toolCalls] is non-empty when the model decided to
 * invoke one or more tools instead of (or in addition to) producing text.
 */
data class ChatCompletion(
    val text: String,
    val toolCalls: List<ToolInvocation> = emptyList(),
    val finishReason: FinishReason
)

/**
 * Why a [ChatCompletion] ended.
 */
enum class FinishReason {
    /** Model emitted a natural text stop. */
    STOP,

    /** Model emitted one or more tool invocations — agent loop should continue. */
    TOOL_CALLS,

    /** Hit [ChatRequest.maxTokens] or the provider's context limit. */
    LENGTH,

    /** Provider-supplied content filter tripped. */
    CONTENT_FILTER,

    /** Anything else — surfacing it as a generic [UNEXPECTED] avoids silent guessing. */
    UNEXPECTED
}

/**
 * Convenience constants used by the default prompt templates and tunables across
 * the AI layer. Co-located with [ChatRequest] because they describe default behaviour
 * for new requests.
 */
const val DEFAULT_TEMPERATURE: Float = 0.7f
