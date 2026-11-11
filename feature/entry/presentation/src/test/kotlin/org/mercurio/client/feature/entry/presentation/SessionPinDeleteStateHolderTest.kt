package org.mercurio.client.feature.entry.presentation

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mercurio.client.feature.entry.application.EntryState
import org.mercurio.client.feature.entry.application.VerifyGatewayConnection
import org.mercurio.client.feature.entry.domain.GatewayCapabilities
import org.mercurio.client.feature.entry.domain.GatewayConnection
import org.mercurio.client.feature.entry.domain.GatewayConnectionRepository
import org.mercurio.client.feature.entry.domain.GatewayErrorCategory
import org.mercurio.client.feature.entry.domain.GatewayException
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class SessionPinDeleteStateHolderTest {
    @Test
    fun pin_and_unpin_apply_only_confirmed_results_and_preserve_server_recency_within_groups() {
        val pinned = session("pinned", "Pinned", pinned = true)
        val unpinnedFirst = session("first", "First")
        val unpinnedSecond = session("second", "Second")
        val gateway =
            FakeSessionGateway(listOf(unpinnedFirst, pinned, unpinnedSecond)).apply {
                enqueuePinResult(SessionPinResult(unpinnedFirst.id, pinned = true))
                enqueuePinResult(SessionPinResult(pinned.id, pinned = false))
            }
        val holder = connectedHolder(gateway)

        assertEquals(listOf("pinned", "first", "second"), sessionIds(holder))
        holder.onEvent(EntryUiEvent.PinSessionClicked(unpinnedFirst.id))
        assertEquals(listOf("first", "pinned", "second"), sessionIds(holder))
        assertEquals(listOf(true, true, false), pinnedValues(holder))

        holder.onEvent(EntryUiEvent.UnpinSessionClicked(pinned.id))
        assertEquals(listOf("first", "pinned", "second"), sessionIds(holder))
        assertEquals(listOf(true, false, false), pinnedValues(holder))
        assertEquals(listOf(unpinnedFirst.id, pinned.id), gateway.pinOperations)
        holder.close()
    }

    @Test
    fun pin_failure_keeps_the_session_unchanged_and_explicit_retry_applies_the_confirmed_result() {
        val target = session("target", "Original")
        val gateway =
            FakeSessionGateway(listOf(target)).apply {
                enqueuePinFailure(
                    GatewayException(GatewayErrorCategory.GATEWAY_REQUEST_FAILED),
                )
                enqueuePinResult(SessionPinResult(target.id, pinned = true))
            }
        val holder = connectedHolder(gateway)

        holder.onEvent(EntryUiEvent.PinSessionClicked(target.id))
        val failed = requireNotNull(holder.uiState.value.sessionList)
        assertFalse(failed.sessions.single().pinned)
        assertEquals(
            SessionMutationErrorCategory.GATEWAY_REQUEST_FAILED,
            failed.sessionMutations[target.id]?.errorCategory,
        )
        assertEquals(SessionMutationAction.PIN, failed.sessionMutations[target.id]?.retryAction)
        assertFalse(failed.sessionMutations[target.id]?.pendingAction != null)

        holder.onEvent(EntryUiEvent.PinSessionClicked(target.id))
        assertTrue(requireNotNull(holder.uiState.value.sessionList).sessions.single().pinned)
        assertNull(requireNotNull(holder.uiState.value.sessionList).sessionMutations[target.id])
        assertEquals(2, gateway.pinCalls)
        holder.close()
    }

    @Test
    fun reopening_a_session_clears_a_failed_pin_mutation_and_uses_authoritative_metadata() =
        runTest {
            val target = session("target", "Original")
            val gateway =
                FakeSessionGateway(listOf(target)).apply {
                    enqueuePinFailure(
                        GatewayException(GatewayErrorCategory.GATEWAY_REQUEST_FAILED),
                    )
                }
            val holder = connectedHolder(gateway)

            holder.onEvent(EntryUiEvent.PinSessionClicked(target.id))
            assertEquals(SessionMutationAction.PIN, mutation(holder, target.id)?.retryAction)

            holder.onEvent(EntryUiEvent.SessionClicked(target.id))

            val reopened = requireNotNull(holder.uiState.value.sessionList)
            assertNull(reopened.sessionMutations[target.id])
            assertEquals("Original", reopened.sessions.single().title)
            assertEquals("Original", reopened.openedSession?.session?.title)
            holder.close()
        }

    @Test
    fun reopen_failure_clears_a_failed_pin_mutation_and_marks_the_session_recoverable() =
        runTest {
            val target = session("target", "Original")
            val gateway =
                FakeSessionGateway(listOf(target)).apply {
                    enqueuePinFailure(
                        GatewayException(GatewayErrorCategory.GATEWAY_REQUEST_FAILED),
                    )
                    openFailure = GatewayException(GatewayErrorCategory.GATEWAY_REQUEST_FAILED)
                }
            val holder = connectedHolder(gateway)

            holder.onEvent(EntryUiEvent.PinSessionClicked(target.id))
            assertEquals(SessionMutationAction.PIN, mutation(holder, target.id)?.retryAction)

            holder.onEvent(EntryUiEvent.SessionClicked(target.id))

            val unavailable = requireNotNull(holder.uiState.value.sessionList)
            assertTrue(unavailable.isUnavailable)
            assertNull(unavailable.sessionMutations[target.id])
            holder.close()
        }

    @Test
    fun duplicate_pin_submissions_are_blocked_while_the_gateway_call_is_pending() =
        runTest {
            val target = session("target", "Original")
            val pinStarted = CountDownLatch(1)
            val pinRelease = CountDownLatch(1)
            val gateway =
                ScriptedPinGateway(target).apply {
                    onPin = {
                        pinStarted.countDown()
                        assertTrue(pinRelease.await(ASYNC_TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))
                    }
                }
            val holder = connectedHolder(gateway, Dispatchers.Default)
            awaitState(holder) { it.sessionList?.sessions?.isNotEmpty() == true }

            try {
                holder.onEvent(EntryUiEvent.PinSessionClicked(target.id))
                assertTrue(pinStarted.await(ASYNC_TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))
                assertEquals(SessionMutationAction.PIN, mutation(holder, target.id)?.pendingAction)

                holder.onEvent(EntryUiEvent.PinSessionClicked(target.id))
                runBlocking { holder.awaitStateChangesApplied() }
                assertEquals(1, gateway.pinCalls)
                assertFalse(requireNotNull(holder.uiState.value.sessionList).sessions.single().pinned)

                pinRelease.countDown()
                gateway.onPin = null
                awaitState(holder) { it.sessionList?.sessions?.single()?.pinned == true }
                assertEquals(1, gateway.pinCalls)
                assertNull(mutation(holder, target.id))
            } finally {
                gateway.onPin = null
                pinRelease.countDown()
                holder.close()
            }
        }

    @Test
    fun pending_mutation_blocks_opening_and_loading_more_sessions() =
        runTest {
            val target = session("target", "Original")
            val gateway = ScriptedPinGateway(target)
            val holder = connectedHolder(gateway, StandardTestDispatcher(testScheduler))
            awaitState(holder) { it.sessionList?.sessions?.isNotEmpty() == true }
            advanceUntilIdle()

            holder.onEvent(EntryUiEvent.PinSessionClicked(target.id))

            holder.onEvent(EntryUiEvent.LoadMoreSessionsClicked)
            holder.onEvent(EntryUiEvent.SessionClicked(target.id))
            advanceUntilIdle()

            assertEquals(0, gateway.loadMoreCalls)
            assertEquals(0, gateway.openCalls)
            assertFalse(requireNotNull(holder.uiState.value.sessionList).isLoadingMore)
            assertEquals(1, gateway.pinCalls)
            holder.close()
        }

    @Test
    fun delete_requires_confirmation_names_the_session_and_removes_only_after_gateway_success() {
        val target = session("target", "Delete me")
        val gateway = FakeSessionGateway(listOf(target))
        val holder = connectedHolder(gateway)

        holder.onEvent(EntryUiEvent.DeleteSessionClicked(target.id))
        val confirmation = requireNotNull(holder.uiState.value.sessionList)
        assertEquals(0, gateway.deleteCalls)
        assertTrue(confirmation.sessionMutations[target.id]?.delete != null)

        holder.onEvent(EntryUiEvent.ConfirmDeleteSessionClicked(target.id))

        assertEquals(1, gateway.deleteCalls)
        assertTrue(requireNotNull(holder.uiState.value.sessionList).sessions.isEmpty())
        holder.close()
    }

    @Test
    fun delete_failure_keeps_the_session_and_confirmation_for_explicit_retry() {
        val target = session("target", "Delete me")
        val gateway =
            FakeSessionGateway(listOf(target)).apply {
                enqueueDeleteFailure(
                    GatewayException(GatewayErrorCategory.GATEWAY_REQUEST_FAILED),
                )
                enqueueDeleteSuccess()
            }
        val holder = connectedHolder(gateway)

        holder.onEvent(EntryUiEvent.DeleteSessionClicked(target.id))
        holder.onEvent(EntryUiEvent.ConfirmDeleteSessionClicked(target.id))

        val failed = requireNotNull(holder.uiState.value.sessionList)
        assertEquals(listOf("target"), failed.sessions.map { it.id.value })
        assertEquals(
            SessionMutationErrorCategory.GATEWAY_REQUEST_FAILED,
            failed.sessionMutations[target.id]?.errorCategory,
        )
        assertEquals(SessionMutationAction.DELETE, failed.sessionMutations[target.id]?.retryAction)

        holder.onEvent(EntryUiEvent.ConfirmDeleteSessionClicked(target.id))
        assertTrue(requireNotNull(holder.uiState.value.sessionList).sessions.isEmpty())
        assertEquals(2, gateway.deleteCalls)
        holder.close()
    }

    @Test
    fun cancelling_a_failed_delete_clears_the_error_and_retry_state() =
        runTest {
            val target = session("target", "Delete me")
            val gateway =
                FakeSessionGateway(listOf(target)).apply {
                    enqueueDeleteFailure(
                        GatewayException(GatewayErrorCategory.GATEWAY_REQUEST_FAILED),
                    )
                }
            val holder = connectedHolder(gateway)

            holder.onEvent(EntryUiEvent.DeleteSessionClicked(target.id))
            holder.onEvent(EntryUiEvent.ConfirmDeleteSessionClicked(target.id))
            assertEquals(SessionMutationAction.DELETE, mutation(holder, target.id)?.retryAction)

            holder.onEvent(EntryUiEvent.CancelDeleteSessionClicked(target.id))

            val state = requireNotNull(holder.uiState.value.sessionList)
            assertNull(state.sessionMutations[target.id])
            assertEquals(listOf("target"), state.sessions.map { it.id.value })
            holder.close()
        }

    private fun connectedHolder(
        gateway: SessionGatewayPort,
        dispatcher: CoroutineDispatcher = Dispatchers.Unconfined,
    ): EntryStateHolder {
        val holder =
            EntryStateHolder(
                initialState = EntryState(isGatewayConnectionConfigured = false),
                verifyGatewayConnection =
                    VerifyGatewayConnection(FakeGatewayConnectionRepository()) { _, _ ->
                        GatewayCapabilities(PublicBetaGatewayCapabilityManifest.current.requiredEndpoints)
                    },
                scope = CoroutineScope(SupervisorJob() + dispatcher),
                sessionGatewayFactory = { _, _ -> gateway },
            )
        holder.onEvent(EntryUiEvent.AddGatewayConnectionClicked)
        holder.onEvent(EntryUiEvent.EndpointChanged("https://gateway.example/profile"))
        holder.onEvent(EntryUiEvent.BearerCredentialChanged("memory-only-token"))
        holder.onEvent(EntryUiEvent.VerifyGatewayConnectionClicked)
        return holder
    }

    private fun sessionIds(holder: EntryStateHolder): List<String> =
        requireNotNull(holder.uiState.value.sessionList).sessions.map { it.id.value }

    private fun pinnedValues(holder: EntryStateHolder): List<Boolean> =
        requireNotNull(holder.uiState.value.sessionList).sessions.map { it.pinned }

    private fun mutation(
        holder: EntryStateHolder,
        sessionId: SessionId,
    ): SessionMutationUiState? = requireNotNull(holder.uiState.value.sessionList).sessionMutations[sessionId]

    private suspend fun awaitState(
        holder: EntryStateHolder,
        predicate: (EntryUiState) -> Boolean,
    ) = holder.awaitUiState("the Session mutation state", predicate)

    private fun session(
        id: String,
        title: String,
        pinned: Boolean = false,
    ): Session =
        Session(
            id = SessionId(id),
            title = title,
            preview = "Preview",
            pinned = pinned,
        )

    private class FakeGatewayConnectionRepository : GatewayConnectionRepository {
        override fun load(): GatewayConnection? = null

        override fun save(connection: GatewayConnection) = Unit
    }

    private class FakeSessionGateway(
        sessions: List<Session>,
    ) : SessionGatewayPort {
        private val listedSessions = sessions.toMutableList()
        private val pinResults = ArrayDeque<Result<SessionPinResult>>()
        private val deleteResults = ArrayDeque<Result<Unit>>()
        val pinOperations = mutableListOf<SessionId>()
        var pinCalls = 0
        var deleteCalls = 0
        var openFailure: GatewayException? = null

        fun enqueuePinResult(result: SessionPinResult) {
            pinResults += Result.success(result)
        }

        fun enqueuePinFailure(error: GatewayException) {
            pinResults += Result.failure(error)
        }

        fun enqueueDeleteFailure(error: GatewayException) {
            deleteResults += Result.failure(error)
        }

        fun enqueueDeleteSuccess() {
            deleteResults += Result.success(Unit)
        }

        override fun listSessions(request: SessionListRequest): SessionPage = SessionPage(listedSessions.toList(), null)

        override fun createSession(title: String?): Session = error("not used")

        override fun openSession(sessionId: SessionId): Session {
            openFailure?.let { throw it }
            return listedSessions.first { it.id == sessionId }
        }

        override fun loadSessionHistory(sessionId: SessionId): SessionHistory =
            SessionHistory(sessionId, emptyList<GatewayHistoryMessage>())

        override fun renameSession(
            sessionId: SessionId,
            title: String,
        ): Session = error("not used")

        override fun deleteSession(sessionId: SessionId) {
            deleteCalls += 1
            if (deleteResults.isEmpty()) return
            deleteResults.removeFirst().getOrThrow()
        }

        override fun pinSession(sessionId: SessionId): SessionPinResult {
            pinCalls += 1
            pinOperations += sessionId
            return applyPinResult(sessionId, pinResults.removeFirst().getOrThrow())
        }

        override fun unpinSession(sessionId: SessionId): SessionPinResult {
            pinCalls += 1
            pinOperations += sessionId
            return applyPinResult(sessionId, pinResults.removeFirst().getOrThrow())
        }

        private fun applyPinResult(
            sessionId: SessionId,
            result: SessionPinResult,
        ): SessionPinResult {
            val index = listedSessions.indexOfFirst { it.id == sessionId }
            if (index >= 0) {
                listedSessions[index] = listedSessions[index].copy(pinned = result.pinned)
            }
            return result
        }
    }

    private class ScriptedPinGateway(
        listedSession: Session,
    ) : SessionGatewayPort {
        private var currentSession = listedSession
        var pinCalls = 0
            private set
        var loadMoreCalls = 0
            private set
        var openCalls = 0
            private set
        var onPin: (() -> Unit)? = null

        override fun listSessions(request: SessionListRequest): SessionPage {
            if (request.offset > 0) {
                loadMoreCalls += 1
            }
            return SessionPage(listOf(currentSession), if (request.offset == 0) 1 else null)
        }

        override fun createSession(title: String?): Session = error("not used")

        override fun openSession(sessionId: SessionId): Session {
            openCalls += 1
            error("not used")
        }

        override fun loadSessionHistory(sessionId: SessionId): SessionHistory =
            SessionHistory(sessionId, emptyList<GatewayHistoryMessage>())

        override fun renameSession(
            sessionId: SessionId,
            title: String,
        ): Session = error("not used")

        override fun deleteSession(sessionId: SessionId) = error("not used")

        override fun pinSession(sessionId: SessionId): SessionPinResult {
            pinCalls += 1
            onPin?.invoke()
            currentSession = currentSession.copy(pinned = true)
            return SessionPinResult(sessionId, pinned = true)
        }

        override fun unpinSession(sessionId: SessionId): SessionPinResult = error("not used")
    }

    private companion object {
        const val ASYNC_TEST_TIMEOUT_MILLIS = 15_000L
    }
}
