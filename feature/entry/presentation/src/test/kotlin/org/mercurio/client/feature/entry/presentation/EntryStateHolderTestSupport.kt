package org.mercurio.client.feature.entry.presentation

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.mercurio.client.feature.entry.application.EntryState
import org.mercurio.client.feature.entry.application.VerifyGatewayConnection
import org.mercurio.client.feature.entry.domain.GatewayCapabilities
import org.mercurio.client.feature.entry.domain.GatewayConnection
import org.mercurio.client.feature.entry.domain.GatewayConnectionRepository
import org.mercurio.client.feature.entry.domain.GatewayException
import org.mercurio.client.feature.entry.domain.GatewayHistoryMessage
import org.mercurio.client.feature.entry.domain.PublicBetaGatewayCapabilityManifest
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
import java.util.ArrayDeque
import java.util.concurrent.CopyOnWriteArrayList

internal const val ASYNC_TEST_TIMEOUT_MILLIS = 15_000L

internal fun clientManifestCapabilities(): GatewayCapabilities =
    GatewayCapabilities(
        PublicBetaGatewayCapabilityManifest.current.requiredEndpoints,
    )

internal fun holder(
    repository: FakeGatewayConnectionRepository,
    verifier: (String, String) -> GatewayCapabilities,
    gateway: SessionGatewayPort? = null,
    dispatcher: CoroutineDispatcher = Dispatchers.Unconfined,
    dependencies: EntryStateHolderDependencies = EntryStateHolderDependencies(),
): EntryStateHolder = holderOn(CoroutineScope(SupervisorJob() + dispatcher), repository, verifier, gateway, dependencies)

internal fun holderOn(
    scope: CoroutineScope,
    repository: FakeGatewayConnectionRepository,
    verifier: (String, String) -> GatewayCapabilities,
    gateway: SessionGatewayPort? = null,
    dependencies: EntryStateHolderDependencies = EntryStateHolderDependencies(),
): EntryStateHolder =
    EntryStateHolder(
        initialState = EntryState(isGatewayConnectionConfigured = false),
        verifyGatewayConnection = VerifyGatewayConnection(repository, discoverCapabilities = verifier),
        scope = scope,
        sessionGatewayFactory =
            gateway?.let { sessionGateway ->
                { _, _ -> sessionGateway }
            },
        dependencies = dependencies,
    )

internal fun verify(holder: EntryStateHolder) {
    holder.onEvent(EntryUiEvent.AddGatewayConnectionClicked)
    holder.onEvent(EntryUiEvent.EndpointChanged("https://gateway.example/profile"))
    holder.onEvent(EntryUiEvent.BearerCredentialChanged("memory-only-token"))
    holder.onEvent(EntryUiEvent.VerifyGatewayConnectionClicked)
}

internal fun scriptedHolder(
    gateway: SessionGatewayPort,
    dispatcher: CoroutineDispatcher = Dispatchers.Unconfined,
    dependencies: EntryStateHolderDependencies = EntryStateHolderDependencies(),
    runGateway: RunGatewayPort? = null,
): EntryStateHolder = scriptedHolderOn(CoroutineScope(SupervisorJob() + dispatcher), gateway, dependencies, runGateway)

internal fun scriptedHolderOn(
    scope: CoroutineScope,
    gateway: SessionGatewayPort,
    dependencies: EntryStateHolderDependencies = EntryStateHolderDependencies(),
    runGateway: RunGatewayPort? = null,
): EntryStateHolder =
    EntryStateHolder(
        initialState = EntryState(isGatewayConnectionConfigured = false),
        verifyGatewayConnection =
            VerifyGatewayConnection(
                FakeGatewayConnectionRepository(),
            ) { _, _ ->
                clientManifestCapabilities()
            },
        scope = scope,
        sessionGatewayFactory = { _, _ -> gateway },
        runGatewayFactory =
            runGateway?.let { runGatewayPort ->
                { _, _ -> runGatewayPort }
            },
        dependencies = dependencies,
    )

internal suspend fun verifyScripted(holder: EntryStateHolder) {
    verify(holder)
    awaitState(holder, "initial Session list") {
        it.sessionList?.sessions?.isNotEmpty() == true
    }
}

