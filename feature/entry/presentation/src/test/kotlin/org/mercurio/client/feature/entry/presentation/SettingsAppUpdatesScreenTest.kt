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
import org.mercurio.client.feature.entry.application.NightlyUpdateFailure
import org.mercurio.client.feature.entry.application.NightlyUpdateRefusal
import org.mercurio.client.feature.entry.domain.InstallSource
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
        val publishedAt = Instant.parse("2026-11-05T18:31:00Z")
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(
                    state =
                        settingsState().copy(
                            appUpdates =
                                AppUpdatesUiState(
                                    status =
                                        AppUpdateStatus.Offered(
                                            versionName = "nightly-7002-0123456789ab",
                                            sourceCommit = "0123456",
                                            publishedAt = publishedAt,
                                            releaseTag = "nightly-7002-0123456789abcdef0123456789abcdef01234567",
                                        ),
                                ),
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
