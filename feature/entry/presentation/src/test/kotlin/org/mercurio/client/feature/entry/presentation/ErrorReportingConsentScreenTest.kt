package org.mercurio.client.feature.entry.presentation

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class ErrorReportingConsentScreenTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun the_question_names_sentry_the_consequence_and_where_the_answer_changes() {
        setContent(consentState())

        composeTestRule.onNodeWithText("Send crash and error reports?").assertIsDisplayed()
        composeTestRule
            .onNodeWithText(
                "When Mercurio crashes or hangs, it can send a report to Sentry. " +
                    "The report says what broke, which is how most bugs get found.",
            )
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(
                "Nothing is sent until you answer. If you say no, crash and hang details stay on this device. " +
                    "You can change your mind later in Settings › Diagnostics.",
            )
            .assertIsDisplayed()
    }

    @Test
    fun the_question_replaces_the_gateway_setup_until_it_is_answered() {
        setContent(consentState())

        composeTestRule.onNodeWithText("Add Gateway Connection").assertDoesNotExist()
    }

    @Test
    fun both_answers_are_reachable_and_report_the_choice() {
        val events = mutableListOf<EntryUiEvent>()
        setContent(consentState(), events = events)

        composeTestRule.onNodeWithText("Send reports").performClick()
        composeTestRule.onNodeWithText("Don't send").performClick()

        assertEquals(
            listOf(
                EntryUiEvent.ErrorReportingConsentGranted,
                EntryUiEvent.ErrorReportingConsentDeclined,
            ),
            events,
        )
    }

    @Test
    @Config(qualifiers = "w411dp-h640dp")
    fun both_answers_stay_reachable_at_two_hundred_percent_font_scale() {
        composeTestRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                HermesTheme {
                    EntryScreen(state = consentState(), onEvent = {})
                }
            }
        }

        composeTestRule.onNodeWithText("Send reports").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Don't send").performScrollTo().assertIsDisplayed()
    }

    private fun setContent(
        state: EntryUiState,
        events: MutableList<EntryUiEvent>? = null,
    ) {
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(state = state, onEvent = { event -> events?.add(event) })
            }
        }
    }

    private fun consentState(): EntryUiState =
        EntryUiState(
            title = UiText.Resource(R.string.entry_title_connect),
            supportingText = UiText.Resource(R.string.entry_supporting_connect),
            actionLabel = UiText.Resource(R.string.entry_action_add_connection),
            isErrorReportingConsentRequired = true,
        )
}
