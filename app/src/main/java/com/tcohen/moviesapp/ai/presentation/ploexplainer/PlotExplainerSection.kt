package com.tcohen.moviesapp.ai.presentation.ploexplainer

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tcohen.moviesapp.presentation.common.ErrorView
import com.tcohen.moviesapp.presentation.theme.MoviesAppTheme

/**
 * Streaming plot-summary section embedded at the bottom of
 * `MovieDetailContent`. State-owning wrapper that observes
 * [PlotExplainerViewModel.state] and delegates rendering to the stateless
 * [PlotExplainerContent] — the same code path that UI tests exercise.
 *
 * The wrapping pattern lets instrumentation tests render any
 * [PlotExplainerState] without needing a real [LlmClient] or Hilt.
 */
@Composable
fun PlotExplainerSection(
    title: String,
    year: Int?,
    runtimeMinutes: Int?,
    modifier: Modifier = Modifier,
    viewModel: PlotExplainerViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    PlotExplainerContent(
        state = state,
        onExplain = {
            viewModel.processIntent(PlotExplainerIntent.Explain(title, year, runtimeMinutes))
        },
        onReset = { viewModel.processIntent(PlotExplainerIntent.Reset) },
        onRetry = {
            viewModel.processIntent(PlotExplainerIntent.Explain(title, year, runtimeMinutes))
        },
        modifier = modifier
    )
}

/**
 * Stateless renderer used by both the production wrapper and the UI tests.
 *
 * Renders one of the four [PlotExplainerState] branches exhaustively — every
 * branch carries a unique `testTag(...)` so the UI test can locate it
 * semantically.
 */
@Composable
fun PlotExplainerContent(
    state: PlotExplainerState,
    onExplain: () -> Unit,
    onReset: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = PADDING_HORIZONTAL_DP.dp, vertical = PADDING_VERTICAL_DP.dp)
            .testTag(TEST_TAG_SECTION),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(PADDING_INNER_DP.dp),
            verticalArrangement = Arrangement.spacedBy(SPACE_BETWEEN_ELEMENTS_DP.dp)
        ) {
            SectionHeader()
            when (state) {
                PlotExplainerState.Idle -> IdleButton(onClick = onExplain)

                is PlotExplainerState.Streaming -> StreamingBubble(
                    text = state.text,
                    isStreaming = true
                )

                is PlotExplainerState.Done -> DoneBubble(
                    text = state.text,
                    onReset = onReset
                )

                is PlotExplainerState.Error -> ErrorView(
                    message = state.message,
                    onRetry = onRetry,
                    modifier = Modifier.testTag(TEST_TAG_ERROR)
                )
            }
        }
    }
}

// ── Sub-composables (private) ────────────────────────────────────────────────

@Composable
private fun SectionHeader() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Filled.AutoAwesome,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(SPACE_BETWEEN_ELEMENTS_DP.dp))
        Text(
            text = "AI plot summary",
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
private fun IdleButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TEST_TAG_EXPLAIN_BUTTON)
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null
        )
        Spacer(modifier = Modifier.width(SPACE_BETWEEN_ELEMENTS_DP.dp))
        Text("Explain plot")
    }
}

@Composable
private fun StreamingBubble(text: String, isStreaming: Boolean) {
    Column {
        Text(
            text = text.ifEmpty { "Thinking…" },
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
                .testTag(TEST_TAG_STREAMING_TEXT)
        )
        if (isStreaming) {
            Spacer(modifier = Modifier.height(SPACE_AFTER_TEXT_DP.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.testTag(TEST_TAG_STREAMING_INDICATOR)
                )
            }
        }
    }
}

@Composable
private fun DoneBubble(text: String, onReset: () -> Unit) {
    Column {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TEST_TAG_DONE_TEXT)
        )
        Spacer(modifier = Modifier.height(SPACE_AFTER_TEXT_DP.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(
                onClick = onReset,
                modifier = Modifier.testTag(TEST_TAG_RESET_BUTTON)
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(SPACE_BETWEEN_ELEMENTS_DP.dp))
                Text("Reset")
            }
        }
    }
}

// ── Test tags (mirrors the project's `movie_card` convention) ────────────────

private const val TEST_TAG_SECTION: String = "plot_explainer_section"
private const val TEST_TAG_EXPLAIN_BUTTON: String = "plot_explainer_explain_button"
private const val TEST_TAG_STREAMING_TEXT: String = "plot_explainer_streaming_text"
private const val TEST_TAG_STREAMING_INDICATOR: String = "plot_explainer_streaming_indicator"
private const val TEST_TAG_DONE_TEXT: String = "plot_explainer_done_text"
private const val TEST_TAG_RESET_BUTTON: String = "plot_explainer_reset_button"
private const val TEST_TAG_ERROR: String = "plot_explainer_error"

// ── Pixel constants (mirrors MovieCardDefaults / DetailScreenDefaults) ──────

private const val PADDING_HORIZONTAL_DP: Int = 16
private const val PADDING_VERTICAL_DP: Int = 8
private const val PADDING_INNER_DP: Int = 16
private const val SPACE_BETWEEN_ELEMENTS_DP: Int = 8
private const val SPACE_AFTER_TEXT_DP: Int = 8

// ── @Preview (matches the project's pattern — every composable has @Preview) ──

/**
 * Idle-branch preview. The production code path goes through `hiltViewModel()`;
 * the preview drives the stateless [PlotExplainerContent] directly so we don't
 * need a Hilt graph inside Android Studio.
 */
@Preview(showBackground = true)
@Composable
private fun PlotExplainerContentIdlePreview() {
    MoviesAppTheme {
        PlotExplainerContent(
            state = PlotExplainerState.Idle,
            onExplain = {},
            onReset = {},
            onRetry = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PlotExplainerContentStreamingPreview() {
    MoviesAppTheme {
        PlotExplainerContent(
            state = PlotExplainerState.Streaming("Paul Atreides unites with the Fremen…"),
            onExplain = {},
            onReset = {},
            onRetry = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PlotExplainerContentDonePreview() {
    MoviesAppTheme {
        PlotExplainerContent(
            state = PlotExplainerState.Done(
                "Paul Atreides unites with the Fremen while seeking revenge against the conspirators who destroyed his family."
            ),
            onExplain = {},
            onReset = {},
            onRetry = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PlotExplainerContentErrorPreview() {
    MoviesAppTheme {
        PlotExplainerContent(
            state = PlotExplainerState.Error("Couldn't authenticate with the AI provider"),
            onExplain = {},
            onReset = {},
            onRetry = {}
        )
    }
}
