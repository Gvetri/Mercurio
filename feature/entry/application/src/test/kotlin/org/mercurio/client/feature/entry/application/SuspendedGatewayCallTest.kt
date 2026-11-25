package org.mercurio.client.feature.entry.application

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.mercurio.client.feature.entry.domain.GatewayErrorCategory
import org.mercurio.client.feature.entry.domain.GatewayException
import org.mercurio.client.feature.entry.domain.GatewayHistoryMessage
import org.mercurio.client.feature.entry.domain.Run
import org.mercurio.client.feature.entry.domain.RunEventObservation
import org.mercurio.client.feature.entry.domain.RunGatewayPort
import org.mercurio.client.feature.entry.domain.RunId
import org.mercurio.client.feature.entry.domain.Session
import org.mercurio.client.feature.entry.domain.SessionGatewayPort
import org.mercurio.client.feature.entry.domain.SessionHistory
import org.mercurio.client.feature.entry.domain.SessionId
import org.mercurio.client.feature.entry.domain.SessionListRequest
import org.mercurio.client.feature.entry.domain.SessionPage
import org.mercurio.client.feature.entry.domain.SessionPinResult

class SuspendedGatewayCallTest {
    @Test
    fun an_opened_session_collected_across_two_suspensions_reaches_the_caller() =
        runBlocking {
            val session = session("session-open")
            val history = SessionHistory(session.id, emptyList())
            val gateway = SuspendingGateway(session = session, history = history)

            val opened = async { OpenSession(gateway).execute(session.id) }
            gateway.releaseCalls(2)

            val result = opened.await()
            assertEquals(session, result.session)
            assertEquals(history, result.history)
        }

    @Test
    fun an_open_failure_that_arrives_after_the_call_suspended_keeps_its_category() =
        runBlocking {
            val gateway = SuspendingGateway(session = session("session-open-failure"))
            gateway.failNext(GatewayException(GatewayErrorCategory.INVALID_RESPONSE))

            val opened =
                async {
                    runCatching { OpenSession(gateway).executeSession(SessionId("session-open-failure")) }
                }
            gateway.releaseCalls(1)

            assertEquals(GatewayErrorCategory.INVALID_RESPONSE, opened.await().failureCategory())
        }

    @Test
    fun a_history_failure_that_arrives_after_the_call_suspended_keeps_its_category() =
        runBlocking {
            val gateway = SuspendingGateway(session = session("session-history-failure"))
            gateway.failNext(null)
            gateway.failNext(GatewayException(GatewayErrorCategory.GATEWAY_REQUEST_FAILED))

            val history =
                async {
                    runCatching { OpenSession(gateway).execute(SessionId("session-history-failure")) }
                }
            gateway.releaseCalls(2)

            assertEquals(GatewayErrorCategory.GATEWAY_REQUEST_FAILED, history.await().failureCategory())
        }

    @Test
    fun an_observed_run_returned_after_a_suspension_reaches_the_caller() =
        runBlocking {
            val observation = NoopObservation()
            val gateway = SuspendingGateway(session = session("session-observe"), observation = observation)
            val runId = RunId("run-observe")

            val observed = async { ObserveRun(gateway).execute(runId) }
            gateway.releaseCalls(1)
            assertSame(observation, observed.await())

            val invoked = async { ObserveRun(gateway).invoke(runId) }
            gateway.releaseCalls(1)
            assertSame(observation, invoked.await())
        }

    @Test
    fun a_deleted_session_that_suspended_completes_at_the_caller() =
        runBlocking {
            val gateway = SuspendingGateway(session = session("session-delete"))

            val deleted = async { DeleteSession(gateway).execute(SessionId("session-delete")) }
            gateway.releaseCalls(1)
            deleted.await()

            assertEquals(listOf("session-delete"), gateway.deleted)
        }

