package org.mercurio.client.feature.entry.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mercurio.client.feature.entry.domain.RunId
import org.mercurio.client.feature.entry.domain.RunPresentationState
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Pins the transcript follow behavior: an opened Session shows the newest content's end, a
 * streamed response that grows in place stays followed, a reader who dragged away is not pulled
 * back, the follow returns once they settle at the newest content, and a Message long enough to
 * fold keeps its end reachable through the fold's disclosure.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class SessionTranscriptFollowTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun opened_session_shows_the_newest_content_of_a_transcript_taller_than_the_pane() {
        setTranscriptContent { conversationState(messages = (1..12).map(::transcriptMessage)) }

        composeTestRule.onNodeWithText("Transcript message 12").assertIsDisplayed()
        composeTestRule.onNodeWithText("Transcript message 1").assertIsNotDisplayed()
    }

    @Test
    fun growing_streamed_response_stays_in_view_while_the_reader_stays_at_the_newest_content() {
        val state = mutableStateOf(streamingState(activeResponse = streamedResponse(transcriptBody(40))))
        setTranscriptContent { state.value }

        composeTestRule.onNodeWithText("Paragraph 40").assertIsDisplayed()

        composeTestRule.runOnIdle {
            state.value = streamingState(activeResponse = streamedResponse(transcriptBody(52)))
        }

        composeTestRule.onNodeWithText("Paragraph 52").assertIsDisplayed()
    }

    @Test
    fun a_burst_of_chunks_still_ends_at_the_newest_content() {
        val state = mutableStateOf(streamingState(activeResponse = streamedResponse(transcriptBody(40))))
        setTranscriptContent { state.value }

        composeTestRule.runOnUiThread {
            state.value = streamingState(activeResponse = streamedResponse(transcriptBody(52)))
        }
        composeTestRule.runOnUiThread {
            state.value = streamingState(activeResponse = streamedResponse(transcriptBody(64)))
        }

        composeTestRule.onNodeWithText("Paragraph 64").assertIsDisplayed()
    }

    @Test
    fun a_chunk_arriving_right_after_a_follow_scroll_still_ends_at_the_newest_content() {
        val state = mutableStateOf(streamingState(activeResponse = streamedResponse(transcriptBody(40))))
        setTranscriptContent { state.value }

        composeTestRule.runOnUiThread {
            state.value = streamingState(activeResponse = streamedResponse(transcriptBody(52)))
        }
        composeTestRule.mainClock.advanceTimeBy(100)
        composeTestRule.runOnUiThread {
            state.value = streamingState(activeResponse = streamedResponse(transcriptBody(64)))
        }

        composeTestRule.onNodeWithText("Paragraph 64").assertIsDisplayed()
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun short_viewport_follows_the_newest_streamed_text() {
        val state = mutableStateOf(streamingState(activeResponse = streamedResponse(transcriptBody(24))))
        composeTestRule.setContent {
            HermesTheme {
                Box(Modifier.height(240.dp)) {
                    EntryScreen(state = state.value, onEvent = {})
                }
            }
        }
        composeTestRule.onNodeWithText("Paragraph 24").assertIsDisplayed()
        composeTestRule.onNodeWithText("Paragraph 1").assertIsNotDisplayed()

        composeTestRule.runOnIdle {
            state.value = streamingState(activeResponse = streamedResponse(transcriptBody(36)))
        }
        composeTestRule.onNodeWithText("Paragraph 36").assertIsDisplayed()
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun shortest_keyboard_viewport_keeps_a_complete_newest_text_line_readable() {
        composeTestRule.setContent {
            HermesTheme {
                Box(Modifier.height(176.dp)) {
                    EntryScreen(
                        state = streamingState(activeResponse = streamedResponse("Short answer")),
                        onEvent = {},
                    )
                }
            }
        }
        composeTestRule.onNodeWithText("Short answer").performScrollTo().assertIsDisplayed()
        val newestLine = composeTestRule.onNodeWithText("Short answer").fetchSemanticsNode()
        assertTrue(
            "The newest line must keep a readable height in a 176 dp window",
            newestLine.boundsInRoot.height >= with(composeTestRule.density) { 16.dp.toPx() },
        )
    }

    @Test
    fun a_reader_who_dragged_away_from_the_newest_content_is_not_pulled_back_by_growth() {
        val state = mutableStateOf(conversationState(messages = (1..12).map(::transcriptMessage)))
        setTranscriptContent { state.value }

        composeTestRule.onNodeWithText("Transcript message 12").assertIsDisplayed()

        composeTestRule.onNode(hasScrollToIndexAction()).performTouchInput {
            swipeDown(startY = top + 8f, endY = bottom - 8f, durationMillis = 300)
        }
        composeTestRule.onNodeWithText("Transcript message 12").assertIsNotDisplayed()

        composeTestRule.runOnIdle {
            state.value = conversationState(messages = (1..13).map(::transcriptMessage))
        }

        composeTestRule.onNodeWithText("Transcript message 13").assertIsNotDisplayed()
    }

    @Test
    fun newly_arrived_message_stays_in_view_while_the_reader_stays_at_the_newest_content() {
        val state = mutableStateOf(conversationState(messages = (1..12).map(::transcriptMessage)))
        setTranscriptContent { state.value }

        composeTestRule.onNodeWithText("Transcript message 12").assertIsDisplayed()

        composeTestRule.runOnIdle {
            state.value = conversationState(messages = (1..13).map(::transcriptMessage))
        }

        composeTestRule.onNodeWithText("Transcript message 13").assertIsDisplayed()
    }

    @Test
    fun follow_returns_once_the_reader_settles_back_at_the_newest_content() {
        val state = mutableStateOf(streamingState(activeResponse = streamedResponse(transcriptBody(40))))
        setTranscriptContent { state.value }

        composeTestRule.onNodeWithText("Paragraph 40").assertIsDisplayed()

        composeTestRule.onNode(hasScrollToIndexAction()).performTouchInput {
            swipeDown(startY = top + 8f, endY = bottom - 8f, durationMillis = 300)
        }
        composeTestRule.onNodeWithText("Paragraph 40").assertIsNotDisplayed()

        composeTestRule.onNodeWithText("Paragraph 40").performScrollTo().assertIsDisplayed()
        composeTestRule.waitForIdle()

        composeTestRule.runOnIdle {
            state.value = streamingState(activeResponse = streamedResponse(transcriptBody(52)))
        }

        composeTestRule.onNodeWithText("Paragraph 52").assertIsDisplayed()
    }

    @Test
    fun a_long_message_folds_and_its_end_stays_reachable() {
        val longReply = message("message-long", "assistant", longBody(40))
        setTranscriptContent {
            conversationState(messages = listOf(message("message-1", "user", "Show me"), longReply))
        }

        composeTestRule.onNodeWithText("[ show more ]").performScrollTo().assertIsDisplayed().performClick()
        composeTestRule.onNodeWithTag("chat-fold-message-long").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("[ show less ]").assertExists()
        composeTestRule.onNodeWithText("Long line 40").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun a_short_viewport_keeps_the_safe_failure_visible_when_there_is_no_exportable_body() {
        val failed =
            SessionMessageUiState(
                id = "failed-message",
                role = "assistant",
                content = null,
                runId = RunId("failed-run"),
                runState = RunPresentationState.FAILED,
                failureSafeMessage = "The message was not sent.",
            )
        composeTestRule.setContent {
            HermesTheme {
                Box(Modifier.height(176.dp)) {
                    EntryScreen(state = conversationState(messages = listOf(failed)), onEvent = {})
                }
            }
        }
        composeTestRule.onNodeWithText("The message was not sent.").performScrollTo().assertIsDisplayed()
    }

    private fun setTranscriptContent(state: () -> EntryUiState) {
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(state = state(), onEvent = {})
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun conversationState(messages: List<SessionMessageUiState>): EntryUiState =
        entryState(
            SessionListUiState(
                openedSession =
                    OpenSessionUiState(
                        session = session("session-1", "Conversation Session"),
                        messages = messages,
                    ),
            ),
        )

    private fun streamingState(activeResponse: SessionMessageUiState): EntryUiState =
        entryState(
            SessionListUiState(
                openedSession =
                    OpenSessionUiState(
                        session = session("session-1", "Streaming Session"),
                        messages = listOf(message("message-1", "user", "Show me the long answer")),
                        latestRun =
                            org.mercurio.client.feature.entry.domain.Run(
                                RunId("4b2a-run"),
                                org.mercurio.client.feature.entry.domain.SessionId("session-1"),
                                "running",
                            ),
                        latestRunState = RunPresentationState.RUNNING,
                        activeResponse = activeResponse,
                    ),
            ),
        )

    private fun streamedResponse(content: String): SessionMessageUiState =
        SessionMessageUiState(
            id = "streamed-response",
            role = "assistant",
            content = content,
            runId = RunId("4b2a-run"),
            runState = RunPresentationState.RUNNING,
            isStreaming = true,
        )

    private fun transcriptMessage(index: Int): SessionMessageUiState {
        return message("message-$index", "user", "Transcript message $index")
    }

    private fun transcriptBody(paragraphCount: Int): String =
        (1..paragraphCount).joinToString(separator = "\n\n") { number -> "Paragraph $number" }

    private fun longBody(lineCount: Int): String {
        return (1..lineCount).joinToString(separator = "\n\n") { number -> "Long line $number" }
    }
}
