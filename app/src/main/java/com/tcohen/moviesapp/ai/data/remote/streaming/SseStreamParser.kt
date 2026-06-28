package com.tcohen.moviesapp.ai.data.remote.streaming

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.ResponseBody
import okio.BufferedSource

/**
 * Parses the Server-Sent Events wire format from an OpenAI-compatible chat
 * completion streaming response (`Content-Type: text/event-stream`).
 *
 * Each `data: …` line is passed verbatim to [extractDelta]. The caller decides
 * whether a given payload carries a text delta, a tool call, or nothing — and
 * signals "no useful delta" by returning `null`.
 *
 * Correctness notes baked into the implementation:
 *
 * - **Chunked deliveries:** a single HTTP body chunk may contain multiple events,
 *   a partial event, or both. We loop over `readUtf8Line()` and never assume a
 *   boundary aligns with a network read.
 * - **Line endings:** SSI uses `LF` (`\n`); some servers send `CRLF` (`\r\n`).
 *   `readUtf8Line` strips both.
 * - **The `[DONE]` sentinel** ends the stream — no further delta is emitted.
 * - **Cancellation:** OkHttp's [okio.Source] implements [okio.Source]; on flow
 *   cancellation the `finally` block closes the source AND the body, so the
 *   socket is released back to the pool promptly.
 *
 * The parser is deliberately tiny so it stays unit-testable without any
 * HTTP overhead — tests feed it a synthetic [ResponseBody] built from a string.
 */
internal class SseStreamParser(
    private val extractDelta: (payload: String) -> String?
) {
    /**
     * Returns a [Flow] of text deltas. The flow completes when:
     *  - the server sends `[DONE]`,
     *  - the underlying source is exhausted,
     *  - the collector cancels.
     *
     * Errors mid-stream must be surfaced by the caller — the parser is dumb
     * about HTTP errors because it only sees the body, not the response code.
     */
    fun stream(body: ResponseBody): Flow<String> = flow {
        val source: BufferedSource = body.source()
        try {
            while (!source.exhausted()) {
                val rawLine = source.readUtf8Line() ?: break
                val trimmed = rawLine.trim()
                if (trimmed.isEmpty()) continue
                if (!trimmed.startsWith(DATA_PREFIX)) continue
                val payload = trimmed.removePrefix(DATA_PREFIX).trim()
                if (payload == DONE) break
                val delta = extractDelta(payload)
                if (delta != null) emit(delta)
            }
        } finally {
            runCatching { source.close() }
            runCatching { body.close() }
        }
    }

    companion object {
        /** Prefix that marks the data line of an SSE event. */
        const val DATA_PREFIX = "data:"

        /** Sentinel string sent by OpenAI and Gemini to signal end-of-stream. */
        const val DONE = "[DONE]"
    }
}