internal suspend fun awaitSessions(
    holder: EntryStateHolder,
    vararg ids: String,
) {
    awaitState(holder, "Sessions ${ids.toList()}") {
        it.sessionList?.sessions?.map { session -> session.id.value } == ids.toList()
    }
}

internal suspend fun awaitState(
    holder: EntryStateHolder,
    description: String,
    predicate: (EntryUiState) -> Boolean,
) = holder.awaitUiState(description, predicate)

internal fun entrySession(
    id: String,
    title: String? = null,
    pinned: Boolean = false,
): Session =
    Session(
        id = SessionId(id),
        title = title,
        preview = "Server preview",
        pinned = pinned,
    )

internal class FakeGatewayConnectionRepository : GatewayConnectionRepository {
    var saved: GatewayConnection? = null
    var throwOnSave = false
    var onSave: (() -> Unit)? = null
    var clearCount = 0

    override fun load(): GatewayConnection? = saved

    override fun save(connection: GatewayConnection) {
        onSave?.invoke()
        if (throwOnSave) error("storage failure")
        saved = connection
    }

    override fun clear() {
        clearCount += 1
        saved = null
    }
}

internal class FakeSessionGateway : SessionGatewayPort {
    private val listResults = ArrayDeque<Result<SessionPage>>()
    private val createResults = ArrayDeque<Result<Session>>()
    val listRequests = mutableListOf<SessionListRequest>()
    val operations = mutableListOf<String>()
    val createTitles = mutableListOf<String?>()
    var createCalls = 0
    var openedSession: Session? = null
    var openedHistory: SessionHistory? = null
    var openFailure: GatewayException? = null
    var onList: (suspend (SessionListRequest) -> Unit)? = null

    fun enqueueList(page: SessionPage) {
        listResults += Result.success(page)
    }

    fun enqueueFailure(error: GatewayException) {
        listResults += Result.failure(error)
    }

    fun enqueueCreate(session: Session) {
        createResults += Result.success(session)
    }

    fun enqueueCreateFailure(error: GatewayException) {
        createResults += Result.failure(error)
    }

    override suspend fun listSessions(request: SessionListRequest): SessionPage {
        listRequests += request
        onList?.invoke(request)
        return listResults.removeFirst().getOrThrow()
    }

    override suspend fun createSession(title: String?): Session {
        createCalls += 1
        createTitles += title
        operations += "create"
        return createResults.removeFirst().getOrThrow()
    }

    override suspend fun openSession(sessionId: SessionId): Session {
        operations += "open"
        openFailure?.let { throw it }
        return requireNotNull(openedSession)
    }

    override suspend fun loadSessionHistory(sessionId: SessionId): SessionHistory {
        operations += "history"
        return requireNotNull(openedHistory)
    }

    override suspend fun renameSession(
        sessionId: SessionId,
        title: String,
    ): Session = error("not used")

    override suspend fun deleteSession(sessionId: SessionId): Unit = error("not used")

    override suspend fun pinSession(sessionId: SessionId): SessionPinResult = error("not used")

    override suspend fun unpinSession(sessionId: SessionId): SessionPinResult = error("not used")
}

internal class FakeRunGateway : RunGatewayPort {
    val statusRequests = mutableListOf<RunId>()
    val statuses = ArrayDeque<Run>()
    var statusFailure: GatewayException? = null

    override suspend fun createRun(
        sessionId: SessionId,
        input: String,
    ): Run = error("not used")

    override suspend fun getRunStatus(runId: RunId): Run {
        statusRequests += runId
        statusFailure?.let { failure -> throw failure }
        return statuses.removeFirst()
    }

    override suspend fun observeRun(runId: RunId): RunEventObservation = ReconciliationScriptedObservation(emptyList())
}

