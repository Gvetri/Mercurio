package org.hermesnative.client.feature.entry.presentation

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
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
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The renderer inside the authoritative Session history and the streamed response, plus
 * the message-level Copy and Share actions that only an explicit user selection runs.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-notnight")
class SessionMessageRenderingTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun authoritative_roles_form_a_readable_hierarchy_without_diagnostic_prefixes() {
        composeTestRule.setContent {
            HermesTheme {
                Column {
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
        composeTestRule.onNodeWithText("User").assertIsDisplayed()
        composeTestRule.onNodeWithText("Assistant").assertIsDisplayed()
        composeTestRule.onNodeWithText("tool").assertExists()
        composeTestRule.onNodeWithText("gateway-special").assertExists()
        composeTestRule.onAllNodesWithText("Role:", substring = true).assertCountEquals(0)
        composeTestRule.onAllNodesWithText("User").assertCountEquals(1)
        composeTestRule.onAllNodesWithText("Assistant").assertCountEquals(1)
        val user = composeTestRule.onNodeWithText("Message body 0").fetchSemanticsNode().boundsInRoot
        val assistant = composeTestRule.onNodeWithText("Message body 1").fetchSemanticsNode().boundsInRoot
        assertTrue("User content must be visually distinct from the assistant response", user.left > assistant.left)
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
    fun the_copy_action_carries_only_the_selected_message_content_and_needs_a_user_action() {
        val clipboard = clipboardManager()
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("before", "before"))

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
        composeTestRule.onNodeWithText("Copy message").performClick()
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
        composeTestRule.onNodeWithText("Share message").performClick()
        assertEquals(listOf("Safe **Markdown** text"), recorded.shared)
    }

    @Test
    fun copy_and_share_are_explicit_touch_targets() {
        val recorded = RecordingActions()
        composeTestRule.setContent {
            HermesTheme {
                SessionMessageContent(
                    message = SessionMessageUiState("message", "assistant", "Selected content"),
                    retryEnabled = false,
                    onRetry = {},
                    actions = recorded.asMessageActions(),
                )
            }
        }
        val copy =
            composeTestRule.onNode(hasText("Copy message") or hasContentDescription("Copy message"))
                .assertIsDisplayed().assertHasClickAction().assertHeightIsAtLeast(48.dp)
        val share =
            composeTestRule.onNode(hasText("Share message") or hasContentDescription("Share message"))
                .assertIsDisplayed().assertHasClickAction().assertHeightIsAtLeast(48.dp)
        copy.performClick()
        share.performClick()
        assertEquals(listOf("Selected content"), recorded.copied)
        assertEquals(listOf("Selected content"), recorded.shared)
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

    private fun clipboardManager(): ClipboardManager =
        RuntimeEnvironment.getApplication().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
}
