package org.mercurio.client.feature.entry.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mercurio.client.feature.entry.domain.SessionHistory
import org.mercurio.client.feature.entry.domain.SessionId
import org.mercurio.client.feature.entry.domain.SessionPage

class SessionOpenPhasesTest {
    @Test
    fun session_metadata_is_presented_while_the_history_request_still_runs() {
        val sessionId = SessionId("session-one")
        val gateway = FakeSessionGateway()
        gateway.enqueueList(SessionPage(listOf(entrySession(sessionId.value, "Listed title")), null))
        gateway.openedSession = entrySession(sessionId.value, "Authoritative title")
        gateway.openedHistory = SessionHistory(sessionId, emptyList())
        val holder =
            holder(
                repository = FakeGatewayConnectionRepository(),
                verifier = { _, _ ->
                    clientManifestCapabilities()
                },
                gateway = gateway,
            )

        verify(holder)
        val before = requireNotNull(holder.uiState.value.sessionList).copy(openingSessionId = sessionId)
        val metadata =
            holder.applyOpenedSessionMetadata(
                before,
                entrySession(sessionId.value, "Authoritative title"),
                sessionId,
            )

        val opened = requireNotNull(metadata.openedSession)
        assertTrue(opened.messages.isEmpty())
        assertTrue(opened.isRefreshing)
        assertEquals("Authoritative title", opened.session.title)
        assertEquals(sessionId, metadata.openingSessionId)
        assertEquals("Authoritative title", requireNotNull(metadata.sessions.single()).title)
        assertFalse(metadata.isStale)
        assertNull(metadata.errorCategory)
        holder.close()
    }

    @Test
    fun failed_history_load_closes_the_session_screen_it_never_filled() {
        val sessionId = SessionId("session-one")
        val gateway = FakeSessionGateway()
        gateway.enqueueList(SessionPage(listOf(entrySession(sessionId.value, "Listed title")), null))
        gateway.openedSession = entrySession(sessionId.value, "Authoritative title")
        gateway.openedHistory = null
        val holder =
            holder(
                repository = FakeGatewayConnectionRepository(),
                verifier = { _, _ ->
                    clientManifestCapabilities()
                },
                gateway = gateway,
            )

        verify(holder)
        holder.onEvent(EntryUiEvent.SessionClicked(sessionId))

        val failed = requireNotNull(holder.uiState.value.sessionList)
        assertEquals(listOf("open", "history"), gateway.operations)
        assertNull(failed.openedSession)
        assertNull(failed.openingSessionId)
        assertTrue(failed.isStale)
        assertTrue(failed.isUnavailable)
        assertEquals(SessionListErrorCategory.SESSION_UNAVAILABLE, failed.errorCategory)
        holder.close()
    }
}