    @Test
    fun a_delete_failure_that_arrives_after_the_call_suspended_keeps_its_category() =
        runBlocking {
            val gateway = SuspendingGateway(session = session("session-delete-failure"))
            gateway.failNext(GatewayException(GatewayErrorCategory.GATEWAY_REQUEST_FAILED))

            val deleted =
                async {
                    runCatching { DeleteSession(gateway).execute(SessionId("session-delete-failure")) }
                }
            gateway.releaseCalls(1)

            assertEquals(GatewayErrorCategory.GATEWAY_REQUEST_FAILED, deleted.await().failureCategory())
        }

    @Test
    fun a_run_reconciled_across_two_suspensions_reaches_the_caller() =
        runBlocking {
            val sessionId = SessionId("session-reconcile")
            val run = Run(RunId("run-reconcile"), sessionId, "succeeded")
            val history = SessionHistory(sessionId, emptyList())
            val gateway = SuspendingGateway(session = session("session-reconcile"), history = history, run = run)

            val reconciliation = async { ReconcileRun(gateway, gateway).execute(run.id, sessionId) }
            gateway.releaseCalls(2)

            val result = reconciliation.await()
            assertEquals(run, result.run)
            assertEquals(history, result.history)
        }

    @Test
    fun a_run_status_failure_that_arrives_after_the_call_suspended_keeps_its_category() =
        runBlocking {
            val sessionId = SessionId("session-status-failure")
            val gateway = SuspendingGateway(session = session("session-status-failure"))
            gateway.failNext(GatewayException(GatewayErrorCategory.INVALID_RESPONSE))

            val reconciliation =
                async {
                    runCatching { ReconcileRun(gateway, gateway).execute(RunId("run-status"), sessionId) }
                }
            gateway.releaseCalls(1)

            assertEquals(GatewayErrorCategory.INVALID_RESPONSE, reconciliation.await().failureCategory())
        }

    @Test
    fun a_reconciliation_history_failure_that_arrives_after_the_call_suspended_keeps_its_category() =
        runBlocking {
            val sessionId = SessionId("session-reconcile-history-failure")
            val gateway = SuspendingGateway(session = session("session-reconcile-history-failure"))
            gateway.failNext(GatewayException(GatewayErrorCategory.GATEWAY_REQUEST_FAILED))

            val reconciliation =
                async {
                    runCatching { ReconcileSession(gateway, gateway).execute(sessionId) }
                }
            gateway.releaseCalls(1)

            assertEquals(GatewayErrorCategory.GATEWAY_REQUEST_FAILED, reconciliation.await().failureCategory())
        }

    @Test
    fun a_session_reconciled_across_a_suspension_reaches_the_caller() =
        runBlocking {
            val sessionId = SessionId("session-discovery")
            val run = Run(RunId("run-discovery"), sessionId, "running")
            val history =
                SessionHistory(
                    sessionId,
                    listOf(GatewayHistoryMessage("message-discovery", "assistant", "Discovered", run.id, "running")),
                )
            val gateway = SuspendingGateway(session = session("session-discovery"), history = history, run = run)

            val reconciliation = async { ReconcileSession(gateway, gateway).execute(sessionId) }
            gateway.releaseCalls(2)

            val result = reconciliation.await()
            assertEquals(history, result.history)
            assertEquals(listOf(run), result.discoveredRuns)
        }

    @Test
    fun a_discovered_run_status_failure_that_arrives_after_the_call_suspended_keeps_its_category() =
        runBlocking {
            val sessionId = SessionId("session-discovery-failure")
            val run = Run(RunId("run-discovery-failure"), sessionId, "running")
            val history =
                SessionHistory(
                    sessionId,
                    listOf(GatewayHistoryMessage("message-failure", "assistant", "Discovered", run.id, "running")),
                )
            val gateway = SuspendingGateway(session = session("session-discovery-failure"), history = history)
            gateway.failNext(null)
            gateway.failNext(GatewayException(GatewayErrorCategory.INVALID_RESPONSE))

            val reconciliation =
                async {
                    runCatching { ReconcileSession(gateway, gateway).execute(sessionId) }
                }
            gateway.releaseCalls(2)

            assertEquals(GatewayErrorCategory.INVALID_RESPONSE, reconciliation.await().failureCategory())
        }

