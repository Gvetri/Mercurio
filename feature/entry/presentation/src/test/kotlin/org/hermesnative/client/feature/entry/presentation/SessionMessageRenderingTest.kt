package org.hermesnative.client.feature.entry.presentation

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import android.view.inspector.WindowInspector
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import org.hermesnative.client.feature.entry.domain.Run
import org.hermesnative.client.feature.entry.domain.RunId
import org.hermesnative.client.feature.entry.domain.RunPresentationState
import org.hermesnative.client.feature.entry.domain.SessionId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The renderer inside the authoritative Session history and the streamed response, plus
 * the message-level three-dot menu whose Copy and Share actions only an explicit user
 * selection runs.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-notnight")
class SessionMessageRenderingTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun authoritative_roles_form_a_readable_hierarchy_without_diagnostic_prefixes() {
        composeTestRule.setContent {
            HermesTheme {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    listOf("user", "assistant", "tool", "gateway-special", null, "").forEachIndexed { index, role ->
                        SessionMessageContent(
                            message = SessionMessageUiState("message-$index", role, "Message body $index"),
                            retryEnabled = false,
                            onRetry = {},
                        )
                    }
                }
            }
        }
        composeTestRule.onNodeWithText("User").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Assistant").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("tool").performScrollTo().assertExists()
        composeTestRule.onNodeWithText("gateway-special").performScrollTo().assertExists()
        composeTestRule.onAllNodesWithText("Role unavailable").assertCountEquals(2)
        composeTestRule.onAllNodesWithText("Role:", substring = true).assertCountEquals(0)
        composeTestRule.onAllNodesWithText("User").assertCountEquals(1)
        composeTestRule.onAllNodesWithText("Assistant").assertCountEquals(1)
        val header = composeTestRule.onNodeWithText("User").fetchSemanticsNode().boundsInRoot
        val body = composeTestRule.onNodeWithText("Message body 0").fetchSemanticsNode().boundsInRoot
        assertTrue("The role header must lead the message it owns", header.bottom <= body.top)
    }

    @Test
    fun authoritative_history_renders_supported_markdown_as_native_text() {
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(
                    state =
                        entryState(
                            SessionListUiState(
                                sessions = listOf(session("first", "First Session")),
                                openedSession =
                                    OpenSessionUiState(
                                        session = session("first", "First Session"),
                                        messages =
                                            listOf(
                                                SessionMessageUiState(
                                                    id = "message-1",
                                                    role = "assistant",
                                                    content = "Safe **Markdown** text",
                                                ),
                                            ),
                                    ),
                            ),
                        ),
                    onEvent = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Safe Markdown text").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("**", substring = true).assertCountEquals(0)
    }

    @Test
    fun the_streamed_response_and_its_code_block_render_safely_while_it_grows() {
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(
                    state =
                        entryState(
                            SessionListUiState(
                                sessions = listOf(session("first", "First Session")),
                                openedSession =
                                    OpenSessionUiState(
                                        session = session("first", "First Session"),
                                        messages =
                                            listOf(
                                                SessionMessageUiState(
                                                    id = "message-1",
                                                    role = "user",
                                                    content = "Run the build",
                                                ),
                                            ),
                                        activeResponse =
                                            SessionMessageUiState(
                                                id = "message-2",
                                                role = "assistant",
                                                content = "Building now\n\n```kotlin\nval total = 1\n```",
                                                runId = RunId("run-1"),
                                                isStreaming = true,
                                            ),
                                    ),
                            ),
                        ),
                    onEvent = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Building now").assertIsDisplayed()
        composeTestRule.onNodeWithText("val total = 1").assertExists()
        composeTestRule.onNodeWithText("Copy code").assertExists()
        composeTestRule.onNodeWithText("Streaming response…").assertExists()
    }

    @Test
    fun every_message_card_exposes_a_visible_three_dot_actions_trigger() {
        composeTestRule.setContent {
            HermesTheme {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    listOf(
                        SessionMessageUiState("message-a", "user", "First body"),
                        SessionMessageUiState("message-b", "assistant", "Second body"),
                        SessionMessageUiState("message-c", "tool", "Third body"),
                    ).forEach { message ->
                        SessionMessageContent(message = message, retryEnabled = false, onRetry = {})
                    }
                }
            }
        }
        listOf("message-a", "message-b", "message-c").forEach { messageId ->
            composeTestRule
                .onNodeWithContentDescription("Message actions for $messageId")
                .performScrollTo()
                .assertIsDisplayed()
                .assertHasClickAction()
                .assertHeightIsAtLeast(48.dp)
        }
    }

    @Test
    fun the_copy_action_carries_only_the_selected_message_content_and_needs_a_user_action() {
        val clipboard = clipboardManager()
        clipboard.setPrimaryClip(ClipData.newPlainText("before", "before"))

        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(
                    state =
                        entryState(
                            SessionListUiState(
                                sessions = listOf(session("first", "First Session")),
                                openedSession =
                                    OpenSessionUiState(
                                        session = session("first", "First Session"),
                                        messages =
                                            listOf(
                                                SessionMessageUiState(
                                                    id = "message-1",
                                                    role = "assistant",
                                                    content = "Safe **Markdown** text",
                                                    runId = RunId("run-secret"),
                                                    runStatus = "failed",
                                                    failureTechnicalDetail = "diagnostic detail",
                                                ),
                                            ),
                                    ),
                            ),
                        ),
                    onEvent = {},
                )
            }
        }

        assertEquals("before", clipboard.primaryClip?.getItemAt(0)?.text?.toString())
        openMessageMenu("message-1")
        assertEquals(
            "Opening the menu must not copy anything",
            "before",
            clipboard.primaryClip?.getItemAt(0)?.text?.toString(),
        )
        composeTestRule.onNodeWithText("Copy message").assertIsDisplayed().assertIsEnabled().performClick()
        assertEquals("Safe **Markdown** text", clipboard.primaryClip?.getItemAt(0)?.text?.toString())
    }

    @Test
    fun the_share_action_carries_only_the_selected_message_content() {
        val recorded = RecordingActions()
        composeTestRule.setContent {
            HermesTheme {
                SessionMessageContent(
                    message =
                        SessionMessageUiState(
                            id = "message-1",
                            role = "assistant",
                            content = "Safe **Markdown** text",
                            runId = RunId("run-secret"),
                            runStatus = "failed",
                            failureTechnicalDetail = "diagnostic detail",
                            timestamp = java.time.Instant.parse("2026-01-01T00:00:00Z"),
                        ),
                    retryEnabled = false,
                    onRetry = {},
                    actions = recorded.asMessageActions(),
                )
            }
        }

        assertTrue("Nothing may be shared before the user acts", recorded.shared.isEmpty())
        openMessageMenu("message-1")
        assertTrue("Opening the menu must not share anything", recorded.shared.isEmpty())
        composeTestRule.onNodeWithText("Share message").assertIsDisplayed().assertIsEnabled().performClick()
        assertEquals(listOf("Safe **Markdown** text"), recorded.shared)
    }

    @Test
    fun opening_and_dismissing_the_message_menu_runs_no_copy_or_share() {
        val clipboard = clipboardManager()
        clipboard.setPrimaryClip(ClipData.newPlainText("before", "before"))
        val events = mutableListOf<EntryUiEvent>()
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(
                    state = twoPaneConversationState(messageId = "message-1", content = "Selected content"),
                    onEvent = events::add,
                )
            }
        }
        openMessageMenu("message-1")
        composeTestRule.onNodeWithText("Copy message").assertIsDisplayed()
        composeTestRule.onNodeWithText("Share message").assertIsDisplayed()
        assertEquals("before", clipboard.primaryClip?.getItemAt(0)?.text?.toString())
        assertNull(shadowOf(composeTestRule.activity).nextStartedActivity)
        composeTestRule.runOnUiThread {
            val popup =
                WindowInspector.getGlobalWindowViews().single { it !== composeTestRule.activity.window.decorView }
            popup.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
            popup.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))
        }
        composeTestRule.onNodeWithText("Copy message").assertDoesNotExist()
        composeTestRule.onNodeWithText("Share message").assertDoesNotExist()
        assertEquals("before", clipboard.primaryClip?.getItemAt(0)?.text?.toString())
        assertNull(shadowOf(composeTestRule.activity).nextStartedActivity)
        assertTrue(events.isEmpty())
        assertFalse(composeTestRule.activity.isFinishing)
    }

    @Test
    fun a_message_without_content_offers_disabled_copy_and_share_items_without_callbacks() {
        val recorded = RecordingActions()
        composeTestRule.setContent {
            HermesTheme {
                SessionMessageContent(
                    message = SessionMessageUiState("message-empty", "assistant", null),
                    retryEnabled = false,
                    onRetry = {},
                    actions = recorded.asMessageActions(),
                )
            }
        }

        openMessageMenu("message-empty")
        composeTestRule.onNodeWithText("Copy message").assertIsDisplayed().assertIsNotEnabled()
        composeTestRule.onNodeWithText("Share message").assertIsDisplayed().assertIsNotEnabled()
        composeTestRule.onNodeWithText("Copy message").performTouchInput { click(center) }
        composeTestRule.onNodeWithText("Share message").performTouchInput { click(center) }
        composeTestRule.waitForIdle()
        assertTrue("Disabled items must run no callback", recorded.copied.isEmpty() && recorded.shared.isEmpty())
    }

    @Test
    fun a_failed_message_offers_disabled_menu_items_without_a_synthetic_payload() {
        val recorded = RecordingActions()
        composeTestRule.setContent {
            HermesTheme {
                SessionMessageContent(
                    message =
                        SessionMessageUiState(
                            id = "message-failed",
                            role = "assistant",
                            content = "Unsafe **Markdown** text",
                            failureSafeMessage = "The message was not sent.",
                        ),
                    retryEnabled = false,
                    onRetry = {},
                    actions = recorded.asMessageActions(),
                )
            }
        }

        openMessageMenu("message-failed")
        composeTestRule.onNodeWithText("Copy message").assertIsDisplayed().assertIsNotEnabled()
        composeTestRule.onNodeWithText("Share message").assertIsDisplayed().assertIsNotEnabled()
        composeTestRule.onNodeWithText("Copy message").performTouchInput { click(center) }
        composeTestRule.waitForIdle()
        assertTrue(
            "A failed message must not export its content",
            recorded.copied.isEmpty() && recorded.shared.isEmpty(),
        )
    }

    @Test
    @Config(qualifiers = "w1000dp-h800dp")
    fun two_pane_message_menu_copy_and_share_carry_the_exact_selected_payload() {
        val clipboard = clipboardManager()
        clipboard.setPrimaryClip(ClipData.newPlainText("before", "before"))

        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(
                    state = twoPaneConversationState(messageId = "message-1", content = "Safe **Markdown** text"),
                    onEvent = {},
                )
            }
        }

        assertEquals("before", clipboard.primaryClip?.getItemAt(0)?.text?.toString())
        openMessageMenu("message-1")
        composeTestRule.onNodeWithText("Copy message").assertIsDisplayed().performClick()
        assertEquals("Safe **Markdown** text", clipboard.primaryClip?.getItemAt(0)?.text?.toString())
        assertNull(shadowOf(composeTestRule.activity).nextStartedActivity)
        openMessageMenu("message-1")
        composeTestRule.onNodeWithText("Share message").performClick()
        assertSharedPayload("Safe **Markdown** text")
        assertEquals("Safe **Markdown** text", clipboard.primaryClip?.getItemAt(0)?.text?.toString())
    }

    @Test
    @Config(qualifiers = "w1000dp-h800dp")
    fun a_changed_session_owner_dismisses_the_menu_even_when_the_message_id_is_reused() {
        val clipboard = clipboardManager()
        clipboard.setPrimaryClip(ClipData.newPlainText("before", "before"))
        val state = mutableStateOf(twoPaneConversationState(messageId = "shared-message", content = "Alpha payload"))

        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(state = state.value, onEvent = {})
            }
        }

        openMessageMenu("shared-message")
        composeTestRule.onNodeWithText("Copy message").assertIsDisplayed()
        composeTestRule.runOnIdle {
            state.value =
                twoPaneConversationState(
                    messageId = "shared-message",
                    content = "Beta payload",
                    owner = session("second", "Second Session"),
                )
        }
        composeTestRule.onNodeWithText("Copy message").assertDoesNotExist()
        assertEquals(
            "A changed selection must not copy or share anything",
            "before",
            clipboard.primaryClip?.getItemAt(0)?.text?.toString(),
        )
        composeTestRule.onNodeWithText("Share message").assertDoesNotExist()
        assertNull(shadowOf(composeTestRule.activity).nextStartedActivity)
        openMessageMenu("shared-message")
        composeTestRule.onNodeWithText("Share message").performClick()
        assertSharedPayload("Beta payload")
        assertEquals("before", clipboard.primaryClip?.getItemAt(0)?.text?.toString())
        openMessageMenu("shared-message")
        composeTestRule.onNodeWithText("Copy message").assertIsDisplayed().performClick()
        assertEquals("Beta payload", clipboard.primaryClip?.getItemAt(0)?.text?.toString())
        assertNull(shadowOf(composeTestRule.activity).nextStartedActivity)
        openMessageMenu("shared-message")
        composeTestRule.runOnIdle {
            state.value = twoPaneConversationState(messageId = "shared-message", content = "Alpha payload")
        }
        composeTestRule.onNodeWithText("Copy message").assertDoesNotExist()
        composeTestRule.onNodeWithText("Share message").assertDoesNotExist()
        assertEquals("Beta payload", clipboard.primaryClip?.getItemAt(0)?.text?.toString())
        assertNull(shadowOf(composeTestRule.activity).nextStartedActivity)
        openMessageMenu("shared-message")
        composeTestRule.onNodeWithText("Copy message").performClick()
        assertEquals("Alpha payload", clipboard.primaryClip?.getItemAt(0)?.text?.toString())
        openMessageMenu("shared-message")
        composeTestRule.onNodeWithText("Share message").performClick()
        assertSharedPayload("Alpha payload")
    }

    @Test
    fun run_identifiers_are_secondary_to_message_content_and_run_state() {
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(
                    state =
                        entryState(
                            SessionListUiState(
                                openedSession =
                                    OpenSessionUiState(
                                        session = session("first", "First Session"),
                                        messages =
                                            listOf(
                                                SessionMessageUiState(
                                                    "message",
                                                    "assistant",
                                                    "Answer",
                                                    runId = RunId("message-run"),
                                                ),
                                            ),
                                        latestRun = Run(RunId("latest-run"), SessionId("first"), "succeeded"),
                                        latestRunState = RunPresentationState.SUCCEEDED,
                                    ),
                            ),
                        ),
                    onEvent = {},
                )
            }
        }
        val content = composeTestRule.onNodeWithText("Answer").fetchSemanticsNode().boundsInRoot
        val messageId = composeTestRule.onNodeWithText("Run ID: message-run").fetchSemanticsNode().boundsInRoot
        val latestId = composeTestRule.onNodeWithText("Latest Run: latest-run").fetchSemanticsNode().boundsInRoot
        val status =
            composeTestRule.onNodeWithText("Run state: ${RunPresentationState.SUCCEEDED.label}")
                .fetchSemanticsNode().boundsInRoot
        assertTrue("Run ID $messageId must be secondary to content $content", messageId.height < content.height)
        assertTrue("Run state must be more prominent than its raw identifier", latestId.height < status.height)
    }

    private fun twoPaneConversationState(
        messageId: String,
        content: String,
        owner: SessionItemUiState = session("first", "First Session"),
    ): EntryUiState =
        entryState(
            SessionListUiState(
                sessions = listOf(session("first", "First Session"), session("second", "Second Session")),
                openedSession =
                    OpenSessionUiState(
                        session = owner,
                        messages = listOf(message(messageId, "assistant", content)),
                    ),
            ),
        )

    private fun assertSharedPayload(expected: String) {
        val chooser = shadowOf(composeTestRule.activity).nextStartedActivity
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        val share = chooser.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        assertEquals(Intent.ACTION_SEND, share?.action)
        assertEquals("text/plain", share?.type)
        assertEquals(expected, share?.getStringExtra(Intent.EXTRA_TEXT))
        assertNull(shadowOf(composeTestRule.activity).nextStartedActivity)
    }

    private fun openMessageMenu(messageId: String) {
        composeTestRule
            .onNodeWithContentDescription("Message actions for $messageId")
            .apply { if (!isDisplayed()) performScrollTo() }
            .assertIsDisplayed()
            .performClick()
        composeTestRule.waitForIdle()
    }

    private fun clipboardManager(): ClipboardManager =
        RuntimeEnvironment.getApplication().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
}
