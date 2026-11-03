package org.mercurio.client.feature.entry.presentation

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RoborazziRule
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mercurio.client.feature.entry.domain.SessionId
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-notnight")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SharedTextVisualRegressionTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @get:Rule
    val roborazziRule = RoborazziRule()

    @Test
    fun session_list_shared_text_picker_light() {
        setContent(darkTheme = false)
        capture()
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-night")
    fun session_list_shared_text_picker_dark() {
        setContent(darkTheme = true)
        capture()
    }

    private fun setContent(darkTheme: Boolean) {
        composeTestRule.setContent {
            HermesTheme(darkTheme = darkTheme) {
                EntryScreen(state = pickerState(), onEvent = {})
            }
        }
    }

    private fun pickerState(): EntryUiState =
        EntryUiState(
            title = UiText.Resource(R.string.entry_title_connected),
            supportingText = UiText.Value(""),
            actionLabel = UiText.Value(""),
            isConnected = true,
            inboundLinkNotice = InboundLinkNoticeCategory.SHARE_PICK_SESSION,
            isSharedTextSessionPickerActive = true,
            sessionList =
                SessionListUiState(
                    sessions =
                        listOf(
                            SessionItemUiState(
                                id = SessionId("nightly"),
                                title = "Nightly pipeline triage",
                                preview = "Two failing tests; retry green",
                                pinned = true,
                            ),
                            SessionItemUiState(
                                id = SessionId("gateway"),
                                title = "Gateway connection check",
                                preview = "Endpoint and stored credential",
                                pinned = false,
                            ),
                        ),
                ),
        )

    private fun capture() {
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage()
    }
}