    @Test
    fun a_submitted_message_created_after_a_suspension_reaches_the_caller() =
        runBlocking {
            val sessionId = SessionId("session-submit")
            val run = Run(RunId("run-submit"), sessionId, "started")
            val gateway = SuspendingGateway(session = session("session-submit"), run = run)

            val submitted = async { SubmitMessage(gateway).execute(sessionId, "Run this") }
            gateway.releaseCalls(1)

            assertEquals(run, submitted.await())
        }

    @Test
    fun a_submission_failure_that_arrives_after_the_call_suspended_keeps_its_category() =
        runBlocking {
            val gateway = SuspendingGateway(session = session("session-submit-failure"))
            gateway.failNext(GatewayException(GatewayErrorCategory.MODEL_REFUSED))

            val submitted =
                async {
                    runCatching { SubmitMessage(gateway).execute(SessionId("session-submit-failure"), "Run this") }
                }
            gateway.releaseCalls(1)

            assertEquals(GatewayErrorCategory.MODEL_REFUSED, submitted.await().failureCategory())
        }
}

private fun session(id: String): Session =
    Session(
        id = SessionId(id),
        title = "Session $id",
        preview = "Preview",
        pinned = false,
    )

private fun Result<*>.failureCategory(): GatewayErrorCategory =
    ((exceptionOrNull() as? GatewayException)?.category) ?: error("Expected a GatewayException but got $this")

private class NoopObservation : RunEventObservation {
    override suspend fun next() = null

    override fun close() = Unit
}

private class SuspendingGateway(
    private val session: Session,
    private val history: SessionHistory = SessionHistory(session.id, emptyList()),
    private val run: Run = Run(RunId("run"), session.id, "succeeded"),
    private val observation: RunEventObservation = NoopObservation(),
) : SessionGatewayPort,
    RunGatewayPort {
    private val gates = Channel<CompletableDeferred<Unit>>(Channel.UNLIMITED)
    private val failures = ArrayDeque<Exception?>()
    val deleted = mutableListOf<String>()

    fun failNext(failure: Exception?) {
        failures += failure
    }

    suspend fun releaseCalls(count: Int) {
        repeat(count) {
            gates.receive().complete(Unit)
        }
    }

    override suspend fun listSessions(request: SessionListRequest): SessionPage {
        gate()
        return SessionPage(listOf(session), null)
    }

    override suspend fun createSession(title: String?): Session {
        gate()
        return session
    }

    override suspend fun openSession(sessionId: SessionId): Session {
        gate()
        return session
    }

    override suspend fun loadSessionHistory(sessionId: SessionId): SessionHistory {
        gate()
        return history
    }

    override suspend fun renameSession(
        sessionId: SessionId,
        title: String,
    ): Session {
        gate()
        return session
    }

    override suspend fun deleteSession(sessionId: SessionId) {
        gate()
        deleted += sessionId.value
    }

    override suspend fun pinSession(sessionId: SessionId): SessionPinResult {
        gate()
        return SessionPinResult(sessionId, pinned = true)
    }

    override suspend fun unpinSession(sessionId: SessionId): SessionPinResult {
        gate()
        return SessionPinResult(sessionId, pinned = false)
    }

    override suspend fun createRun(
        sessionId: SessionId,
        input: String,
    ): Run {
        gate()
        return run
    }

    override suspend fun getRunStatus(runId: RunId): Run {
        gate()
        return run
    }

    override suspend fun observeRun(runId: RunId): RunEventObservation {
        gate()
        return observation
    }

    private suspend fun gate() {
        val gate = CompletableDeferred<Unit>()
        gates.send(gate)
        gate.await()
        failures.removeFirstOrNull()?.let { failure -> throw failure }
    }
}
