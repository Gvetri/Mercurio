package org.mercurio.client.feature.entry.presentation

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.mercurio.client.feature.entry.application.EntryState
import org.mercurio.client.feature.entry.application.RemoveGatewayConnection
import org.mercurio.client.feature.entry.application.VerifyGatewayConnection
import org.mercurio.client.feature.entry.data.DefaultGatewayConnectionRepository
import org.mercurio.client.feature.entry.data.InMemoryGatewayConnectionDataSource
import org.mercurio.client.feature.entry.data.RunSubmissionUncertaintyStorage
import org.mercurio.client.feature.entry.domain.GatewayCapabilities
import org.mercurio.client.feature.entry.domain.GatewayConnectionRepository
import org.mercurio.client.feature.entry.domain.NoOpSessionDraftStore
import org.mercurio.client.feature.entry.domain.PendingRunSubmissionKey
import org.mercurio.client.feature.entry.domain.PublicBetaGatewayCapabilityManifest
import org.mercurio.client.feature.entry.domain.Run
import org.mercurio.client.feature.entry.domain.RunEvent
import org.mercurio.client.feature.entry.domain.RunEventObservation
import org.mercurio.client.feature.entry.domain.RunEventType
import org.mercurio.client.feature.entry.domain.RunGatewayPort
import org.mercurio.client.feature.entry.domain.RunId
import org.mercurio.client.feature.entry.domain.RunRecoveryEntry
import org.mercurio.client.feature.entry.domain.RunRecoveryRegistry
import org.mercurio.client.feature.entry.domain.RunSubmissionUncertaintySnapshot
import org.mercurio.client.feature.entry.domain.RunSubmissionUncertaintyStore
import org.mercurio.client.feature.entry.domain.Session
import org.mercurio.client.feature.entry.domain.SessionDraftStore
import org.mercurio.client.feature.entry.domain.SessionGatewayPort
import org.mercurio.client.feature.entry.domain.SessionHistory
import org.mercurio.client.feature.entry.domain.SessionId
import org.mercurio.client.feature.entry.domain.SessionListRequest
import org.mercurio.client.feature.entry.domain.SessionPage
import org.mercurio.client.feature.entry.domain.SessionPinResult
import java.util.ArrayDeque
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Asserts that a cancelled parent job releases the run observer. With the observation start held
 * at the gateway call, a cancelled parent must stop the job before it registers its observation
 * and must not start a replacement one. Cancelled before the submission runs, the holder must not
 * open an observation at all.
 */
internal suspend fun TestScope.assertParentCancellationReleasesObserver(beforeSubmissionRuns: Boolean) {
    val session = reconciliationSession()
    val run = Run(RunId("parent-cancelled-run"), session.id, "running")
    val parent = SupervisorJob()
    val failures = CopyOnWriteArrayList<Throwable>()
    val scope =
        CoroutineScope(
            parent + StandardTestDispatcher(testScheduler) +
                CoroutineExceptionHandler {
                    _,
                    error,
                    ->
                    failures += error
                },
        )
    val gateway =
        ReconciliationFakeGateway(session).apply {
            runs.add(run)
            blockNextObservationOpen = true
        }
    val holder = reconciliationHolderOn(scope, gateway)

    try {
        openReconciledSession(holder, gateway)
        holder.onEvent(EntryUiEvent.ComposerTextChanged("Cancel the parent"))
        holder.onEvent(EntryUiEvent.SendMessageClicked)
        if (beforeSubmissionRuns) {
            parent.cancel()
        } else {
            advanceUntilIdle()
            assertEquals(1, gateway.observationRequests.size)
            assertEquals(1, holder.testSurface.runObservationJobs.size)
            assertTrue(holder.testSurface.runObservationJobs.values.single().isActive)
            assertTrue(gateway.observedRunIds.isEmpty())
            parent.cancel()
        }
        advanceUntilIdle()

        val expectedObservationRequests = if (beforeSubmissionRuns) 0 else 1
        assertEquals(
            "A cancelled parent must not register a replacement observer.",
            expectedObservationRequests,
            gateway.observationRequests.size,
        )
        assertTrue("Unexpected coroutine failures: $failures", failures.isEmpty())
        assertTrue(parent.isCompleted)
        assertTrue(holder.testSurface.runObservationJobs.isEmpty())
        assertTrue(holder.testSurface.runObservationRunIds.isEmpty())
        assertTrue(holder.testSurface.pendingRunObservationRequests.isEmpty())
        assertTrue(gateway.observedRunIds.isEmpty())
        if (!beforeSubmissionRuns) {
            assertEquals(listOf(session.id to "Cancel the parent"), gateway.runRequests)
            assertEquals(run.id, holder.uiState.value.sessionList?.openedSession?.latestRun?.id)
        }
    } finally {
        gateway.releaseObservationOpen.complete(Unit)
        holder.close()
        advanceUntilIdle()
    }
}

