package org.mercurio.client.feature.entry.presentation

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mercurio.client.feature.entry.application.NightlyDownloadRefusal
import org.mercurio.client.feature.entry.application.NightlyUpdateFailure
import org.mercurio.client.feature.entry.application.NightlyUpdateRefusal
import org.mercurio.client.feature.entry.domain.InstallSource
import org.mercurio.client.feature.entry.domain.NightlyApk
import org.mercurio.client.feature.entry.domain.NightlyDownloadProgress
import org.mercurio.client.feature.entry.domain.NightlyUpdateOffer
import org.mercurio.client.feature.entry.domain.SessionId
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class SettingsAppUpdatesScreenTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun settings_app_updates_section_reports_the_offered_build_identifiers() {
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(
                    state =
                        settingsState().copy(
                            appUpdates = AppUpdatesUiState(status = AppUpdateStatus.Offered(offeredOffer())),
                        ),
                    onEvent = {},
                )
            }
        }

        composeTestRule.onNodeWithText("APP UPDATES").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("nightly-7002-0123456789ab · 0123456").performScrollTo().assertIsDisplayed()
        composeTestRule
            .onNodeWithText("2026-11-05 18:31 UTC · nightly-7002-0123456789abcdef0123456789abcdef01234567")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Check for updates").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Download").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun settings_app_updates_download_action_raises_the_download_event() {
        val events = mutableListOf<EntryUiEvent>()
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(
                    state =
                        settingsState().copy(
                            appUpdates = AppUpdatesUiState(status = AppUpdateStatus.Offered(offeredOffer())),
                        ),
                    onEvent = events::add,
                )
            }
        }

        composeTestRule.onNodeWithText("Download").performScrollTo().performClick()

        assertEquals(listOf<EntryUiEvent>(EntryUiEvent.DownloadUpdateClicked), events)
    }

    @Test
    fun settings_app_updates_running_download_shows_progress_and_a_cancel_action() {
        val events = mutableListOf<EntryUiEvent>()
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(
                    state =
                        settingsState().copy(
                            appUpdates =
                                AppUpdatesUiState(
                                    status =
                                        AppUpdateStatus.Downloading(
                                            NightlyDownloadProgress(6_501_171L, 13_002_342L),
                                        ),
                                ),
                        ),
                    onEvent = events::add,
                )
            }
        }

        composeTestRule.onNodeWithText("Downloading 50%").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").performScrollTo().performClick()

        assertEquals(listOf<EntryUiEvent>(EntryUiEvent.CancelUpdateDownloadClicked), events)
    }

    @Test
    fun settings_app_updates_signer_refusal_names_the_certificate_mismatch() {
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(
                    state =
                        settingsState().copy(
                            appUpdates =
                                AppUpdatesUiState(
                                    status =
                                        AppUpdateStatus.DownloadRefused(
                                            NightlyDownloadRefusal.SIGNER_MISMATCH,
                                        ),
                                ),
                        ),
                    onEvent = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Download refused").performScrollTo().assertIsDisplayed()
        composeTestRule
            .onNodeWithText(
                "Update refused: the download is not signed by this app's certificate. Nothing was installed.",
            )
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun settings_app_updates_action_is_disabled_when_google_play_manages_the_install() {
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(state = settingsState(installSource = InstallSource.GOOGLE_PLAY), onEvent = {})
            }
        }

        composeTestRule
            .onNodeWithText("Google Play manages updates for this app.")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Check for updates").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun settings_app_updates_section_offers_the_check_action() {
        val events = mutableListOf<EntryUiEvent>()
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(state = settingsState(), onEvent = events::add)
            }
        }

        composeTestRule.onNodeWithText("Check for updates").performScrollTo().performClick()

        assertEquals(listOf<EntryUiEvent>(EntryUiEvent.CheckForUpdatesClicked), events)
    }

    @Test
    fun settings_app_updates_section_keeps_a_refusal_banner_with_its_release_tag() {
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(
                    state =
                        settingsState().copy(
                            appUpdates =
                                AppUpdatesUiState(
                                    status =
                                        AppUpdateStatus.Refused(
                                            releaseTag = "nightly-7002-0123456789abcdef0123456789abcdef01234567",
                                            reason = NightlyUpdateRefusal.CONTRADICTORY_RELEASE,
                                        ),
                                ),
                        ),
                    onEvent = {},
                )
            }
        }

        composeTestRule
            .onNodeWithText(
                "Update refused: release nightly-7002-0123456789abcdef0123456789abcdef01234567 " +
                    "contradicts its signing metadata. Nothing was downloaded.",
            ).performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun settings_app_updates_section_lists_the_installed_version() {
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(state = settingsState(), onEvent = {})
            }
        }

        composeTestRule.onNodeWithText("Installed version").performScrollTo().assertIsDisplayed()
        composeTestRule.onAllNodesWithText("nightly-6001-89abcdef0123").assertCountEquals(2)
    }

    @Test
    fun settings_app_updates_section_pins_the_failed_banner_copy() {
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(
                    state =
                        settingsState().copy(
                            appUpdates =
                                AppUpdatesUiState(
                                    status = AppUpdateStatus.Failed(NightlyUpdateFailure.SERVICE_UNAVAILABLE),
                                ),
                        ),
                    onEvent = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Check failed").performScrollTo().assertIsDisplayed()
        composeTestRule
            .onNodeWithText("The update service did not answer. Try again later.")
            .performScrollTo()
            .assertIsDisplayed()
    }

    private fun offeredOffer(): NightlyUpdateOffer =
        NightlyUpdateOffer(
            releaseTag = "nightly-7002-0123456789abcdef0123456789abcdef01234567",
            versionName = "nightly-7002-0123456789ab",
            versionCode = 7002L,
            sourceCommit = "0123456",
            publishedAt = Instant.parse("2026-11-05T18:31:00Z"),
            apk =
                NightlyApk(
                    address = "https://api.github.com/repos/Gvetri/Mercurio/releases/assets/1",
                    sizeBytes = 13_002_342L,
                    sha256 = "1111111111111111111111111111111111111111111111111111111111111111",
                    certificateSha256 = "2222222222222222222222222222222222222222222222222222222222222222",
                    nativeAbis = listOf("arm64-v8a"),
                ),
        )

    private fun settingsState(installSource: InstallSource = InstallSource.OTHER_SOURCE): EntryUiState =
        EntryUiState(
            title = UiText.Resource(R.string.entry_title_connected),
            supportingText = UiText.Resource(R.string.entry_supporting_connected),
            actionLabel = UiText.Resource(R.string.entry_action_connected),
            isConnected = true,
            isSettingsOpen = true,
            clientVersion = "nightly-6001-89abcdef0123",
            buildNumber = 6001L,
            installSource = installSource,
            sessionList =
                SessionListUiState(
                    sessions =
                        listOf(
                            SessionItemUiState(
                                id = SessionId("first"),
                                title = "First Session",
                                preview = "First preview",
                                pinned = false,
                            ),
                        ),
                ),
        )
}
