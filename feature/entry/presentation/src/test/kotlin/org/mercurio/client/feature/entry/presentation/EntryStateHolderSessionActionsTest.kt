package org.mercurio.client.feature.entry.presentation

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mercurio.client.feature.entry.domain.GatewayErrorCategory
import org.mercurio.client.feature.entry.domain.GatewayException
import org.mercurio.client.feature.entry.domain.GatewayHistoryMessage
import org.mercurio.client.feature.entry.domain.SessionHistory
import org.mercurio.client.feature.entry.domain.SessionId
import org.mercurio.client.feature.entry.domain.SessionPage

class EntryStateHolderSessionActionsTest {
    @Test
    fun starting_creation_from_an_open_conversation_keeps_the_conversation_and_creates() =
        runTest {
            val sessionId = SessionId("session-one")
            val gateway = FakeSessionGateway()
            val created = entrySession("created", title = "Created")
            gateway.enqueueList(SessionPage(listOf(entrySession(sessionId.value, "Listed title")), null))
            gateway.enqueueCreate(created)
            gateway.enqueueList(SessionPage(listOf(entrySession(sessionId.value, "Listed title"), created), null))
            gateway.openedSession = entrySession(sessionId.value, "Authoritative title")
            gateway.openedHistory =
                SessionHistory(
                    sessionId = sessionId,
                    messages = listOf(GatewayHistoryMessage("message-one", "user", "Initial content")),
                )
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
            assertNotNull(requireNotNull(holder.uiState.value.sessionList).openedSession)

            holder.onEvent(EntryUiEvent.CreateSessionClicked)
            awaitState(holder, "the created Session row") { state ->
                state.sessionList?.sessions?.any { session -> session.id == created.id } == true
            }

            val state = requireNotNull(holder.uiState.value.sessionList)
            assertNotNull(state.openedSession)
            assertEquals(1, gateway.createCalls)
            assertTrue(state.sessions.any { it.id == created.id })
            holder.close()
        }

    @Test
    fun refreshing_the_session_list_reloads_list_data_and_keeps_the_open_conversation() =
        runTest {
            val sessionId = SessionId("session-one")
            val gateway = FakeSessionGateway()
            gateway.enqueueList(SessionPage(listOf(entrySession(sessionId.value, "Listed title")), null))
            gateway.openedSession = entrySession(sessionId.value, "Authoritative title")
            gateway.openedHistory =
                SessionHistory(
                    sessionId = sessionId,
                    messages = listOf(GatewayHistoryMessage("message-one", "user", "Initial content")),
                )
            gateway.enqueueList(SessionPage(listOf(entrySession(sessionId.value, "Reloaded title")), null))
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
            awaitState(holder, "open conversation") {
                it.sessionList?.openingSessionId == null && it.sessionList?.openedSession != null
            }

            holder.onEvent(EntryUiEvent.RefreshSessionListClicked)
            awaitState(holder, "reloaded list") {
                it.sessionList?.sessions?.singleOrNull()?.title == "Reloaded title"
            }

            val state = requireNotNull(holder.uiState.value.sessionList)
            assertEquals(sessionId, state.openedSession?.session?.id)
            assertTrue(requireNotNull(state.openedSession).messages.isNotEmpty())
            holder.close()
        }

    @Test
    fun refreshing_open_history_replaces_only_confirmed_gateway_data_and_preserves_content_on_failure() {
        val sessionId = SessionId("session-one")
        val gateway = FakeSessionGateway()
        gateway.enqueueList(SessionPage(listOf(entrySession(sessionId.value, "Listed title")), null))
        gateway.openedSession = entrySession(sessionId.value, "Authoritative title")
        gateway.openedHistory =
            SessionHistory(
                sessionId = sessionId,
                messages = listOf(GatewayHistoryMessage("message-one", "user", "Initial content")),
            )
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
        holder.onEvent(EntryUiEvent.ComposerTextChanged("Draft"))

        gateway.openedHistory =
            SessionHistory(
                sessionId = sessionId,
                messages = listOf(GatewayHistoryMessage("message-two", "assistant", "Confirmed content")),
            )
        holder.onEvent(EntryUiEvent.RefreshSessionsClicked)

        val refreshed = requireNotNull(requireNotNull(holder.uiState.value.sessionList).openedSession)
        assertEquals(listOf("Confirmed content"), refreshed.messages.map { it.content })
        assertEquals("Draft", refreshed.composerText)
        assertFalse(refreshed.isRefreshing)
        assertFalse(refreshed.isStale)
        assertNull(refreshed.errorCategory)

        gateway.openedHistory = null
        holder.onEvent(EntryUiEvent.RefreshSessionsClicked)

        val failed = requireNotNull(requireNotNull(holder.uiState.value.sessionList).openedSession)
        assertEquals(listOf("Confirmed content"), failed.messages.map { it.content })
        assertEquals("Draft", failed.composerText)
        assertFalse(failed.isRefreshing)
        assertTrue(failed.isStale)
        assertEquals(SessionHistoryErrorCategory.GATEWAY_REQUEST_FAILED, failed.errorCategory)
        holder.close()
    }

