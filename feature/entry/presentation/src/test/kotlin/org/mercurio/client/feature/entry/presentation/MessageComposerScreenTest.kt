package org.mercurio.client.feature.entry.presentation

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
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
class MessageComposerScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun explicit_send_emits_only_the_send_intent_and_shows_the_current_run() {
        val events = mutableListOf<EntryUiEvent>()
        val run = Run(RunId("run-1"), SessionId("session-1"), "succeeded")
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(
                    state = connectedState(composerText = "Run this", latestRun = run),
                    onEvent = events::add,
                )
            }
        }

        composeTestRule.onNodeWithText("Latest Run").assertIsDisplayed()
        composeTestRule.onNodeWithText("Run status: Completed").assertIsDisplayed()
        composeTestRule.onNodeWithText("Send").assertHasClickAction().assertIsEnabled().performClick()

        assertEquals(listOf(EntryUiEvent.SendMessageClicked), events)
    }

    @Test
    fun the_latest_run_identity_stays_behind_an_explicit_disclosure() {
        val run = Run(RunId("run-1"), SessionId("session-1"), "succeeded")
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(
                    state = connectedState(latestRun = run),
                    onEvent = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Latest Run").assertIsDisplayed()
        composeTestRule.onNodeWithText("Run status: Completed").assertIsDisplayed()
        composeTestRule.onNodeWithText("run-1", substring = true).assertDoesNotExist()
        composeTestRule.onNodeWithText("Show run ID").performClick()
        composeTestRule.onNodeWithText("Run ID: run-1").assertIsDisplayed()
    }

    @Test
    fun focused_keyboard_send_emits_the_same_explicit_send_intent() {
        val events = mutableListOf<EntryUiEvent>()
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(
                    state = connectedState(composerText = "Run this"),
                    onEvent = events::add,
                )
            }
        }

        composeTestRule.onNodeWithText("Message").performImeAction()

        assertEquals(listOf(EntryUiEvent.SendMessageClicked), events)
    }

    @Test
    fun active_observed_run_replaces_send_with_the_stop_affordance() {
        val events = mutableListOf<EntryUiEvent>()
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(
                    state =
                        connectedState(
                            composerText = "Running draft",
                            latestRun = Run(RunId("run-1"), SessionId("session-1"), "running"),
                            latestRunState = RunPresentationState.RUNNING,
                        ),
                    onEvent = events::add,
                )
            }
        }

        composeTestRule.onNodeWithText("Message").assertIsEnabled()
        composeTestRule.onNodeWithText("Send").assertDoesNotExist()
        composeTestRule.onNodeWithText("Stop").assertHasClickAction().assertIsEnabled().performClick()

        assertEquals(listOf<EntryUiEvent>(EntryUiEvent.StopRunClicked), events)
    }

    @Test
    fun completed_run_returns_the_send_control_to_the_normal_send_state() {
        val events = mutableListOf<EntryUiEvent>()
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(
                    state =
                        connectedState(
                            composerText = "Next question",
                            latestRun = Run(RunId("run-1"), SessionId("session-1"), "completed"),
                            latestRunState = RunPresentationState.SUCCEEDED,
                        ),
                    onEvent = events::add,
                )
            }
        }

        composeTestRule.onNodeWithText("Stop").assertDoesNotExist()
        composeTestRule.onNodeWithText("Send").assertHasClickAction().assertIsEnabled().performClick()

        assertEquals(listOf(EntryUiEvent.SendMessageClicked), events)
    }

    @Test
    fun active_run_keeps_the_composer_editable_but_disables_send() {
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(
                    state =
                        connectedState(
                            composerText = "Draft",
                            latestRun = Run(RunId("run-1"), SessionId("session-1"), "running"),
                            latestRunState = RunPresentationState.RUNNING,
                        ),
                    onEvent = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Message").assertIsEnabled()
        composeTestRule.onNodeWithText("Send").assertDoesNotExist()
        composeTestRule.onNodeWithText("Stop").assertHasClickAction()
    }

    @Test
    fun confirmed_failure_shows_the_safe_error_and_keeps_try_again_explicit() {
        val events = mutableListOf<EntryUiEvent>()
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(
                    state =
                        connectedState(
                            composerText = "Keep this draft",
                            sendErrorCategory = MessageSendErrorCategory.GATEWAY_REQUEST_FAILED,
                        ),
                    onEvent = events::add,
                )
            }
        }

        composeTestRule.onNodeWithText("Keep this draft").assertIsDisplayed()
        composeTestRule
            .onNodeWithText(MessageSendErrorCategory.GATEWAY_REQUEST_FAILED.safeMessage)
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Try again").assertIsEnabled().performClick()

        assertEquals(listOf(EntryUiEvent.SendMessageClicked), events)
    }

    @Test
    fun known_failed_run_summary_exposes_the_explicit_retry_for_a_recoverable_original_input() {
        val events = mutableListOf<EntryUiEvent>()
        val run = Run(RunId("run-failed"), SessionId("session-1"), "failed")
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(
                    state =
                        connectedState(
                            latestRun = run,
                            latestRunState = RunPresentationState.FAILED,
                            latestRunRetryAvailable = true,
                        ),
                    onEvent = events::add,
                )
            }
        }

        composeTestRule.onNodeWithText("Run state: Failed").assertIsDisplayed()
        composeTestRule.onNodeWithText("Try again").assertIsDisplayed().assertHasClickAction().performClick()

        assertEquals(listOf<EntryUiEvent>(EntryUiEvent.RetryRunClicked(RunId("run-failed"))), events)
    }

    @Test
    fun known_failed_run_summary_offers_no_retry_without_a_recoverable_original_input() {
        val run = Run(RunId("run-failed"), SessionId("session-1"), "failed")
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(
                    state =
                        connectedState(
                            latestRun = run,
                            latestRunState = RunPresentationState.FAILED,
                        ),
                    onEvent = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Run state: Failed").assertIsDisplayed()
        composeTestRule.onNodeWithText("Try again").assertDoesNotExist()
    }

    @Test
    fun unresolved_failed_send_keeps_the_resolve_action_pressable_and_offers_discard() {
        val events = mutableListOf<EntryUiEvent>()
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(
                    state =
                        connectedState(
                            composerText = FAILED_SEND_COMPOSER_DRAFT,
                            sendErrorCategory = MessageSendErrorCategory.GATEWAY_REQUEST_FAILED,
                        ).withUnresolvedSubmission(),
                    onEvent = events::add,
                )
            }
        }

        composeTestRule.onNodeWithText("Refresh history to resolve").assertIsEnabled()
        composeTestRule.onNodeWithText("Discard unsent record").assertHasClickAction().performClick()

        assertEquals(
            listOf(EntryUiEvent.DiscardUnresolvedSendClicked),
            events,
        )
    }

    @Test
    fun send_control_keeps_a_button_role_and_stays_enabled_for_a_valid_draft() {
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(
                    state = connectedState(composerText = "Run this"),
                    onEvent = {},
                )
            }
        }

        val sendNode = composeTestRule.onNodeWithTag("conversation-send").fetchSemanticsNode()
        assertEquals(Role.Button, sendNode.config[SemanticsProperties.Role])
        composeTestRule.onNodeWithTag("conversation-send").assertIsEnabled()
    }

    @Test
    fun send_control_is_disabled_when_the_draft_is_blank() {
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(
                    state = connectedState(composerText = ""),
                    onEvent = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("conversation-send").assertIsNotEnabled()
    }

    @Test
    fun send_control_distinguishes_itself_from_the_keyboard_ime_send_action() {
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(
                    state = connectedState(composerText = "Run this"),
                    onEvent = {},
                )
            }
        }

        val sendNode = composeTestRule.onNodeWithTag("conversation-send").fetchSemanticsNode()
        assertEquals(listOf("Send message"), sendNode.config[SemanticsProperties.ContentDescription])
    }

    @Test
    fun composer_controls_expose_stable_resource_ids_for_assistive_tech() {
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(
                    state =
                        connectedState(
                            composerText = FAILED_SEND_COMPOSER_DRAFT,
                            sendErrorCategory = MessageSendErrorCategory.GATEWAY_REQUEST_FAILED,
                        ).withUnresolvedSubmission(),
                    onEvent = {},
                )
            }
        }

        val messageInput = composeTestRule.onNodeWithTag("conversation-message-input").fetchSemanticsNode()
        assertEquals("conversation-message-input", messageInput.config[SemanticsProperties.TestTag])
        val discard = composeTestRule.onNodeWithTag("conversation-discard-unsent").fetchSemanticsNode()
        assertEquals("conversation-discard-unsent", discard.config[SemanticsProperties.TestTag])
    }

    private fun connectedState(
        composerText: String = "",
        latestRun: Run? = null,
        latestRunState: RunPresentationState? = null,
        latestRunRetryAvailable: Boolean = false,
        sendErrorCategory: MessageSendErrorCategory? = null,
    ): EntryUiState =
        EntryUiState(
            title = "Gateway connected",
            supportingText = "Connected",
            actionLabel = "Connected",
            isConnected = true,
            sessionList =
                SessionListUiState(
                    openedSession =
                        OpenSessionUiState(
                            session = SessionItemUiState(SessionId("session-1"), "Session", null, false),
                            messages = emptyList(),
                            composerText = composerText,
                            latestRun = latestRun,
                            latestRunState = latestRunState,
                            latestRunRetryAvailable = latestRunRetryAvailable,
                            sendErrorCategory = sendErrorCategory,
                            hasUnresolvedSubmission = false,
                        ),
                ),
        )

    private fun EntryUiState.withUnresolvedSubmission(): EntryUiState =
        copy(
            sessionList =
                sessionList?.let { list ->
                    list.copy(openedSession = list.openedSession?.copy(hasUnresolvedSubmission = true))
                },
        )
}

private const val FAILED_SEND_COMPOSER_DRAFT = "Keep this unresolved draft"