internal class ScriptedCreateGateway(
    private val initialSession: Session,
    private val createdSession: Session,
) : SessionGatewayPort {
    val listRequests = CopyOnWriteArrayList<SessionListRequest>()
    val operations = CopyOnWriteArrayList<String>()
    var createCalls = 0
        private set
    var onCreate: (suspend () -> Unit)? = null
    private var created = false

    override suspend fun listSessions(request: SessionListRequest): SessionPage {
        listRequests += request
        return SessionPage(listOf(if (created) createdSession else initialSession), null)
    }

    override suspend fun createSession(title: String?): Session {
        operations += "create"
        createCalls += 1
        onCreate?.invoke()
        created = true
        return createdSession
    }

    override suspend fun openSession(sessionId: SessionId): Session {
        operations += "open"
        check(sessionId == createdSession.id) { "Unexpected Session open: $sessionId" }
        return createdSession
    }

    override suspend fun loadSessionHistory(sessionId: SessionId): SessionHistory {
        operations += "history"
        return SessionHistory(sessionId, emptyList<GatewayHistoryMessage>())
    }

    override suspend fun renameSession(
        sessionId: SessionId,
        title: String,
    ): Session = error("not used")

    override suspend fun deleteSession(sessionId: SessionId): Unit = error("not used")

    override suspend fun pinSession(sessionId: SessionId): SessionPinResult = error("not used")

    override suspend fun unpinSession(sessionId: SessionId): SessionPinResult = error("not used")
}

internal class ScriptedRefreshGateway(
    private val nextPageOffset: Int? = null,
) : SessionGatewayPort {
    val sessionId = SessionId("session-one")
    var pinCalls = 0
        private set
    var historyCalls = 0
        private set
    var listCalls = 0
        private set
    var onList: (suspend (Int) -> Unit)? = null
    var onHistory: (suspend (Int) -> Unit)? = null
    private val listedSession =
        Session(
            id = sessionId,
            title = "Session",
            preview = "Preview",
            pinned = false,
        )

    override suspend fun listSessions(request: SessionListRequest): SessionPage {
        listCalls += 1
        onList?.invoke(listCalls)
        return SessionPage(listOf(listedSession), nextPageOffset)
    }

    override suspend fun createSession(title: String?): Session = error("not used")

    override suspend fun openSession(sessionId: SessionId): Session {
        check(sessionId == this.sessionId) { "Unexpected Session open: $sessionId" }
        return listedSession
    }

    override suspend fun loadSessionHistory(sessionId: SessionId): SessionHistory {
        check(sessionId == this.sessionId) { "Unexpected Session history: $sessionId" }
        historyCalls += 1
        onHistory?.invoke(historyCalls)
        return SessionHistory(
            sessionId = this.sessionId,
            messages = listOf(GatewayHistoryMessage("message-$historyCalls", null, null)),
        )
    }

    override suspend fun renameSession(
        sessionId: SessionId,
        title: String,
    ): Session = error("not used")

    override suspend fun deleteSession(sessionId: SessionId): Unit = error("not used")

    override suspend fun pinSession(sessionId: SessionId): SessionPinResult {
        check(sessionId == this.sessionId) { "Unexpected Session pin: $sessionId" }
        pinCalls += 1
        return SessionPinResult(sessionId, pinned = true)
    }

    override suspend fun unpinSession(sessionId: SessionId): SessionPinResult = error("not used")
}

internal class ScriptedSessionGateway(
    private val initialPage: SessionPage,
) : SessionGatewayPort {
    val listRequests = CopyOnWriteArrayList<SessionListRequest>()
    private val queuedPages = ArrayDeque<SessionPage>()
    private val requestOccurrences = mutableMapOf<SessionListRequest, Int>()
    var onList: (suspend (SessionListRequest, Int) -> Unit)? = null

    fun enqueuePage(page: SessionPage) {
        queuedPages += page
    }

    override suspend fun listSessions(request: SessionListRequest): SessionPage {
        val occurrence = requestOccurrences.merge(request, 1, Int::plus)!! - 1
        listRequests += request
        onList?.invoke(request, occurrence)
        if (request == SessionListRequest() && occurrence == 0) return initialPage
        return queuedPages.pollFirst()
            ?: error("No SessionPage was queued for request $request occurrence $occurrence.")
    }

    override suspend fun createSession(title: String?): Session = error("not used")

    override suspend fun openSession(sessionId: SessionId): Session = error("not used")

    override suspend fun loadSessionHistory(sessionId: SessionId): SessionHistory = error("not used")

    override suspend fun renameSession(
        sessionId: SessionId,
        title: String,
    ): Session = error("not used")

    override suspend fun deleteSession(sessionId: SessionId): Unit = error("not used")

    override suspend fun pinSession(sessionId: SessionId): SessionPinResult = error("not used")

    override suspend fun unpinSession(sessionId: SessionId): SessionPinResult = error("not used")
}
