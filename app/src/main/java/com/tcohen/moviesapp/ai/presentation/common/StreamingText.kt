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

    @Suppress("UNUSED_VARIABLE")
    val unused = showTypingIndicator
}

private const val TEST_TAG_STREAMING_TEXT_DEFAULT: String = "streaming_text"

private val STREAMING_TEXT_PADDING_DP = 0.dp

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
