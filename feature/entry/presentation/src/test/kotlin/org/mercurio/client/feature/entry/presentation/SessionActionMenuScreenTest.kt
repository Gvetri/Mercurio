package org.mercurio.client.feature.entry.presentation

import android.view.KeyEvent
import android.view.inspector.WindowInspector
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
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
class SessionActionMenuScreenTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun list_row_actions_are_reachable_only_after_a_hold() {
        val events = mutableListOf<EntryUiEvent>()
        setSurface(events)

        composeTestRule.onNodeWithText("Pin").assertDoesNotExist()
        composeTestRule.onNodeWithText("Rename").assertDoesNotExist()
        composeTestRule.onNodeWithText("Delete").assertDoesNotExist()

        composeTestRule.onNodeWithText("First Session").performTouchInput { longClick() }

        composeTestRule.onNodeWithText("Pin").assertIsDisplayed().assertHasClickAction()
        composeTestRule.onNodeWithText("Rename").assertIsDisplayed().assertHasClickAction()
        composeTestRule.onNodeWithText("Delete").assertIsDisplayed().assertHasClickAction()
        assertTrue(events.isEmpty())
    }

    @Test
    fun back_closes_the_list_menu_without_leaving_the_screen() {
        val events = mutableListOf<EntryUiEvent>()
        setSurface(events)
        composeTestRule.onNodeWithText("First Session").performTouchInput { longClick() }
        composeTestRule.onNodeWithText("Pin").assertIsDisplayed()
        composeTestRule.runOnUiThread {
            val popup =
                WindowInspector.getGlobalWindowViews().single { it !== composeTestRule.activity.window.decorView }
            popup.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
            popup.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Pin").assertDoesNotExist()
        assertEquals(emptyList<EntryUiEvent>(), events)
        assertFalse(composeTestRule.activity.isFinishing)
    }

    @Test
    fun pending_list_mutations_disable_the_row_and_hide_the_menu() {
        val events = mutableListOf<EntryUiEvent>()
        setSurface(events, mutation = SessionMutationUiState(pendingAction = SessionMutationAction.PIN))

        composeTestRule.onNodeWithText("First Session").assertIsDisplayed().assertIsNotEnabled()
        composeTestRule.onNodeWithText("Pin").assertDoesNotExist()
        composeTestRule.onNodeWithText("Pinning Session…").assertIsDisplayed()
        assertTrue(events.isEmpty())
    }

    @Test
    @Config(qualifiers = "w411dp-h480dp")
    fun list_menu_actions_remain_reachable_in_a_short_window_at_large_font_scale() {
        val events = mutableListOf<EntryUiEvent>()
        setSurface(events, fontScale = 2f)

        composeTestRule.onNodeWithText("First Session").performTouchInput { longClick() }

        listOf("Pin", "Rename", "Delete").forEach { text ->
            composeTestRule.onNodeWithText(text).assertIsDisplayed().assertHasClickAction()
        }
        assertTrue(events.isEmpty())
    }

    private fun setSurface(
        events: MutableList<EntryUiEvent>,
        mutation: SessionMutationUiState? = null,
        fontScale: Float = 1f,
    ) {
        val session = SessionItemUiState(SessionId("first"), "First Session", "Synthetic preview", pinned = false)
        composeTestRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                HermesTheme {
                    EntryScreen(
                        state =
                            EntryUiState(
                                title = UiText.Resource(R.string.entry_title_connected),
                                supportingText = UiText.Resource(R.string.entry_supporting_connected),
                                actionLabel = UiText.Resource(R.string.entry_action_connected),
                                isConnected = true,
                                sessionList =
                                    SessionListUiState(
                                        sessions = listOf(session),
                                        sessionMutations = mutation?.let { mapOf(session.id to it) }.orEmpty(),
                                    ),
                            ),
                        onEvent = events::add,
                    )
                }
            }
        }
    }
}
