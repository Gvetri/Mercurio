package org.mercurio.client.feature.entry.presentation

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class EntryScreenSessionFlowsTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun populated_and_empty_session_lists_expose_the_new_session_row() {
        val events = mutableListOf<EntryUiEvent>()
        val state =
            mutableStateOf(
                EntryUiState(
                    title = "Gateway connected",
                    supportingText = "The Gateway contract was verified successfully.",
                    actionLabel = "Connected",
                    isConnected = true,
                    sessionList =
                        SessionListUiState(
                            sessions =
                                listOf(
                                    SessionItemUiState(
                                        id = SessionId("existing"),
                                        title = "Existing Session",
                                        preview = null,
                                        pinned = false,
                                    ),
                                ),
                        ),
                ),
            )

        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(state = state.value, onEvent = events::add)
            }
        }

        composeTestRule.onNodeWithText("new session").assertHasClickAction().performClick()
        assertEquals(listOf(EntryUiEvent.CreateSessionClicked), events)

        events.clear()
        composeTestRule.runOnIdle {
            state.value = state.value.copy(sessionList = SessionListUiState())
        }
        composeTestRule.onNodeWithText("new session").assertHasClickAction().performClick()
        assertEquals(listOf(EntryUiEvent.CreateSessionClicked), events)
    }

    @Test
    fun creation_failure_stays_on_the_list_without_a_title_dialog() {
        val events = mutableListOf<EntryUiEvent>()
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(
                    state =
                        EntryUiState(
                            title = "Gateway connected",
                            supportingText = "The Gateway contract was verified successfully.",
                            actionLabel = "Connected",
                            isConnected = true,
                            sessionList =
                                SessionListUiState(
                                    sessions =
                                        listOf(
                                            SessionItemUiState(
                                                id = SessionId("existing"),
                                                title = "Existing Session",
                                                preview = null,
                                                pinned = false,
                                            ),
                                        ),
                                    creationErrorCategory = SessionCreationErrorCategory.GATEWAY_REQUEST_FAILED,
                                ),
                        ),
                    onEvent = events::add,
                )
            }
        }

        composeTestRule.onNode(isDialog()).assertDoesNotExist()
        composeTestRule
            .onNodeWithText(SessionCreationErrorCategory.GATEWAY_REQUEST_FAILED.safeMessage)
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("new session").assertHasClickAction().performClick()
        assertEquals(listOf(EntryUiEvent.CreateSessionClicked), events)
    }

    @Test
    fun loading_stale_and_recoverable_session_states_have_clear_accessible_recovery() {
        val state = mutableStateOf(SessionListUiState(isLoading = true))
        val events = mutableListOf<EntryUiEvent>()
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(
                    state =
                        EntryUiState(
                            title = "Gateway connected",
                            supportingText = "The Gateway contract was verified successfully.",
                            actionLabel = "Connected",
                            isConnected = true,
                            sessionList = state.value,
                        ),
                    onEvent = events::add,
                )
            }
        }

        composeTestRule.onNodeWithText("loading sessions…").assertIsDisplayed()
        composeTestRule.runOnIdle {
            state.value =
                SessionListUiState(
                    sessions =
                        listOf(
                            SessionItemUiState(
                                id = SessionId("stale"),
                                title = "Stale Session",
                                preview = "Previous server preview",
                                pinned = false,
                            ),
                        ),
                    isStale = true,
                    isUnavailable = true,
                    errorCategory = SessionListErrorCategory.GATEWAY_UNAVAILABLE,
                )
        }

        composeTestRule.onNodeWithText("gateway :: offline").assertIsDisplayed()
        composeTestRule.onNodeWithText("Stale Session").assertIsDisplayed()
    }

    @Test
    fun opened_session_displays_authoritative_history_and_has_a_local_return_action() {
        val events = mutableListOf<EntryUiEvent>()
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(
                    state =
                        EntryUiState(
                            title = "Gateway connected",
                            supportingText = "The Gateway contract was verified successfully.",
                            actionLabel = "Connected",
                            isConnected = true,
                            sessionList =
                                SessionListUiState(
                                    openedSession =
                                        OpenSessionUiState(
                                            session =
                                                SessionItemUiState(
                                                    id = SessionId("session-one"),
                                                    title = "Authoritative title",
                                                    preview = "Authoritative preview",
                                                    pinned = false,
                                                ),
                                            messages =
                                                listOf(
                                                    SessionMessageUiState(
                                                        id = "message-one",
                                                        role = "user",
                                                        content = "Authoritative history",
                                                    ),
                                                ),
                                        ),
                                ),
                        ),
                    onEvent = events::add,
                )
            }
        }

        composeTestRule.onNodeWithText("Authoritative title").assertIsDisplayed()
        composeTestRule.onNodeWithText("Authoritative history").assertIsDisplayed()
        composeTestRule.onNodeWithText("Back to Sessions").assertHasClickAction().performClick()
        assertEquals(EntryUiEvent.ReturnToSessionListClicked, events.single())
    }

    @Test
    fun new_session_row_is_disabled_while_a_session_is_opening() {
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(
                    state =
                        EntryUiState(
                            title = "Gateway connected",
                            supportingText = "The Gateway contract was verified successfully.",
                            actionLabel = "Connected",
                            isConnected = true,
                            sessionList =
                                SessionListUiState(
                                    sessions =
                                        listOf(
                                            SessionItemUiState(
                                                id = SessionId("session-one"),
                                                title = "Session one",
                                                preview = null,
                                                pinned = false,
                                            ),
                                        ),
                                    openingSessionId = SessionId("session-one"),
                                ),
                        ),
                    onEvent = {},
                )
            }
        }

        composeTestRule.onNodeWithText("new session").assertIsNotEnabled()
    }

    @Test
    fun streamed_response_exposes_only_truthful_run_state_and_interruption_semantics() {
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(
                    state =
                        EntryUiState(
                            title = "Gateway connected",
                            supportingText = "The Gateway contract was verified successfully.",
                            actionLabel = "Connected",
                            isConnected = true,
                            sessionList =
                                SessionListUiState(
                                    openedSession =
                                        OpenSessionUiState(
                                            session =
                                                SessionItemUiState(
                                                    id = SessionId("session-one"),
                                                    title = "Streaming Session",
                                                    preview = null,
                                                    pinned = false,
                                                ),
                                            messages = emptyList(),
                                            latestRun = Run(RunId("run-one"), SessionId("session-one"), "running"),
                                            latestRunState = RunPresentationState.RUNNING,
                                            activeResponse =
                                                SessionMessageUiState(
                                                    id = "active-response:run-one",
                                                    role = "assistant",
                                                    content = "Partial answer",
                                                    runId = RunId("run-one"),
                                                    isStreaming = true,
                                                    runState = RunPresentationState.RUNNING,
                                                ),
                                        ),
                                ),
                        ),
                    onEvent = {},
                )
            }
        }

        composeTestRule
            .onNodeWithText("Partial answer")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText("Streaming response…")
            .performScrollTo()
            .assertIsDisplayed()
        val runStateNodes = composeTestRule.onAllNodesWithText("Run state: Running")
        runStateNodes.assertCountEquals(2)
        runStateNodes[0].assertIsDisplayed()
        runStateNodes[1].assertIsDisplayed()
        composeTestRule.onNodeWithText("Run status: Running").assertDoesNotExist()
    }
}
