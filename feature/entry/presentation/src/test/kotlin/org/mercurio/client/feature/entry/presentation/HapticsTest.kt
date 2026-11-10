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
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The standard haptic contract: the send control and the keyboard send action, a hold on a
 * Session row, and the copy and share actions each play the one standard tick through
 * [LocalHapticFeedback], which routes to the platform haptic service and honours the system
 * touch-feedback setting. The fake records what the client asked for; the app manifest stays
 * free of the vibrate permission.
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
                        state = conversationEntryState(composerText = "Run this"),
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
                        state = conversationEntryState(composerText = "Run this"),
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
                        state = conversationEntryState(composerText = ""),
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
                    EntryScreen(
                        state =
                            entryState(
                                SessionListUiState(
                                    sessions =
                                        listOf(
                                            session("first", "First Session", preview = "Synthetic preview"),
                                        ),
                                ),
                            ),
                        onEvent = {},
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("First Session").performTouchInput { longClick() }

        composeTestRule.onNodeWithText("Pin").assertIsDisplayed()
        assertEquals(listOf(HapticFeedbackType.LongPress), haptics.performed)
    }

    @Test
    fun the_manifest_declares_exactly_the_expected_permissions() {
        val manifest =
            File(
                requireNotNull(System.getProperty("fixture.repositoryRoot")) {
                    "fixture.repositoryRoot must identify the repository root"
                },
                "app/src/main/AndroidManifest.xml",
            )
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(manifest)
        val nodes = document.getElementsByTagName("uses-permission")
        val permissions =
            (0 until nodes.length)
                .map { nodes.item(it).attributes.getNamedItem("android:name").nodeValue }
                .toSet()

        assertEquals(
            setOf(
                "android.permission.INTERNET",
                "android.permission.POST_NOTIFICATIONS",
                "android.permission.REQUEST_INSTALL_PACKAGES",
            ),
            permissions,
        )
    }

    private class RecordingHapticFeedback : HapticFeedback {
        val performed = mutableListOf<HapticFeedbackType>()

        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
            performed.add(hapticFeedbackType)
        }
    }
}
