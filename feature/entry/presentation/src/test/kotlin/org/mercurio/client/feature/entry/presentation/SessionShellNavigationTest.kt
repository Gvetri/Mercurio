package org.mercurio.client.feature.entry.presentation

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.core.graphics.Insets
import androidx.core.view.WindowInsetsCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mercurio.client.feature.entry.domain.SessionId
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class SessionShellNavigationTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun system_back_on_a_phone_conversation_requests_the_session_list() {
        val events = mutableListOf<EntryUiEvent>()
        setContent(conversationEntryState(), events::add)

        composeTestRule.runOnUiThread {
            composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeTestRule.waitForIdle()

        assertEquals(listOf(EntryUiEvent.ReturnToSessionListClicked), events)
    }

    @Test
    fun system_back_on_the_session_list_is_not_intercepted() {
        val events = mutableListOf<EntryUiEvent>()
        setContent(
            entryState(SessionListUiState(sessions = listOf(session("first", "First Session")))),
            events::add,
        )

        composeTestRule.runOnUiThread {
            composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeTestRule.waitForIdle()

        assertTrue(events.isEmpty())
        assertTrue(composeTestRule.activity.isFinishing)
    }

    @Test
    @Config(qualifiers = "w1000dp-h800dp")
    fun system_back_on_a_two_pane_conversation_requests_the_session_list() {
        val events = mutableListOf<EntryUiEvent>()
        setContent(conversationEntryState(), events::add)

        composeTestRule.runOnUiThread {
            composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeTestRule.waitForIdle()

        assertEquals(listOf(EntryUiEvent.ReturnToSessionListClicked), events)
    }

    @Test
    fun system_back_dismisses_an_open_delete_confirmation() {
        val sessionId = SessionId("first")
        val events = mutableListOf<EntryUiEvent>()
        setContent(
            entryState(
                SessionListUiState(
                    sessions = listOf(session("first", "First Session")),
                    sessionMutations = mapOf(sessionId to SessionMutationUiState(delete = SessionDeleteUiState())),
                ),
            ),
            events::add,
        )

        composeTestRule.runOnUiThread {
            composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeTestRule.waitForIdle()

        assertEquals(listOf(EntryUiEvent.CancelDeleteSessionClicked(sessionId)), events)
        assertFalse(composeTestRule.activity.isFinishing)
    }

    @Test
    @Config(qualifiers = "w1000dp-h800dp")
    fun system_back_dismisses_a_rename_confirmation_before_leaving_the_conversation() {
        val sessionId = SessionId("first")
        val events = mutableListOf<EntryUiEvent>()
        setContent(
            entryState(
                SessionListUiState(
                    sessions = listOf(session("first", "First Session")),
                    openedSession =
                        OpenSessionUiState(
                            session = session("first", "First Session"),
                            messages = listOf(message("message-1", "user", "Conversation message")),
                        ),
                    sessionMutations =
                        mapOf(sessionId to SessionMutationUiState(rename = SessionRenameUiState("First Session"))),
                ),
            ),
            events::add,
        )

        composeTestRule.runOnUiThread {
            composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeTestRule.waitForIdle()

        assertEquals(listOf(EntryUiEvent.CancelRenameSessionClicked(sessionId)), events)
    }

    @Test
    fun delivered_system_bar_insets_move_the_shell_content() {
        val topInset = 120
        val bottomInset = 48
        setContent(conversationEntryState(), {})

        val backTopBefore = composeTestRule.onNodeWithText("Back to Sessions").fetchSemanticsNode().boundsInWindow.top
        val sendBottomBefore = composeTestRule.onNodeWithText("Send").fetchSemanticsNode().boundsInWindow.bottom

        composeTestRule.runOnUiThread {
            val insets =
                WindowInsetsCompat
                    .Builder()
                    .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(0, topInset, 0, bottomInset))
                    .build()
            composeTestRule.activity.window.decorView.dispatchApplyWindowInsets(insets.toWindowInsets())
        }
        composeTestRule.waitForIdle()

        val backTopAfter = composeTestRule.onNodeWithText("Back to Sessions").fetchSemanticsNode().boundsInWindow.top
        val sendBottomAfter = composeTestRule.onNodeWithText("Send").fetchSemanticsNode().boundsInWindow.bottom

        assertEquals(topInset.toFloat(), backTopAfter - backTopBefore, 0.5f)
        assertEquals(bottomInset.toFloat(), sendBottomBefore - sendBottomAfter, 0.5f)
    }

    @Test
    fun rename_ime_keeps_the_focused_title_above_the_keyboard() {
        val id = SessionId("first")
        setContent(
            entryState(
                SessionListUiState(
                    sessions = listOf(session("first", "First Session")),
                    sessionMutations =
                        mapOf(id to SessionMutationUiState(rename = SessionRenameUiState("First Session"))),
                ),
            ),
            {},
        )
        composeTestRule.onNodeWithContentDescription("Session title").performScrollTo().performClick()
        val imeInset = 320
        composeTestRule.runOnUiThread {
            val insets =
                WindowInsetsCompat
                    .Builder()
                    .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, imeInset))
                    .setVisible(WindowInsetsCompat.Type.ime(), true)
                    .build()
            composeTestRule.activity.window.decorView.dispatchApplyWindowInsets(insets.toWindowInsets())
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Session title").performScrollTo().assertIsDisplayed()
        val keyboardTop = composeTestRule.activity.window.decorView.height - imeInset + 1
        val titleBottom = composeTestRule.onNodeWithContentDescription("Session title").fetchSemanticsNode().boundsInWindow.bottom
        assertTrue("The Rename input must remain above the keyboard", titleBottom <= keyboardTop)
    }

    @Test
    fun search_ime_keeps_the_status_strip_above_the_keyboard() {
        setContent(
            entryState(SessionListUiState(sessions = listOf(session("first", "First Session")))),
            {},
        )
        val imeInset = 320
        composeTestRule.onNodeWithContentDescription("Search Sessions").performClick()

        composeTestRule.runOnUiThread {
            val insets =
                WindowInsetsCompat
                    .Builder()
                    .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, imeInset))
                    .setVisible(WindowInsetsCompat.Type.ime(), true)
                    .build()
            composeTestRule.activity.window.decorView.dispatchApplyWindowInsets(insets.toWindowInsets())
        }
        composeTestRule.waitForIdle()

        val keyboardTop = composeTestRule.activity.window.decorView.height - imeInset + 1
        val statusBottom = composeTestRule.onNodeWithText("gateway :: ready").fetchSemanticsNode().boundsInWindow.bottom
        assertTrue("The status strip must stay above the keyboard", statusBottom <= keyboardTop)
    }

    private fun setContent(
        state: EntryUiState,
        onEvent: (EntryUiEvent) -> Unit,
    ) {
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(state = state, onEvent = onEvent)
            }
        }
    }
}