internal suspend fun connectReconciliationGateway(
    holder: EntryStateHolder,
    gateway: ReconciliationFakeGateway,
) {
    holder.onEvent(EntryUiEvent.AddGatewayConnectionClicked)
    holder.onEvent(EntryUiEvent.EndpointChanged("https://gateway.example/profile"))
    holder.onEvent(EntryUiEvent.BearerCredentialChanged("memory-only-token"))
    holder.onEvent(EntryUiEvent.VerifyGatewayConnectionClicked)
    awaitReconciliationState(holder) { it.sessionList?.sessions == listOf(gateway.session.toSessionItemUiState()) }
}

internal class ReconciliationArmingRegistry(
    private val failAfterRelease: Boolean = false,
) : RunRecoveryRegistry {
    val entered = CompletableDeferred<Unit>()
    val release = CompletableDeferred<Unit>()

    @Volatile
    var armed = false

    override suspend fun load(): List<RunRecoveryEntry> {
        if (!armed) return emptyList()
        entered.complete(Unit)
        release.await()
        if (failAfterRelease) error("recovery registry is unavailable")
        return emptyList()
    }

    override suspend fun save(entry: RunRecoveryEntry) = Unit

    override suspend fun remove(entry: RunRecoveryEntry) = Unit
}

internal class ReconciliationHolderSettings(
    val sendTimeoutMillis: Long = 30_000L,
    val recoveryRegistry: RunRecoveryRegistry? = null,
    val uncertaintyStore: RunSubmissionUncertaintyStore = NoOpRunSubmissionUncertaintyStore,
    val sessionDraftStore: SessionDraftStore = NoOpSessionDraftStore,
) {
    var stateChangeDispatcher: kotlinx.coroutines.CoroutineDispatcher? = null
}

internal fun TestScope.reconciliationHolder(
    gateway: ReconciliationFakeGateway,
    settings: ReconciliationHolderSettings = ReconciliationHolderSettings(),
): EntryStateHolder =
    reconciliationHolderOn(CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)), gateway, settings)

internal fun reconciliationHolderOn(
    scope: CoroutineScope,
    gateway: ReconciliationFakeGateway,
    settings: ReconciliationHolderSettings = ReconciliationHolderSettings(),
): EntryStateHolder {
    val repository: GatewayConnectionRepository =
        DefaultGatewayConnectionRepository(InMemoryGatewayConnectionDataSource())
    return EntryStateHolder(
        initialState = EntryState(isGatewayConnectionConfigured = false),
        verifyGatewayConnection =
            VerifyGatewayConnection(repository) { _, _ ->
                GatewayCapabilities(PublicBetaGatewayCapabilityManifest.current.requiredEndpoints)
            },
        scope = scope,
        sessionGatewayFactory = { _, _ -> gateway },
        runGatewayFactory = { _, _ -> gateway },
        dependencies =
            EntryStateHolderDependencies(
                runRecoveryRegistry = settings.recoveryRegistry,
                runSubmissionUncertaintyStore = settings.uncertaintyStore,
                sessionDraftStore = settings.sessionDraftStore,
                persistRunRecoveryEntry =
                    settings.recoveryRegistry?.let { registry -> { _, entry -> registry.save(entry) } },
                removeRunRecoveryEntry =
                    settings.recoveryRegistry?.let { registry -> { _, entry -> registry.remove(entry) } },
                removeGatewayConnectionUseCase = RemoveGatewayConnection(repository),
                onRunSubmissionCompleted = {
                    gateway.submissionCompleted.complete(Unit)
                    if (gateway.blockSubmissionCompletion) {
                        gateway.releaseSubmissionCompletion.await()
                    }
                },
                sendTimeoutMillis = settings.sendTimeoutMillis,
                stateChangeDispatcher = settings.stateChangeDispatcher,
            ),
    )
}

