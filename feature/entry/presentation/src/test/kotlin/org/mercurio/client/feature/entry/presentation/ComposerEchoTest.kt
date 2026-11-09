package org.mercurio.client.feature.entry.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class ComposerEchoTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun a_lagging_state_echo_does_not_reset_the_typed_draft() {
        var state by mutableStateOf(conversationEntryState())
        val forwarded = mutableListOf<String>()
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(
                    state = state,
                    onEvent = { event ->
                        if (event is EntryUiEvent.ComposerTextChanged) forwarded += event.value
                    },
                )
            }
        }
        val field = composeTestRule.onNodeWithTag("conversation-message-input")
        field.performTextInput("Recover")
        field.performTextInput(" this offline draft")
        composeTestRule.runOnIdle { state = state.withComposerText(forwarded.first()) }
        composeTestRule.waitForIdle()
        assertEquals("Recover this offline draft", typedText())
    }

    @Test
    fun a_cleared_draft_replaces_the_text_the_field_already_shows() {
        var state by mutableStateOf(conversationEntryState())
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(
                    state = state,
                    onEvent = { event ->
                        if (event is EntryUiEvent.ComposerTextChanged) {
                            state = state.withComposerText(event.value)
                        }
                    },
                )
            }
        }
        val field = composeTestRule.onNodeWithTag("conversation-message-input")
        field.performTextInput("Recover this offline draft")
        assertEquals("Recover this offline draft", typedText())
        composeTestRule.runOnIdle { state = state.withComposerText("") }
        composeTestRule.waitForIdle()
        assertEquals("", typedText())
    }

    private fun EntryUiState.withComposerText(composerText: String): EntryUiState {
        val list = sessionList
        val opened = list?.openedSession
        if (list == null || opened == null) return this
        return copy(sessionList = list.copy(openedSession = opened.copy(composerText = composerText)))
    }

    private fun typedText(): String {
        val node = composeTestRule.onNode(hasSetTextAction()).fetchSemanticsNode()
        return node.config.getOrNull(SemanticsProperties.EditableText)?.text ?: "<none>"
    }
}
