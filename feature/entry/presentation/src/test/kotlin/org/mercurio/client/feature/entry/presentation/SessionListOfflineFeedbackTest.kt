package org.mercurio.client.feature.entry.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
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
class SessionListOfflineFeedbackTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun failed_refresh_feedback_is_visible_above_a_populated_list_without_scrolling() {
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(
                    state =
                        connectedState(
                            SessionListUiState(
                                sessions = populatedRows(),
                                isStale = true,
                                errorCategory = SessionListErrorCategory.GATEWAY_UNAVAILABLE,
                            ),
                        ),
                    onEvent = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Row Session 01").assertIsDisplayed()
        composeTestRule
            .onNodeWithText("The displayed Session data may be stale.")
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(
                "The Gateway is unavailable. The current Session list is preserved. " +
                    "Try again or reconnect.",
            )
            .assertIsDisplayed()
    }

    @Test
    fun failed_row_open_feedback_is_visible_above_a_populated_list_without_scrolling() {
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(
                    state =
                        connectedState(
                            SessionListUiState(
                                sessions = populatedRows(),
                                isStale = true,
                                isUnavailable = true,
                                errorCategory = SessionListErrorCategory.SESSION_UNAVAILABLE,
                            ),
                        ),
                    onEvent = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Row Session 01").assertIsDisplayed()
        composeTestRule
            .onNodeWithText(
                "The displayed Session data may be stale. " +
                    "Gateway actions are unavailable until the connection recovers.",
            )
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(
                "This Session could not be opened. It may have been removed. " +
                    "Return to the list or refresh.",
            )
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Try again").assertIsDisplayed()
    }

    @Test
    fun opening_session_feedback_is_visible_above_a_populated_list_without_scrolling() {
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(
                    state =
                        connectedState(
                            SessionListUiState(
                                sessions = populatedRows(),
                                openingSessionId = SessionId("row-01"),
                            ),
                        ),
                    onEvent = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Row Session 01").assertIsDisplayed()
        composeTestRule.onNodeWithText("Opening Session…").assertIsDisplayed()
    }

    @Test
    fun refresh_control_stays_usable_while_the_list_reports_the_gateway_unavailable() {
        val events = mutableListOf<EntryUiEvent>()
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(
                    state =
                        connectedState(
                            SessionListUiState(
                                sessions = populatedRows(),
                                isStale = true,
                                isUnavailable = true,
                                errorCategory = SessionListErrorCategory.GATEWAY_UNAVAILABLE,
                            ),
                        ),
                    onEvent = events::add,
                )
            }
        }

        composeTestRule.onNodeWithText("Try again").assertIsDisplayed().performClick()

        assertEquals(listOf(EntryUiEvent.RefreshSessionListClicked), events)
    }

    private fun connectedState(sessionList: SessionListUiState): EntryUiState =
        EntryUiState(
            title = "Gateway connected",
            supportingText = "The Gateway contract was verified successfully.",
            actionLabel = "Connected",
            isConnected = true,
            sessionList = sessionList,
        )

    private fun populatedRows(count: Int = 20): List<SessionItemUiState> =
        (1..count).map { index ->
            SessionItemUiState(
                id = SessionId("row-%02d".format(index)),
                title = "Row Session %02d".format(index),
                preview = "Preview %02d".format(index),
                pinned = false,
            )
        }
}