internal suspend fun openReconciledSession(
    holder: EntryStateHolder,
    gateway: ReconciliationFakeGateway,
) {
    holder.onEvent(EntryUiEvent.AddGatewayConnectionClicked)
    holder.onEvent(EntryUiEvent.EndpointChanged("https://gateway.example/profile"))
    holder.onEvent(EntryUiEvent.BearerCredentialChanged("memory-only-token"))
    holder.onEvent(EntryUiEvent.VerifyGatewayConnectionClicked)
    awaitReconciliationState(holder) { it.sessionList?.sessions == listOf(gateway.session.toSessionItemUiState()) }
    holder.onEvent(EntryUiEvent.SessionClicked(gateway.session.id))
    awaitReconciliationState(holder) {
        it.sessionList?.openingSessionId == null &&
            it.sessionList?.openedSession?.session?.id == gateway.session.id &&
            !it.sessionList.openedSession.isReconciliationInProgress
    }
}

internal suspend fun awaitReconciliationState(
    holder: EntryStateHolder,
    predicate: (EntryUiState) -> Boolean,
) = holder.awaitUiState("the reconciliation state", predicate)

internal suspend fun TestScope.awaitReconciliationCondition(predicate: suspend () -> Boolean) {
    advanceUntilIdle()
    check(predicate()) { "The reconciliation condition did not hold after the scheduler drained." }
}

internal suspend fun TestScope.awaitRunSubmissionAllowed(
    holder: EntryStateHolder,
    sessionId: SessionId,
) {
    awaitReconciliationCondition {
        val sessionList = holder.uiState.value.sessionList
        sessionList != null && !holder.blocksRunSubmissionNow(sessionList, sessionId)
    }
}

internal fun recoveredDeltaObservation(run: Run): ReconciliationScriptedObservation =
    ReconciliationScriptedObservation(
        listOf(RunEvent(RunEventType.MESSAGE_DELTA, run.id, "running", "Recovered observation", "delta")),
    )

internal suspend fun joinHolder(holder: EntryStateHolder) {
    holder.testSurface.scope.coroutineContext[Job]?.join()
}

internal fun reconciliationSession(): Session =
    Session(
        id = SessionId("session-1"),
        title = "Session",
        preview = "Preview",
        pinned = false,
    )

internal suspend fun invokePersistedRecoveryEntry(
    holder: EntryStateHolder,
    endpoint: String,
    entry: RunRecoveryEntry,
) {
    holder.reconcilePersistedRecoveryEntryIfConnected(endpoint, entry)
}

