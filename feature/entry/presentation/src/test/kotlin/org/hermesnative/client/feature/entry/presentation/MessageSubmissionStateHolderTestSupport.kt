package org.hermesnative.client.feature.entry.presentation

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.hermesnative.client.feature.entry.application.EntryState
import org.hermesnative.client.feature.entry.application.RemoveGatewayConnection
import org.hermesnative.client.feature.entry.application.VerifyGatewayConnection
import org.hermesnative.client.feature.entry.data.DefaultGatewayConnectionRepository
import org.hermesnative.client.feature.entry.data.InMemoryGatewayConnectionDataSource
import org.hermesnative.client.feature.entry.domain.GatewayCapabilities
import org.hermesnative.client.feature.entry.domain.GatewayConnectionRepository
import org.hermesnative.client.feature.entry.domain.GatewayException
import org.hermesnative.client.feature.entry.domain.PendingRunSubmissionKey
import org.hermesnative.client.feature.entry.domain.PublicBetaGatewayCapabilityManifest
import org.hermesnative.client.feature.entry.domain.Run
import org.hermesnative.client.feature.entry.domain.RunGatewayPort
import org.hermesnative.client.feature.entry.domain.RunId
import org.hermesnative.client.feature.entry.domain.RunSubmissionUncertaintyStore
import org.hermesnative.client.feature.entry.domain.Session
import org.hermesnative.client.feature.entry.domain.SessionGatewayPort
import org.hermesnative.client.feature.entry.domain.SessionHistory
import org.hermesnative.client.feature.entry.domain.SessionId
import org.hermesnative.client.feature.entry.domain.SessionListRequest
import org.hermesnative.client.feature.entry.domain.SessionPage
import org.hermesnative.client.feature.entry.domain.SessionPinResult
import java.util.ArrayDeque
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

private const val TEST_TIMEOUT_MILLIS = 5_000L

internal fun holder(
    gateway: FakeGateway,
    dispatcher: CoroutineDispatcher = Dispatchers.Unconfined,
    repository: GatewayConnectionRepository =
        DefaultGatewayConnectionRepository(InMemoryGatewayConnectionDataSource()),
    uncertaintyStore: RunSubmissionUncertaintyStore = NoOpRunSubmissionUncertaintyStore,
): EntryStateHolder =
    EntryStateHolder(
        initialState = EntryState(isGatewayConnectionConfigured = false),
        verifyGatewayConnection =
            VerifyGatewayConnection(repository) { _, _ ->
                GatewayCapabilities(PublicBetaGatewayCapabilityManifest.current.requiredEndpoints)
            },
        scope = CoroutineScope(SupervisorJob() + dispatcher),
        sessionGatewayFactory = { _, _ -> gateway },
        runGatewayFactory = { _, _ -> gateway },
        dependencies = submissionHolderDependencies(repository, uncertaintyStore, gateway.runFinished::countDown),
    )

internal fun connect(
    holder: EntryStateHolder,
    gateway: FakeGateway,
) {
    holder.onEvent(EntryUiEvent.AddGatewayConnectionClicked)
    holder.onEvent(EntryUiEvent.EndpointChanged("https://gateway.example/profile"))
    holder.onEvent(EntryUiEvent.BearerCredentialChanged("memory-only-token"))
    holder.onEvent(EntryUiEvent.VerifyGatewayConnectionClicked)
    awaitState(holder) {
        it.sessionList?.sessions ==
            gateway.sessions.map { session -> session.toSessionItemUiState() }
    }
}

internal fun open(
    holder: EntryStateHolder,
    gateway: FakeGateway,
    sessionId: SessionId,
) {
    connect(holder, gateway)
    holder.onEvent(EntryUiEvent.SessionClicked(sessionId))
    awaitState(holder) { it.sessionList?.openedSession?.session?.id == sessionId }
}

internal fun awaitState(
    holder: EntryStateHolder,
    predicate: (EntryUiState) -> Boolean,
) {
    runBlocking {
        withTimeout(TEST_TIMEOUT_MILLIS) {
            holder.uiState.first(predicate)
        }
    }
}

internal fun session(id: String): Session =
    Session(
        id = SessionId(id),
        title = "Session $id",
        preview = "Preview",
        pinned = false,
    )

internal fun submissionHolderDependencies(
    repository: GatewayConnectionRepository,
    uncertaintyStore: RunSubmissionUncertaintyStore,
    onRunSubmissionSettled: () -> Unit,
): EntryStateHolderDependencies =
    EntryStateHolderDependencies(
        removeGatewayConnectionUseCase = RemoveGatewayConnection(repository),
        runSubmissionUncertaintyStore = uncertaintyStore,
        onRunSubmissionSettled = onRunSubmissionSettled,
    )

internal class FakeGateway(
    val sessions: List<Session>,
    private val histories: Map<SessionId, SessionHistory> = emptyMap(),
    private val runStatuses: Map<RunId, Run> = emptyMap(),
) : SessionGatewayPort, RunGatewayPort {
    private val mutableHistories = histories.toMutableMap()
    private val mutableRunStatuses = runStatuses.toMutableMap()
    val runRequests = mutableListOf<Pair<SessionId, String>>()
    private val runResults = ArrayDeque<Result<Run>>()
    val runStarted = CountDownLatch(1)
    val runFinished = CountDownLatch(1)
    val releaseRun = CountDownLatch(1)
    var blockRunCreation = false

    fun enqueueRun(run: Run) {
        runResults += Result.success(run)
    }

    fun enqueueRunFailure(error: GatewayException) {
        runResults += Result.failure(error)
    }

    fun setRunStatus(run: Run) {
        mutableRunStatuses[run.id] = run
    }

    fun setHistory(history: SessionHistory) {
        mutableHistories[history.sessionId] = history
    }

    override fun listSessions(request: SessionListRequest): SessionPage = SessionPage(sessions, null)

    override fun createSession(title: String?): Session = error("not used")

    override fun openSession(sessionId: SessionId): Session = sessions.single { it.id == sessionId }

    override fun loadSessionHistory(sessionId: SessionId): SessionHistory =
        mutableHistories[sessionId] ?: SessionHistory(sessionId, emptyList())

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
        runRequests += sessionId to input
        if (blockRunCreation) {
            runStarted.countDown()
            check(releaseRun.await(TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                "Timed out waiting for Run release."
            }
        }
        return runResults.removeFirst().getOrThrow()
    }

    override fun getRunStatus(runId: RunId): Run = mutableRunStatuses[runId] ?: error("not used")

    override fun observeRun(runId: RunId) = error("not used")
}

internal class FailingShutdownUncertaintyStore : RunSubmissionUncertaintyStore {
    private var addCount = 0

    override fun add(
        key: PendingRunSubmissionKey,
        knownRunIds: Set<RunId>,
        attemptId: String?,
    ): Boolean {
        addCount += 1
        if (addCount > 1) error("test persistence failure")
        return true
    }

    override fun remove(
        key: PendingRunSubmissionKey,
        attemptId: String?,
    ): Boolean = true

    override fun contains(key: PendingRunSubmissionKey): Boolean = addCount > 0
}
