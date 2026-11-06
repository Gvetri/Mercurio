package org.mercurio.client.feature.entry.presentation

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTouchInput
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mercurio.client.feature.entry.domain.SessionId
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * The standard haptic contract: the send control and the keyboard send action, a hold on a
 * Session row, and the copy and share actions each play the one standard tick through
 * [LocalHapticFeedback], which routes to
 * the platform haptic service and honours the system touch-feedback setting. The fake records
 * what the client asked for; the app manifest stays free of the vibrate permission.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class HapticsTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun copy_and_share_each_play_the_standard_tick() {
        val haptics = RecordingHapticFeedback()
        lateinit var actions: MessageActions
        composeTestRule.setContent {
            HermesTheme {
                CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                    actions = rememberMessageActions()
                }
            }
        }

        composeTestRule.runOnIdle {
            actions.copy("Full message")
            actions.share("Full message")
        }
        composeTestRule.waitForIdle()

        assertEquals(
            listOf(HapticFeedbackType.LongPress, HapticFeedbackType.LongPress),
            haptics.performed,
        )
    }

    @Test
    fun the_send_control_plays_the_standard_tick_and_emits_the_send_intent() {
        val events = mutableListOf<EntryUiEvent>()
        val haptics = RecordingHapticFeedback()
        composeTestRule.setContent {
            HermesTheme {
                CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                    EntryScreen(
                        state = connectedState(composerText = "Run this"),
                        onEvent = events::add,
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag("conversation-send").assertHasClickAction().performClick()

        assertEquals(listOf(EntryUiEvent.SendMessageClicked), events)
        assertEquals(listOf(HapticFeedbackType.LongPress), haptics.performed)
    }

    @Test
    fun the_ime_send_action_plays_the_standard_tick_and_emits_the_send_intent() {
        val events = mutableListOf<EntryUiEvent>()
        val haptics = RecordingHapticFeedback()
        composeTestRule.setContent {
            HermesTheme {
                CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                    EntryScreen(
                        state = connectedState(composerText = "Run this"),
                        onEvent = events::add,
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag("conversation-message-input").performImeAction()

        assertEquals(listOf(EntryUiEvent.SendMessageClicked), events)
        assertEquals(listOf(HapticFeedbackType.LongPress), haptics.performed)
    }

    @Test
    fun a_send_that_is_not_enabled_plays_no_tick_and_emits_nothing() {
        val events = mutableListOf<EntryUiEvent>()
        val haptics = RecordingHapticFeedback()
        composeTestRule.setContent {
            HermesTheme {
                CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                    EntryScreen(
                        state = connectedState(composerText = ""),
                        onEvent = events::add,
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag("conversation-message-input").performImeAction()

        assertEquals(emptyList<EntryUiEvent>(), events)
        assertEquals(emptyList<HapticFeedbackType>(), haptics.performed)
    }

    @Test
    fun a_hold_on_a_session_row_plays_the_long_press_tick_and_opens_the_menu() {
        val haptics = RecordingHapticFeedback()
        composeTestRule.setContent {
            HermesTheme {
                CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                    EntryScreen(state = listState(), onEvent = {})
                }
            }
        }

        composeTestRule.onNodeWithText("First Session").performTouchInput { longClick() }

        composeTestRule.onNodeWithText("Pin").assertIsDisplayed()
        assertEquals(listOf(HapticFeedbackType.LongPress), haptics.performed)
    }

    @Test
    fun the_manifest_stays_free_of_the_vibrate_permission() {
        val repositoryRoot = requireNotNull(System.getProperty("fixture.repositoryRoot"))
        val manifest = File(repositoryRoot, "app/src/main/AndroidManifest.xml").readText()

        val permissions =
            Regex("<uses-permission[^>]*android:name=\"([^\"]+)\"")
                .findAll(manifest)
                .map { it.groupValues[1] }
                .toList()

        assertEquals(
            listOf("android.permission.INTERNET", "android.permission.POST_NOTIFICATIONS"),
            permissions,
        )
    }

    private fun listState(): EntryUiState =
        EntryUiState(
            title = UiText.Resource(R.string.entry_title_connected),
            supportingText = UiText.Resource(R.string.entry_supporting_connected),
            actionLabel = UiText.Resource(R.string.entry_action_connected),
            isConnected = true,
            sessionList =
                SessionListUiState(
                    sessions =
                        listOf(
                            SessionItemUiState(
                                SessionId("first"),
                                "First Session",
                                "Synthetic preview",
                                pinned = false,
                            ),
                        ),
                ),
        )

    private fun connectedState(composerText: String): EntryUiState =
        EntryUiState(
            title = UiText.Resource(R.string.entry_title_connected),
            supportingText = UiText.Resource(R.string.entry_action_connected),
            actionLabel = UiText.Resource(R.string.entry_action_connected),
            isConnected = true,
            sessionList =
                SessionListUiState(
                    openedSession =
                        OpenSessionUiState(
                            session = SessionItemUiState(SessionId("session-1"), "Session", null, false),
                            messages = emptyList(),
                            composerText = composerText,
                        ),
                ),
        )

    private class RecordingHapticFeedback : HapticFeedback {
        val performed = mutableListOf<HapticFeedbackType>()

        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
            performed.add(hapticFeedbackType)
        }
    }
}
