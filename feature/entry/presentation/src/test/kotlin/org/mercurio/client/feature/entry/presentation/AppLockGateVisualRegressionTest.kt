package org.mercurio.client.feature.entry.presentation

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RoborazziRule
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-notnight")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppLockGateVisualRegressionTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @get:Rule
    val roborazziRule = RoborazziRule()

    @Test
    @Config(qualifiers = "w360dp-h800dp-notnight")
    fun app_lock_gate_light() {
        showGate(darkTheme = false)
        capture()
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp-night")
    fun app_lock_gate_dark() {
        showGate(darkTheme = true)
        capture()
    }

    private fun showGate(darkTheme: Boolean) {
        composeTestRule.setContent {
            HermesTheme(darkTheme = darkTheme) {
                EntryScreen(state = lockedState(), onEvent = {})
            }
        }
    }

    private fun lockedState(): EntryUiState =
        EntryUiState(
            title = UiText.Resource(R.string.entry_title_connected),
            supportingText = UiText.Value(""),
            actionLabel = UiText.Value(""),
            isConnected = true,
            clientVersion = "1.3.2",
            appLock = AppLockUiState(isEnabled = true, isAvailable = true, isLocked = true),
        )

    private fun capture() {
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage()
    }
}
