package org.mercurio.client.feature.entry.presentation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mercurio.client.feature.entry.application.EntryState
import org.mercurio.client.feature.entry.application.VerifyGatewayConnection
import org.mercurio.client.feature.entry.domain.GatewayCapabilities
import org.mercurio.client.feature.entry.domain.GatewayConnection
import org.mercurio.client.feature.entry.domain.GatewayConnectionRepository
import org.mercurio.client.feature.entry.domain.GatewayHistoryMessage
import org.mercurio.client.feature.entry.domain.PublicBetaGatewayCapabilityManifest
import org.mercurio.client.feature.entry.domain.Session
import org.mercurio.client.feature.entry.domain.SessionGatewayPort
import org.mercurio.client.feature.entry.domain.SessionHistory
import org.mercurio.client.feature.entry.domain.SessionId
import org.mercurio.client.feature.entry.domain.SessionListRequest
import org.mercurio.client.feature.entry.domain.SessionPage
import org.mercurio.client.feature.entry.domain.SessionPinResult
import java.util.ArrayDeque

class SessionRenameStateHolderTest {
    private companion object {
        const val TEST_TIMEOUT_MILLIS = 15_000L
    }

    @Test
    fun rename_is_a_confirmed_gateway_mutation_and_applies_the_authoritative_session() {
        val sessionId = SessionId("session-one")
        val listed = session(sessionId, title = "Listed title", preview = "Listed preview")
        val confirmed = session(sessionId, title = "Gateway title", preview = "Gateway preview", pinned = true)
        val gateway = FakeSessionGateway(listed, confirmed)
        val holder = connectedHolder(gateway)

        assertEquals("Listed title", requireNotNull(holder.uiState.value.sessionList).sessions.single().title)
        holder.onEvent(EntryUiEvent.RenameSessionClicked(sessionId))
        holder.onEvent(EntryUiEvent.RenameSessionTitleChanged(sessionId, "  New title  "))

        assertEquals(0, gateway.renameCalls)
        assertEquals(
            "  New title  ",
            requireNotNull(
                requireNotNull(holder.uiState.value.sessionList).sessionMutations[sessionId],
            ).rename?.titleDraft,
        )
        assertEquals(
            "Listed title",
            requireNotNull(holder.uiState.value.sessionList).sessions.single().title,
        )

        holder.onEvent(EntryUiEvent.ConfirmRenameSessionClicked(sessionId))

        assertEquals(listOf("New title"), gateway.renameTitles)
        val state = requireNotNull(holder.uiState.value.sessionList)
        assertEquals("Gateway title", state.sessions.single().title)
        assertEquals("Gateway preview", state.sessions.single().preview)
        assertEquals(true, state.sessions.single().pinned)
        assertNull(state.sessionMutations[sessionId])
        holder.close()
    }

    @Test
    fun rename_rejects_a_title_that_is_empty_after_trimming_without_calling_the_gateway() {
        val sessionId = SessionId("session-one")
        val gateway =
            FakeSessionGateway(
                session(sessionId, "Current title", "Preview"),
                session(sessionId, "Confirmed", "Preview"),
            )
        val holder = connectedHolder(gateway)

        holder.onEvent(EntryUiEvent.RenameSessionClicked(sessionId))
        holder.onEvent(EntryUiEvent.RenameSessionTitleChanged(sessionId, "  \t  "))
        holder.onEvent(EntryUiEvent.ConfirmRenameSessionClicked(sessionId))

        val state = requireNotNull(holder.uiState.value.sessionList)
        assertEquals(SessionRenameErrorCategory.EMPTY_TITLE, state.sessionMutations[sessionId]?.rename?.errorCategory)
        assertEquals(0, gateway.renameCalls)
        assertEquals("Current title", state.sessions.single().title)
        holder.close()
    }

    @Test
    fun rename_rejects_control_characters_without_calling_the_gateway() {
        val sessionId = SessionId("session-one")
        val gateway =
            FakeSessionGateway(
                session(sessionId, "Current title", "Preview"),
                session(sessionId, "Confirmed", "Preview"),
            )
        val holder = connectedHolder(gateway)

        holder.onEvent(EntryUiEvent.RenameSessionClicked(sessionId))
        holder.onEvent(EntryUiEvent.RenameSessionTitleChanged(sessionId, "New\n"))
        holder.onEvent(EntryUiEvent.ConfirmRenameSessionClicked(sessionId))

        val state = requireNotNull(holder.uiState.value.sessionList)
        assertEquals(
            SessionRenameErrorCategory.CONTROL_CHARACTER,
            state.sessionMutations[sessionId]?.rename?.errorCategory,
        )
        assertEquals(0, gateway.renameCalls)
        assertEquals("Current title", state.sessions.single().title)
        holder.close()
    }

