package org.mercurio.client.feature.entry.presentation

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ThreadContextElement
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
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
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.AbstractCoroutineContextElement
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

    override fun close() = Unit
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
): String = runCatching { privateField(holder, name).toString() }.getOrElse { error -> "$name=<unavailable: $error>" }

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

private fun consumerLaneHolder(
    recorder: LaneRecorder,
    gateway: ObservingGateway,
    store: LaneRecordingUncertaintyStore,
): EntryStateHolder {
    val repository = DefaultGatewayConnectionRepository(InMemoryGatewayConnectionDataSource())
    return EntryStateHolder(
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
        val holder = consumerLaneHolder(recorder, gateway, store)

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

    @Test
    fun ui_event_dispatch_writes_state_on_the_state_change_consumer() {
        val holder = bareHolderOn(Dispatchers.Unconfined)
        val recording = installRecordingUiState(holder) { false }
        try {
            holder.onEvent(EntryUiEvent.EndpointChanged("https://gateway.example/profile"))
            holder.onEvent(EntryUiEvent.BearerCredentialChanged("memory-only-token"))

            assertEquals("https://gateway.example/profile", holder.uiState.value.endpoint)
            assertEquals("memory-only-token", holder.uiState.value.bearerCredential)
            assertTrue(
                "every UI-event write must run on the consumer: ${recording.writes.map { it.appliedOnConsumer }}",
                recording.writes.isNotEmpty() && recording.writes.all { it.appliedOnConsumer },
            )
        } finally {
            holder.close()
        }
    }

    @Test
    fun queued_events_are_not_applied_until_the_consumer_runs_and_apply_in_submission_order() {
        val dispatcher = ReconciliationPausingDispatcher()
        val holder = bareHolderOn(dispatcher)
        val recording = installRecordingUiState(holder) { false }
        try {
            dispatcher.paused = true
            holder.onEvent(EntryUiEvent.EndpointChanged("first"))
            holder.onEvent(EntryUiEvent.EndpointChanged("second"))
            holder.onEvent(EntryUiEvent.EndpointChanged("third"))

            assertTrue(
                "the paused consumer must not apply an enqueued event: ${recording.writes.size}",
                recording.writes.isEmpty(),
            )
            assertEquals("", holder.uiState.value.endpoint)

            dispatcher.drain()

            assertEquals("third", holder.uiState.value.endpoint)
            assertEquals(
                "the consumer must apply enqueued events in submission order",
                listOf("first", "second", "third"),
                recording.writes.map { it.state.endpoint },
            )
            assertTrue(recording.writes.all { it.appliedOnConsumer })
        } finally {
            holder.close()
            dispatcher.drain()
        }
    }

    @Test
    fun close_applies_queued_events_before_the_scope_teardown() {
        val dispatcher = ReconciliationPausingDispatcher()
        val holder = bareHolderOn(dispatcher)
        val closeCalled = AtomicBoolean(false)
        val recording = installRecordingUiState(holder) { closeCalled.get() }
        try {
            dispatcher.paused = true
            holder.onEvent(EntryUiEvent.EndpointChanged("queued-endpoint"))
            holder.close()
            closeCalled.set(true)

            dispatcher.drain()

            assertEquals("queued-endpoint", holder.uiState.value.endpoint)
            assertTrue(
                "the event queued before close must still be applied ahead of the teardown: " +
                    recording.writes.map { it.state.endpoint },
                recording.writes.size == 2 && recording.writes.first().state.endpoint == "queued-endpoint",
            )
            assertTrue(
                "the queued event must apply after close() returned, on the consumer",
                recording.writes.first().appliedAfterClose && recording.writes.first().appliedOnConsumer,
            )
            assertTrue(
                "the teardown must end the holder scope",
                (privateField(holder, "scope") as CoroutineScope).coroutineContext[Job]!!.isCancelled,
            )
        } finally {
            holder.close()
            dispatcher.drain()
        }
    }
}

private class RecordedStateWrite(
    val state: EntryUiState,
    val appliedOnConsumer: Boolean,
    val appliedAfterClose: Boolean,
)

private class ConsumerThreadProbe : ThreadContextElement<Boolean>, AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<ConsumerThreadProbe>

    private val onConsumerThreads = ThreadLocal.withInitial { false }

    fun isOnConsumer(): Boolean = onConsumerThreads.get()

    override fun updateThreadContext(context: CoroutineContext): Boolean {
        val previous = onConsumerThreads.get()
        onConsumerThreads.set(true)
        return previous
    }

    override fun restoreThreadContext(
        context: CoroutineContext,
        oldState: Boolean,
    ) {
        onConsumerThreads.set(oldState)
    }
}

private class RecordingUiStateFlow(
    private val delegate: MutableStateFlow<EntryUiState>,
    private val consumerProbe: ConsumerThreadProbe,
    private val closeCalled: () -> Boolean,
) : MutableStateFlow<EntryUiState> by delegate {
    val writes = mutableListOf<RecordedStateWrite>()

    override var value: EntryUiState
        get() = delegate.value
        set(newState) {
            record(newState)
            delegate.value = newState
        }

    override fun compareAndSet(
        expect: EntryUiState,
        update: EntryUiState,
    ): Boolean {
        val changed = delegate.compareAndSet(expect, update)
        if (changed) record(update)
        return changed
    }

    private fun record(state: EntryUiState) {
        writes += RecordedStateWrite(state, consumerProbe.isOnConsumer(), closeCalled())
    }
}

private fun installRecordingUiState(
    holder: EntryStateHolder,
    closeCalled: () -> Boolean,
): RecordingUiStateFlow {
    val recording =
        RecordingUiStateFlow(
            privateField(holder, "mutableUiState") as MutableStateFlow<EntryUiState>,
            CONSUMER_THREAD_PROBE,
            closeCalled,
        )
    privateField(holder, "mutableUiState", recording)
    return recording
}

private val CONSUMER_THREAD_PROBE = ConsumerThreadProbe()

private fun bareHolderOn(dispatcher: CoroutineDispatcher): EntryStateHolder =
    EntryStateHolder(
        initialState = EntryState(isGatewayConnectionConfigured = false),
        scope = CoroutineScope(SupervisorJob() + CONSUMER_THREAD_PROBE + dispatcher),
    )
