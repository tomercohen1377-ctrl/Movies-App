package com.tcohen.moviesapp.ai.data.local.cache

import com.tcohen.moviesapp.ai.data.local.cache.sha256
import com.tcohen.moviesapp.ai.domain.model.ChatCompletion
import com.tcohen.moviesapp.ai.domain.model.ChatMessage
import com.tcohen.moviesapp.ai.domain.model.ChatRequest
import com.tcohen.moviesapp.ai.domain.model.ChatRole
import com.tcohen.moviesapp.ai.domain.model.FinishReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class InMemoryLlmResponseCacheTest {

    @Test
    fun `get returns null on empty cache`() {
        val cache = InMemoryLlmResponseCache()
        assertNull(cache.get(sampleRequest(), PROMPT_V1))
    }

    @Test
    fun `put then get round-trip returns the same completion`() {
        val cache = InMemoryLlmResponseCache()
        val request = sampleRequest(text = "hello")
        val completion = completion("hi there")

        cache.put(request, PROMPT_V1, completion)

        val restored = cache.get(request, PROMPT_V1)
        assertNotNull(restored)
        assertEquals(completion.text, restored!!.text)
        assertEquals(completion.finishReason, restored.finishReason)
    }

    @Test
    fun `different prompt versions produce different keys`() {
        val cache = InMemoryLlmResponseCache()
        val request = sampleRequest()

        cache.put(request, "prompt-v1", completion("answer-v1"))

        assertEquals("answer-v1", cache.get(request, "prompt-v1")?.text)
        assertNull("fresh prompt version should miss", cache.get(request, "prompt-v2"))
    }

    @Test
    fun `bumping prompt version invalidates the cache`() {
        val cache = InMemoryLlmResponseCache()
        val request = sampleRequest()

        cache.put(request, "v1", completion("cached under v1"))
        cache.put(request, "v2", completion("cached under v2"))

        assertEquals("cached under v1", cache.get(request, "v1")?.text)
        assertEquals("cached under v2", cache.get(request, "v2")?.text)
    }

    @Test
    fun `overwriting same key replaces the stored completion`() {
        val cache = InMemoryLlmResponseCache()
        val request = sampleRequest()

        cache.put(request, PROMPT_V1, completion("first"))
        cache.put(request, PROMPT_V1, completion("second"))

        assertEquals("second", cache.get(request, PROMPT_V1)?.text)
    }

    @Test
    fun `cacheKey is stable across calls`() {
        val cache = InMemoryLlmResponseCache()
        val request = sampleRequest()
        val keyA = cache.cacheKey(request, PROMPT_V1)
        val keyB = cache.cacheKey(request, PROMPT_V1)
        assertEquals(keyA, keyB)
    }

    @Test
    fun `cacheKey differs when text differs`() {
        val cache = InMemoryLlmResponseCache()
        val keyA = cache.cacheKey(sampleRequest(text = "alpha"), PROMPT_V1)
        val keyB = cache.cacheKey(sampleRequest(text = "beta"),  PROMPT_V1)
        assertNotEquals(keyA, keyB)
    }

    @Test
    fun `cacheKey differs when model differs`() {
        val cache = InMemoryLlmResponseCache()
        val keyA = cache.cacheKey(sampleRequest(model = "m-a"), PROMPT_V1)
        val keyB = cache.cacheKey(sampleRequest(model = "m-b"), PROMPT_V1)
        assertNotEquals(keyA, keyB)
    }

    @Test
    fun `cacheKey differs when temperature differs`() {
        val cache = InMemoryLlmResponseCache()
        val keyA = cache.cacheKey(sampleRequest(temperature = 0.0f), PROMPT_V1)
        val keyB = cache.cacheKey(sampleRequest(temperature = 0.7f), PROMPT_V1)
        assertNotEquals(keyA, keyB)
    }

    @Test
    fun `sha256 produces 64-char lowercase hex`() {
        val hash = sha256("hello")
        assertEquals(64, hash.length)
        assertEquals(hash, hash.lowercase())
    }

    private fun sampleRequest(
        text: String = "summarise Inception",
        model: String = "gemini-2.0-flash",
        temperature: Float = 0.4f
    ): ChatRequest = ChatRequest(
        messages = listOf(ChatMessage(ChatRole.USER, text)),
        model = model,
        temperature = temperature
    )

    private fun completion(text: String): ChatCompletion = ChatCompletion(
        text = text,
        toolCalls = emptyList(),
        finishReason = FinishReason.STOP
    )

    companion object {
        private const val PROMPT_V1 = "prompt-v1"
    }
}
