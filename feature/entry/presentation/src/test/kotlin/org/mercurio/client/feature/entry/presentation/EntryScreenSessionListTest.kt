package org.mercurio.client.feature.entry.presentation

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mercurio.client.feature.entry.domain.SessionId
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class EntryScreenSessionListTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun session_list_renders_server_metadata_in_pinned_and_all_sessions_groups() {
        val events = mutableListOf<EntryUiEvent>()
        val state =
            connectedState(
                sessions =
                    listOf(
                        item("pinned", "Pinned title", "Pinned preview", pinned = true, time = "1h"),
                        item("untitled", "", null, time = "now", pending = true),
                        item("plain", "Plain title", "Plain preview", time = "2d"),
                    ),
            )

        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(state = state, onEvent = events::add)
            }
        }

        composeTestRule.onNodeWithText("mercurio").assertIsDisplayed()
        composeTestRule.onNodeWithText("PINNED").assertIsDisplayed()
        composeTestRule.onNodeWithText("ALL SESSIONS · 2").assertIsDisplayed()
        composeTestRule.onNodeWithText("Pinned title").assertIsDisplayed()
        composeTestRule.onNodeWithText("1h").assertIsDisplayed()
        composeTestRule.onNodeWithText("Generating title…").assertIsDisplayed()
        composeTestRule.onNodeWithText("Untitled session").assertIsDisplayed()
        composeTestRule.onNodeWithText("new session").assertIsDisplayed()
        composeTestRule.onNodeWithText("3 sessions · connected").assertIsDisplayed()
        composeTestRule.onNodeWithText("gateway :: ready").assertIsDisplayed()

        composeTestRule.onNodeWithText("Plain title").performClick()
        assertEquals(listOf(EntryUiEvent.SessionClicked(SessionId("plain"))), events)
    }

    @Test
    fun settings_entry_point_is_the_bar_control_and_replaces_the_more_options_menu() {
        val events = mutableListOf<EntryUiEvent>()
        setConnectedListScreen(events)

        composeTestRule.onNodeWithContentDescription("More options").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("Settings").assertIsEnabled().performClick()

        assertEquals(listOf<EntryUiEvent>(EntryUiEvent.OpenSettingsClicked), events)
    }

    @Test
    fun search_row_forwards_the_query_and_keeps_the_clear_control() {
        val events = mutableListOf<EntryUiEvent>()
        val state =
            connectedState(
                sessions = listOf(item("existing", "Existing Session", "Previous preview")),
            )
        val current = androidx.compose.runtime.mutableStateOf(state)
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(
                    state = current.value,
                    onEvent = { event ->
                        events += event
                        if (event is EntryUiEvent.SessionSearchQueryChanged) {
                            current.value =
                                current.value.copy(
                                    sessionList = current.value.sessionList?.copy(searchQuery = event.value),
                                )
                        }
                    },
                )
            }
        }

        composeTestRule.onNodeWithText("search sessions").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Search Sessions").performTextInput("needle")
        composeTestRule.onNodeWithText("needle").assertIsDisplayed()
        assertEquals(listOf(EntryUiEvent.SessionSearchQueryChanged("needle")), events)

        events.clear()
        composeTestRule.onNodeWithContentDescription("Clear search").performClick()
        assertEquals(listOf(EntryUiEvent.ClearSessionSearchClicked), events)
    }

    @Test
    fun no_match_state_keeps_the_query_and_uses_the_loaded_scope_copy() {
        val events = mutableListOf<EntryUiEvent>()
        val state =
            connectedState(
                sessions = listOf(item("existing", "Existing Session", "Previous preview")),
                searchQuery = "missing session",
            )
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(state = state, onEvent = events::add)
            }
        }

        composeTestRule.onNodeWithText("missing session").assertIsDisplayed()
        composeTestRule.onNodeWithText("No sessions match").assertIsDisplayed()
        composeTestRule
            .onNodeWithText("Search covers the titles and previews already loaded.")
            .assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Clear search").assertHasClickAction().performClick()
        assertEquals(listOf(EntryUiEvent.ClearSessionSearchClicked), events)
    }

    @Test
    fun empty_session_list_uses_the_approved_empty_copy_and_keeps_the_new_session_row() {
        val events = mutableListOf<EntryUiEvent>()
        val state = connectedState(sessions = emptyList())
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(state = state, onEvent = events::add)
            }
        }

        composeTestRule.onNodeWithText("No sessions").assertIsDisplayed()
        composeTestRule
            .onNodeWithText("The Gateway returned no sessions. Create one to start.")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("0 sessions · connected").assertIsDisplayed()
        composeTestRule.onNodeWithText("new session").assertHasClickAction().performClick()
        assertEquals(listOf(EntryUiEvent.CreateSessionClicked), events)
    }

    @Test
    fun unavailable_empty_list_uses_the_recovery_state_instead_of_the_valid_empty_state() {
        val events = mutableListOf<EntryUiEvent>()
        val state =
            connectedState(
                sessions = emptyList(),
                isStale = true,
                isUnavailable = true,
            )
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(state = state, onEvent = events::add)
            }
        }

        composeTestRule.onNodeWithText("Sessions are unavailable").assertIsDisplayed()
        composeTestRule.onNodeWithText("No sessions").assertDoesNotExist()
        composeTestRule.onNodeWithText("gateway :: offline").assertIsDisplayed()
        composeTestRule.onNodeWithText("Try again").assertHasClickAction().performClick()

        assertEquals(listOf(EntryUiEvent.RefreshSessionListClicked), events)
    }

    @Test
    fun cached_unavailable_list_keeps_the_rows_and_reports_the_offline_gateway() {
        val state =
            connectedState(
                sessions = listOf(item("cached", "Cached Session", "Cached preview")),
                isStale = true,
                isUnavailable = true,
                isCached = true,
            )
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(state = state, onEvent = {})
            }
        }

        composeTestRule.onNodeWithText("Cached Session").assertIsDisplayed()
        composeTestRule.onNodeWithText("1 session · cached").assertIsDisplayed()
        composeTestRule.onNodeWithText("gateway :: offline").assertIsDisplayed()
    }

    @Test
    fun loading_first_read_shows_skeleton_rows_and_the_footer_label() {
        val state = connectedState(sessions = emptyList(), isLoading = true)
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(state = state, onEvent = {})
            }
        }

        composeTestRule.onNodeWithText("loading sessions…").assertIsDisplayed()
        composeTestRule.onNodeWithText("connecting…").assertIsDisplayed()
        composeTestRule.onNodeWithText("gateway :: waiting").assertIsDisplayed()
    }

    @Test
    fun pagination_uses_the_footer_and_has_no_load_more_control() {
        val state =
            connectedState(
                sessions = listOf(item("first", "First Session", null)),
                nextOffset = 20,
                isLoadingMore = true,
            )
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(state = state, onEvent = {})
            }
        }

        composeTestRule.onNodeWithText("Load more Sessions").assertDoesNotExist()
        composeTestRule.onAllNodesWithText("loading more…").assertCountEquals(2)
    }

    @LooperMode(LooperMode.Mode.LEGACY)
    @Test
    fun holding_a_row_opens_the_context_menu_with_pin_rename_and_delete() {
        val events = mutableListOf<EntryUiEvent>()
        val state =
            connectedState(
                sessions = listOf(item("held", "Held Session", "Held preview")),
            )
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(state = state, onEvent = events::add)
            }
        }

        composeTestRule.onNodeWithText("Held Session").performTouchInput { longClick() }

        composeTestRule.onNodeWithText("Pin").assertIsDisplayed()
        composeTestRule.onNodeWithText("Rename").assertIsDisplayed()
        composeTestRule.onNodeWithText("Delete").assertIsDisplayed().performClick()
        assertEquals(listOf(EntryUiEvent.DeleteSessionClicked(SessionId("held"))), events)
    }

    @Test
    fun inline_rename_offers_the_filled_check_and_the_neutral_cross() {
        val events = mutableListOf<EntryUiEvent>()
        val sessionId = SessionId("rename")
        val state =
            connectedState(
                sessions = listOf(item("rename", "Rename me", null)),
                sessionMutations =
                    mapOf(
                        sessionId to SessionMutationUiState(rename = SessionRenameUiState(titleDraft = "Renamed")),
                    ),
            )
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(state = state, onEvent = events::add)
            }
        }

        composeTestRule.onNodeWithText("Renamed").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Session title").assertIsFocused()
        composeTestRule.onNodeWithContentDescription("Confirm rename").performClick()
        composeTestRule.onNodeWithContentDescription("Cancel rename").performClick()

        assertEquals(
            listOf(
                EntryUiEvent.ConfirmRenameSessionClicked(sessionId),
                EntryUiEvent.CancelRenameSessionClicked(sessionId),
            ),
            events,
        )
    }

    @LooperMode(LooperMode.Mode.LEGACY)
    @Test
    fun delete_confirmation_names_the_session_and_keeps_cancel_available() {
        val events = mutableListOf<EntryUiEvent>()
        val sessionId = SessionId("delete")
        val state =
            connectedState(
                sessions = listOf(item("delete", "Delete me", null)),
                sessionMutations = mapOf(sessionId to SessionMutationUiState(delete = SessionDeleteUiState())),
            )
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(state = state, onEvent = events::add)
            }
        }

        composeTestRule.onNodeWithText("Delete “Delete me”?").assertIsDisplayed()
        composeTestRule
            .onNodeWithText("The session and its transcript are removed from this client. This cannot be undone here.")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").assertHasClickAction().performClick()
        assertEquals(listOf(EntryUiEvent.CancelDeleteSessionClicked(sessionId)), events)
    }

    @Test
    fun session_search_and_the_new_session_row_remain_reachable_at_increased_font_scale() {
        val events = mutableListOf<EntryUiEvent>()
        val state = connectedState(sessions = listOf(item("font-scaled", "Font scaled Session", null)))
        composeTestRule.setContent {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides
                    androidx.compose.ui.unit.Density(1f, 2f),
            ) {
                HermesTheme {
                    EntryScreen(state = state, onEvent = events::add)
                }
            }
        }

        composeTestRule.onNodeWithContentDescription("Search Sessions").assertIsDisplayed()
        composeTestRule.onNodeWithText("new session").assertIsEnabled().performClick()
        assertEquals(listOf(EntryUiEvent.CreateSessionClicked), events)
    }

    @Test
    fun the_create_row_is_pinned_outside_the_scroll_owner_in_every_state() {
        val states =
            listOf(
                connectedState(sessions = listOf(item("plain", "Plain title", "Plain preview"))),
                connectedState(sessions = emptyList()),
                connectedState(sessions = emptyList(), isLoading = true),
                connectedState(
                    sessions = listOf(item("cached", "Cached Session", null)),
                    isStale = true,
                    isUnavailable = true,
                    isCached = true,
                ),
                connectedState(
                    sessions = listOf(item("plain", "Plain title", null)),
                    searchQuery = "missing",
                ),
                connectedState(sessions = listOf(item("untitled", "", null, pending = true))),
            )
        val current = androidx.compose.runtime.mutableStateOf(states.first())
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(state = current.value, onEvent = {})
            }
        }

        states.forEach { state ->
            composeTestRule.runOnIdle { current.value = state }
            composeTestRule.onNodeWithTag("new-session-row").assertIsDisplayed()
            composeTestRule
                .onAllNodes(hasTestTag("new-session-row") and hasAnyAncestor(hasTestTag("session-list")))
                .assertCountEquals(0)
        }
    }

    @LooperMode(LooperMode.Mode.LEGACY)
    @Test
    fun the_row_menu_stays_disabled_while_a_list_refresh_is_in_flight() {
        val state =
            connectedState(
                sessions = listOf(item("held", "Held Session", "Held preview")),
                isRefreshing = true,
            )
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(state = state, onEvent = {})
            }
        }

        composeTestRule.onNodeWithText("Held Session").assertIsEnabled()
        composeTestRule.onNodeWithText("Held Session").performTouchInput { longClick() }

        composeTestRule.onNodeWithText("Pin").assertIsNotEnabled()
        composeTestRule.onNodeWithText("Rename").assertIsNotEnabled()
        composeTestRule.onNodeWithText("Delete").assertIsNotEnabled()
    }

    @Test
    fun the_creation_error_banner_renders_once_in_every_empty_state() {
        val error = SessionCreationErrorCategory.GATEWAY_REQUEST_FAILED
        val states =
            listOf(
                connectedState(sessions = emptyList(), creationErrorCategory = error),
                connectedState(
                    sessions = emptyList(),
                    searchQuery = "missing",
                    creationErrorCategory = error,
                ),
                connectedState(
                    sessions = emptyList(),
                    isStale = true,
                    isUnavailable = true,
                    creationErrorCategory = error,
                ),
            )
        val current = androidx.compose.runtime.mutableStateOf(states.first())
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(state = current.value, onEvent = {})
            }
        }

        states.forEach { state ->
            composeTestRule.runOnIdle { current.value = state }
            composeTestRule.onAllNodesWithText(copy(error.messageRes)).assertCountEquals(1)
        }
    }

    private fun setConnectedListScreen(events: MutableList<EntryUiEvent>) {
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(state = connectedState(sessions = emptyList()), onEvent = events::add)
            }
        }
    }

    @Suppress("LongParameterList")
    private fun connectedState(
        sessions: List<SessionItemUiState>,
        searchQuery: String = "",
        isLoading: Boolean = false,
        isRefreshing: Boolean = false,
        isStale: Boolean = false,
        isUnavailable: Boolean = false,
        isCached: Boolean = false,
        isLoadingMore: Boolean = false,
        nextOffset: Int? = null,
        sessionMutations: Map<SessionId, SessionMutationUiState> = emptyMap(),
        creationErrorCategory: SessionCreationErrorCategory? = null,
    ): EntryUiState =
        EntryUiState(
            title = UiText.Resource(R.string.entry_title_connected),
            supportingText = UiText.Resource(R.string.entry_supporting_connected),
            actionLabel = UiText.Resource(R.string.entry_action_connected),
            isConnected = true,
            sessionList =
                SessionListUiState(
                    sessions = sessions,
                    searchQuery = searchQuery,
                    isLoading = isLoading,
                    isRefreshing = isRefreshing,
                    isStale = isStale,
                    isUnavailable = isUnavailable,
                    isCached = isCached,
                    isLoadingMore = isLoadingMore,
                    nextOffset = nextOffset,
                    sessionMutations = sessionMutations,
                    creationErrorCategory = creationErrorCategory,
                ),
        )

    @Suppress("LongParameterList")
    private fun item(
        id: String,
        title: String,
        preview: String?,
        pinned: Boolean = false,
        time: String = "—",
        pending: Boolean = false,
    ): SessionItemUiState =
        SessionItemUiState(
            id = SessionId(id),
            title = title,
            preview = preview,
            pinned = pinned,
            lastActive = relativeLastActive(time),
            isPendingTitle = pending,
        )
}
