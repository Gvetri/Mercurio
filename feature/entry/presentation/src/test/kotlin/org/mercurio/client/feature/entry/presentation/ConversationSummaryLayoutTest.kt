package org.mercurio.client.feature.entry.presentation

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The run line keeps one shape at every width: the state on the left and either the Gateway state
 * or one action on the right. The action is the only control the line offers, and it always emits
 * the refresh the reader asked for.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-notnight")
class ConversationSummaryLayoutTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun phone_run_line_offers_one_refresh_action() = assertRefreshAction()

    @Test
    @Config(qualifiers = "w411dp-h891dp-night")
    fun dark_phone_run_line_offers_one_refresh_action() = assertRefreshAction()

    @Test
    @Config(qualifiers = "w600dp-h900dp-notnight")
    fun two_pane_run_line_offers_one_refresh_action() = assertRefreshAction()

    @Test
    @Config(qualifiers = "w840dp-h900dp-notnight")
    fun wide_run_line_offers_one_refresh_action() = assertRefreshAction()

    @Test
    fun the_run_line_reports_the_gateway_when_no_action_is_offered() {
        showRunLine(cachedUnavailable = false)
        composeTestRule.onNodeWithText("idle").assertIsDisplayed()
        composeTestRule.onNodeWithText("gateway :: ready").assertIsDisplayed()
        composeTestRule.onNodeWithTag("chat-run-action").assertDoesNotExist()
    }

    private fun assertRefreshAction() {
        val events = showRunLine(cachedUnavailable = true)
        composeTestRule.onNodeWithText("cached · unavailable").assertIsDisplayed()
        val action = composeTestRule.onNodeWithTag("chat-run-action").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        action.performClick()
        assertEquals(listOf(EntryUiEvent.RefreshSessionsClicked), events)
    }

    private fun showRunLine(cachedUnavailable: Boolean): List<EntryUiEvent> {
        val events = mutableListOf<EntryUiEvent>()
        val selectedSession = session("first", "First Session")
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(
                    state =
                        entryState(
                            SessionListUiState(
                                openedSession =
                                    OpenSessionUiState(
                                        session = selectedSession,
                                        messages = emptyList(),
                                        isCached = cachedUnavailable,
                                        isStale = cachedUnavailable,
                                        errorCategory =
                                            if (cachedUnavailable) {
                                                SessionHistoryErrorCategory.GATEWAY_REQUEST_FAILED
                                            } else {
                                                null
                                            },
                                    ),
                            ),
                        ),
                    onEvent = events::add,
                )
            }
        }
        return events
    }
}
