package org.mercurio.client.feature.entry.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mercurio.client.feature.entry.application.EntryState
import org.mercurio.client.feature.entry.domain.SessionPage

class EntryStateHolderSettingsTest {
    @Test
    fun opening_settings_keeps_the_loaded_session_list_and_query() {
        val gateway = FakeSessionGateway()
        gateway.enqueueList(SessionPage(listOf(entrySession("session-one", "Listed title")), null))
        val holder =
            holder(
                repository = FakeGatewayConnectionRepository(),
                verifier = { _, _ ->
                    clientManifestCapabilities()
                },
                gateway = gateway,
            )

        try {
            verify(holder)
            awaitSessions(holder, "session-one")
            holder.onEvent(EntryUiEvent.SessionSearchQueryChanged("Listed"))

            holder.onEvent(EntryUiEvent.OpenSettingsClicked)

            val state = holder.uiState.value
            assertTrue(state.isSettingsOpen)
            val list = requireNotNull(state.sessionList)
            assertEquals("Listed", list.searchQuery)
            assertEquals("Listed title", list.visibleSessions.single().title)
        } finally {
            holder.close()
        }
    }

    @Test
    fun closing_settings_restores_the_session_list_without_a_new_request() {
        val gateway = FakeSessionGateway()
        gateway.enqueueList(SessionPage(listOf(entrySession("session-one", "Listed title")), null))
        val holder =
            holder(
                repository = FakeGatewayConnectionRepository(),
                verifier = { _, _ ->
                    clientManifestCapabilities()
                },
                gateway = gateway,
            )

        try {
            verify(holder)
            awaitSessions(holder, "session-one")
            holder.onEvent(EntryUiEvent.SessionSearchQueryChanged("Listed"))
            val requestsAfterLoad = gateway.listRequests.size

            holder.onEvent(EntryUiEvent.OpenSettingsClicked)
            holder.onEvent(EntryUiEvent.CloseSettingsClicked)

            val state = holder.uiState.value
            assertFalse(state.isSettingsOpen)
            val list = requireNotNull(state.sessionList)
            assertEquals("Listed", list.searchQuery)
            assertEquals("Listed title", list.visibleSessions.single().title)
            assertEquals(requestsAfterLoad, gateway.listRequests.size)
        } finally {
            holder.close()
        }
    }

    @Test
    fun opening_settings_requires_a_connected_session_surface() {
        val holder =
            EntryStateHolder(
                initialState = EntryState(isGatewayConnectionConfigured = false),
            )

        try {
            holder.onEvent(EntryUiEvent.OpenSettingsClicked)

            assertFalse(holder.uiState.value.isSettingsOpen)
        } finally {
            holder.close()
        }
    }

    @Test
    fun opening_settings_is_rejected_while_creation_is_open() {
        val gateway = FakeSessionGateway()
        gateway.enqueueList(SessionPage(listOf(entrySession("session-one", "Listed title")), null))
        val holder =
            holder(
                repository = FakeGatewayConnectionRepository(),
                verifier = { _, _ ->
                    clientManifestCapabilities()
                },
                gateway = gateway,
            )

        try {
            verify(holder)
            awaitSessions(holder, "session-one")
            holder.onEvent(EntryUiEvent.CreateSessionClicked)
            val withDialog = requireNotNull(holder.uiState.value.sessionList)
            assertTrue(withDialog.createSession != null)

            holder.onEvent(EntryUiEvent.OpenSettingsClicked)

            assertFalse(holder.uiState.value.isSettingsOpen)
        } finally {
            holder.close()
        }
    }
}
