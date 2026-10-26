package org.mercurio.client.feature.entry.presentation

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mercurio.client.feature.entry.domain.Run
import org.mercurio.client.feature.entry.domain.RunId
import org.mercurio.client.feature.entry.domain.RunPresentationState
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-notnight")
class ConversationSummaryLayoutTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    @Config(qualifiers = "w840dp-h900dp-notnight")
    fun wide_refresh_shares_a_row_with_the_run_information() {
        assertGroupedSummary()
    }

    @Test
    @Config(qualifiers = "w840dp-h900dp-night")
    fun dark_wide_refresh_shares_a_row_with_the_run_information() {
        assertGroupedSummary(darkTheme = true)
    }

    @Test
    @Config(qualifiers = "w600dp-h900dp-notnight")
    fun refresh_groups_with_run_information_at_the_two_pane_breakpoint() {
        assertGroupedSummary()
    }

    @Test
    fun phone_refresh_remains_above_the_run_information() {
        assertStackedSummary()
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-night")
    fun dark_phone_refresh_remains_above_the_run_information() {
        assertStackedSummary(darkTheme = true)
    }

    @Test
    @Config(qualifiers = "w599dp-h900dp-notnight")
    fun refresh_remains_stacked_below_the_two_pane_breakpoint() {
        assertStackedSummary()
    }

    private fun assertGroupedSummary(darkTheme: Boolean = false) {
        val events = showSummary(darkTheme)
        val refresh =
            composeTestRule.onNodeWithText("Refresh history").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        val refreshBounds = refresh.fetchSemanticsNode().boundsInRoot
        val latest =
            composeTestRule.onNodeWithText("Latest Run").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val status =
            composeTestRule.onNodeWithText("Run state: Succeeded").assertIsDisplayed().fetchSemanticsNode().boundsInRoot

        assertTrue(
            "Refresh $refreshBounds must share the Latest Run band $latest",
            latest.center.y in refreshBounds.top..refreshBounds.bottom,
        )
        assertTrue("Run information must be to the right of Refresh", latest.left >= refreshBounds.right)
        assertTrue("Run labels must remain stacked", status.top >= latest.bottom)
        assertTrue("Run state must share the Refresh band", status.center.y in refreshBounds.top..refreshBounds.bottom)
        refresh.performClick()
        assertEquals(listOf(EntryUiEvent.RefreshSessionsClicked), events)
    }

    private fun assertStackedSummary(darkTheme: Boolean = false) {
        val events = showSummary(darkTheme)
        val refresh =
            composeTestRule.onNodeWithText("Refresh history").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        val refreshBounds = refresh.fetchSemanticsNode().boundsInRoot
        val latest =
            composeTestRule.onNodeWithText("Latest Run").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val status =
            composeTestRule.onNodeWithText("Run state: Succeeded").assertIsDisplayed().fetchSemanticsNode().boundsInRoot

        assertTrue("Phone Refresh must stay above the Run block", latest.top >= refreshBounds.bottom)
        assertEquals("Phone Refresh must retain the full row", latest.left, refreshBounds.left, 1f)
        assertEquals(composeTestRule.onRoot().fetchSemanticsNode().boundsInRoot.center.x, refreshBounds.center.x, 1f)
        assertTrue("Run labels must remain stacked", status.top >= latest.bottom)
        refresh.performClick()
        assertEquals(listOf(EntryUiEvent.RefreshSessionsClicked), events)
    }

    private fun showSummary(darkTheme: Boolean): List<EntryUiEvent> {
        val events = mutableListOf<EntryUiEvent>()
        val selectedSession = session("first", "First Session")
        composeTestRule.setContent {
            HermesTheme(darkTheme = darkTheme) {
                EntryScreen(
                    state =
                        entryState(
                            SessionListUiState(
                                openedSession =
                                    OpenSessionUiState(
                                        session = selectedSession,
                                        messages = emptyList(),
                                        latestRun = Run(RunId("run-A1"), selectedSession.id, "succeeded"),
                                        latestRunState = RunPresentationState.SUCCEEDED,
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
