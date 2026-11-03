package org.mercurio.client.feature.entry.presentation

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mercurio.client.feature.entry.domain.RunId
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant

/**
 * The Message rows inside the authoritative Session history and the streamed response, plus the
 * message action menu whose Copy, Copy run ID and Share actions only an explicit user selection
 * runs. The run label lives in the menu's last line, never in the row itself.
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
                    val roles = listOf("user", "assistant", "developer", "gateway-special", null, "")
                    roles.forEachIndexed { index, role ->
                        ChatMessageRow(
                            row = chatRow(SessionMessageUiState("message-$index", role, "Message body $index")),
                            callbacks = inertCallbacks(),
                        )
                    }
                }
            }
        }
        composeTestRule.onNodeWithText("USER").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("ASSISTANT").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("DEVELOPER").performScrollTo().assertExists()
        composeTestRule.onNodeWithText("GATEWAY-SPECIAL").performScrollTo().assertExists()
        composeTestRule.onAllNodesWithText("SYSTEM").assertCountEquals(2)
        composeTestRule.onAllNodesWithText("Role:", substring = true).assertCountEquals(0)
        val header = composeTestRule.onNodeWithText("USER").fetchSemanticsNode().boundsInRoot
        val body = composeTestRule.onNodeWithText("Message body 0").fetchSemanticsNode().boundsInRoot
        assertTrue("The role header must lead the message it owns", header.bottom <= body.top)
    }

    @Test
    fun the_run_label_lives_in_the_action_menu_not_in_the_row() {
        composeTestRule.setContent {
            HermesTheme {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    ChatMessageRow(
                        row =
                            chatRow(
                                SessionMessageUiState(
                                    "message",
                                    "assistant",
                                    "Answer",
                                    runId = RunId("message-run"),
                                    timestamp = Instant.parse("2026-01-01T10:12:00Z"),
                                ),
                            ),
                        callbacks = inertCallbacks(),
                    )
                }
            }
        }
        composeTestRule.onAllNodesWithText("run mess", substring = true).assertCountEquals(0)
        openMessageMenu("message")
        composeTestRule.onAllNodesWithText("run mess", substring = true).assertCountEquals(1)
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
    fun an_assistant_reply_renders_markdown_blocks_instead_of_literal_markers() {
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
                                                    content =
                                                        "# Alpha Heading\n\n" +
                                                            "This is a paragraph.\n\n" +
                                                            "> This is a blockquote.\n\n" +
                                                            "---\n\n" +
                                                            "- First bullet\n- Second bullet",
                                                ),
                                            ),
                                    ),
                            ),
                        ),
                    onEvent = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Alpha Heading").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("This is a blockquote.").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("First bullet").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Second bullet").performScrollTo().assertIsDisplayed()
        composeTestRule.onAllNodesWithText("#", substring = true).assertCountEquals(0)
        composeTestRule.onAllNodesWithText("> This is a blockquote.").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("---").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("## Alpha Heading").assertCountEquals(0)
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
                                                runId = RunId("4b2a-run"),
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
        composeTestRule.onNodeWithText("[ copy ]").assertExists()
        composeTestRule.onAllNodesWithText("streaming", substring = true).assertCountEquals(1)
    }

    @Test
    fun every_message_row_exposes_a_visible_action_trigger() {
        composeTestRule.setContent {
            HermesTheme {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    listOf(
                        SessionMessageUiState("message-a", "user", "First body"),
                        SessionMessageUiState("message-b", "assistant", "Second body"),
                        SessionMessageUiState("message-c", "tool", "Third body"),
                    ).forEach { message ->
                        ChatMessageRow(row = chatRow(message), callbacks = inertCallbacks())
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
        val copies = mutableListOf<String>()
        val shares = mutableListOf<String>()
        composeTestRule.setContent {
            HermesTheme {
                ChatMessageRow(
                    row =
                        chatRow(
                            SessionMessageUiState(
                                id = "message-1",
                                role = "assistant",
                                content = "Safe **Markdown** text",
                                runId = RunId("run-secret"),
                                runStatus = "failed",
                                failureTechnicalDetail = "diagnostic detail",
                                timestamp = Instant.parse("2026-01-01T00:00:00Z"),
                            ),
                        ),
                    callbacks =
                        inertCallbacks().copy(
                            actions = MessageActions(openLink = {}, copy = { copies += it }, share = { shares += it }),
                        ),
                )
            }
        }

        assertTrue(copies.isEmpty())
        assertTrue(shares.isEmpty())
        openMessageMenu("message-1")
        composeTestRule.onNodeWithText("Share").assertIsDisplayed().assertIsEnabled().performClick()
        assertEquals(listOf("Safe **Markdown** text"), shares)
        assertTrue("Share must not copy", copies.isEmpty())
    }

    @Test
    fun opening_and_dismissing_the_message_menu_runs_no_copy_or_share() {
        val clipboard = clipboardManager()
        clipboard.setPrimaryClip(ClipData.newPlainText("before", "before"))
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
                                                SessionMessageUiState("message-1", "assistant", "Safe payload"),
                                            ),
                                    ),
                            ),
                        ),
                    onEvent = {},
                )
            }
        }

        openMessageMenu("message-1")
        composeTestRule.onNodeWithText("Copy message").assertIsDisplayed()
        composeTestRule.onNodeWithText("Share").assertIsDisplayed()
        assertEquals("before", clipboard.primaryClip?.getItemAt(0)?.text?.toString())

        composeTestRule.onNodeWithContentDescription("Message actions for message-1").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Copy message").assertDoesNotExist()
        composeTestRule.onNodeWithText("Share").assertDoesNotExist()
        assertEquals("before", clipboard.primaryClip?.getItemAt(0)?.text?.toString())
    }

    @Test
    fun a_message_without_content_offers_disabled_menu_items_without_callbacks() {
        val copies = mutableListOf<String>()
        val shares = mutableListOf<String>()
        composeTestRule.setContent {
            HermesTheme {
                ChatMessageRow(
                    row = chatRow(SessionMessageUiState("message-1", "assistant", null)),
                    callbacks =
                        inertCallbacks().copy(
                            actions = MessageActions(openLink = {}, copy = { copies += it }, share = { shares += it }),
                        ),
                )
            }
        }
        openMessageMenu("message-1")
        composeTestRule.onNodeWithText("Copy message").assertIsDisplayed().assertIsNotEnabled()
        composeTestRule.onNodeWithText("Share").assertIsDisplayed().assertIsNotEnabled()
        composeTestRule.onNodeWithText("Copy message").performTouchInput { click(center) }
        composeTestRule.onNodeWithText("Share").performTouchInput { click(center) }
        assertTrue(copies.isEmpty())
        assertTrue(shares.isEmpty())
    }

    @Test
    fun a_failed_message_offers_disabled_menu_items_without_a_synthetic_payload() {
        val copies = mutableListOf<String>()
        composeTestRule.setContent {
            HermesTheme {
                ChatMessageRow(
                    row =
                        chatRow(
                            SessionMessageUiState(
                                id = "message-1",
                                role = "assistant",
                                content = "Answer",
                                failureMessage = UiText.Value("This Run failed."),
                            ),
                        ),
                    callbacks =
                        inertCallbacks().copy(
                            actions = MessageActions(openLink = {}, copy = { copies += it }, share = {}),
                        ),
                )
            }
        }
        openMessageMenu("message-1")
        composeTestRule.onNodeWithText("Copy message").assertIsDisplayed().assertIsNotEnabled()
        composeTestRule.onNodeWithText("Copy message").performTouchInput { click(center) }
        assertTrue(copies.isEmpty())
    }

    @Test
    fun two_pane_message_menu_copy_and_share_carry_the_exact_selected_payload() {
        val clipboard = clipboardManager()
        clipboard.setPrimaryClip(ClipData.newPlainText("before", "before"))
        val state =
            mutableStateOf(
                twoPaneConversationState(messageId = "shared-message", content = "Safe **Markdown** text"),
            )
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(state = state.value, onEvent = {})
            }
        }

        assertEquals("before", clipboard.primaryClip?.getItemAt(0)?.text?.toString())
        openMessageMenu("shared-message")
        composeTestRule.onNodeWithText("Copy message").assertIsDisplayed().performClick()
        assertEquals("Safe **Markdown** text", clipboard.primaryClip?.getItemAt(0)?.text?.toString())
        openMessageMenu("shared-message")
        composeTestRule.onNodeWithText("Share").performClick()
        assertSharedPayload("Safe **Markdown** text")
        assertEquals("Safe **Markdown** text", clipboard.primaryClip?.getItemAt(0)?.text?.toString())
    }

    @Test
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
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Copy message").assertDoesNotExist()
        assertEquals("before", clipboard.primaryClip?.getItemAt(0)?.text?.toString())
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

    private fun inertCallbacks(): ChatCallbacks =
        ChatCallbacks(
            actions = MessageActions(openLink = {}, copy = {}, share = {}),
            retryEnabled = false,
            onRetry = {},
            onTableExpand = {},
            onEvent = {},
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