internal class ReconciliationFakeGateway(
    val session: Session,
) : SessionGatewayPort,
    RunGatewayPort {
    val histories = ArrayDeque<SessionHistory>()
    val statuses = ArrayDeque<Run>()
    val runs = ArrayDeque<Run>()
    val runRequests: MutableList<Pair<SessionId, String>> = CopyOnWriteArrayList()
    val statusRequests: MutableList<RunId> = CopyOnWriteArrayList()
    val failStatusRunIds = mutableSetOf<RunId>()
    val observedRunIds: MutableList<RunId> = CopyOnWriteArrayList()
    val observationRequests: MutableList<RunId> = CopyOnWriteArrayList()
    val observations = ArrayDeque<RunEventObservation>()
    private var historyRequestCount = 0
    val historyRequests: Int get() = historyRequestCount
    private var listRequestCount = 0
    val listRequests: Int get() = listRequestCount
    var observation: RunEventObservation = ReconciliationScriptedObservation(emptyList())
    var blockRunCreation = false
    var failRunCreation = false
    var failHistoryRequest: Int? = null
    var blockNextStatus = false
    private val lastServedStatuses = mutableMapOf<RunId, Run>()
    var blockSubmissionCompletion = false
    var blockNextObservationOpen = false
    var openIgnoresCancellation = false
    val runStarted = CompletableDeferred<Unit>()
    val submissionCompleted = CompletableDeferred<Unit>()
    val runFinished = CompletableDeferred<Unit>()
    val releaseRun = CompletableDeferred<Unit>()
    val releaseSubmissionCompletion = CompletableDeferred<Unit>()
    val statusStarted = CompletableDeferred<Unit>()
    val statusFinished = CompletableDeferred<Unit>()
    val releaseStatus = CompletableDeferred<Unit>()
    val staleHistoryLoaded = CompletableDeferred<Unit>()
    val observationOpenStarted = CompletableDeferred<Unit>()
    val observationOpenFinished = CompletableDeferred<Unit>()
    val releaseObservationOpen = CompletableDeferred<Unit>()
    val secondObservationRequested = CompletableDeferred<Unit>()

    override suspend fun listSessions(request: SessionListRequest): SessionPage {
        listRequestCount += 1
        return SessionPage(listOf(session), null)
    }

    override suspend fun createSession(title: String?): Session = error("not used")

    override suspend fun openSession(sessionId: SessionId): Session = session

    override suspend fun loadSessionHistory(sessionId: SessionId): SessionHistory {
        historyRequestCount += 1
        val history =
            if (histories.isEmpty()) {
                SessionHistory(sessionId, emptyList())
            } else {
                histories.removeFirst()
            }
        if (historyRequestCount == failHistoryRequest) error("history request failed")
        if (history.messages.any { it.content == "stale-refresh" }) {
            staleHistoryLoaded.complete(Unit)
        }
        return history
    }

    override suspend fun renameSession(
        sessionId: SessionId,
        title: String,
    ): Session = error("not used")

    override suspend fun deleteSession(sessionId: SessionId) = error("not used")

    override suspend fun pinSession(sessionId: SessionId): SessionPinResult = error("not used")

    override suspend fun unpinSession(sessionId: SessionId): SessionPinResult = error("not used")

    override suspend fun createRun(
        sessionId: SessionId,
        input: String,
    ): Run {
        runRequests += sessionId to input
        if (failRunCreation) error("run creation failed")
        if (blockRunCreation) {
            runStarted.complete(Unit)
            try {
                releaseRun.await()
            } finally {
                runFinished.complete(Unit)
            }
        }
        return if (runs.isEmpty()) error("missing run") else runs.removeFirst()
    }

    override suspend fun getRunStatus(runId: RunId): Run {
        statusRequests += runId
        if (failStatusRunIds.remove(runId)) error("status request failed")
        val shouldBlock = blockNextStatus.also { blockNextStatus = false }
        val status =
            statuses.pollFirst()
                ?: lastServedStatuses[runId]
                ?: error("no status scripted")
        lastServedStatuses[runId] = status
        if (shouldBlock) {
            statusStarted.complete(Unit)
            try {
                releaseStatus.await()
            } finally {
                statusFinished.complete(Unit)
            }
        }
        return status
    }

    override suspend fun observeRun(runId: RunId): RunEventObservation {
        observationRequests += runId
        if (observationRequests.size == 2) {
            secondObservationRequested.complete(Unit)
        }
        if (blockNextObservationOpen) {
            blockNextObservationOpen = false
            observationOpenStarted.complete(Unit)
            awaitObservationOpenRelease()
            observationOpenFinished.complete(Unit)
        }
        observedRunIds += runId
        return if (observations.isEmpty()) observation else observations.removeFirst()
    }

    private suspend fun awaitObservationOpenRelease() {
        if (openIgnoresCancellation) {
            withContext(NonCancellable) { releaseObservationOpen.await() }
        } else {
            releaseObservationOpen.await()
        }
    }
}

internal open class ReconciliationScriptedObservation(
    events: List<RunEvent>,
) : RunEventObservation {
    private val remaining = events.iterator()

    override suspend fun next(): RunEvent? = if (remaining.hasNext()) remaining.next() else null

    override fun close() = Unit
}

internal class ReconciliationThrowingObservation(
    events: List<RunEvent>,
) : ReconciliationScriptedObservation(events) {
    override suspend fun next(): RunEvent? = super.next() ?: error("stream interrupted")
}

