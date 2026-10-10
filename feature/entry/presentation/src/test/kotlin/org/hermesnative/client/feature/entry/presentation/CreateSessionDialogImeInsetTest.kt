package org.hermesnative.client.feature.entry.presentation

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowDialog
import kotlin.math.roundToInt

/**
 * Pins the IME-aware Create Session dialog seam at the measured short-window witness.
 *
 * The device evidence measures the real Gboard at 891x411 dp as 679 px (258.7 dp) tall, while
 * the dialog's action row lands below the keyboard edge. This test models that window and that
 * reported IME inset through the platform's public window-inset dispatch and requires the
 * dialog to reserve the IME space: the Create action must stay above the reported IME top edge
 * whenever the space above the IME can host the dialog (the measured witness does).
 *
 * A dialog that ignores the reported IME insets keeps its action row below the IME top edge and
 * fails here; the IME-aware dialog bounds its scrollable text region above the keyboard and
 * keeps the action row visible.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w891dp-h411dp-notnight")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CreateSessionDialogImeInsetTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    // Robolectric's paused looper cannot settle the standard dialog window with its text field
    // (robolectric/robolectric#8460). Only the looper mode changes here; the measured layout is
    // the same production composition the device journeys exercise.
    @LooperMode(LooperMode.Mode.LEGACY)
    @Test
    fun create_session_dialog_action_row_stays_above_the_reported_ime() {
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
                                    createSession =
                                        SessionCreationUiState(titleDraft = "Release checklist"),
                                ),
                        ),
                    onEvent = {},
                )
            }
        }
        composeTestRule.waitForIdle()

        val dialog = ShadowDialog.getLatestDialog()
        val decorView = dialog.window!!.decorView
        val density = decorView.resources.displayMetrics.density
        // The measured real-Gboard height at this geometry: 258.7 dp (679 px at 420 dpi).
        val imePx = (258.7f * density).roundToInt()
        ViewCompat.dispatchApplyWindowInsets(
            decorView,
            WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, imePx))
                .setVisible(WindowInsetsCompat.Type.ime(), true)
                .build(),
        )
        composeTestRule.waitForIdle()

        val create = composeTestRule.onNodeWithText("Create").fetchSemanticsNode()
        val createBottom = create.boundsInWindow.bottom
        val windowHeight = decorView.height
        val imeTop = windowHeight - imePx
        println(
            "ime-inset-seam: window=${decorView.width}x$windowHeight ime=$imePx " +
                "createBottom=$createBottom imeTop=$imeTop",
        )
        assertTrue(
            "The Create action must stay above the reported IME top edge " +
                "(create bottom=$createBottom, IME top=$imeTop, window height=$windowHeight, " +
                "IME height=$imePx).",
            createBottom <= imeTop,
        )
    }
}
