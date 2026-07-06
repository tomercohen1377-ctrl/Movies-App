package com.tcohen.moviesapp.ai.data.local.cache

import com.tcohen.moviesapp.ai.domain.model.ChatCompletion
import com.tcohen.moviesapp.ai.domain.model.ChatRequest
import java.security.MessageDigest

interface LlmResponseCache {

    fun get(request: ChatRequest, promptVersion: String): ChatCompletion?

    fun put(request: ChatRequest, promptVersion: String, completion: ChatCompletion)

    fun cacheKey(request: ChatRequest, promptVersion: String): String = sha256(
        buildString {
            append(promptVersion)
            append(KEY_FIELD_SEPARATOR)
            append(request.model)
            append(KEY_FIELD_SEPARATOR)
            append(request.temperature)
            append(KEY_FIELD_SEPARATOR)
            append(request.maxTokens?.toString().orEmpty())
            append(KEY_FIELD_SEPARATOR)
            request.messages.forEach { msg ->
                append(msg.role.name)
                append(KEY_INNER_SEPARATOR)
                append(msg.text)
                append(KEY_MESSAGE_SEPARATOR)
            }
        }
    )
}

class InMemoryLlmResponseCache : LlmResponseCache {
    private val store = java.util.concurrent.ConcurrentHashMap<String, ChatCompletion>()

    override fun get(request: ChatRequest, promptVersion: String): ChatCompletion? =
        store[cacheKey(request, promptVersion)]

    override fun put(request: ChatRequest, promptVersion: String, completion: ChatCompletion) {
        store[cacheKey(request, promptVersion)] = completion
    }
}

internal fun sha256(input: String): String {
    val bytes = MessageDigest.getInstance(SHA_256_ALGORITHM).digest(input.toByteArray(Charsets.UTF_8))
    val hex = StringBuilder(bytes.size * HEX_DIGITS_PER_BYTE)
    for (b in bytes) {
        val v = b.toInt() and BYTE_UNSIGNED_MASK
        if (v < HEX_HALF_BYTE_THRESHOLD) hex.append(HEX_PADDING_CHAR)
        hex.append(Integer.toHexString(v))
    }
    return hex.toString()
}

private const val KEY_FIELD_SEPARATOR: Char = '|'

private const val KEY_INNER_SEPARATOR: Char = ':'

private const val KEY_MESSAGE_SEPARATOR: Char = ';'

private const val SHA_256_ALGORITHM: String = "SHA-256"

private const val BYTE_UNSIGNED_MASK: Int = 0xff

private const val HEX_HALF_BYTE_THRESHOLD: Int = 0x10

private const val HEX_DIGITS_PER_BYTE: Int = 2

private const val HEX_PADDING_CHAR: Char = '0'
