package org.mercurio.client.feature.entry.presentation

import android.graphics.BitmapFactory
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * The two in-place actions the approved Chat design draws as text: the run line's refresh and the
 * composer's `[ send ]`. Both keep a full 48 dp target and neither grows an outlined border.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-notnight")
class ConversationTextControlsTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun the_run_line_action_is_a_text_control_with_a_full_target() {
        val events = mutableListOf<EntryUiEvent>()
        var outline = Color.Unspecified
        composeTestRule.setContent {
            HermesTheme {
                outline = MaterialTheme.colorScheme.outline
                EntryScreen(
                    state =
                        entryState(
                            SessionListUiState(
                                openedSession =
                                    OpenSessionUiState(
                                        session = session("first", "First Session"),
                                        messages = emptyList(),
                                        isCached = true,
                                        isStale = true,
                                        errorCategory = SessionHistoryErrorCategory.GATEWAY_REQUEST_FAILED,
                                    ),
                            ),
                        ),
                    onEvent = events::add,
                )
            }
        }
        val control = composeTestRule.onNodeWithTag("chat-run-action").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        assertNoOutlineBorder(control, "run-line refresh", outline)
        control.performClick()
        assertEquals(listOf(EntryUiEvent.RefreshSessionsClicked), events)
    }

    @Test
    fun the_send_action_is_a_text_control_with_a_full_target() {
        val events = mutableListOf<EntryUiEvent>()
        var outline = Color.Unspecified
        composeTestRule.setContent {
            HermesTheme {
                outline = MaterialTheme.colorScheme.outline
                EntryScreen(
                    state =
                        entryState(
                            SessionListUiState(
                                openedSession =
                                    OpenSessionUiState(
                                        session = session("first", "First Session"),
                                        messages = emptyList(),
                                        composerText = "Draft",
                                    ),
                            ),
                        ),
                    onEvent = events::add,
                )
            }
        }
        val control =
            composeTestRule
                .onNodeWithTag("conversation-send")
                .assertIsDisplayed()
                .assertHeightIsAtLeast(48.dp)
        assertNoOutlineBorder(control, "send", outline)
        control.performClick()
        assertEquals(listOf(EntryUiEvent.SendMessageClicked), events)
    }

    private fun assertNoOutlineBorder(
        control: SemanticsNodeInteraction,
        label: String,
        outline: Color,
    ) {
        val capture = File("build/tmp/text-controls/$label.png").apply { parentFile.mkdirs() }
        control.captureRoboImage(
            filePath = capture.absolutePath,
            roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record),
        )
        val pixels = BitmapFactory.decodeFile(capture.absolutePath)
        val outlinePixels =
            (0 until pixels.height).sumOf { y ->
                (0 until pixels.width).count { x -> pixels.getPixel(x, y) == outline.toArgb() }
            }
        assertEquals("$label must not have an outlined border", 0, outlinePixels)
    }
}
