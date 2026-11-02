package org.mercurio.client.feature.entry.presentation

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mercurio.client.feature.entry.application.EntryState
import org.mercurio.client.feature.entry.application.RemoveGatewayConnection
import org.mercurio.client.feature.entry.application.VerifyGatewayConnection
import org.mercurio.client.feature.entry.data.DefaultGatewayConnectionRepository
import org.mercurio.client.feature.entry.data.InMemoryGatewayConnectionDataSource
import org.mercurio.client.feature.entry.domain.GatewayCapabilities
import org.mercurio.client.feature.entry.domain.PendingRunSubmissionKey
import org.mercurio.client.feature.entry.domain.PublicBetaGatewayCapabilityManifest
import org.mercurio.client.feature.entry.domain.Run
import org.mercurio.client.feature.entry.domain.RunEvent
import org.mercurio.client.feature.entry.domain.RunEventObservation
import org.mercurio.client.feature.entry.domain.RunEventType
import org.mercurio.client.feature.entry.domain.RunGatewayPort
import org.mercurio.client.feature.entry.domain.RunId
import org.mercurio.client.feature.entry.domain.RunSubmissionUncertaintyStore
import org.mercurio.client.feature.entry.domain.Session
import org.mercurio.client.feature.entry.domain.SessionGatewayPort
import org.mercurio.client.feature.entry.domain.SessionHistory
import org.mercurio.client.feature.entry.domain.SessionId
import org.mercurio.client.feature.entry.domain.SessionListRequest
import org.mercurio.client.feature.entry.domain.SessionPage
import org.mercurio.client.feature.entry.domain.SessionPinResult
import kotlin.coroutines.CoroutineContext

private const val TEST_TIMEOUT_MILLIS = 5_000L

private const val CONSUMER_LANE = "consumer"

private const val PRODUCER_LANE = "producer"

private class LaneRecorder {
    private val lanes = ThreadLocal.withInitial { ArrayDeque<String>() }

    fun push(lane: String) {
        lanes.get().addLast(lane)
    }

    fun pop() {
        lanes.get().removeLast()
    }

    fun current(): String = lanes.get().lastOrNull() ?: "none"
}

private class ConsumerLaneDispatcher(
    private val recorder: LaneRecorder,
) : CoroutineDispatcher() {
    override fun dispatch(
        context: CoroutineContext,
        block: Runnable,
    ) {
        val lane =
            if (context[StateChangeConsumerMarker] != null) {
                CONSUMER_LANE
            } else {
                PRODUCER_LANE
            }
        recorder.push(lane)
        try {
            block.run()
        } finally {
            recorder.pop()
        }
    }
}

private class LaneRecordingUncertaintyStore(
    private val recorder: LaneRecorder,
) : RunSubmissionUncertaintyStore {
    val lanes = mutableListOf<String>()

    override fun add(
        key: PendingRunSubmissionKey,
        knownRunIds: Set<RunId>,
        attemptId: String?,
    ): Boolean {
        lanes += recorder.current()
        return true
    }

    override fun remove(
        key: PendingRunSubmissionKey,
        attemptId: String?,
    ): Boolean {
        lanes += recorder.current()
        return false
    }

    override fun contains(key: PendingRunSubmissionKey): Boolean {
        lanes += recorder.current()
        return false
    }
}

private class CountingObservation(
    private val events: List<RunEvent>,
    private val gateway: ObservingGateway,
) : RunEventObservation {
    override fun iterator(): Iterator<RunEvent> =
        object : Iterator<RunEvent> {
            val inner = events.iterator()

            override fun hasNext(): Boolean = inner.hasNext()

            override fun next(): RunEvent {
                gateway.nextCalls += 1
                return inner.next()
            }
        }

    override fun close() {}
}

private class ObservingGateway(
    private val session: Session,
    private val run: Run,
    private val recorder: LaneRecorder,
) : SessionGatewayPort, RunGatewayPort {
    val createRunLanes = mutableListOf<String>()
    var observeCalls = 0
    var nextCalls = 0
    var statusCalls = 0

    override fun listSessions(request: SessionListRequest): SessionPage = SessionPage(listOf(session), null)

    override fun createSession(title: String?): Session = error("not used")

    override fun openSession(sessionId: SessionId): Session = session

    override fun loadSessionHistory(sessionId: SessionId): SessionHistory = SessionHistory(sessionId, emptyList())

    override fun renameSession(
        sessionId: SessionId,
        title: String,
    ): Session = error("not used")

    override fun deleteSession(sessionId: SessionId): Unit = error("not used")

    override fun pinSession(sessionId: SessionId): SessionPinResult = error("not used")

    override fun unpinSession(sessionId: SessionId): SessionPinResult = error("not used")

    override fun createRun(
        sessionId: SessionId,
        input: String,
    ): Run {
        createRunLanes += recorder.current()
        return run
    }

    override fun getRunStatus(runId: RunId): Run {
        statusCalls += 1
        return run.copy(status = "succeeded")
    }

    override fun observeRun(runId: RunId): RunEventObservation {
        observeCalls += 1
        return CountingObservation(listOf(RunEvent(RunEventType.SUCCEEDED, run.id, status = "succeeded")), this)
    }
}

