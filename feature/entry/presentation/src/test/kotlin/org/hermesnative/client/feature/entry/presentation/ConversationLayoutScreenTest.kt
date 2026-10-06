package org.hermesnative.client.feature.entry.presentation

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import org.hermesnative.client.feature.entry.domain.Run
import org.hermesnative.client.feature.entry.domain.RunId
import org.hermesnative.client.feature.entry.domain.RunPresentationState
import org.hermesnative.client.feature.entry.domain.SessionId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class ConversationLayoutScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun composer_position_stays_stable_when_empty_history_receives_its_first_reply() {
        var conversation by
            mutableStateOf(
                OpenSessionUiState(
                    session = SessionItemUiState(SessionId("session"), "A Session", null, false),
                    messages = emptyList(),
                ),
            )
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(
                    state =
                        EntryUiState(
                            title = "Connected",
                            supportingText = "Connected",
                            actionLabel = "Connected",
                            isConnected = true,
                            sessionList = SessionListUiState(openedSession = conversation),
                        ),
                    onEvent = {},
                )
            }
        }
        val emptyBounds = composeTestRule.onNodeWithTag("conversation-composer-tail").fetchSemanticsNode().boundsInRoot
        composeTestRule.runOnIdle {
            conversation = conversation.copy(messages = listOf(SessionMessageUiState("reply", "assistant", "First reply")))
        }
        val replyBounds = composeTestRule.onNodeWithTag("conversation-composer-tail").fetchSemanticsNode().boundsInRoot
        assertEquals("The first reply must not move the composer", emptyBounds.top, replyBounds.top, 1f)
        assertEquals("The composer must retain its bottom anchor", emptyBounds.bottom, replyBounds.bottom, 1f)
    }

    @Test
    fun composer_keeps_its_bottom_anchor_and_focus_across_conversation_states() {
        val initial =
            OpenSessionUiState(
                session = SessionItemUiState(SessionId("session"), "A Session", null, false),
                messages = emptyList(),
                composerText = "Draft",
            )
        var conversation by mutableStateOf(initial)
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(entryState(SessionListUiState(openedSession = conversation)), onEvent = {})
            }
        }
        val composer = composeTestRule.onNodeWithText("Message").performClick().assertIsFocused()
        val bottom = composer.fetchSemanticsNode().boundsInRoot.bottom
        val run = Run(RunId("run"), initial.session.id, "running")
        val response = SessionMessageUiState("response", "assistant", "First reply", isStreaming = true)
        val cases =
            listOf(
                "sending" to initial.copy(isSending = true),
                "streaming" to initial.copy(latestRun = run, latestRunState = RunPresentationState.RUNNING, activeResponse = response),
                "settled" to initial.copy(messages = listOf(response.copy(isStreaming = false))),
                "failed" to initial.copy(sendErrorCategory = MessageSendErrorCategory.GATEWAY_REQUEST_FAILED),
                "uncertain" to initial.copy(sendErrorCategory = MessageSendErrorCategory.UNCERTAIN, hasUnresolvedSubmission = true),
            )
        cases.forEach { (name, state) ->
            composeTestRule.runOnIdle { conversation = state }
            composer.assertIsFocused()
            assertEquals("$name must keep the composer anchored", bottom, composer.fetchSemanticsNode().boundsInRoot.bottom, 1f)
        }
    }

    @Test
    fun send_is_adjacent_to_a_multiline_draft() {
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(
                    state =
                        EntryUiState(
                            title = "Connected",
                            supportingText = "Connected",
                            actionLabel = "Connected",
                            isConnected = true,
                            sessionList =
                                SessionListUiState(
                                    openedSession =
                                        OpenSessionUiState(
                                            session = SessionItemUiState(SessionId("session"), "A Session", null, false),
                                            messages = emptyList(),
                                            composerText = "First line\nSecond line\nThird line",
                                        ),
                                ),
                        ),
                    onEvent = {},
                )
            }
        }
        val draft = composeTestRule.onNodeWithText("Message").fetchSemanticsNode().boundsInRoot
        val send = composeTestRule.onNodeWithText("Send").fetchSemanticsNode().boundsInRoot
        assertTrue("Send must be beside the draft, not in a row below it", send.left >= draft.right)
        assertTrue("Send must share the draft's vertical area", send.center.y in draft.top..draft.bottom)
    }

    @Test
    @Config(qualifiers = "w411dp-h480dp")
    fun an_uncertain_outcome_does_not_squeeze_out_the_draft_at_large_font_scale() {
        composeTestRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                HermesTheme {
                    EntryScreen(
                        entryState(
                            SessionListUiState(
                                openedSession =
                                    OpenSessionUiState(
                                        session("first", "First Session"),
                                        emptyList(),
                                        composerText = "Retained draft",
                                        sendErrorCategory = MessageSendErrorCategory.UNCERTAIN,
                                        hasUnresolvedSubmission = true,
                                    ),
                            ),
                        ),
                        onEvent = {},
                    )
                }
            }
        }
        val send =
            composeTestRule.onNode(
                hasClickAction() and (hasText("Send") or hasText("Refresh history to resolve")),
            ).performScrollTo().assertIsNotEnabled().fetchSemanticsNode().boundsInRoot
        val draft = composeTestRule.onNodeWithText("Message").fetchSemanticsNode().boundsInRoot
        assertTrue("The draft must keep more room than the disabled Send control", draft.width > send.width)
    }

    @Test
    fun back_title_and_session_menu_share_one_compact_bar() {
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(
                    state =
                        EntryUiState(
                            title = "Connected",
                            supportingText = "Connected",
                            actionLabel = "Connected",
                            isConnected = true,
                            sessionList =
                                SessionListUiState(
                                    openedSession =
                                        OpenSessionUiState(
                                            session = SessionItemUiState(SessionId("session"), "A Session", null, false),
                                            messages = emptyList(),
                                        ),
                                ),
                        ),
                    onEvent = {},
                )
            }
        }
        val back =
            composeTestRule.onNode(hasText("Back to Sessions") or hasContentDescription("Back to Sessions"))
                .fetchSemanticsNode().boundsInRoot
        val title = composeTestRule.onNodeWithText("A Session").fetchSemanticsNode().boundsInRoot
        val menu = composeTestRule.onNodeWithContentDescription("Session actions for A Session").fetchSemanticsNode().boundsInRoot
        assertTrue("The title must share Back's row", title.center.y in back.top..back.bottom)
        assertTrue("The title must share the menu's row", title.center.y in menu.top..menu.bottom)
        assertTrue("The title must sit between the two controls", title.left >= back.right && title.right <= menu.left)
    }
}
