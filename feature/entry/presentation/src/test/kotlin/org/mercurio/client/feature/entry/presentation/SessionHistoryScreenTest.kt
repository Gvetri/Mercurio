package org.mercurio.client.feature.entry.presentation

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.After
import org.junit.Before
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
import java.util.TimeZone

/**
 * The transcript states an opened Session can be in: populated, refreshing, failed, unreachable,
 * empty, loading and reconciling. Each one says its own state on the run line and keeps the
 * composer honest about what the reader can do next.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-notnight")
class SessionHistoryScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var originalTimeZone: TimeZone

    @Before
    fun fixTheTimeZoneTheTranscriptPrints() {
        originalTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"))
    }

    @After
    fun restoreTheTimeZone() {
        TimeZone.setDefault(originalTimeZone)
    }

    @Test
    fun populated_history_shows_roles_times_and_a_usable_composer() {
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(state = historyState(messages = populatedMessages()), onEvent = {})
            }
        }

        composeTestRule.onNodeWithText("USER").assertIsDisplayed()
        composeTestRule.onNodeWithText("ASSISTANT").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("09:12").onFirst().assertIsDisplayed()
        composeTestRule.onNodeWithText("idle").assertIsDisplayed()
        composeTestRule.onNodeWithTag("conversation-send").assertIsDisplayed()
    }

    @Test
    fun epoch_second_timestamp_reaches_the_rendered_time_cell() {
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(
                    state =
                        historyState(
                            messages =
                                listOf(
                                    SessionMessageUiState(
                                        id = "message-1",
                                        role = "user",
                                        content = "Epoch timestamp",
                                        timestamp = Instant.parse("2026-11-01T08:12:00Z"),
                                    ),
                                ),
                        ),
                    onEvent = {},
                )
            }
        }

        composeTestRule.onAllNodesWithText("09:12").onFirst().assertIsDisplayed()
    }

    @Test
    fun refreshing_history_preserves_content_and_shows_the_refreshing_state() {
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(
                    state =
                        historyState(
                            messages = populatedMessages(),
                            isCached = true,
                            isRefreshing = true,
                        ),
                    onEvent = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Check the nightly release.").assertIsDisplayed()
        composeTestRule.onNodeWithText("cached · refreshing").assertIsDisplayed()
        composeTestRule.onNodeWithText("Refreshing history.").assertIsDisplayed()
    }

    @Test
    fun a_refreshing_transcript_offers_no_second_refresh_action() {
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(
                    state =
                        historyState(
                            messages = populatedMessages(),
                            isCached = true,
                            isRefreshing = true,
                        ),
                    onEvent = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("chat-run-action").assertDoesNotExist()
    }

    @Test
    fun reconciliation_states_its_progress_and_keeps_the_stream_visible() {
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(
                    state =
                        historyState(
                            messages = populatedMessages(),
                            isRefreshing = true,
                            isReconciliationInProgress = true,
                            latestRun = Run(RunId("4b2a-run"), SessionId("session-1"), "running"),
                            latestRunState = RunPresentationState.RUNNING,
                        ),
                    onEvent = {},
                )
            }
        }

        composeTestRule.onNodeWithText("reconciling run state").assertIsDisplayed()
        composeTestRule.onNodeWithText("Reconciling the Run state.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Check the nightly release.").assertIsDisplayed()
    }

    @Test
    fun an_unresolved_submission_states_its_reason_and_keeps_the_draft() {
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(
                    state =
                        historyState(
                            messages = populatedMessages(),
                            composerText = "Keep this draft",
                            sendErrorCategory = MessageSendErrorCategory.UNCERTAIN,
                            hasUnresolvedSubmission = true,
                        ),
                    onEvent = {},
                )
            }
        }

        composeTestRule.onNodeWithText(MessageSendErrorCategory.UNCERTAIN.safeMessage).assertIsDisplayed()
        composeTestRule.onNodeWithText("Keep this draft").assertIsDisplayed()
        composeTestRule.onNodeWithText("Discard unsent record").assertIsDisplayed().assertIsEnabled()
    }

    @Test
    fun a_failed_refresh_preserves_cached_content_and_offers_one_refresh() {
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(
                    state =
                        historyState(
                            messages = populatedMessages(),
                            isCached = true,
                            isStale = true,
                            errorCategory = SessionHistoryErrorCategory.GATEWAY_REQUEST_FAILED,
                        ),
                    onEvent = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Check the nightly release.").assertIsDisplayed()
        composeTestRule.onNodeWithText("cached · unavailable").assertIsDisplayed()
        composeTestRule.onNodeWithTag("chat-run-action").assertIsDisplayed()
    }

    @Test
    fun an_unreachable_session_without_a_cache_shows_the_unavailable_state() {
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(
                    state =
                        entryState(
                            SessionListUiState(
                                openedSession =
                                    OpenSessionUiState(
                                        session = SessionItemUiState(SessionId("session-1"), "Session", null, false),
                                        messages = emptyList(),
                                        errorCategory = SessionHistoryErrorCategory.GATEWAY_REQUEST_FAILED,
                                    ),
                            ),
                        ),
                    onEvent = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Session history is unavailable").assertIsDisplayed()
        composeTestRule.onNodeWithText("[ try again ]").assertIsDisplayed()
        composeTestRule.onNodeWithText("history · unavailable").assertIsDisplayed()
        composeTestRule.onNodeWithText("gateway :: offline").assertIsDisplayed()
    }

    @Test
    fun empty_history_shows_guidance_and_the_composer() {
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(state = historyState(messages = emptyList()), onEvent = {})
            }
        }

        composeTestRule.onNodeWithText("No messages yet").assertIsDisplayed()
        composeTestRule
            .onNodeWithText("This Session has no history on the Gateway. Write the first message.")
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag("conversation-message-input").assertIsDisplayed()
    }

    @Test
    fun loading_history_shows_progress_instead_of_an_empty_transcript() {
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(state = historyState(messages = emptyList(), isRefreshing = true), onEvent = {})
            }
        }

        composeTestRule.onNodeWithText("Loading Session history…").assertIsDisplayed()
        composeTestRule.onNodeWithText("history · loading").assertIsDisplayed()
        composeTestRule.onNodeWithText("Sending is unavailable until the history loads.").assertIsDisplayed()
        composeTestRule.onNodeWithText("No messages yet").assertDoesNotExist()
    }

    @Test
    fun a_failed_run_shows_the_safe_message_and_never_the_server_text() {
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(
                    state =
                        historyState(
                            messages =
                                listOf(
                                    SessionMessageUiState(
                                        id = "message-failed",
                                        role = "assistant",
                                        content = null,
                                        runId = RunId("run-failed"),
                                        runStatus = "failed",
                                        runResult = "Remote failure",
                                        failureSafeMessage = RunFailureCategory.GATEWAY_REPORTED.safeMessage,
                                    ),
                                ),
                            latestRun = Run(RunId("run-failed"), SessionId("session-1"), "failed"),
                            latestRunState = RunPresentationState.FAILED,
                        ),
                    onEvent = {},
                )
            }
        }

        composeTestRule.onNodeWithText("FAILED").assertIsDisplayed()
        composeTestRule.onNodeWithText(RunFailureCategory.GATEWAY_REPORTED.safeMessage).assertIsDisplayed()
        composeTestRule.onNodeWithText("Remote failure").assertDoesNotExist()
        composeTestRule.onAllNodesWithText("failed", substring = true).assertCountEquals(2)
    }

    @Test
    fun client_derived_technical_detail_can_be_expanded_as_an_optional_safe_surface() {
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(
                    state =
                        historyState(
                            messages =
                                listOf(
                                    SessionMessageUiState(
                                        id = "message-failed",
                                        role = "assistant",
                                        content = null,
                                        runId = RunId("run-failed"),
                                        runStatus = "failed",
                                        failureSafeMessage = RunFailureCategory.GATEWAY_REPORTED.safeMessage,
                                        failureTechnicalDetail = "gateway status 500",
                                    ),
                                ),
                            latestRun = Run(RunId("run-failed"), SessionId("session-1"), "failed"),
                            latestRunState = RunPresentationState.FAILED,
                        ),
                    onEvent = {},
                )
            }
        }

        composeTestRule.onNodeWithText("gateway status 500").assertDoesNotExist()
        composeTestRule.onNodeWithText("Show technical detail").performScrollTo().performClick()
        composeTestRule.onNodeWithText("gateway status 500").assertIsDisplayed()
    }

    private fun populatedMessages(): List<SessionMessageUiState> =
        listOf(
            SessionMessageUiState(
                id = "message-1",
                role = "user",
                content = "Check the nightly release.",
                timestamp = Instant.parse("2026-11-01T08:12:00Z"),
            ),
            SessionMessageUiState(
                id = "message-2",
                role = "assistant",
                content = "Two checks did not pass.",
                timestamp = Instant.parse("2026-11-01T08:12:00Z"),
            ),
        )

    @Suppress("LongParameterList")
    private fun historyState(
        messages: List<SessionMessageUiState>,
        composerText: String = "",
        isCached: Boolean = false,
        isRefreshing: Boolean = false,
        isStale: Boolean = false,
        errorCategory: SessionHistoryErrorCategory? = null,
        latestRun: Run? = null,
        latestRunState: RunPresentationState? = null,
        isReconciliationInProgress: Boolean = false,
        sendErrorCategory: MessageSendErrorCategory? = null,
        hasUnresolvedSubmission: Boolean = false,
    ): EntryUiState =
        entryState(
            SessionListUiState(
                openedSession =
                    OpenSessionUiState(
                        session = SessionItemUiState(SessionId("session-1"), "Session", null, false),
                        messages = messages,
                        composerText = composerText,
                        isCached = isCached,
                        isRefreshing = isRefreshing,
                        isStale = isStale,
                        errorCategory = errorCategory,
                        latestRun = latestRun,
                        latestRunState = latestRunState,
                        isReconciliationInProgress = isReconciliationInProgress,
                        sendErrorCategory = sendErrorCategory,
                        hasUnresolvedSubmission = hasUnresolvedSubmission,
                    ),
            ),
        )
}
