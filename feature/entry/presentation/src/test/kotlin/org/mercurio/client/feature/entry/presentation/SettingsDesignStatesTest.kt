package org.mercurio.client.feature.entry.presentation

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.RoborazziRule
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mercurio.client.feature.entry.application.NightlyUpdateRefusal
import org.mercurio.client.feature.entry.domain.ErrorReportingConsent
import org.mercurio.client.feature.entry.domain.InstallSource
import org.mercurio.client.feature.entry.domain.SessionId
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant

/**
 * The approved Settings design's shipped states, captured at the design's review size. Each capture
 * is compared with the design page by the reviewer checklist, not by a pixel diff across renderers.
 *
 * Appearance waits on its own capability, so its frames are absent here. #273 ships the sections
 * whose capability exists, #268 adds the Security frames with the app-lock capability they draw, and
 * the error-reporting consent row appears only with a reporting endpoint, so its absent state is
 * captured as an extra witness. The App updates frames cover the states the manual check reaches:
 * idle, checking, up to date, offered, and refused. Downloading, permission, installed, and recovery
 * wait on the later tickets that add those capabilities.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-notnight")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)
class SettingsDesignStatesTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

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
    fun settings_06_security_no_enrolment_light() = captureSettings(dark = false, state = security(unavailable))

    @Test
    @Config(qualifiers = "w360dp-h800dp-night")
    fun settings_06_security_no_enrolment_dark() = captureSettings(dark = true, state = security(unavailable))

    @Test
    fun settings_07_security_off_light() = captureSettings(dark = false, state = security(gateOff))

    @Test
    @Config(qualifiers = "w360dp-h800dp-night")
    fun settings_07_security_off_dark() = captureSettings(dark = true, state = security(gateOff))

    @Test
    fun settings_08_security_on_light() = captureSettings(dark = false, state = security(gateOn))

    @Test
    @Config(qualifiers = "w360dp-h800dp-night")
    fun settings_08_security_on_dark() = captureSettings(dark = true, state = security(gateOn))

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
    @Config(qualifiers = "w360dp-h640dp-notnight")
    fun settings_23_about_light() = captureSettings(dark = false, beforeCapture = { scrollToAbout() })

    @Test
    @Config(qualifiers = "w360dp-h640dp-night")
    fun settings_23_about_dark() = captureSettings(dark = true, beforeCapture = { scrollToAbout() })

    @Test
    @Config(qualifiers = "w360dp-h640dp-notnight")
    fun settings_27_font_scale_200_light() = captureSettings(dark = false, fontScale = 2f)

    @Test
    @Config(qualifiers = "w360dp-h640dp-night")
    fun settings_27_font_scale_200_dark() = captureSettings(dark = true, fontScale = 2f)

    @Test
    fun settings_24_error_reporting_off_light() =
        captureSettings(
            dark = false,
            state = errorReportingState(granted = false),
            beforeCapture = { scrollToErrorReporting() },
        )

    @Test
    @Config(qualifiers = "w360dp-h800dp-night")
    fun settings_24_error_reporting_off_dark() =
        captureSettings(
            dark = true,
            state = errorReportingState(granted = false),
            beforeCapture = { scrollToErrorReporting() },
        )

    @Test
    fun settings_25_error_reporting_on_light() =
        captureSettings(
            dark = false,
            state = errorReportingState(granted = true),
            beforeCapture = { scrollToErrorReporting() },
        )

    @Test
    @Config(qualifiers = "w360dp-h800dp-night")
    fun settings_25_error_reporting_on_dark() =
        captureSettings(
            dark = true,
            state = errorReportingState(granted = true),
            beforeCapture = { scrollToErrorReporting() },
        )

    @Test
    fun settings_14_app_updates_idle_light() =
        captureSettings(
            dark = false,
            state = appUpdatesState(AppUpdateStatus.Idle),
            beforeCapture = ::scrollToAppUpdates,
        )

    @Test
    @Config(qualifiers = "w360dp-h800dp-night")
    fun settings_14_app_updates_idle_dark() =
        captureSettings(
            dark = true,
            state = appUpdatesState(AppUpdateStatus.Idle),
            beforeCapture = ::scrollToAppUpdates,
        )

    @Test
    fun settings_15_app_updates_checking_light() =
        captureSettings(
            dark = false,
            state = appUpdatesState(AppUpdateStatus.Checking),
            beforeCapture = ::scrollToAppUpdates,
        )

    @Test
    @Config(qualifiers = "w360dp-h800dp-night")
    fun settings_15_app_updates_checking_dark() =
        captureSettings(
            dark = true,
            state = appUpdatesState(AppUpdateStatus.Checking),
            beforeCapture = ::scrollToAppUpdates,
        )

    @Test
    fun settings_16_app_updates_up_to_date_light() =
        captureSettings(
            dark = false,
            state = appUpdatesState(AppUpdateStatus.UpToDate(Instant.parse("2026-11-05T18:41:00Z"))),
            beforeCapture = ::scrollToAppUpdates,
        )

    @Test
    @Config(qualifiers = "w360dp-h800dp-night")
    fun settings_16_app_updates_up_to_date_dark() =
        captureSettings(
            dark = true,
            state = appUpdatesState(AppUpdateStatus.UpToDate(Instant.parse("2026-11-05T18:41:00Z"))),
            beforeCapture = ::scrollToAppUpdates,
        )

    @Test
    fun settings_17_app_updates_offered_light() =
        captureSettings(
            dark = false,
            state = appUpdatesState(offeredStatus()),
            beforeCapture = ::scrollToAppUpdates,
        )

    @Test
    @Config(qualifiers = "w360dp-h800dp-night")
    fun settings_17_app_updates_offered_dark() =
        captureSettings(
            dark = true,
            state = appUpdatesState(offeredStatus()),
            beforeCapture = ::scrollToAppUpdates,
        )

    @Test
    fun settings_19_app_updates_refused_light() =
        captureSettings(
            dark = false,
            state = appUpdatesState(refusedStatus()),
            beforeCapture = ::scrollToAppUpdates,
        )

    @Test
    @Config(qualifiers = "w360dp-h800dp-night")
    fun settings_19_app_updates_refused_dark() =
        captureSettings(
            dark = true,
            state = appUpdatesState(refusedStatus()),
            beforeCapture = ::scrollToAppUpdates,
        )

    @Test
    fun settings_error_reporting_absent_light() =
        captureSettings(
            dark = false,
            state = settingsState(),
            beforeCapture = { scrollToDiagnostics() },
        )

    @Test
    @Config(qualifiers = "w360dp-h800dp-night")
    fun settings_error_reporting_absent_dark() =
        captureSettings(
            dark = true,
            state = settingsState(),
            beforeCapture = { scrollToDiagnostics() },
        )

    private fun captureSettings(
        dark: Boolean,
        state: EntryUiState = settingsState(),
        fontScale: Float = 1f,
        beforeCapture: () -> Unit = {},
    ) {
        composeTestRule.activity.actionBar?.hide()
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
        composeTestRule.onNodeWithText("Install source").performScrollTo()
    }

    private fun scrollToErrorReporting() {
        composeTestRule.onNodeWithText("Share crash and error reports").performScrollTo()
    }

    private fun scrollToAppUpdates() {
        composeTestRule.onNodeWithText("APP UPDATES").performScrollTo()
    }

    private fun appUpdatesState(status: AppUpdateStatus): EntryUiState =
        settingsState().copy(appUpdates = AppUpdatesUiState(status = status))

    private fun offeredStatus(): AppUpdateStatus =
        AppUpdateStatus.Offered(
            versionName = "nightly-7002-0123456789ab",
            sourceCommit = "0123456",
            publishedAt = Instant.parse("2026-11-05T18:31:00Z"),
            releaseTag = "nightly-7002-0123456789abcdef0123456789abcdef01234567",
        )

    private fun refusedStatus(): AppUpdateStatus =
        AppUpdateStatus.Refused(
            releaseTag = "nightly-7002-0123456789abcdef0123456789abcdef01234567",
            reason = NightlyUpdateRefusal.CONTRADICTORY_RELEASE,
        )

    private fun scrollToDiagnostics() {
        composeTestRule.onNodeWithText("DIAGNOSTICS").performScrollTo()
    }

    private fun errorReportingState(granted: Boolean): EntryUiState =
        settingsState().copy(
            isErrorReportingConfigured = true,
            errorReportingConsent =
                if (granted) ErrorReportingConsent.Granted else ErrorReportingConsent.Declined,
        )

    private fun notificationsOnState(): EntryUiState = settingsState(notificationsEnabled = true)

    private val unavailable = AppLockUiState(isAvailable = false)

    private val gateOff = AppLockUiState(isAvailable = true)

    private val gateOn = AppLockUiState(isEnabled = true, isAvailable = true)

    private fun security(appLock: AppLockUiState): EntryUiState = settingsState(appLock = appLock)

    private fun settingsState(
        notificationsEnabled: Boolean = false,
        autoLoadImages: Boolean = true,
        appLock: AppLockUiState = AppLockUiState(isAvailable = true),
        installSource: InstallSource = InstallSource.OTHER_SOURCE,
    ): EntryUiState =
        EntryUiState(
            title = UiText.Resource(R.string.entry_title_connected),
            supportingText = UiText.Value(""),
            actionLabel = UiText.Value(""),
            isConnected = true,
            isSettingsOpen = true,
            clientVersion = "1.3.2",
            buildNumber = 412L,
            installSource = installSource,
            autoLoadImages = autoLoadImages,
            appLock = appLock,
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
