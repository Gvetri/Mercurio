package org.mercurio.client.feature.entry.presentation

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.RoborazziRule
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mercurio.client.feature.entry.domain.InstallSource
import org.mercurio.client.feature.entry.domain.SessionId
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The approved Settings design's shipped states, captured at the design's review size. Each capture
 * is compared with the design page by the reviewer checklist, not by a pixel diff across renderers.
 *
 * Security, Appearance, App updates, and the error-reporting consent row wait on their own
 * capabilities, so their frames are absent here; #273 ships the sections whose capability exists.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-notnight")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)
class SettingsDesignStatesTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @get:Rule
    val roborazziRule = RoborazziRule()

    @Test
    fun settings_02_connected_light() = captureSettings(dark = false)

    @Test
    @Config(qualifiers = "w360dp-h800dp-night")
    fun settings_02_connected_dark() = captureSettings(dark = true)

    @Test
    fun settings_03_connection_menu_light() = captureSettings(dark = false, beforeCapture = { openConnectionMenu() })

    @Test
    @Config(qualifiers = "w360dp-h800dp-night")
    fun settings_03_connection_menu_dark() = captureSettings(dark = true, beforeCapture = { openConnectionMenu() })

    @Test
    fun settings_10_notifications_on_light() = captureSettings(dark = false, state = notificationsOnState())

    @Test
    @Config(qualifiers = "w360dp-h800dp-night")
    fun settings_10_notifications_on_dark() = captureSettings(dark = true, state = notificationsOnState())

    @Test
    fun settings_12_media_off_light() = captureSettings(dark = false, state = settingsState(autoLoadImages = false))

    @Test
    @Config(qualifiers = "w360dp-h800dp-night")
    fun settings_12_media_off_dark() = captureSettings(dark = true, state = settingsState(autoLoadImages = false))

    @Test
    fun settings_23_about_light() = captureSettings(dark = false, beforeCapture = { scrollToAbout() })

    @Test
    @Config(qualifiers = "w360dp-h800dp-night")
    fun settings_23_about_dark() = captureSettings(dark = true, beforeCapture = { scrollToAbout() })

    @Test
    @Config(qualifiers = "w360dp-h640dp-notnight")
    fun settings_27_font_scale_200_light() = captureSettings(dark = false, fontScale = 2f)

    @Test
    @Config(qualifiers = "w360dp-h640dp-night")
    fun settings_27_font_scale_200_dark() = captureSettings(dark = true, fontScale = 2f)

    private fun captureSettings(
        dark: Boolean,
        state: EntryUiState = settingsState(),
        fontScale: Float = 1f,
        beforeCapture: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                HermesTheme(darkTheme = dark) {
                    EntryScreen(state = state, onEvent = {})
                }
            }
        }
        composeTestRule.waitForIdle()
        beforeCapture()
        composeTestRule.waitForIdle()
        captureScreenRoboImage()
    }

    private fun openConnectionMenu() {
        composeTestRule
            .onNode(hasContentDescription("Connection actions"))
            .performClick()
    }

    private fun scrollToAbout() {
        composeTestRule.onNodeWithText("ABOUT").performScrollTo()
    }

    private fun notificationsOnState(): EntryUiState = settingsState(notificationsEnabled = true)

    private fun settingsState(
        notificationsEnabled: Boolean = false,
        autoLoadImages: Boolean = true,
    ): EntryUiState =
        EntryUiState(
            title = UiText.Resource(R.string.entry_title_connected),
            supportingText = UiText.Value(""),
            actionLabel = UiText.Value(""),
            isConnected = true,
            isSettingsOpen = true,
            clientVersion = "1.3.2",
            buildNumber = 412L,
            installSource = InstallSource.GOOGLE_PLAY,
            autoLoadImages = autoLoadImages,
            runStatusNotifications = RunStatusNotificationsUiState(enabled = notificationsEnabled),
            sessionList =
                SessionListUiState(
                    sessions =
                        listOf(
                            SessionItemUiState(
                                id = SessionId("release-checklist"),
                                title = "Release checklist",
                                preview = "Review the release checklist.",
                                pinned = true,
                            ),
                        ),
                ),
        )
}
