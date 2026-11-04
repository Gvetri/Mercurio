package org.mercurio.client.feature.entry.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mercurio.client.feature.entry.domain.Run
import org.mercurio.client.feature.entry.domain.RunId
import org.mercurio.client.feature.entry.domain.RunPresentationState
import org.mercurio.client.feature.entry.domain.SessionId
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant

/**
 * The retried turn keeps its own row, a failed turn carries its own retry, and an unresolved Run
 * says `stale` without inventing a failure until the Gateway confirms one. The transcript still
 * lands on its newest Message and still leaves a reader who scrolled away where they are.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class SessionHistoryRetryScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun failed_run_try_again_dispatches_a_user_initiated_retry_event() {
        val failedRunId = RunId("run-failed")
        val events = mutableListOf<EntryUiEvent>()
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(state = failedTurnState(failedRunId, retryAvailable = true), onEvent = { events += it })
            }
        }

        composeTestRule.onNodeWithText("Try again").performScrollTo().performClick()
        assertEquals(listOf<EntryUiEvent>(EntryUiEvent.RetryRunClicked(failedRunId)), events)
    }

    @Test
    fun the_retried_turn_keeps_its_own_row_and_the_failed_turn_keeps_its_retry() {
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(state = retriedRunHistoryState(), onEvent = {})
            }
        }

        composeTestRule.onNodeWithText("Transcript message 1").assertIsDisplayed()
        composeTestRule.onNodeWithText(copy(RunFailureCategory.GATEWAY_REPORTED.messageRes)).assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Try again").assertCountEquals(1)
        composeTestRule.onNodeWithText("Retry answer").performScrollTo().assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Try again").assertCountEquals(1)
    }

    @Test
    fun an_uncertain_run_says_stale_without_a_failure_detail_until_reconciliation_confirms_one() {
        var state by mutableStateOf(uncertainRunState())
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(state = state, onEvent = {})
            }
        }

        composeTestRule.onAllNodesWithText("· stale", substring = true).assertCountEquals(1)
        composeTestRule.onNodeWithText("[ refresh history ]").assertIsDisplayed()
        composeTestRule.onNodeWithText("Try again").assertDoesNotExist()
        composeTestRule.onNodeWithText("Show technical detail").assertDoesNotExist()

        state = confirmedFailureState(state)

        composeTestRule.onNodeWithText("FAILED").assertIsDisplayed()
        composeTestRule.onNodeWithText(copy(RunFailureCategory.GATEWAY_REPORTED.messageRes)).assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Try again").assertCountEquals(1)
    }

    @Test
    fun retry_is_disabled_while_another_run_is_in_flight() {
        val sessionId = SessionId("session-1")
        val activeRun = Run(RunId("run-active"), sessionId, "running")
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(
                    state =
                        entryState(
                            SessionListUiState(
                                openedSession =
                                    OpenSessionUiState(
                                        session = SessionItemUiState(sessionId, "Session", null, false),
                                        messages =
                                            listOf(
                                                SessionMessageUiState(
                                                    id = "message-failed",
                                                    role = "assistant",
                                                    content = null,
                                                    runId = RunId("run-failed"),
                                                    runStatus = "failed",
                                                    failureMessage =
                                                        UiText.Resource(RunFailureCategory.GATEWAY_REPORTED.messageRes),
                                                    retryAvailable = true,
                                                ),
                                            ),
                                        activeRuns = listOf(activeRun),
                                        latestRun = activeRun,
                                        latestRunState = RunPresentationState.RUNNING,
                                    ),
                            ),
                        ),
                    onEvent = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Try again").performScrollTo().assertIsDisplayed().assertIsNotEnabled()
    }

    @Test
    fun opening_a_session_lands_on_its_newest_transcript_message() {
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(
                    state = transcriptEntryState(messages = (1..12).map(::transcriptMessage)),
                    onEvent = {},
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Transcript message 12").assertIsDisplayed()
        composeTestRule.onNodeWithText("Transcript message 1").assertDoesNotExist()
    }

    @Test
    fun an_arriving_message_is_followed_while_the_newest_content_is_shown() {
        var state by mutableStateOf(transcriptEntryState(messages = (1..10).map(::transcriptMessage)))
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(state = state, onEvent = {})
            }
        }
        composeTestRule.waitForIdle()

        state = transcriptEntryState(messages = (1..11).map(::transcriptMessage))
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Transcript message 11").assertIsDisplayed()
    }

    @Test
    fun an_arriving_message_does_not_pull_a_reader_who_scrolled_away_and_follow_resumes() {
        var state by mutableStateOf(transcriptEntryState(messages = (1..10).map(::transcriptMessage)))
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(state = state, onEvent = {})
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNode(hasScrollToIndexAction()).performTouchInput {
            swipeDown(startY = centerY - 200f, endY = centerY + 200f, durationMillis = 200)
        }
        composeTestRule.waitForIdle()

        state = transcriptEntryState(messages = (1..11).map(::transcriptMessage))
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Transcript message 11").assertDoesNotExist()

        composeTestRule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Transcript message 11"))
        composeTestRule.onNodeWithText("Transcript message 11").assertIsDisplayed()
        state = transcriptEntryState(messages = (1..12).map(::transcriptMessage))
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Transcript message 12").assertIsDisplayed()
    }

    private fun transcriptEntryState(messages: List<SessionMessageUiState>): EntryUiState =
        entryState(
            SessionListUiState(
                openedSession =
                    OpenSessionUiState(
                        session = SessionItemUiState(SessionId("session-1"), "Session", null, false),
                        messages = messages,
                    ),
            ),
        )

    private fun transcriptMessage(index: Int): SessionMessageUiState {
        return message("message-$index", "user", "Transcript message $index")
    }

    private fun failedTurnState(
        failedRunId: RunId,
        retryAvailable: Boolean,
    ): EntryUiState =
        entryState(
            SessionListUiState(
                openedSession =
                    OpenSessionUiState(
                        session = SessionItemUiState(SessionId("session-1"), "Session", null, false),
                        messages =
                            listOf(
                                SessionMessageUiState(
                                    id = "message-failed",
                                    role = "user",
                                    content = "Original request",
                                    runId = failedRunId,
                                    timestamp = Instant.parse("2026-09-08T20:00:00Z"),
                                    failureMessage = UiText.Resource(RunFailureCategory.GATEWAY_REPORTED.messageRes),
                                    retryAvailable = retryAvailable,
                                ),
                            ),
                        latestRun = Run(failedRunId, SessionId("session-1"), "failed"),
                        latestRunState = RunPresentationState.FAILED,
                    ),
            ),
        )

    private fun retriedRunHistoryState(): EntryUiState =
        entryState(
            SessionListUiState(
                openedSession =
                    OpenSessionUiState(
                        session = SessionItemUiState(SessionId("session-1"), "Session", null, false),
                        messages =
                            listOf(
                                SessionMessageUiState(
                                    id = "original-user-message",
                                    role = "user",
                                    content = "Transcript message 1",
                                    timestamp = Instant.parse("2026-09-08T20:00:00Z"),
                                ),
                                SessionMessageUiState(
                                    id = "message-failed",
                                    role = "assistant",
                                    content = null,
                                    runId = RunId("run-failed"),
                                    runStatus = "failed",
                                    timestamp = Instant.parse("2026-09-08T20:01:00Z"),
                                    failureMessage = UiText.Resource(RunFailureCategory.GATEWAY_REPORTED.messageRes),
                                    retryAvailable = true,
                                ),
                                SessionMessageUiState(
                                    id = "message-retried",
                                    role = "assistant",
                                    content = "Retry answer",
                                    runId = RunId("run-retried"),
                                    runStatus = "succeeded",
                                    timestamp = Instant.parse("2026-09-08T20:10:00Z"),
                                ),
                            ),
                        latestRun = Run(RunId("run-retried"), SessionId("session-1"), "succeeded"),
                        latestRunState = RunPresentationState.SUCCEEDED,
                    ),
            ),
        )

    private fun uncertainRunState(): EntryUiState =
        entryState(
            SessionListUiState(
                openedSession =
                    OpenSessionUiState(
                        session = SessionItemUiState(SessionId("session-1"), "Session", null, false),
                        messages =
                            listOf(
                                SessionMessageUiState(
                                    id = "message-interrupted",
                                    role = "assistant",
                                    content = "Partial answer",
                                    runId = RunId("run-interrupted"),
                                    runState = RunPresentationState.UNCERTAIN,
                                    streamInterrupted = true,
                                    timestamp = Instant.parse("2026-09-08T20:00:00Z"),
                                ),
                            ),
                        latestRun = Run(RunId("run-interrupted"), SessionId("session-1"), "interrupted"),
                        latestRunState = RunPresentationState.UNCERTAIN,
                    ),
            ),
        )

    private fun confirmedFailureState(state: EntryUiState): EntryUiState =
        state.copy(
            sessionList =
                state.sessionList?.copy(
                    openedSession =
                        state.sessionList.openedSession?.copy(
                            messages =
                                listOf(
                                    SessionMessageUiState(
                                        id = "message-confirmed-failed",
                                        role = "assistant",
                                        content = null,
                                        runId = RunId("run-interrupted"),
                                        runStatus = "failed",
                                        timestamp = Instant.parse("2026-09-08T20:01:00Z"),
                                        failureMessage =
                                            UiText.Resource(RunFailureCategory.GATEWAY_REPORTED.messageRes),
                                        retryAvailable = true,
                                    ),
                                ),
                            latestRunState = RunPresentationState.FAILED,
                        ),
                ),
        )
}
