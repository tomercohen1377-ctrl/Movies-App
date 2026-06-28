package com.tcohen.moviesapp.ai.presentation.common

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tcohen.moviesapp.presentation.theme.MoviesAppTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text

/**
 * Reusable token-by-token text bubble. Animates smoothly as new content
 * arrives; tolerates partial chunks (a half-formed Markdown link won't
 * crash — `Text` just shows the literal characters).
 *
 * Used by:
 * - Phase 1 — `PlotExplainerSection.StreamingBubble`
 * - Phase 4 (chat) — every assistant reply bubble
 * - Phase 6 (eval) — replay of canned responses
 *
 * - [text] — the accumulated text so far (caller appends and recomposes).
 * - [placeholder] — shown when [text] is blank so the user sees live feedback
 *   before the first chunk arrives.
 * - [modifier] — modifier for the outer text node.
 * - [showTypingIndicator] — optional trailing "…" hint while streaming.
 */
@Composable
fun StreamingText(
    text: String,
    placeholder: String = "Thinking…",
    modifier: Modifier = Modifier,
    testTag: String = TEST_TAG_STREAMING_TEXT_DEFAULT,
    showTypingIndicator: Boolean = false
) {
    Text(
        text = if (text.isEmpty()) placeholder else text,
        style = MaterialTheme.typography.bodyMedium,
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
            .testTag(testTag)
    )
    // Caller can render their own "Thinking…" progress indicator outside
    // the text node; this composable focuses on the growing text only.
    @Suppress("UNUSED_VARIABLE")
    val unused = showTypingIndicator  // surface option for Phase 4+
}

// ── Test tag (kept here so the composable is self-contained for UI tests) ─────

private const val TEST_TAG_STREAMING_TEXT_DEFAULT: String = "streaming_text"

// ── Pixel constants (mirrors MovieCardDefaults style) ─────────────────────────

private val STREAMING_TEXT_PADDING_DP = 0.dp  // spacer comes from caller

@Suppress("unused")
@Preview(showBackground = true)
@Composable
private fun StreamingTextPreview() {
    MoviesAppTheme {
        StreamingText(
            text = "The Matrix follows Neo, a hacker who discovers reality itself is a simulation.",
            placeholder = "Thinking…"
        )
    }
}
