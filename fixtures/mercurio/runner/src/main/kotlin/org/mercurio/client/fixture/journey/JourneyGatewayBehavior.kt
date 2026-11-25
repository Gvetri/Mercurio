package org.mercurio.client.fixture.journey

import org.mercurio.client.fixture.GatewayHttpSupport
import org.mercurio.client.fixture.SyntheticGatewayRequest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

internal class JourneyGatewayBehavior(
    val scenario: JourneyScenario,
) {
    val runSelections = CopyOnWriteArrayList<JourneyRunSelectionRecord>()
    val sessions = CopyOnWriteArrayList(scenario.sessions)
    val requests = CopyOnWriteArrayList<SyntheticGatewayRequest>()
    val sessionMutations = CopyOnWriteArrayList<Pair<String, String>>()
    val errorReports = CopyOnWriteArrayList<Pair<String, String>>()

    @Volatile
    private var failNextSessionList = scenario.failNextSessionList

    val offlineGate =
        OfflineAfterFirstSessionListGate(
            enabled = scenario.failAfterFirstSessionList,
            recoversAfterBlockedSessionLists = scenario.offlineRecoversAfterBlockedSessionLists,
        )

    val offlineOpenGate =
        OfflineAfterFirstSessionOpenGate(
            enabled = scenario.failAfterFirstSessionOpen,
            recoversAfterBlockedSessionOpens = scenario.offlineRecoversAfterBlockedSessionOpens,
        )

    val sseOpened: ConcurrentHashMap<String, AtomicInteger> = ConcurrentHashMap()
    val sseClosed: ConcurrentHashMap<String, AtomicInteger> = ConcurrentHashMap()
    val runStatusRequests = AtomicInteger(0)
    val interruptedRunIds = CopyOnWriteArrayList<String>()
    private val createdRunCounters = ConcurrentHashMap<String, AtomicInteger>()
    private val createdSessionCounter = AtomicInteger(0)
    internal val observationDelivered = ConcurrentHashMap<String, Boolean>()

    fun recordSelection(
        sessionId: String,
        model: String?,
        effort: String?,
        accepted: Boolean,
    ) {
        runSelections += JourneyRunSelectionRecord(sessionId, model, effort, accepted)
    }

    fun consumeFailNextSessionList(): Boolean {
        if (!failNextSessionList) return false
        failNextSessionList = false
        return true
    }

    fun createdSessionId(): String {
        val counter = createdSessionCounter.incrementAndGet()
        return "synthetic-created-session-$counter"
    }

    fun replaceSession(session: JourneySession) {
        val index = sessions.indexOfFirst { it.id == session.id }
        if (index >= 0) sessions[index] = session
    }

    fun nextRunScript(sessionId: String): JourneyRunScript? {
        val index = createdRunCounters.getOrPut(sessionId, ::AtomicInteger).getAndIncrement()
        return scenario.runs.filter { it.sessionId == sessionId }.getOrNull(index)
    }

    fun runStatus(runId: String): String? {
        val script = scenario.runs.firstOrNull { it.runId == runId } ?: return null
        return if (observationDelivered[runId] == true || runId in interruptedRunIds) {
            script.finalStatus ?: script.createStatus
        } else {
            script.createStatus
        }
    }

    fun sessionIdOf(runId: String): String? = scenario.runs.firstOrNull { it.runId == runId }?.sessionId

    fun historyMessagesFor(session: JourneySession): List<JourneyMessage> {
        val terminalHistory =
            scenario.runs
                .filter { run ->
                    run.sessionId == session.id &&
                        (observationDelivered[run.runId] == true || run.runId in interruptedRunIds)
                }
                .flatMap(JourneyRunScript::terminalHistory)
        return (session.history + terminalHistory).toList()
    }

    fun telemetryJson(): String =
        buildString {
            append("""{"scenario":${scenario.name.jsonValue()},"provenance":${scenario.hermesRevision.jsonValue()}""")
            appendRequests(requests)
            appendSessionMutations(sessionMutations)
            append(""","run_status_requests":${runStatusRequests.get()}""")
            appendRunCreates(scenario.sessions, createdRunCounters)
            appendRunSelections(runSelections)
            append(""","interrupted_run_ids":[${interruptedRunIds.joinToString(",") { it.jsonValue() }}]""")
            appendErrorReports(errorReports)
            appendSseConnections(scenario.runs, sseOpened, sseClosed)
            append('}')
        }
}

private fun String.jsonValue(): String = GatewayHttpSupport.quote(this)

private fun StringBuilder.appendRequests(requests: List<SyntheticGatewayRequest>) {
    append(""","requests":[""")
    append(
        requests.joinToString(",") { request ->
            """{"method":${request.method.jsonValue()},"path":${request.path.jsonValue()}}"""
        },
    )
    append(']')
}

private fun StringBuilder.appendSessionMutations(sessionMutations: List<Pair<String, String>>) {
    append(""","session_mutations":[""")
    append(
        sessionMutations.joinToString(",") { (operation, sessionId) ->
            """{"operation":${operation.jsonValue()},"session_id":${sessionId.jsonValue()}}"""
        },
    )
    append(']')
}

private fun StringBuilder.appendRunCreates(
    sessions: List<JourneySession>,
    createdRunCounters: Map<String, AtomicInteger>,
) {
    append(""","run_creates":{""")
    append(
        sessions.joinToString(",") { session ->
            """${session.id.jsonValue()}:${createdRunCounters[session.id]?.get() ?: 0}"""
        },
    )
    append('}')
}

private fun StringBuilder.appendRunSelections(runSelections: List<JourneyRunSelectionRecord>) {
    append(""","run_selections":[""")
    append(
        runSelections.joinToString(",") { selection ->
            "{\"session_id\":${selection.sessionId.jsonValue()}," +
                "\"model\":${selection.model.jsonValue()}," +
                "\"effort\":${selection.effort.jsonValue()}," +
                "\"accepted\":${selection.accepted}}"
        },
    )
    append(']')
}

private fun StringBuilder.appendErrorReports(errorReports: List<Pair<String, String>>) {
    append(""","error_reports":[""")
    append(
        errorReports.joinToString(",") { (release, environment) ->
            """{"release":${release.jsonValue()},"environment":${environment.jsonValue()}}"""
        },
    )
    append(']')
}

private fun StringBuilder.appendSseConnections(
    runs: List<JourneyRunScript>,
    sseOpened: Map<String, AtomicInteger>,
    sseClosed: Map<String, AtomicInteger>,
) {
    append(""","sse_connections":{""")
    append(
        runs.joinToString(",") { run ->
            val opened = sseOpened[run.runId]?.get() ?: 0
            val closed = sseClosed[run.runId]?.get() ?: 0
            """${run.runId.jsonValue()}:{"opened":$opened,"closed":$closed}"""
        },
    )
    append('}')
}
