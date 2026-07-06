package com.tcohen.moviesapp.ai.data.remote.streaming

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.ResponseBody
import okio.BufferedSource

internal class SseStreamParser(
    private val extractDelta: (payload: String) -> String?
) {

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

        const val DATA_PREFIX = "data:"

        const val DONE = "[DONE]"
    }
}
