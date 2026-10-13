package org.hermesnative.client.feature.entry.presentation

import android.graphics.BitmapFactory
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
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

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-notnight")
class ConversationTextControlsTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun back_and_refresh_are_text_controls_with_unchanged_targets() {
        var outline = Color.Unspecified
        val events = mutableListOf<EntryUiEvent>()
        composeTestRule.setContent {
            HermesTheme {
                outline = MaterialTheme.colorScheme.outline
                EntryScreen(
                    state =
                        entryState(
                            SessionListUiState(
                                openedSession = OpenSessionUiState(session("first", "First Session"), emptyList()),
                            ),
                        ),
                    onEvent = events::add,
                )
            }
        }
        listOf("Back to Sessions", "Refresh history").forEach { label ->
            val control = composeTestRule.onNodeWithText(label).assertIsDisplayed().assertHeightIsAtLeast(48.dp)
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
            control.performClick()
        }
        assertEquals(listOf(EntryUiEvent.ReturnToSessionListClicked, EntryUiEvent.RefreshSessionsClicked), events)
    }
}
