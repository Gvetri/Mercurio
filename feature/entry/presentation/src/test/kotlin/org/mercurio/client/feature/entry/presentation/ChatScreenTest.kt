package org.mercurio.client.feature.entry.presentation

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.TimeZone

/** The approved Chat shell shows the design's own copy for every state it owns. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-notnight")
class ChatScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var originalTimeZone: TimeZone

    @Before
    fun fixTheTimeZoneTheDesignPrints() {
        originalTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"))
    }

    @After
    fun restoreTheTimeZone() {
        TimeZone.setDefault(originalTimeZone)
    }

    @Test
    fun the_populated_transcript_shows_the_day_roles_times_and_run_line() {
        composeTestRule.setContent {
            HermesTheme(darkTheme = false) {
                SessionDetailContent(detail = ChatDesignFixtures.populated(), onEvent = {})
            }
        }

        composeTestRule.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        composeTestRule.onNodeWithText("SUN 01 NOV").assertExists()
        composeTestRule.onAllNodesWithText("USER").assertCountEquals(1)
        composeTestRule.onAllNodesWithText("ASSISTANT").assertCountEquals(2)
        composeTestRule.onAllNodesWithText("09:12").assertCountEquals(2)
        composeTestRule.onNodeWithText("idle").assertExists()
        composeTestRule.onNodeWithText("gateway :: ready").assertExists()
        composeTestRule.onNodeWithText("write a message").assertExists()
        composeTestRule.onNodeWithText("[ send ]").assertExists()
        composeTestRule.onNodeWithText("What failed").assertExists()
    }

    @Test
    fun the_empty_state_names_the_fact_and_keeps_the_composer() {
        composeTestRule.setContent {
            HermesTheme(darkTheme = false) {
                SessionDetailContent(detail = ChatDesignFixtures.empty(), onEvent = {})
            }
        }

        composeTestRule.onNodeWithText("No messages yet").assertExists()
        composeTestRule
            .onNodeWithText("This Session has no history on the Gateway. Write the first message.")
            .assertExists()
        composeTestRule.onNodeWithText("· 0 messages").assertExists()
    }

    @Test
    fun the_streaming_state_offers_stop_on_the_run_line_and_keeps_send_in_place() {
        composeTestRule.setContent {
            HermesTheme(darkTheme = false) {
                SessionDetailContent(detail = ChatDesignFixtures.streamingWithDraft(), onEvent = {})
            }
        }

        composeTestRule.onNodeWithText("[ stop ]").assertExists()
        composeTestRule.onNodeWithText("[ send ]").assertExists()
        composeTestRule.onNodeWithText("run 4b2a · streaming").assertExists()
    }

    @Test
    fun the_unavailable_state_names_the_fact_and_offers_the_only_recovery() {
        composeTestRule.setContent {
            HermesTheme(darkTheme = false) {
                SessionDetailContent(detail = ChatDesignFixtures.unavailable(), onEvent = {})
            }
        }

        composeTestRule.onNodeWithText("Session history is unavailable").assertExists()
        composeTestRule.onNodeWithText("[ try again ]").assertExists()
        composeTestRule.onNodeWithText("gateway :: offline").assertExists()
    }
}
