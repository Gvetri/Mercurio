package org.mercurio.client.feature.entry.presentation

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mercurio.client.feature.entry.domain.InstallSource
import org.mercurio.client.feature.entry.domain.SessionId
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class AppLockScreenTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun a_locked_app_shows_the_gate_instead_of_the_session_list() {
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(state = connectedState(isLocked = true), onEvent = {})
            }
        }

        composeTestRule.onNodeWithText("Mercurio is locked").assertIsDisplayed()
        composeTestRule
            .onNodeWithText("Unlock with your device screen lock to see your Sessions.")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Unlock").assertHasClickAction()
        composeTestRule.onNodeWithText("First Session").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("Settings").assertDoesNotExist()
    }

    @Test
    fun the_gate_routes_the_unlock_action() {
        val events = mutableListOf<EntryUiEvent>()
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(state = connectedState(isLocked = true), onEvent = events::add)
            }
        }

        composeTestRule.onNodeWithText("Unlock").performClick()

        assertEquals(listOf<EntryUiEvent>(EntryUiEvent.AppUnlockRequested), events)
    }

    @Test
    fun an_unlocked_app_shows_the_session_list() {
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(state = connectedState(isLocked = false), onEvent = {})
            }
        }

        composeTestRule.onNodeWithText("First Session").assertIsDisplayed()
        composeTestRule.onNodeWithText("Mercurio is locked").assertDoesNotExist()
    }

    private fun connectedState(isLocked: Boolean): EntryUiState =
        EntryUiState(
            title = UiText.Resource(R.string.entry_title_connected),
            supportingText = UiText.Resource(R.string.entry_supporting_connected),
            actionLabel = UiText.Resource(R.string.entry_action_connected),
            isConnected = true,
            clientVersion = "unknown",
            installSource = InstallSource.OTHER_SOURCE,
            sessionList =
                SessionListUiState(
                    sessions =
                        listOf(
                            SessionItemUiState(
                                id = SessionId("first"),
                                title = "First Session",
                                preview = "First preview",
                                pinned = false,
                            ),
                        ),
                ),
            appLock = AppLockUiState(isEnabled = true, isAvailable = true, isLocked = isLocked),
        )
}
