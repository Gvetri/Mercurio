package org.hermesnative.client.feature.entry.presentation

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.core.graphics.Insets
import androidx.core.view.WindowInsetsCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h480dp")
class ConversationKeyboardScreenTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun multiline_composer_stays_above_the_keyboard_when_the_first_reply_arrives() {
        assertComposerAboveKeyboard()
    }

    @Test
    @Config(qualifiers = "w411dp-h480dp-night")
    fun dark_conversation_keeps_the_composer_above_the_keyboard() {
        assertComposerAboveKeyboard()
    }

    @Test
    @Config(qualifiers = "w1000dp-h480dp")
    fun two_pane_conversation_keeps_the_composer_above_the_keyboard() {
        assertComposerAboveKeyboard()
    }

    private fun assertComposerAboveKeyboard() {
        val conversation =
            mutableStateOf(
                OpenSessionUiState(
                    session("first", "First Session"),
                    emptyList(),
                    composerText = (1..20).joinToString("\n") { "Draft line $it" },
                ),
            )
        composeTestRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                HermesTheme {
                    EntryScreen(entryState(SessionListUiState(openedSession = conversation.value)), onEvent = {})
                }
            }
        }
        composeTestRule.onNodeWithText("Message").performScrollTo().performClick()
        val windowHeight = composeTestRule.activity.window.decorView.height
        val imeInset = windowHeight / 2
        composeTestRule.runOnUiThread {
            val insets =
                WindowInsetsCompat.Builder()
                    .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, imeInset))
                    .setVisible(WindowInsetsCompat.Type.ime(), true)
                    .build()
            composeTestRule.activity.window.decorView.dispatchApplyWindowInsets(insets.toWindowInsets())
        }
        composeTestRule.waitForIdle()
        val send = composeTestRule.onNodeWithText("Send").performScrollTo().assertIsDisplayed()
        val before = send.fetchSemanticsNode().boundsInWindow.bottom
        assertTrue("Send must be above the keyboard (bottom=$before)", before <= windowHeight - imeInset + 1f)
        composeTestRule.onNodeWithText("Message").performScrollTo().assertIsDisplayed()
        composeTestRule.runOnIdle {
            conversation.value =
                conversation.value.copy(
                    messages = listOf(SessionMessageUiState("reply", "assistant", "First reply")),
                )
        }
        val sendAfterReply = composeTestRule.onNodeWithText("Send").performScrollTo().assertIsDisplayed()
        val after = sendAfterReply.fetchSemanticsNode().boundsInWindow.bottom
        assertEquals("A reply must not move Send behind the keyboard", before, after, 1f)
        composeTestRule.onNodeWithText("Message").performScrollTo().assertIsDisplayed()

        listOf(MessageSendErrorCategory.GATEWAY_REQUEST_FAILED, MessageSendErrorCategory.UNCERTAIN).forEach { error ->
            composeTestRule.runOnIdle {
                conversation.value =
                    conversation.value.copy(
                        sendErrorCategory = error,
                        hasUnresolvedSubmission = error == MessageSendErrorCategory.UNCERTAIN,
                    )
            }
            composeTestRule.onNodeWithText(error.safeMessage).performScrollTo().assertIsDisplayed()
            val label = if (error == MessageSendErrorCategory.UNCERTAIN) "Refresh history to resolve" else "Try again"
            val action = composeTestRule.onNodeWithText(label).performScrollTo().assertIsDisplayed()
            assertTrue(action.fetchSemanticsNode().boundsInWindow.bottom <= windowHeight - imeInset + 1f)
            composeTestRule.onNodeWithText("Message").performScrollTo().assertIsDisplayed()
        }
    }
}
