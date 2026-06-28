package com.tcohen.moviesapp.ai.data.local.cache

import com.tcohen.moviesapp.ai.domain.model.ChatCompletion
import com.tcohen.moviesapp.ai.domain.model.ChatRequest
import java.security.MessageDigest

/**
 * Read-through / write-through cache for [ChatCompletion]s.
 *
 * Implementations are responsible only for persistence and key derivation:
 *
 * - Key derivation lives in [cacheKey] so every implementation hashes identically.
 *   Bumping [ChatRequest]'s shape (e.g. adding a `user` field) does **not** invalidate
 *   existing entries — callers should bump the embedded [promptVersion] if they
 *   change system prompts semantically.
 * - Callers MUST check [get] before issuing a network request and MUST call [put]
 *   after every successful network response. Hit-rate is the lever that keeps
 *   cost + latency at bay.
 *
 * Phase 0 ships the in-memory implementation ([InMemoryLlmResponseCache]). Phase 2
 * adds a `RoomLlmResponseCache` and the [LlmModule] is updated to bind it.
 */
interface LlmResponseCache {
    /**
     * Look up a cached completion for [request] under the given [promptVersion] tag.
     * Returns null on miss or invalidation.
     */
    fun get(request: ChatRequest, promptVersion: String): ChatCompletion?

    /**
     * Store [completion] for [request] under the [promptVersion] tag. Overwrites
     * any previous entry.
     */
    fun put(request: ChatRequest, promptVersion: String, completion: ChatCompletion)

    /**
     * Stable hash of [request] + [model] + [promptVersion]. Two requests that
     * produce identical LLM output hash to the same key.
     */
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

/**
 * Process-lifetime implementation backed by a [java.util.concurrent.ConcurrentHashMap].
 * Survives within a single app run only — useful for Phase 0 tests and for a
 * fast path during a session.
 */
class InMemoryLlmResponseCache : LlmResponseCache {
    private val store = java.util.concurrent.ConcurrentHashMap<String, ChatCompletion>()

    override fun get(request: ChatRequest, promptVersion: String): ChatCompletion? =
        store[cacheKey(request, promptVersion)]

    override fun put(request: ChatRequest, promptVersion: String, completion: ChatCompletion) {
        store[cacheKey(request, promptVersion)] = completion
    }
}

/**
 * SHA-256 of [input] as a lowercase hex string. Used by [LlmResponseCache.cacheKey].
 *
 * Visible to tests so they can assert key stability directly.
 */
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

// ── File-private constants ────────────────────────────────────────────────────
//
// All literals here are deliberately pinned to single source-of-truth values so
// the cache key format can never drift across cache implementations.

/** Separates every top-level field in the pre-hash key string (version, model, …). */
private const val KEY_FIELD_SEPARATOR: Char = '|'

/** Separates the role name from the message text inside one entry. */
private const val KEY_INNER_SEPARATOR: Char = ':'

/** Marks the end of one message before the next one begins. */
private const val KEY_MESSAGE_SEPARATOR: Char = ';'

/** Algorithm name passed to [MessageDigest.getInstance]. */
private const val SHA_256_ALGORITHM: String = "SHA-256"

/** Bit mask that reinterprets a Java signed `byte` as an unsigned 0..255 value. */
private const val BYTE_UNSIGNED_MASK: Int = 0xff

/** Any byte whose high nibble is below this value is rendered as `0X` (two hex digits). */
private const val HEX_HALF_BYTE_THRESHOLD: Int = 0x10

/** Number of lowercase hex characters one byte expands to (`0a` → 2 chars). */
private const val HEX_DIGITS_PER_BYTE: Int = 2

/** Single zero character used to pad hex output below the half-byte threshold. */
private const val HEX_PADDING_CHAR: Char = '0'
