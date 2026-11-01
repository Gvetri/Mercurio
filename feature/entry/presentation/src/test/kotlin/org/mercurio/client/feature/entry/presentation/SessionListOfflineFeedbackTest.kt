package org.mercurio.client.feature.entry.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
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
    fun failed_refresh_keeps_the_rows_and_reports_the_gateway_offline_without_scrolling() {
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(
                    state =
                        connectedState(
                            SessionListUiState(
                                sessions = populatedRows(),
                                isStale = true,
                                isCached = true,
                                errorCategory = SessionListErrorCategory.GATEWAY_UNAVAILABLE,
                            ),
                        ),
                    onEvent = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Row Session 01").assertIsDisplayed()
        composeTestRule.onNodeWithText("20 sessions · cached").assertIsDisplayed()
        composeTestRule.onNodeWithText("gateway :: offline").assertIsDisplayed()
    }

    @Test
    fun unavailable_list_keeps_the_cached_rows_and_has_no_separate_refresh_button() {
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(
                    state =
                        connectedState(
                            SessionListUiState(
                                sessions = populatedRows(),
                                isStale = true,
                                isUnavailable = true,
                                isCached = true,
                                errorCategory = SessionListErrorCategory.SESSION_UNAVAILABLE,
                            ),
                        ),
                    onEvent = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Row Session 01").assertIsDisplayed()
        composeTestRule.onNodeWithText("gateway :: offline").assertIsDisplayed()
        composeTestRule.onNodeWithText("Try again").assertDoesNotExist()
        composeTestRule.onNodeWithText("Refresh").assertDoesNotExist()
    }

    @Test
    fun opening_a_session_disables_the_rows_while_the_list_stays_visible() {
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

        composeTestRule.onNodeWithText("Row Session 01").assertIsDisplayed().assertIsNotEnabled()
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
