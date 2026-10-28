package org.mercurio.client.feature.entry.presentation

import android.content.res.Configuration
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

/**
 * Pins the accepted Create Session dialog input typography at the public rendering boundary.
 *
 * The standard dialog's text slot provides `bodyMedium` to descendants, so the input only
 * renders the accepted owner-approved `bodyLarge` role (16sp/24sp/0.5sp) when the dialog
 * passes it explicitly. The regression reads the text layout the production composition
 * actually used for rendering, so moving the input back to the inherited style fails here
 * even before any golden comparison runs.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-notnight")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CreateSessionDialogRenderingTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    /**
     * Robolectric's paused looper cannot settle the standard dialog window with its text field
     * (robolectric/robolectric#8460). Only the looper mode changes here; the measured layout is
     * the same production composition the device journeys exercise.
     */
    @LooperMode(LooperMode.Mode.LEGACY)
    @Test
    fun create_session_dialog_input_renders_the_accepted_body_large_role() {
        setDialogContent(titleDraft = "Release checklist")

        val input = inputTextLayout("Release checklist")

        assertEquals(16.sp, input.layoutInput.style.fontSize)
        assertEquals(24.sp, input.layoutInput.style.lineHeight)
        assertEquals(0.5.sp, input.layoutInput.style.letterSpacing)
    }

    /**
     * Robolectric's paused looper cannot settle the standard dialog window with its text field
     * (robolectric/robolectric#8460). Only the looper mode changes here; the measured layout is
     * the same production composition the device journeys exercise.
     */
    @LooperMode(LooperMode.Mode.LEGACY)
    @Test
    fun create_session_dialog_input_keeps_the_body_large_role_at_twice_the_font_scale() {
        val application = RuntimeEnvironment.getApplication()
        val configuration = Configuration(application.resources.configuration)
        configuration.fontScale = 2f
        application.resources.updateConfiguration(configuration, application.resources.displayMetrics)

        setDialogContent(titleDraft = "Release checklist")

        val input = inputTextLayout("Release checklist")

        assertEquals(16.sp, input.layoutInput.style.fontSize)
        assertEquals(24.sp, input.layoutInput.style.lineHeight)
        assertTrue(
            "The accepted 24sp line must scale with the 2x font scale (rendered height=${input.size.height}).",
            input.size.height >= 36,
        )
    }

    private fun setDialogContent(titleDraft: String) {
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(
                    state =
                        EntryUiState(
                            title = "Gateway connected",
                            supportingText = "",
                            actionLabel = "",
                            sessionList =
                                SessionListUiState(
                                    createSession = SessionCreationUiState(titleDraft = titleDraft),
                                ),
                        ),
                    onEvent = {},
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun inputTextLayout(draft: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        composeTestRule.onNodeWithText(draft).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult]
            .action
            ?.invoke(results)
        return results.singleOrNull()
            ?: throw AssertionError("The dialog input rendered no text layout result.")
    }
}