private fun reflectionDump(
    holder: EntryStateHolder,
    name: String,
): String =
    runCatching { privateField(holder, name).toString() }.getOrElse { error -> "$name=<unavailable: $error>" }

private fun awaitUiState(
    holder: EntryStateHolder,
    description: String,
    detail: () -> String = { "" },
    predicate: (EntryUiState) -> Boolean,
) {
    try {
        runBlocking {
            withTimeout(TEST_TIMEOUT_MILLIS) {
                holder.uiState.first(predicate)
            }
        }
    } catch (_: TimeoutCancellationException) {
        val internals =
            listOf(
                "runObservationJobs",
                "runObservationRunIds",
                "runObservationStates",
                "pendingRunObservationRequests",
                "sessionRuns",
                "authoritativeSessionRuns",
                "uncertainSubmissionRunIds",
            ).joinToString("; ") { reflectionDump(holder, it) }
        throw AssertionError(
            "Timed out waiting for $description. ${detail()} state=${holder.uiState.value} internals=[$internals]",
        )
    }
}

class EntryStateHolderStateConsumerTest {
    @Test
    fun observation_event_application_runs_on_the_state_change_consumer_not_the_producing_thread() {
        val recorder = LaneRecorder()
        val session =
            Session(
                id = SessionId("session-1"),
                title = "Session session-1",
                preview = "Preview",
                pinned = false,
            )
        val run = Run(RunId("run-1"), session.id, "starting")
        val gateway = ObservingGateway(session, run, recorder)
        val store = LaneRecordingUncertaintyStore(recorder)
        val repository = DefaultGatewayConnectionRepository(InMemoryGatewayConnectionDataSource())
        val holder =
            EntryStateHolder(
                initialState = EntryState(isGatewayConnectionConfigured = false),
                verifyGatewayConnection =
                    VerifyGatewayConnection(repository) { _, _ ->
                        GatewayCapabilities(PublicBetaGatewayCapabilityManifest.current.requiredEndpoints)
                    },
                scope = CoroutineScope(SupervisorJob() + ConsumerLaneDispatcher(recorder)),
                sessionGatewayFactory = { _, _ -> gateway },
                runGatewayFactory = { _, _ -> gateway },
                dependencies =
                    EntryStateHolderDependencies(
                        removeGatewayConnectionUseCase = RemoveGatewayConnection(repository),
                        runSubmissionUncertaintyStore = store,
                    ),
            )

        try {
            holder.onEvent(EntryUiEvent.AddGatewayConnectionClicked)
            holder.onEvent(EntryUiEvent.EndpointChanged("https://gateway.example/profile"))
            holder.onEvent(EntryUiEvent.BearerCredentialChanged("memory-only-token"))
            holder.onEvent(EntryUiEvent.VerifyGatewayConnectionClicked)
            awaitUiState(holder, "initial Session list") {
                it.sessionList?.sessions?.map { item -> item.id } == listOf(session.id)
            }
            holder.onEvent(EntryUiEvent.SessionClicked(session.id))
            awaitUiState(holder, "opened Session") {
                it.sessionList?.openedSession?.session?.id == session.id
            }
            holder.onEvent(EntryUiEvent.ComposerTextChanged("Run once"))
            holder.onEvent(EntryUiEvent.SendMessageClicked)
            awaitUiState(
                holder,
                "terminal run status",
                detail = {
                    "observeCalls=${gateway.observeCalls} nextCalls=${gateway.nextCalls}" +
                        " statusCalls=${gateway.statusCalls} createRunLanes=${gateway.createRunLanes}"
                },
            ) {
                it.sessionList?.openedSession?.latestRun?.status == "succeeded"
            }

            assertTrue(
                "the producing gateway call must run outside the consumer",
                gateway.createRunLanes.contains(PRODUCER_LANE),
            )
            assertTrue(
                "the run event application must run on the consumer",
                store.lanes.any { it == CONSUMER_LANE },
            )
        } finally {
            holder.close()
        }
    }
}
