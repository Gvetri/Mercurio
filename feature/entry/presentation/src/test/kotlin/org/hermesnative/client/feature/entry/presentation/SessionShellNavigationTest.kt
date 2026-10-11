package org.hermesnative.client.feature.entry.presentation

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.core.graphics.Insets
import androidx.core.view.WindowInsetsCompat
import org.hermesnative.client.feature.entry.domain.SessionId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

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

    /**
     * Robolectric's paused looper cannot settle the standard dialog window with its text field
     * (robolectric/robolectric#8460). Only the looper mode changes here; the asserted behavior
     * is the same, and the real dialog window is exercised on a device by the journey layer.
     */
    @LooperMode(LooperMode.Mode.LEGACY)
    @Test
    fun system_back_on_the_creation_form_cancels_creation() {
        val events = mutableListOf<EntryUiEvent>()
        setContent(
            entryState(SessionListUiState(createSession = SessionCreationUiState())),
            events::add,
        )

        composeTestRule.runOnUiThread {
            composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeTestRule.waitForIdle()

        assertEquals(listOf(EntryUiEvent.CancelCreateSessionClicked), events)
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

    /**
     * Robolectric's paused looper cannot settle the standard dialog window with its text field
     * (robolectric/robolectric#8460). Only the looper mode changes here; the asserted behavior
     * is the same, and the real dialog window is exercised on a device by the journey layer.
     */
    @LooperMode(LooperMode.Mode.LEGACY)
    @Test
    @Config(qualifiers = "w1000dp-h800dp")
    fun system_back_cancels_creation_in_the_two_pane_detail_pane() {
        val events = mutableListOf<EntryUiEvent>()
        setContent(
            entryState(SessionListUiState(createSession = SessionCreationUiState())),
            events::add,
        )

        composeTestRule.runOnUiThread {
            composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeTestRule.waitForIdle()

        assertEquals(listOf(EntryUiEvent.CancelCreateSessionClicked), events)
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
        composeTestRule.onNodeWithText("New Session title").performScrollTo().performClick()
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
        composeTestRule.onNodeWithText("New Session title").performScrollTo().assertIsDisplayed()
        val keyboardTop = composeTestRule.activity.window.decorView.height - imeInset + 1
        val titleBottom = composeTestRule.onNodeWithText("New Session title").fetchSemanticsNode().boundsInWindow.bottom
        assertTrue("The Rename input must remain above the keyboard", titleBottom <= keyboardTop)
    }

    @Test
    fun search_ime_keeps_pinned_session_controls_above_the_keyboard() {
        setContent(
            entryState(SessionListUiState(sessions = listOf(session("first", "First Session")))),
            {},
        )
        val imeInset = 320
        composeTestRule.onNodeWithText("Search Sessions").performClick()

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
        val refreshBottom = composeTestRule.onNodeWithText("Refresh").fetchSemanticsNode().boundsInWindow.bottom
        val notificationsBottom =
            composeTestRule.onNodeWithContentDescription("Run status notifications")
                .fetchSemanticsNode().boundsInWindow.bottom
        assertTrue("Refresh must stay above the keyboard", refreshBottom <= keyboardTop)
        assertTrue("notification controls must stay above the keyboard", notificationsBottom <= keyboardTop)
    }

    @Test
    fun search_ime_padding_is_removed_when_keyboard_hides_even_if_insets_remain() {
        setContent(
            entryState(SessionListUiState(sessions = listOf(session("first", "First Session")))),
            {},
        )
        val imeInset = 320
        val windowHeight = composeTestRule.activity.window.decorView.height.toFloat()
        val notificationsBottomBefore =
            composeTestRule.onNodeWithContentDescription("Run status notifications")
                .fetchSemanticsNode().boundsInWindow.bottom
        composeTestRule.onNodeWithText("Search Sessions").performClick()

        fun dispatchImeInsets(isVisible: Boolean) {
            composeTestRule.runOnUiThread {
                val insets =
                    WindowInsetsCompat
                        .Builder()
                        .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, imeInset))
                        .setVisible(WindowInsetsCompat.Type.ime(), isVisible)
                        .build()
                composeTestRule.activity.window.decorView.dispatchApplyWindowInsets(insets.toWindowInsets())
            }
            composeTestRule.waitForIdle()
        }

        dispatchImeInsets(isVisible = true)
        val notificationsBottomWhileVisible =
            composeTestRule.onNodeWithContentDescription("Run status notifications")
                .fetchSemanticsNode().boundsInWindow.bottom
        assertTrue(notificationsBottomWhileVisible <= windowHeight - imeInset + 1f)

        dispatchImeInsets(isVisible = false)
        val notificationsBottomAfterHidden =
            composeTestRule.onNodeWithContentDescription("Run status notifications")
                .fetchSemanticsNode().boundsInWindow.bottom
        assertTrue(
            "the controls must return to the full pane when the IME is hidden",
            notificationsBottomAfterHidden - notificationsBottomWhileVisible >= imeInset * 0.75f,
        )
        assertEquals(notificationsBottomBefore, notificationsBottomAfterHidden, 1f)
    }

    @Test
    fun delivered_ime_insets_do_not_collapse_the_session_list() {
        setContent(
            entryState(SessionListUiState(sessions = listOf(session("first", "First Session")))),
            {},
        )
        val rowHeightBefore =
            composeTestRule.onNodeWithText("First Session").fetchSemanticsNode().boundsInWindow.height

        composeTestRule.runOnUiThread {
            val insets =
                WindowInsetsCompat
                    .Builder()
                    .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, 900))
                    .setVisible(WindowInsetsCompat.Type.ime(), true)
                    .build()
            composeTestRule.activity.window.decorView.dispatchApplyWindowInsets(insets.toWindowInsets())
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("First Session").assertIsDisplayed()
        val rowHeightAfter =
            composeTestRule.onNodeWithText("First Session").fetchSemanticsNode().boundsInWindow.height
        assertEquals(rowHeightBefore, rowHeightAfter, 0.5f)
    }

    /**
     * Robolectric's paused looper cannot settle the standard dialog window with its text field
     * (robolectric/robolectric#8460). Only the looper mode changes here; the asserted behavior
     * is the same, and the real dialog window is exercised on a device by the journey layer.
     */
    @LooperMode(LooperMode.Mode.LEGACY)
    @Test
    @Config(qualifiers = "w891dp-h411dp")
    fun create_dialog_actions_stay_reachable_at_twice_the_font_scale() {
        val events = mutableListOf<EntryUiEvent>()
        composeTestRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                HermesTheme {
                    EntryScreen(
                        state =
                            entryState(
                                SessionListUiState(
                                    createSession = SessionCreationUiState(titleDraft = "Draft title"),
                                ),
                            ),
                        onEvent = events::add,
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("Create").assertHasClickAction().assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").assertHasClickAction().assertIsDisplayed()
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
