package org.mercurio.client.feature.entry.presentation

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mercurio.client.feature.entry.domain.SessionId
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class RunStatusNotificationScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun settings_control_renders_label_and_switch_with_accessible_description() {
        val events = mutableListOf<EntryUiEvent>()
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(
                    state = connectedState(runStatusNotifications = RunStatusNotificationsUiState()),
                    onEvent = events::add,
                )
            }
        }

        composeTestRule.onNodeWithText("Notifications").assertIsDisplayed()
        composeTestRule
            .onNodeWithContentDescription("Notifications")
            .assertIsDisplayed()
            .assertIsOff()
            .assertHasClickAction()
            .performClick()

        assertEquals(listOf(EntryUiEvent.RunStatusNotificationsToggleClicked), events)
    }

    @Test
    fun enabled_state_shows_the_switch_on() {
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(
                    state =
                        connectedState(
                            runStatusNotifications = RunStatusNotificationsUiState(enabled = true),
                        ),
                    onEvent = {},
                )
            }
        }

        composeTestRule
            .onNode(hasContentDescription("Notifications"))
            .assertIsOn()
    }

    @Test
    fun permission_denied_state_shows_a_recoverable_explanation_and_keeps_the_switch_off() {
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(
                    state =
                        connectedState(
                            runStatusNotifications =
                                RunStatusNotificationsUiState(
                                    enabled = false,
                                    explanation = RunStatusNotificationExplanation.PERMISSION_DENIED,
                                ),
                        ),
                    onEvent = {},
                )
            }
        }

        composeTestRule
            .onNodeWithText(copy(RunStatusNotificationExplanation.PERMISSION_DENIED.messageRes))
            .assertIsDisplayed()
        composeTestRule
            .onNode(hasContentDescription("Notifications"))
            .assertIsOff()
    }

    private fun connectedState(runStatusNotifications: RunStatusNotificationsUiState): EntryUiState =
        EntryUiState(
            title = UiText.Resource(R.string.entry_title_connected),
            supportingText = UiText.Resource(R.string.entry_supporting_connected),
            actionLabel = UiText.Resource(R.string.entry_action_connected),
            isConnected = true,
            isSettingsOpen = true,
            clientVersion = "demo-build.1",
            sessionList =
                SessionListUiState(
                    sessions =
                        listOf(
                            SessionItemUiState(
                                id = SessionId("session-one"),
                                title = "Session one",
                                preview = "Preview",
                                pinned = false,
                            ),
                        ),
                ),
            runStatusNotifications = runStatusNotifications,
        )
}
