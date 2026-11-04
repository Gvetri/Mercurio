package org.mercurio.client.feature.entry.presentation

import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.core.graphics.Insets
import androidx.core.view.WindowInsetsCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mercurio.client.feature.entry.application.EntryState
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class GatewayConnectionInputTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun credential_requests_a_password_keyboard_without_correction_or_capitalization() {
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(state = setupState(), onEvent = {})
            }
        }
        composeTestRule.onNodeWithText("Gateway credential", substring = true).performClick()
        composeTestRule.waitForIdle()

        val info = currentEditorInfo()

        assertEquals(
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            info.inputType and (InputType.TYPE_MASK_CLASS or InputType.TYPE_MASK_VARIATION),
        )
        assertEquals(0, info.inputType and InputType.TYPE_TEXT_FLAG_AUTO_CORRECT)
        assertEquals(
            0,
            info.inputType and
                (
                    InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS or
                        InputType.TYPE_TEXT_FLAG_CAP_WORDS or
                        InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                ),
        )
    }

    @Test
    fun showing_and_hiding_the_credential_preserves_its_value_and_password_keyboard() {
        val events = mutableListOf<EntryUiEvent>()
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(state = setupState(), onEvent = events::add)
            }
        }

        composeTestRule.onNodeWithText("Gateway credential", substring = true)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
        composeTestRule.onNodeWithContentDescription("Show credential").performClick()
        composeTestRule.onNodeWithText("Gateway credential", substring = true)
            .assertTextContains("synthetic-token")
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Password))
            .performClick()
        val info = currentEditorInfo()
        assertEquals(InputType.TYPE_TEXT_VARIATION_PASSWORD, info.inputType and InputType.TYPE_MASK_VARIATION)
        composeTestRule.onNodeWithContentDescription("Hide credential").performClick()
        composeTestRule.onNodeWithText("Gateway credential", substring = true)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
        assertTrue(events.isEmpty())
    }

    @Test
    fun an_unverified_address_does_not_offer_connection_removal() {
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(state = setupState(), onEvent = {})
            }
        }

        composeTestRule.onNodeWithText("[ manage ]").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("Connection actions").assertDoesNotExist()
    }

    @Test
    fun a_saved_connection_can_be_removed_before_reverification() {
        val holder =
            EntryStateHolder(
                EntryState(isGatewayConnectionConfigured = true, configuredEndpoint = "https://gateway.example"),
            )
        val events = mutableListOf<EntryUiEvent>()
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(state = holder.uiState.value, onEvent = events::add)
            }
        }

        composeTestRule.onNodeWithContentDescription("Connection actions").performClick()
        composeTestRule.onNodeWithText("Remove connection").performClick()
        assertEquals(listOf(EntryUiEvent.RemoveGatewayConnectionClicked), events)
        holder.close()
    }

    @Test
    fun setup_has_a_bar_heading_a_form_heading_and_an_https_address_example() {
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(state = setupState().copy(endpoint = ""), onEvent = {})
            }
        }

        composeTestRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)).assertCountEquals(2)
        composeTestRule.onNodeWithText("Address only. https:// is already added.").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w411dp-h480dp")
    fun verification_and_recovery_scroll_into_view_at_large_font_scale() {
        val state = mutableStateOf(setupState())
        composeTestRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                HermesTheme {
                    EntryScreen(state = state.value, onEvent = {})
                }
            }
        }
        composeTestRule.onNodeWithText("Gateway credential", substring = true).performScrollTo().performClick()
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

        fun assertAboveKeyboard(text: String) {
            val action = composeTestRule.onNodeWithText(text).performScrollTo().assertIsDisplayed()
            val bottom = action.fetchSemanticsNode().boundsInWindow.bottom
            assertTrue("$text must be above the keyboard (bottom=$bottom)", bottom <= windowHeight - imeInset + 1f)
        }

        assertAboveKeyboard("Connect")
        composeTestRule.runOnIdle {
            state.value = state.value.copy(errorCategory = EntryErrorCategory.GATEWAY_REQUEST_FAILED)
        }
        assertAboveKeyboard("[ try again ]")
        assertAboveKeyboard(copy(R.string.gateway_error_network))
    }

    @Test
    fun address_next_moves_to_the_credential_without_verifying() {
        val events = mutableListOf<EntryUiEvent>()
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(state = setupState(), onEvent = events::add)
            }
        }
        composeTestRule.onNodeWithText("Gateway address", substring = true).performClick()
        val addressInfo = currentEditorInfo()
        assertEquals(InputType.TYPE_TEXT_VARIATION_URI, addressInfo.inputType and InputType.TYPE_MASK_VARIATION)
        assertEquals(EditorInfo.IME_ACTION_NEXT, addressInfo.imeOptions and EditorInfo.IME_MASK_ACTION)
        composeTestRule.onNodeWithText("Gateway address", substring = true).performImeAction()
        composeTestRule.onNodeWithText("Gateway credential", substring = true).assertIsFocused()
        val credentialInfo = currentEditorInfo()
        assertEquals(EditorInfo.IME_ACTION_DONE, credentialInfo.imeOptions and EditorInfo.IME_MASK_ACTION)
        composeTestRule.onNodeWithText("Gateway credential", substring = true).performImeAction()
        assertTrue(events.isEmpty())
    }

    private fun currentEditorInfo(): EditorInfo {
        val info = EditorInfo()
        composeTestRule.runOnUiThread {
            val editor = composeTestRule.activity.window.decorView.findInputEditor()
            assertNotNull("The focused credential must have an input editor", editor)
            assertNotNull(editor!!.onCreateInputConnection(info))
        }
        return info
    }

    private fun setupState(): EntryUiState =
        EntryUiState(
            title = UiText.Resource(R.string.entry_title_verify_new),
            supportingText = UiText.Resource(R.string.entry_supporting_enter_credentials),
            actionLabel = UiText.Resource(R.string.entry_action_verify),
            connectionSetupRequested = true,
            endpoint = "https://gateway.example",
            bearerCredential = "synthetic-token",
        )

    private fun View.findInputEditor(): View? {
        if (onCheckIsTextEditor()) return this
        return (this as? ViewGroup)?.let { group ->
            (0 until group.childCount).firstNotNullOfOrNull { index -> group.getChildAt(index).findInputEditor() }
        }
    }
}