    @Test
    fun pin_is_rejected_during_history_refresh_and_history_refresh_can_complete() =
        runTest {
            val gateway = ScriptedRefreshGateway()
            val holder = scriptedHolder(gateway, StandardTestDispatcher(testScheduler))

            verifyScripted(holder)
            holder.onEvent(EntryUiEvent.SessionClicked(gateway.sessionId))
            advanceUntilIdle()
            awaitState(holder, "initial history load") {
                it.sessionList?.openedSession?.isRefreshing == false
            }

            holder.onEvent(EntryUiEvent.RefreshSessionsClicked)
            assertTrue(requireNotNull(holder.uiState.value.sessionList).openedSession?.isRefreshing == true)

            holder.onEvent(EntryUiEvent.PinSessionClicked(gateway.sessionId))
            assertEquals(0, gateway.pinCalls)

            advanceUntilIdle()
            assertEquals(0, gateway.pinCalls)
            assertEquals(2, gateway.historyCalls)
            assertEquals(false, requireNotNull(holder.uiState.value.sessionList).openedSession?.isRefreshing)
        }

    @Test
    fun list_refresh_is_rejected_while_a_history_refresh_is_in_flight() =
        runTest {
            val gateway = ScriptedRefreshGateway()
            val holder = scriptedHolder(gateway, StandardTestDispatcher(testScheduler))

            verifyScripted(holder)
            holder.onEvent(EntryUiEvent.SessionClicked(gateway.sessionId))
            advanceUntilIdle()
            awaitState(holder, "initial history load") {
                it.sessionList?.openedSession?.isRefreshing == false
            }

            holder.onEvent(EntryUiEvent.RefreshSessionsClicked)
            assertTrue(requireNotNull(holder.uiState.value.sessionList).openedSession?.isRefreshing == true)

            holder.onEvent(EntryUiEvent.RefreshSessionListClicked)
            assertEquals(1, gateway.listCalls)

            advanceUntilIdle()
            assertEquals(1, gateway.listCalls)
            assertEquals(2, gateway.historyCalls)
        }

    @Test
    fun load_more_is_rejected_while_a_history_refresh_is_in_flight() =
        runTest {
            val gateway = ScriptedRefreshGateway(nextPageOffset = 2)
            val holder = scriptedHolder(gateway, StandardTestDispatcher(testScheduler))

            verifyScripted(holder)
            holder.onEvent(EntryUiEvent.SessionClicked(gateway.sessionId))
            advanceUntilIdle()
            awaitState(holder, "initial history load") {
                it.sessionList?.openedSession?.isRefreshing == false
            }

            holder.onEvent(EntryUiEvent.RefreshSessionsClicked)
            assertTrue(requireNotNull(holder.uiState.value.sessionList).openedSession?.isRefreshing == true)

            holder.onEvent(EntryUiEvent.LoadMoreSessionsClicked)
            assertEquals(1, gateway.listCalls)

            advanceUntilIdle()
            assertEquals(1, gateway.listCalls)
            assertEquals(2, gateway.historyCalls)
        }

    @Test
    fun history_refresh_is_rejected_while_a_list_refresh_is_in_flight() =
        runTest {
            val gateway = ScriptedRefreshGateway()
            val holder = scriptedHolder(gateway, StandardTestDispatcher(testScheduler))

            verifyScripted(holder)
            holder.onEvent(EntryUiEvent.SessionClicked(gateway.sessionId))
            advanceUntilIdle()

            gateway.onList = { call ->
                if (call == 2) {
                    holder.onEvent(EntryUiEvent.RefreshSessionsClicked)
                }
            }
            holder.onEvent(EntryUiEvent.PinSessionClicked(gateway.sessionId))
            advanceUntilIdle()

            assertEquals(1, gateway.pinCalls)
            assertEquals(2, gateway.listCalls)
            assertEquals(1, gateway.historyCalls)
        }

