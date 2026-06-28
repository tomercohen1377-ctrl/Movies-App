package com.tcohen.moviesapp.ai.presentation.ploexplainer

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tcohen.moviesapp.presentation.theme.MoviesAppTheme
import com.tcohen.moviesapp.util.ApiError
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented UI tests for [PlotExplainerContent] — the stateless renderer
 * that backs [PlotExplainerSection]. Drives each of the four
 * [PlotExplainerState] branches directly so we don't need a real
 * [com.tcohen.moviesapp.ai.domain.client.LlmClient] or Hilt at test time.
 */
@RunWith(AndroidJUnit4::class)
class PlotExplainerSectionTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ── Idle ──────────────────────────────────────────────────────────────────

    @Test
    fun plotExplainer_rendersIdleButtonInitially() {
        var explainCalls = 0
        composeTestRule.setContent {
            MoviesAppTheme {
                PlotExplainerContent(
                    state = PlotExplainerState.Idle,
                    onExplain = { explainCalls++ },
                    onReset = {},
                    onRetry = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("plot_explainer_section").assertIsDisplayed()
        composeTestRule.onNodeWithTag("plot_explainer_explain_button").assertIsDisplayed()
        composeTestRule.onNodeWithText("Explain plot").assertIsDisplayed()
        assertTrue("button must not have fired yet", explainCalls == 0)
    }

    @Test
    fun plotExplainer_idleButtonClick_invokesOnExplain() {
        var explainCalls = 0
        composeTestRule.setContent {
            MoviesAppTheme {
                PlotExplainerContent(
                    state = PlotExplainerState.Idle,
                    onExplain = { explainCalls++ },
                    onReset = {},
                    onRetry = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("plot_explainer_explain_button").performClick()
        assertTrue("onExplain must fire once per click", explainCalls == 1)
    }

    // ── Streaming ─────────────────────────────────────────────────────────────

    @Test
    fun plotExplainer_rendersStreamingBubbleWithGrowingText() {
        composeTestRule.setContent {
            MoviesAppTheme {
                PlotExplainerContent(
                    state = PlotExplainerState.Streaming("Paul Atreides unites"),
                    onExplain = {},
                    onReset = {},
                    onRetry = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("plot_explainer_streaming_text").assertIsDisplayed()
        composeTestRule.onNodeWithText("Paul Atreides unites").assertIsDisplayed()
        composeTestRule.onNodeWithTag("plot_explainer_streaming_indicator").assertIsDisplayed()
    }

    @Test
    fun plotExplainer_streamingWithEmptyTextShowsPlaceholder() {
        composeTestRule.setContent {
            MoviesAppTheme {
                PlotExplainerContent(
                    state = PlotExplainerState.Streaming(""),
                    onExplain = {},
                    onReset = {},
                    onRetry = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Thinking…").assertIsDisplayed()
    }

    // ── Done ──────────────────────────────────────────────────────────────────

    @Test
    fun plotExplainer_rendersDoneBubbleWithFinalText() {
        var resetCalls = 0
        composeTestRule.setContent {
            MoviesAppTheme {
                PlotExplainerContent(
                    state = PlotExplainerState.Done("The final plot summary text."),
                    onExplain = {},
                    onReset = { resetCalls++ },
                    onRetry = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("plot_explainer_done_text").assertIsDisplayed()
        composeTestRule.onNodeWithText("The final plot summary text.").assertIsDisplayed()
        composeTestRule.onNodeWithTag("plot_explainer_reset_button").assertIsDisplayed()
        assertTrue("reset must not have fired yet", resetCalls == 0)
    }

    @Test
    fun plotExplainer_resetButtonClick_invokesOnReset() {
        var resetCalls = 0
        composeTestRule.setContent {
            MoviesAppTheme {
                PlotExplainerContent(
                    state = PlotExplainerState.Done("text"),
                    onExplain = {},
                    onReset = { resetCalls++ },
                    onRetry = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("plot_explainer_reset_button").performClick()
        assertTrue("onReset must fire once per click", resetCalls == 1)
    }

    // ── Error ─────────────────────────────────────────────────────────────────

    @Test
    fun plotExplainer_rendersErrorViewWithRetry() {
        var retryCalls = 0
        composeTestRule.setContent {
            MoviesAppTheme {
                PlotExplainerContent(
                    state = PlotExplainerState.Error(ApiError.UNAUTHORIZED.message),
                    onExplain = {},
                    onReset = {},
                    onRetry = { retryCalls++ }
                )
            }
        }

        composeTestRule.onNodeWithTag("plot_explainer_error").assertIsDisplayed()
        composeTestRule.onNodeWithText(ApiError.UNAUTHORIZED.message).assertIsDisplayed()
    }

    @Test
    fun plotExplainer_errorRetryButtonClick_invokesOnRetry() {
        var retryCalls = 0
        composeTestRule.setContent {
            MoviesAppTheme {
                PlotExplainerContent(
                    state = PlotExplainerState.Error("some error"),
                    onExplain = {},
                    onReset = {},
                    onRetry = { retryCalls++ }
                )
            }
        }

        // `ErrorView` shows a "Retry" button — locating by text is the
        // simplest stable selector across Material 3 theme changes.
        composeTestRule.onNodeWithText("Retry").performClick()
        assertTrue("onRetry must fire once per click", retryCalls == 1)
    }
}
