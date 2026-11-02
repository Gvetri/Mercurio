package org.mercurio.client.feature.entry.presentation

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mercurio.client.feature.entry.domain.SessionId
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class InboundLinkNoticeScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun the_unconnected_link_message_is_visible_on_the_connection_surface() {
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(
                    state =
                        EntryUiState(
                            title = UiText.Resource(R.string.entry_title_connect),
                            supportingText = UiText.Resource(R.string.entry_supporting_connect),
                            actionLabel = UiText.Resource(R.string.entry_action_add_connection),
                            inboundLinkNotice = InboundLinkNoticeCategory.SESSION_LINK_NEEDS_CONNECTION,
                        ),
                    onEvent = {},
                )
            }
        }

        composeTestRule
            .onNodeWithText("Connect to a Gateway to open Session links. This link was discarded.")
            .assertIsDisplayed()
    }

    @Test
    fun the_unknown_session_message_is_visible_on_the_session_list() {
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(
                    state =
                        EntryUiState(
                            title = UiText.Resource(R.string.entry_title_connected),
                            supportingText = UiText.Resource(R.string.entry_supporting_connected),
                            actionLabel = UiText.Resource(R.string.entry_action_connected),
                            isConnected = true,
                            inboundLinkNotice = InboundLinkNoticeCategory.SESSION_LINK_UNKNOWN,
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
                    onEvent = {},
                )
            }
        }

        composeTestRule.onNodeWithText("This Session link could not be opened.").assertIsDisplayed()
    }

    @Test
    fun a_compose_link_request_focuses_the_composer_field() {
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(state = composerFocusState(isSending = false), onEvent = {})
            }
        }

        composeTestRule.onNodeWithTag("conversation-message-input").assertIsFocused()
    }

    @Test
    fun a_compose_link_request_waits_until_the_composer_field_is_enabled() {
        val state = mutableStateOf(composerFocusState(isSending = true))
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(state = state.value, onEvent = {})
            }
        }
        composeTestRule.onNodeWithTag("conversation-message-input").assertIsNotEnabled()

        composeTestRule.runOnIdle {
            state.value = composerFocusState(isSending = false)
        }

        composeTestRule.onNodeWithTag("conversation-message-input").assertIsEnabled().assertIsFocused()
    }

    private fun composerFocusState(isSending: Boolean): EntryUiState =
        EntryUiState(
            title = UiText.Resource(R.string.entry_title_connected),
            supportingText = UiText.Resource(R.string.entry_supporting_connected),
            actionLabel = UiText.Resource(R.string.entry_action_connected),
            isConnected = true,
            sessionList =
                SessionListUiState(
                    openedSession =
                        OpenSessionUiState(
                            session =
                                SessionItemUiState(
                                    id = SessionId("session-one"),
                                    title = "Session one",
                                    preview = null,
                                    pinned = false,
                                ),
                            messages = emptyList(),
                            isSending = isSending,
                            composerFocusRequestId = 1,
                        ),
                ),
        )
}
