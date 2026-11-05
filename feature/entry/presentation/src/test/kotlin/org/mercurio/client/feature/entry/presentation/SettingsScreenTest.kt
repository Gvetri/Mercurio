package org.mercurio.client.feature.entry.presentation

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mercurio.client.feature.entry.domain.ErrorReportingConsent
import org.mercurio.client.feature.entry.domain.InstallSource
import org.mercurio.client.feature.entry.domain.SessionId
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class SettingsScreenTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun session_list_header_offers_settings_and_keeps_global_controls_off_the_list() {
        val events = mutableListOf<EntryUiEvent>()
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(state = connectedListState(), onEvent = events::add)
            }
        }

        composeTestRule.onNodeWithText("Notifications").assertDoesNotExist()
        composeTestRule
            .onNodeWithContentDescription("Settings")
            .assertHasClickAction()
            .performClick()

        assertEquals(listOf<EntryUiEvent>(EntryUiEvent.OpenSettingsClicked), events)
    }

    @Test
    fun settings_surface_renders_the_decided_sections_in_order() {
        val events = mutableListOf<EntryUiEvent>()
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(
                    state =
                        connectedListState(
                            isSettingsOpen = true,
                            clientVersion = "demo-build.1",
                            buildNumber = 412L,
                            installSource = InstallSource.GOOGLE_PLAY,
                        ),
                    onEvent = events::add,
                )
            }
        }

        composeTestRule.onNodeWithText("Settings").assertIsDisplayed()
        composeTestRule.onNodeWithText("CONNECTION").assertIsDisplayed()
        composeTestRule.onNodeWithText("SECURITY").assertIsDisplayed()
        composeTestRule.onNodeWithText("NOTIFICATIONS").assertIsDisplayed()
        composeTestRule.onNodeWithText("MEDIA").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("DIAGNOSTICS").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("ABOUT").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Connected · gateway ready").assertIsDisplayed()
        composeTestRule.onNodeWithText("App lock").assertIsDisplayed()
        composeTestRule.onNodeWithText("Notifications").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Load pictures automatically").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Local diagnostics").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Clear cached conversations").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Version").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("demo-build.1").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Build").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("412").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Install source").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Google Play").performScrollTo().assertIsDisplayed()
        assertTrue(events.isEmpty())
    }

    @Test
    fun settings_sections_are_ordered_from_top_to_bottom() {
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(state = connectedListState(isSettingsOpen = true), onEvent = {})
            }
        }

        val tops =
            listOf("CONNECTION", "SECURITY", "NOTIFICATIONS", "MEDIA", "DIAGNOSTICS", "ABOUT").map { label ->
                composeTestRule.onNodeWithText(label).fetchSemanticsNode().boundsInRoot.top
            }
        assertEquals(tops.sorted(), tops)
    }

    @Test
    fun settings_security_section_offers_the_app_lock_switch() {
        val events = mutableListOf<EntryUiEvent>()
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(
                    state = connectedListState(isSettingsOpen = true).withAppLock(),
                    onEvent = events::add,
                )
            }
        }

        composeTestRule.onNodeWithText("App lock").assertIsDisplayed()
        composeTestRule
            .onNodeWithText("Unlocks Mercurio with your device screen lock.")
            .assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("App lock").assertIsOff().performClick()

        assertEquals(listOf<EntryUiEvent>(EntryUiEvent.AppLockToggleClicked), events)
    }

    @Test
    fun settings_security_switch_is_on_when_the_gate_is_enabled() {
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(
                    state = connectedListState(isSettingsOpen = true).withAppLock(isEnabled = true),
                    onEvent = {},
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("App lock").assertIsOn()
    }

    @Test
    fun settings_security_row_names_the_precondition_and_stays_off_without_a_screen_lock() {
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(
                    state = connectedListState(isSettingsOpen = true).withAppLock(isAvailable = false),
                    onEvent = {},
                )
            }
        }

        composeTestRule
            .onNodeWithText("Set a screen lock on this device to use app lock.")
            .assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("App lock").assertIsNotEnabled().assertIsOff()
    }

    @Test
    fun settings_media_switch_defaults_on_and_routes_the_toggle_event() {
        val events = mutableListOf<EntryUiEvent>()
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(state = connectedListState(isSettingsOpen = true), onEvent = events::add)
            }
        }

        composeTestRule
            .onNodeWithContentDescription("Automatic picture loading")
            .performScrollTo()
            .assertIsOn()
            .assertHasClickAction()
            .performClick()

        assertEquals(listOf<EntryUiEvent>(EntryUiEvent.AutoLoadImagesToggleClicked), events)
    }

    @Test
    fun settings_notification_toggle_routes_the_existing_event() {
        val events = mutableListOf<EntryUiEvent>()
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(state = connectedListState(isSettingsOpen = true), onEvent = events::add)
            }
        }

        composeTestRule
            .onNodeWithContentDescription("Run status notifications")
            .performScrollTo()
            .assertIsOff()
            .performClick()

        assertEquals(listOf<EntryUiEvent>(EntryUiEvent.RunStatusNotificationsToggleClicked), events)
    }

    @Test
    fun settings_surface_shows_the_notification_permission_explanation() {
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(
                    state =
                        connectedListState(isSettingsOpen = true)
                            .copy(
                                runStatusNotifications =
                                    RunStatusNotificationsUiState(
                                        explanation = RunStatusNotificationExplanation.PERMISSION_DENIED,
                                    ),
                            ),
                    onEvent = {},
                )
            }
        }

        composeTestRule
            .onNodeWithText(copy(RunStatusNotificationExplanation.PERMISSION_DENIED.messageRes))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun the_connection_actions_menu_routes_credential_change_and_removal() {
        val events = mutableListOf<EntryUiEvent>()
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(state = connectedListState(isSettingsOpen = true), onEvent = events::add)
            }
        }

        composeTestRule.onNodeWithContentDescription("Connection actions").performClick()
        composeTestRule.onNodeWithText("Change credential").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Connection actions").performClick()
        composeTestRule.onNodeWithText("Remove connection").performClick()

        assertEquals(
            listOf(
                EntryUiEvent.ChangeGatewayCredentialClicked,
                EntryUiEvent.RemoveGatewayConnectionClicked,
            ),
            events,
        )
    }

    @Test
    fun settings_diagnostics_entry_is_disabled_while_a_session_mutation_is_pending() {
        val sessionId = SessionId("session-one")
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(
                    state =
                        connectedListState(isSettingsOpen = true).copy(
                            sessionList =
                                requireNotNull(connectedListState().sessionList).copy(
                                    sessionMutations =
                                        mapOf(
                                            sessionId to
                                                SessionMutationUiState(
                                                    pendingAction = SessionMutationAction.PIN,
                                                ),
                                        ),
                                ),
                        ),
                    onEvent = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Local diagnostics").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun settings_global_actions_route_the_existing_events() {
        val events = mutableListOf<EntryUiEvent>()
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(state = connectedListState(isSettingsOpen = true), onEvent = events::add)
            }
        }

        composeTestRule.onNodeWithText("Local diagnostics").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Clear cached conversations").performScrollTo().performClick()

        assertEquals(
            listOf(
                EntryUiEvent.OpenLocalDiagnosticsClicked,
                EntryUiEvent.ClearHistoryCacheClicked,
            ),
            events,
        )
    }

    @Test
    fun system_back_from_settings_closes_it_and_keeps_the_list_context() {
        val events = mutableListOf<EntryUiEvent>()
        val state = mutableStateOf(connectedListState(searchQuery = "First", clientVersion = "demo-build.1"))
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(
                    state = state.value,
                    onEvent = { event ->
                        events.add(event)
                        if (event == EntryUiEvent.OpenSettingsClicked) {
                            state.value = state.value.copy(isSettingsOpen = true)
                        }
                        if (event == EntryUiEvent.CloseSettingsClicked) {
                            state.value = state.value.copy(isSettingsOpen = false)
                        }
                    },
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Settings").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.runOnUiThread {
            composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeTestRule.waitForIdle()

        assertEquals(
            listOf(EntryUiEvent.OpenSettingsClicked, EntryUiEvent.CloseSettingsClicked),
            events,
        )
        composeTestRule.onNodeWithContentDescription("Search Sessions").assertIsDisplayed()
        composeTestRule.onNodeWithText("First").assertIsDisplayed()
        composeTestRule.onNodeWithText("First Session").assertIsDisplayed()
        composeTestRule.onNodeWithText("Notifications").assertDoesNotExist()
    }

    @Test
    fun system_back_from_settings_closes_it_without_a_remote_mutation() {
        val events = mutableListOf<EntryUiEvent>()
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(state = connectedListState(isSettingsOpen = true), onEvent = events::add)
            }
        }

        composeTestRule.runOnUiThread {
            composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeTestRule.waitForIdle()

        assertEquals(listOf<EntryUiEvent>(EntryUiEvent.CloseSettingsClicked), events)
        assertTrue(events.none { it is EntryUiEvent.SessionNavigation })
        assertTrue(events.none { it is EntryUiEvent.SessionMutation })
    }

    @Test
    fun system_back_cancels_credential_rotation_before_closing_settings() {
        val events = mutableListOf<EntryUiEvent>()
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(
                    state = connectedListState(isSettingsOpen = true).copy(isChangingCredential = true),
                    onEvent = events::add,
                )
            }
        }

        composeTestRule.runOnUiThread {
            composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeTestRule.waitForIdle()

        assertEquals(listOf<EntryUiEvent>(EntryUiEvent.CancelGatewayCredentialChangeClicked), events)
    }

    @Test
    fun settings_clear_cache_control_joins_the_privacy_explanation_column() {
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(state = connectedListState(isSettingsOpen = true), onEvent = {})
            }
        }

        composeTestRule
            .onNodeWithText(
                "Clearing removes cached conversations from this device only. " +
                    "Remote Sessions and Runs are not deleted, " +
                    "and diagnostic files you already exported are not removed.",
            ).performScrollTo()
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText("Clear cached conversations")
            .performScrollTo()
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test
    fun the_clear_cache_control_routes_the_request_event() {
        val events = mutableListOf<EntryUiEvent>()
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(state = connectedListState(isSettingsOpen = true), onEvent = events::add)
            }
        }

        composeTestRule.onNodeWithText("Clear cached conversations").performScrollTo().performClick()

        assertEquals(listOf<EntryUiEvent>(EntryUiEvent.ClearHistoryCacheClicked), events)
    }

    @Test
    fun the_clear_cache_confirmation_distinguishes_cached_conversations_remote_content_and_exports() {
        val events = mutableListOf<EntryUiEvent>()
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(
                    state =
                        connectedListState(isSettingsOpen = true).copy(isClearHistoryCacheConfirmationOpen = true),
                    onEvent = events::add,
                )
            }
        }

        composeTestRule.onNodeWithText("Clear cached conversations?").assertIsDisplayed()
        composeTestRule
            .onNodeWithText(
                "Remove the cached conversations stored on this device. " +
                    "Remote Sessions and Runs are not deleted or changed. " +
                    "Diagnostic files you already exported are not removed.",
            ).assertIsDisplayed()

        composeTestRule.onNodeWithText("Cancel").performClick()
        assertEquals(listOf<EntryUiEvent>(EntryUiEvent.CancelClearHistoryCacheClicked), events)
    }

    @Test
    fun the_clear_cache_confirmation_fires_only_the_confirm_event_from_its_confirm_button() {
        val events = mutableListOf<EntryUiEvent>()
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(
                    state =
                        connectedListState(isSettingsOpen = true).copy(isClearHistoryCacheConfirmationOpen = true),
                    onEvent = events::add,
                )
            }
        }

        composeTestRule.onNodeWithText("Clear").performClick()

        assertEquals(listOf<EntryUiEvent>(EntryUiEvent.ConfirmClearHistoryCacheClicked), events)
    }

    @Test
    fun system_back_cancels_the_clear_cache_confirmation_before_closing_settings() {
        val events = mutableListOf<EntryUiEvent>()
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(
                    state =
                        connectedListState(isSettingsOpen = true).copy(isClearHistoryCacheConfirmationOpen = true),
                    onEvent = events::add,
                )
            }
        }

        composeTestRule.runOnUiThread {
            composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeTestRule.waitForIdle()

        assertEquals(listOf<EntryUiEvent>(EntryUiEvent.CancelClearHistoryCacheClicked), events)
    }

    @Test
    fun system_back_cancels_removal_confirmation_before_closing_settings() {
        val events = mutableListOf<EntryUiEvent>()
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(
                    state = connectedListState(isSettingsOpen = true).copy(isRemoveConfirmationOpen = true),
                    onEvent = events::add,
                )
            }
        }

        composeTestRule.runOnUiThread {
            composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeTestRule.waitForIdle()

        assertEquals(listOf<EntryUiEvent>(EntryUiEvent.CancelRemoveGatewayConnectionClicked), events)
    }

    @Test
    fun settings_error_reporting_row_starts_off_and_states_the_scope() {
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(
                    state = connectedListState(isSettingsOpen = true).copy(isErrorReportingConfigured = true),
                    onEvent = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Share crash and error reports").performScrollTo().assertIsDisplayed()
        composeTestRule
            .onNodeWithText("Crashes, errors, and not-responding reports. Off until you turn it on.")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithContentDescription("Share crash and error reports")
            .performScrollTo()
            .assertIsOff()
    }

    @Test
    fun settings_error_reporting_row_reads_a_granted_answer() {
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(
                    state =
                        connectedListState(isSettingsOpen = true).copy(
                            isErrorReportingConfigured = true,
                            errorReportingConsent = ErrorReportingConsent.Granted,
                        ),
                    onEvent = {},
                )
            }
        }

        composeTestRule
            .onNodeWithText("Crashes, errors, and not-responding reports. On until you turn it off.")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithContentDescription("Share crash and error reports")
            .performScrollTo()
            .assertIsOn()
    }

    @Test
    fun settings_error_reporting_row_is_absent_without_a_reporting_endpoint() {
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(state = connectedListState(isSettingsOpen = true), onEvent = {})
            }
        }

        composeTestRule.onNodeWithText("DIAGNOSTICS").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Share crash and error reports").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("Share crash and error reports").assertDoesNotExist()
    }

    @Test
    fun settings_error_reporting_toggle_routes_the_stored_decision() {
        val events = mutableListOf<EntryUiEvent>()
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(
                    state = connectedListState(isSettingsOpen = true).copy(isErrorReportingConfigured = true),
                    onEvent = events::add,
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription("Share crash and error reports")
            .performScrollTo()
            .performClick()

        assertEquals(
            listOf<EntryUiEvent>(EntryUiEvent.ErrorReportingConsentToggleClicked(granted = true)),
            events,
        )
    }

    @Test
    fun settings_controls_remain_reachable_at_twice_the_font_scale() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                HermesTheme {
                    EntryScreen(state = connectedListState(isSettingsOpen = true), onEvent = {})
                }
            }
        }

        composeTestRule.onNodeWithText("Clear cached conversations").performScrollTo().assertHasClickAction()
        composeTestRule.onNodeWithText("Install source").performScrollTo().assertIsDisplayed()
    }

    private fun connectedListState(
        isSettingsOpen: Boolean = false,
        searchQuery: String = "",
        clientVersion: String = "unknown",
        buildNumber: Long? = null,
        installSource: InstallSource = InstallSource.OTHER_SOURCE,
    ): EntryUiState =
        EntryUiState(
            title = UiText.Resource(R.string.entry_title_connected),
            supportingText = UiText.Resource(R.string.entry_supporting_connected),
            actionLabel = UiText.Resource(R.string.entry_action_connected),
            isConnected = true,
            isSettingsOpen = isSettingsOpen,
            clientVersion = clientVersion,
            buildNumber = buildNumber,
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
                    searchQuery = searchQuery,
                ),
        )

    private fun EntryUiState.withAppLock(
        isEnabled: Boolean = false,
        isAvailable: Boolean = true,
    ): EntryUiState {
        val lock = AppLockUiState(isEnabled = isEnabled, isAvailable = isAvailable)
        return copy(appLock = lock)
    }
}