    @Test
    fun successful_reopen_replaces_stale_session_metadata_and_clears_stale_lifecycle_state() {
        val sessionId = SessionId("session-one")
        val gateway = FakeSessionGateway()
        gateway.enqueueList(SessionPage(listOf(entrySession(sessionId.value, "Listed title")), null))
        gateway.enqueueFailure(GatewayException(GatewayErrorCategory.GATEWAY_REQUEST_FAILED))
        gateway.openedSession = entrySession(sessionId.value, "Authoritative title", pinned = true)
        gateway.openedHistory = SessionHistory(sessionId, emptyList<GatewayHistoryMessage>())
        val holder =
            holder(
                repository = FakeGatewayConnectionRepository(),
                verifier = { _, _ ->
                    clientManifestCapabilities()
                },
                gateway = gateway,
            )

        verify(holder)
        holder.onEvent(EntryUiEvent.RefreshSessionsClicked)
        val stale = requireNotNull(holder.uiState.value.sessionList)
        assertTrue(stale.isStale)
        assertFalse(stale.isUnavailable)

        holder.onEvent(EntryUiEvent.SessionClicked(sessionId))

        val reopened = requireNotNull(holder.uiState.value.sessionList)
        assertFalse(reopened.isStale)
        assertFalse(reopened.isUnavailable)
        assertEquals("Authoritative title", reopened.sessions.single().title)
        assertTrue(reopened.sessions.single().pinned)
        assertEquals("Authoritative title", reopened.openedSession?.session?.title)
        holder.close()
    }

    @Test
    fun creation_sends_no_title_and_stays_on_the_list_with_the_pending_row() =
        runTest {
            val gateway = FakeSessionGateway()
            val initial = entrySession("initial", title = "Initial")
            val created = entrySession("created", title = null)
            gateway.enqueueList(SessionPage(listOf(initial), null))
            gateway.enqueueCreate(created)
            gateway.enqueueList(SessionPage(listOf(created, initial), null))
            val holder =
                holder(
                    repository = FakeGatewayConnectionRepository(),
                    verifier = { _, _ ->
                        clientManifestCapabilities()
                    },
                    gateway = gateway,
                )

            verify(holder)
            awaitSessions(holder, "initial")
            holder.onEvent(EntryUiEvent.CreateSessionClicked)
            awaitState(holder, "the created Session row") { state ->
                state.sessionList?.sessions?.any { session -> session.id == created.id } == true
            }

            val state = requireNotNull(holder.uiState.value.sessionList)
            assertEquals(1, gateway.createCalls)
            assertEquals(listOf<String?>(null), gateway.createTitles)
            assertNull(state.openedSession)
            assertNull(state.createSession)
            assertNull(state.creationErrorCategory)
            val row = state.sessions.first { session -> session.id == created.id }
            assertEquals("", row.title)
            assertTrue(row.isPendingTitle)
            assertTrue(row.isPendingTitle)
            holder.close()
        }

    @Test
    fun creation_failure_shows_the_error_and_the_next_click_creates_again() =
        runTest {
            val gateway = FakeSessionGateway()
            val created = entrySession("created", title = "Created")
            gateway.enqueueList(SessionPage(emptyList(), null))
            gateway.enqueueCreateFailure(GatewayException(GatewayErrorCategory.GATEWAY_REQUEST_FAILED))
            gateway.enqueueCreate(created)
            gateway.enqueueList(SessionPage(listOf(created), null))
            val holder =
                holder(
                    repository = FakeGatewayConnectionRepository(),
                    verifier = { _, _ ->
                        clientManifestCapabilities()
                    },
                    gateway = gateway,
                )

            verify(holder)
            holder.onEvent(EntryUiEvent.CreateSessionClicked)
            awaitState(holder, "the creation failure") { state ->
                state.sessionList?.creationErrorCategory != null
            }

            val failed = requireNotNull(holder.uiState.value.sessionList)
            assertEquals(SessionCreationErrorCategory.GATEWAY_REQUEST_FAILED, failed.creationErrorCategory)
            assertNull(failed.createSession)
            assertEquals(1, gateway.createCalls)

            holder.onEvent(EntryUiEvent.CreateSessionClicked)
            awaitState(holder, "the created Session row") { state ->
                state.sessionList?.sessions?.any { session -> session.id == created.id } == true
            }

            val recovered = requireNotNull(holder.uiState.value.sessionList)
            assertEquals(2, gateway.createCalls)
            assertNull(recovered.creationErrorCategory)
            holder.close()
        }

