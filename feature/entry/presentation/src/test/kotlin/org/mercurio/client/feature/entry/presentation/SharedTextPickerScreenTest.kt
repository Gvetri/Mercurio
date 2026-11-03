package org.mercurio.client.feature.entry.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mercurio.client.feature.entry.domain.SessionId
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class SharedTextPickerScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun the_pick_message_and_discard_action_are_visible_on_the_session_list() {
        var clicked = false
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(
                    state =
                        EntryUiState(
                            title = UiText.Resource(R.string.entry_title_connected),
                            supportingText = UiText.Resource(R.string.entry_supporting_connected),
                            actionLabel = UiText.Resource(R.string.entry_action_connected),
                            isConnected = true,
                            inboundLinkNotice = InboundLinkNoticeCategory.SHARE_PICK_SESSION,
                            sessionList =
                                SessionListUiState(
                                    sessions =
                                        listOf(
                                            SessionItemUiState(
                                                id = SessionId("session-one"),
                                                title = "Session one",
                                                preview = null,
                                                pinned = false,
                                            ),
                                        ),
                                ),
                        ),
                    onEvent = { event ->
                        if (event is EntryUiEvent.SharedTextDismissed) clicked = true
                    },
                )
            }
        }

        composeTestRule.onNodeWithText("Choose a Session to add the shared text.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Discard shared text").performClick()
        composeTestRule.runOnIdle { org.junit.Assert.assertTrue(clicked) }
    }

    @Test
    fun the_unsupported_message_is_visible_on_the_connection_surface() {
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(
                    state =
                        EntryUiState(
                            title = UiText.Resource(R.string.entry_title_connect),
                            supportingText = UiText.Resource(R.string.entry_supporting_connect),
                            actionLabel = UiText.Resource(R.string.entry_action_add_connection),
                            inboundLinkNotice = InboundLinkNoticeCategory.SHARE_UNSUPPORTED,
                        ),
                    onEvent = {},
                )
            }
        }

        composeTestRule
            .onNodeWithText("Only plain text can be shared into Mercurio. This share was discarded.")
            .assertIsDisplayed()
    }
}