    @Test
    fun refresh_preserves_an_unsent_validation_draft_but_clears_its_validation_error() {
        val sessionId = SessionId("session-one")
        val gateway =
            FakeSessionGateway(
                session(sessionId, "Current title", "Preview"),
                session(sessionId, "Confirmed", "Preview"),
            )
        val holder = connectedHolder(gateway)

        holder.onEvent(EntryUiEvent.RenameSessionClicked(sessionId))
        holder.onEvent(EntryUiEvent.RenameSessionTitleChanged(sessionId, "  \t  "))
        holder.onEvent(EntryUiEvent.ConfirmRenameSessionClicked(sessionId))
        assertEquals(
            SessionRenameErrorCategory.EMPTY_TITLE,
            requireNotNull(holder.uiState.value.sessionList).sessionMutations[sessionId]?.rename?.errorCategory,
        )

        holder.onEvent(EntryUiEvent.RefreshSessionsClicked)

        val refreshed = requireNotNull(holder.uiState.value.sessionList)
        assertEquals("  \t  ", refreshed.sessionMutations[sessionId]?.rename?.titleDraft)
        assertNull(refreshed.sessionMutations[sessionId]?.rename?.errorCategory)
        holder.close()
    }

    @Test
    fun late_rename_completion_cannot_mutate_a_reconnected_gateway_and_stays_retryable() =
        runTest {
            val sessionId = SessionId("session-one")
            lateinit var holder: EntryStateHolder
            val oldGateway =
                ScriptedRenameGateway(
                    listed = session(sessionId, "Old gateway title", "Old preview"),
                    confirmed = session(sessionId, "Old remote result", "Old preview"),
                ).apply {
                    onRename = { holder.onEvent(EntryUiEvent.ConfirmRemoveGatewayConnectionClicked) }
                }
            val newGateway =
                FakeSessionGateway(
                    listed = session(sessionId, "New gateway title", "New preview"),
                    confirmed = session(sessionId, "New confirmed title", "New preview"),
                )
            val gateways =
                ArrayDeque<SessionGatewayPort>().apply {
                    add(oldGateway)
                    add(newGateway)
                }
            holder =
                EntryStateHolder(
                    initialState = EntryState(isGatewayConnectionConfigured = false),
                    verifyGatewayConnection =
                        VerifyGatewayConnection(FakeGatewayConnectionRepository()) { _, _ ->
                            GatewayCapabilities(PublicBetaGatewayCapabilityManifest.current.requiredEndpoints)
                        },
                    scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)),
                    sessionGatewayFactory = { _, _ -> gateways.removeFirst() },
                )

            connect(holder)
            advanceUntilIdle()
            awaitState(holder) { it.sessionList?.sessions?.singleOrNull()?.title == "Old gateway title" }
            holder.onEvent(EntryUiEvent.RenameSessionClicked(sessionId))
            holder.onEvent(EntryUiEvent.RenameSessionTitleChanged(sessionId, "Old request"))
            holder.onEvent(EntryUiEvent.ConfirmRenameSessionClicked(sessionId))
            advanceUntilIdle()

            connect(holder)
            advanceUntilIdle()
            awaitState(holder) { it.sessionList?.sessions?.singleOrNull()?.title == "New gateway title" }

            val mutation = holder.uiState.value.sessionList?.sessionMutations?.get(sessionId)
            assertEquals(SessionMutationAction.RENAME, mutation?.retryAction)
            assertNull(mutation?.pendingAction)
            holder.close()
        }

    @Test
    fun edited_restored_rename_draft_survives_refresh_and_reconnect() =
        runTest {
            val sessionId = SessionId("session-one")
            lateinit var holder: EntryStateHolder
            val gateway =
                ScriptedRenameGateway(
                    listed = session(sessionId, "Current title", "Preview"),
                    confirmed = session(sessionId, "Original request", "Preview"),
                ).apply {
                    onRename = { holder.onEvent(EntryUiEvent.ConfirmRemoveGatewayConnectionClicked) }
                }
            holder =
                EntryStateHolder(
                    initialState = EntryState(isGatewayConnectionConfigured = false),
                    verifyGatewayConnection =
                        VerifyGatewayConnection(FakeGatewayConnectionRepository()) { _, _ ->
                            GatewayCapabilities(PublicBetaGatewayCapabilityManifest.current.requiredEndpoints)
                        },
                    scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)),
                    sessionGatewayFactory = { _, _ -> gateway },
                )

            connect(holder)
            advanceUntilIdle()
            awaitState(holder) { it.sessionList?.isLoading == false }
            holder.onEvent(EntryUiEvent.RenameSessionClicked(sessionId))
            holder.onEvent(EntryUiEvent.RenameSessionTitleChanged(sessionId, "Original request"))
            holder.onEvent(EntryUiEvent.ConfirmRenameSessionClicked(sessionId))
            advanceUntilIdle()

            connect(holder)
            advanceUntilIdle()
            awaitState(holder) {
                it.sessionList?.sessionMutations?.get(sessionId)?.retryAction == SessionMutationAction.RENAME
            }
            holder.onEvent(EntryUiEvent.RenameSessionTitleChanged(sessionId, "Edited after reconnect"))
            holder.onEvent(EntryUiEvent.RefreshSessionsClicked)
            advanceUntilIdle()
            awaitState(holder) { it.sessionList?.isRefreshing == false }
            assertEquals(
                "Edited after reconnect",
                holder.uiState.value.sessionList?.sessionMutations?.get(sessionId)?.rename?.titleDraft,
            )

            holder.onEvent(EntryUiEvent.ConfirmRemoveGatewayConnectionClicked)
            connect(holder)
            advanceUntilIdle()
            awaitState(holder) { it.sessionList?.isLoading == false }
            assertEquals(
                "Edited after reconnect",
                holder.uiState.value.sessionList?.sessionMutations?.get(sessionId)?.rename?.titleDraft,
            )
            holder.close()
        }

    private fun connectedHolder(gateway: SessionGatewayPort): EntryStateHolder {
        val holder =
            EntryStateHolder(
                initialState = EntryState(isGatewayConnectionConfigured = false),
                verifyGatewayConnection =
                    VerifyGatewayConnection(FakeGatewayConnectionRepository()) { _, _ ->
                        GatewayCapabilities(PublicBetaGatewayCapabilityManifest.current.requiredEndpoints)
                    },
                scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
                sessionGatewayFactory = { _, _ -> gateway },
            )
        holder.onEvent(EntryUiEvent.AddGatewayConnectionClicked)
        holder.onEvent(EntryUiEvent.EndpointChanged("https://gateway.example/profile"))
        holder.onEvent(EntryUiEvent.BearerCredentialChanged("memory-only-token"))
        holder.onEvent(EntryUiEvent.VerifyGatewayConnectionClicked)
        return holder
    }

    private fun connect(holder: EntryStateHolder) {
        holder.onEvent(EntryUiEvent.AddGatewayConnectionClicked)
        holder.onEvent(EntryUiEvent.EndpointChanged("https://gateway.example/profile"))
        holder.onEvent(EntryUiEvent.BearerCredentialChanged("memory-only-token"))
        holder.onEvent(EntryUiEvent.VerifyGatewayConnectionClicked)
    }

    private suspend fun awaitState(
        holder: EntryStateHolder,
        predicate: (EntryUiState) -> Boolean,
    ) = holder.awaitUiState("the Session rename state", predicate)

    private fun session(
        id: SessionId,
        title: String,
        preview: String,
        pinned: Boolean = false,
    ): Session =
        Session(
            id = id,
            title = title,
            preview = preview,
            pinned = pinned,
        )

    private class FakeGatewayConnectionRepository : GatewayConnectionRepository {
        override fun load(): GatewayConnection? = null

        override fun save(connection: GatewayConnection) = Unit
    }

    private class ScriptedRenameGateway(
        private val listed: Session,
        private val confirmed: Session,
    ) : SessionGatewayPort {
        var onRename: (() -> Unit)? = null

        override fun listSessions(request: SessionListRequest): SessionPage = SessionPage(listOf(listed), null)

        override fun createSession(title: String?): Session = error("not used")

        override fun openSession(sessionId: SessionId): Session = error("not used")

        override fun loadSessionHistory(sessionId: SessionId): SessionHistory =
            SessionHistory(sessionId, emptyList<GatewayHistoryMessage>())

        override fun renameSession(
            sessionId: SessionId,
            title: String,
        ): Session {
            onRename?.invoke()
            return confirmed
        }

        override fun deleteSession(sessionId: SessionId) = error("not used")

        override fun pinSession(sessionId: SessionId): SessionPinResult = error("not used")

        override fun unpinSession(sessionId: SessionId): SessionPinResult = error("not used")
    }

    private class FakeSessionGateway(
        private val listed: Session,
        private val confirmed: Session,
    ) : SessionGatewayPort {
        var renameCalls = 0
        val renameTitles = mutableListOf<String>()

        override fun listSessions(request: SessionListRequest): SessionPage = SessionPage(listOf(listed), null)

        override fun createSession(title: String?): Session = error("not used")

        override fun openSession(sessionId: SessionId): Session = error("not used")

        override fun loadSessionHistory(sessionId: SessionId): SessionHistory =
            SessionHistory(sessionId, emptyList<GatewayHistoryMessage>())

        override fun renameSession(
            sessionId: SessionId,
            title: String,
        ): Session {
            renameCalls += 1
            renameTitles += title
            return confirmed
        }

        override fun deleteSession(sessionId: SessionId) = error("not used")

        override fun pinSession(sessionId: SessionId): SessionPinResult = error("not used")

        override fun unpinSession(sessionId: SessionId): SessionPinResult = error("not used")
    }
}