internal class ReconciliationDelayedTerminalObservation(
    private val terminalEvent: RunEvent,
) : RunEventObservation {
    val started = CompletableDeferred<Unit>()
    val release = CompletableDeferred<Unit>()
    private var emitted = false

    override suspend fun next(): RunEvent? {
        if (emitted) return null
        started.complete(Unit)
        release.await()
        emitted = true
        return terminalEvent
    }

    override fun close() {
        release.complete(Unit)
    }
}

internal class ReconciliationBlockingObservation(
    private val events: List<RunEvent> = emptyList(),
) : RunEventObservation {
    val started = CompletableDeferred<Unit>()
    val closed = CompletableDeferred<Unit>()
    val finished = CompletableDeferred<Unit>()
    val release = CompletableDeferred<Unit>()
    private var closeCount = 0
    private var nextEventIndex = 0

    override suspend fun next(): RunEvent? {
        started.complete(Unit)
        if (nextEventIndex < events.size) return events[nextEventIndex++]
        return try {
            release.await()
            null
        } finally {
            finished.complete(Unit)
        }
    }

    override fun close() {
        release.complete(Unit)
        closeCount += 1
        if (closeCount == 1) closed.complete(Unit)
    }
}

internal class ReconciliationBlockingRecoveryRegistry : RunRecoveryRegistry {
    private val entries = mutableListOf<RunRecoveryEntry>()
    val loadStarted = CompletableDeferred<Unit>()
    val loadFinished = CompletableDeferred<Unit>()
    val releaseLoad = CompletableDeferred<Unit>()

    @Volatile
    var blockLoads = false

    @Volatile
    var blockSaves = false

    @Volatile
    var blockRemovals = false

    val saveStarted = CompletableDeferred<Unit>()
    val releaseSave = CompletableDeferred<Unit>()
    var removeRequests = 0
    var effectiveRemovals = 0
    val releaseRemove = CompletableDeferred<Unit>()

    override suspend fun load(): List<RunRecoveryEntry> {
        val shouldSignalCompletion = blockLoads
        if (shouldSignalCompletion) {
            loadStarted.complete(Unit)
            releaseLoad.await()
        }
        return entries.toList().also {
            if (shouldSignalCompletion) loadFinished.complete(Unit)
        }
    }

    override suspend fun save(entry: RunRecoveryEntry) {
        if (blockSaves) {
            saveStarted.complete(Unit)
            releaseSave.await()
        }
        if (entry !in entries) entries += entry
    }

    override suspend fun remove(entry: RunRecoveryEntry) {
        removeRequests += 1
        if (blockRemovals) {
            releaseRemove.await()
        }
        if (entries.remove(entry)) effectiveRemovals += 1
    }
}

internal fun invokeHistoryRunIdToReconcile(
    holder: EntryStateHolder,
    sessionId: SessionId,
    openedSession: org.mercurio.client.feature.entry.application.OpenedSession,
): RunId? = holder.historyRunIdToReconcile(sessionId, openedSession)

internal class ReconciliationUncertaintyStorage : RunSubmissionUncertaintyStorage {
    private val records = mutableMapOf<PendingRunSubmissionKey, RunSubmissionUncertaintySnapshot>()

    override fun read(key: PendingRunSubmissionKey): RunSubmissionUncertaintySnapshot? = records[key]

    override fun write(
        key: PendingRunSubmissionKey,
        snapshot: RunSubmissionUncertaintySnapshot,
    ) {
        records[key] = snapshot
    }

    override fun remove(key: PendingRunSubmissionKey) {
        records.remove(key)
    }
}

internal class ReconciliationDelayedUnwindObservation : RunEventObservation {
    val started = CompletableDeferred<Unit>()
    val closed = CompletableDeferred<Unit>()
    val finished = CompletableDeferred<Unit>()
    val release = CompletableDeferred<Unit>()
    private var closeCount = 0

    override suspend fun next(): RunEvent? {
        started.complete(Unit)
        return try {
            release.await()
            null
        } finally {
            finished.complete(Unit)
        }
    }

    override fun close() {
        closeCount += 1
        if (closeCount == 1) {
            closed.complete(Unit)
        }
    }
}

internal const val RECONCILIATION_TIMEOUT_MILLIS = 15_000L