    @Test
    fun refresh_failure_after_creation_keeps_the_created_row_cached() =
        runTest {
            val gateway = FakeSessionGateway()
            val initial = entrySession("initial", title = "Initial")
            val created = entrySession("created", title = "Created")
            gateway.enqueueList(SessionPage(listOf(initial), null))
            gateway.enqueueCreate(created)
            gateway.enqueueFailure(GatewayException(GatewayErrorCategory.GATEWAY_REQUEST_FAILED))
            val holder =
                holder(
                    repository = FakeGatewayConnectionRepository(),
                    verifier = { _, _ ->
                        clientManifestCapabilities()
                    },
                    gateway = gateway,
                )

            verify(holder)
            awaitSessions(holder, "initial")
            holder.onEvent(EntryUiEvent.CreateSessionClicked)
            awaitState(holder, "the cached created Session") { state ->
                state.sessionList?.isStale == true &&
                    state.sessionList.sessions.any { session -> session.id == created.id } == true
            }

            val stale = requireNotNull(holder.uiState.value.sessionList)
            assertEquals(1, gateway.createCalls)
            assertTrue(stale.isUnavailable)
            assertNull(stale.createSession)
            assertEquals(listOf("initial", "created"), stale.sessions.map { it.id.value })

            gateway.enqueueList(SessionPage(listOf(created, initial), null))
            holder.onEvent(EntryUiEvent.RefreshSessionsClicked)
            awaitState(holder, "the recovered Session list") { state ->
                state.sessionList?.isStale == false
            }
            assertEquals(1, gateway.createCalls)
            holder.close()
        }

    @Test
    fun pending_creation_ignores_a_duplicate_create_click() =
        runTest {
            val created = entrySession("created", title = "Created")
            val gateway = ScriptedCreateGateway(entrySession("initial", title = "Initial"), created)
            val holder = scriptedHolder(gateway, StandardTestDispatcher(testScheduler))
            verifyScripted(holder)
            advanceUntilIdle()

            holder.onEvent(EntryUiEvent.CreateSessionClicked)
            val pending = requireNotNull(holder.uiState.value.sessionList)
            assertTrue(pending.createSession?.isSubmitting == true)

            holder.onEvent(EntryUiEvent.CreateSessionClicked)
            assertEquals(0, gateway.createCalls)
            holder.onEvent(EntryUiEvent.CreateSessionClicked)
            assertEquals(0, gateway.createCalls)

            advanceUntilIdle()
            awaitState(holder, "the created Session row") { state ->
                state.sessionList?.sessions?.any { session -> session.id == created.id } == true
            }
            val confirmed = requireNotNull(holder.uiState.value.sessionList)
            assertEquals(1, gateway.createCalls)
            assertNull(confirmed.createSession)
            assertNull(confirmed.openedSession)
            holder.close()
        }

    @Test
    fun creation_from_a_filtered_list_keeps_the_query() =
        runTest {
            val gateway = FakeSessionGateway()
            val notesSession = entrySession("design-notes", title = "Design notes")
            val other = entrySession("other-session", title = "Other session")
            val created = entrySession("created-result", title = "Authoritative result")
            gateway.enqueueList(SessionPage(listOf(notesSession, other), null))
            gateway.enqueueCreate(created)
            gateway.enqueueList(SessionPage(listOf(created, notesSession, other), null))
            val holder =
                holder(
                    repository = FakeGatewayConnectionRepository(),
                    verifier = { _, _ ->
                        clientManifestCapabilities()
                    },
                    gateway = gateway,
                )

            verify(holder)
            awaitSessions(holder, "design-notes", "other-session")
            holder.onEvent(EntryUiEvent.SessionSearchQueryChanged("notes"))
            assertEquals(
                listOf("design-notes"),
                requireNotNull(holder.uiState.value.sessionList).visibleSessions.map { it.id.value },
            )

            holder.onEvent(EntryUiEvent.CreateSessionClicked)
            awaitState(holder, "the created Session row") { state ->
                state.sessionList?.sessions?.any { session -> session.id == created.id } == true
            }

            val confirmed = requireNotNull(holder.uiState.value.sessionList)
            assertEquals(1, gateway.createCalls)
            assertNull(confirmed.openedSession)
            assertEquals("notes", confirmed.searchQuery)
            assertTrue(confirmed.sessions.any { session -> session.id == created.id })
            holder.close()
        }
}
