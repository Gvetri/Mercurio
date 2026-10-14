package org.hermesnative.client.fixture.journey

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.security.KeyStore
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory

/**
 * Asserts gateway-side invariants after one Maestro journey.
 *
 * The checks are repository-owned and fail closed: every journey must declare
 * its invariants here, and an unknown journey name is a verification failure.
 *
 * Arguments: <telemetry-url> <scenario-name> [keystore-file] [keystore-password]
 */
fun main(args: Array<String>) {
    require(args.size in MIN_ARGUMENT_COUNT..MAX_ARGUMENT_COUNT) {
        "Usage: JourneyVerifier <telemetry-url> <scenario-name> [keystore-file] [keystore-password]"
    }
    val telemetryUrl = URI.create(args[0])
    val scenarioName = args[1]
    val keystoreFile = args.getOrNull(2)?.let(::File)
    val keystorePassword = args.getOrNull(KEYSTORE_PASSWORD_ARG_INDEX) ?: DEFAULT_KEYSTORE_PASSWORD
    val telemetry = fetchTelemetry(telemetryUrl, keystoreFile, keystorePassword)
    JourneyInvariants.verify(scenarioName, telemetry)
    if (scenarioName == "active-run-isolation") {
        val run =
            fetchTelemetry(
                telemetryUrl.resolve("/v1/runs/${JourneyInvariants.ACTIVE_RUN_ID}"),
                keystoreFile,
                keystorePassword,
            )
        check(run["status"]?.jsonPrimitive?.content == "running") {
            "The active Run did not remain running after the Session switch."
        }
    }
    println("journey-verifier=pass")
}

internal data class RunConnectionTelemetry(
    val opened: Int,
    val closed: Int,
)

internal fun fetchTelemetry(
    telemetryUrl: URI,
    keystoreFile: File? = null,
    keystorePassword: String = DEFAULT_KEYSTORE_PASSWORD,
): JsonObject {
    val connection =
        if (telemetryUrl.scheme == "https") {
            val keyStore =
                KeyStore.getInstance("PKCS12").apply {
                    requireNotNull(keystoreFile).inputStream()
                        .use { input -> load(input, keystorePassword.toCharArray()) }
                }
            val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            trustManagerFactory.init(keyStore)
            val sslContext =
                SSLContext.getInstance("TLS").apply {
                    init(null, trustManagerFactory.trustManagers, null)
                }
            (telemetryUrl.toURL().openConnection() as HttpsURLConnection).apply {
                sslSocketFactory = sslContext.socketFactory
            }
        } else {
            telemetryUrl.toURL().openConnection() as HttpURLConnection
        }
    connection.connectTimeout = TELEMETRY_TIMEOUT_MILLIS
    connection.readTimeout = TELEMETRY_TIMEOUT_MILLIS
    connection.requestMethod = "GET"
    connection.setRequestProperty("X-Journey-Probe", "verifier")
    if (connection is HttpsURLConnection) {
        connection.hostnameVerifier = HostnameVerifier { _, _ -> true }
    }
    return try {
        check(connection.responseCode == HTTP_OK) { "Telemetry endpoint returned HTTP ${connection.responseCode}." }
        val body = connection.inputStream.bufferedReader().use { it.readText() }
        Json.parseToJsonElement(body).jsonObject
    } finally {
        connection.disconnect()
    }
}

private fun sseFor(
    telemetry: JsonObject,
    runId: String,
): RunConnectionTelemetry {
    val run =
        telemetry["sse_connections"]?.jsonObject?.get(runId)?.jsonObject
            ?: error("Telemetry has no SSE connection record for run '$runId'.")
    return RunConnectionTelemetry(
        opened = run.getValue("opened").jsonPrimitive.int,
        closed = run.getValue("closed").jsonPrimitive.int,
    )
}

private fun runCreatesFor(
    telemetry: JsonObject,
    sessionId: String,
): Int =
    telemetry["run_creates"]?.jsonObject?.get(sessionId)?.jsonPrimitive?.int
        ?: error("Telemetry has no run-create record for session '$sessionId'.")

private fun runStatusRequests(telemetry: JsonObject): Int {
    return telemetry.getValue("run_status_requests").jsonPrimitive.int
}

private fun interruptedRunIds(telemetry: JsonObject): Set<String> =
    telemetry["interrupted_run_ids"]?.jsonArray?.map { it.jsonPrimitive.content }?.toSet().orEmpty()

private fun requireConnectionAttempted(telemetry: JsonObject) {
    val requests = telemetry["requests"]?.jsonArray.orEmpty()
    check(requests.isNotEmpty()) { "The journey Gateway recorded no application requests." }
    val paths = recordedRequestPaths(telemetry)
    check(paths.any { it.startsWith("/v1/") }) {
        "The application never issued a Gateway API request."
    }
}

private fun recordedRequestPaths(telemetry: JsonObject): List<String> =
    telemetry["requests"]?.jsonArray.orEmpty().mapNotNull { request ->
        request.jsonObject["path"]?.jsonPrimitive?.content
    }

private fun recordedRequestMethods(telemetry: JsonObject): List<String> =
    telemetry["requests"]?.jsonArray.orEmpty().mapNotNull { request ->
        request.jsonObject["method"]?.jsonPrimitive?.content
    }

internal object JourneyInvariants {
    fun verify(
        scenarioName: String,
        telemetry: JsonObject,
    ) {
        when (scenarioName) {
            "connection" -> requireConnectionAttempted(telemetry)
            "session-list-first" -> requireConnectionAttempted(telemetry)
            "empty-sessions" -> JourneySessionCreationInvariants.verifyEmptySessions(telemetry)
            "pending-creation" -> JourneySessionCreationInvariants.verifyPendingCreation(telemetry)
            "session-lifecycle" -> JourneySessionLifecycleInvariants.verifySessionLifecycle(telemetry)
            "pagination-search" -> requireConnectionAttempted(telemetry)
            "failed-offline-send" -> JourneyRunInvariants.verifyFailedOfflineSend(telemetry)
            "offline-session-list" -> {
                requireConnectionAttempted(telemetry)
                val paths = recordedRequestPaths(telemetry)
                check(paths.count { it == "/api/sessions" } >= 2) {
                    "The client did not retry the Session list while the Gateway was offline."
                }
                check(paths.any { it.startsWith("/api/sessions/") }) {
                    "The client did not attempt to open a Session while the Gateway was offline."
                }
            }
            "markdown-links" -> requireConnectionAttempted(telemetry)
            "accessibility-controls" -> requireConnectionAttempted(telemetry)
            "capabilities-additive" -> verifyCapabilitiesAdditive(telemetry)
            "capabilities-missing-required" -> requireConnectionAttempted(telemetry)
            else -> verifyAdditionalScenario(scenarioName, telemetry)
        }
    }

    private fun verifyAdditionalScenario(
        scenarioName: String,
        telemetry: JsonObject,
    ) {
        when (scenarioName) {
            "active-run-isolation" -> JourneyRunInvariants.verifyActiveRunIsolation(telemetry)
            "streaming" -> JourneyRunInvariants.verifyStreaming(telemetry)
            "live-streaming" -> JourneyRunInvariants.verifyLiveStreaming(telemetry)
            "interrupted-sse-refetch" -> JourneyRunInvariants.verifyInterruptedSseRefetch(telemetry)
            "terminal-success" -> JourneyRunInvariants.verifyTerminal(telemetry)
            "terminal-failure" -> JourneyRunInvariants.verifyTerminal(telemetry)
            "explicit-retry" -> JourneyRunInvariants.verifyExplicitRetry(telemetry)
            "markdown-blocks" -> requireConnectionAttempted(telemetry)
            "stale-recoverable" -> requireConnectionAttempted(telemetry)
            else -> error("No journey invariants declared for scenario '$scenarioName'.")
        }
    }

    private fun verifyCapabilitiesAdditive(telemetry: JsonObject) {
        requireConnectionAttempted(telemetry)
        check(runCreatesFor(telemetry, ACTIVE_SESSION_ID) == 1) {
            "The client submitted the additive Run more than once."
        }
    }

    const val ACTIVE_RUN_ID = "active-run"
    const val ACTIVE_SESSION_ID = "session-alpha"
    const val STREAMING_RUN_ID = "streaming-run"
    const val LIVE_RUN_ID = "live-run"
    const val INTERRUPTED_RUN_ID = "interrupted-run"
    const val RETRY_SESSION_ID = "session-retry"
}

private object JourneySessionCreationInvariants {
    internal fun verifyEmptySessions(telemetry: JsonObject) {
        requireConnectionAttempted(telemetry)
        val requests =
            telemetry.getValue("requests").jsonArray.map { request ->
                val fields = request.jsonObject
                fields.getValue("method").jsonPrimitive.content to fields.getValue("path").jsonPrimitive.content
            }
        val writes = requests.filter { (method, _) -> method != "GET" }
        check(writes == listOf("POST" to "/api/sessions")) {
            "The confirmed creation and its pre-submit dismissal did not write exactly one Session create: $writes"
        }
        val operations =
            telemetry.getValue("session_mutations").jsonArray.map { entry ->
                val fields = entry.jsonObject
                fields.getValue("operation").jsonPrimitive.content to
                    fields.getValue("session_id").jsonPrimitive.content
            }
        check(operations == listOf("create" to "synthetic-created-session-1")) {
            "The confirmed creation did not record exactly one create operation: $operations"
        }
        val runCreates = telemetry["run_creates"]?.jsonObject.orEmpty()
        check(runCreates.isEmpty()) { "The empty-sessions journey recorded a Run creation: $runCreates" }
        var writesSeen = 0
        val checkpoints =
            buildList {
                requests.forEach { (method, path) ->
                    if (method != "GET") {
                        writesSeen += 1
                    } else {
                        when (path) {
                            "/api/sessions" -> add("list" to writesSeen)
                        }
                    }
                }
            }
        check(checkpoints == listOf("list" to 0, "list" to 0, "list" to 1)) {
            "Empty-session cancellation checkpoints recorded missing reads or premature writes: $checkpoints"
        }
    }

    internal fun verifyPendingCreation(telemetry: JsonObject) {
        requireConnectionAttempted(telemetry)
        val requests =
            telemetry.getValue("requests").jsonArray.map { request ->
                val fields = request.jsonObject
                fields.getValue("method").jsonPrimitive.content to fields.getValue("path").jsonPrimitive.content
            }
        val writes = requests.filter { (method, _) -> method != "GET" }
        check(writes == listOf("POST" to "/api/sessions")) {
            "The pending creation did not resolve to exactly one Session create: $writes"
        }
        val operations =
            telemetry.getValue("session_mutations").jsonArray.map { entry ->
                val fields = entry.jsonObject
                fields.getValue("operation").jsonPrimitive.content to
                    fields.getValue("session_id").jsonPrimitive.content
            }
        check(operations == listOf("create" to "synthetic-created-session-1")) {
            "The pending creation did not record exactly one create operation: $operations"
        }
        check(requests.none { (_, path) -> path == "/v1/runs" || path.startsWith("/v1/runs/") }) {
            "The pending creation recorded Run traffic: $requests"
        }
        check(runStatusRequests(telemetry) == 0) {
            "The pending creation read Run status ${runStatusRequests(telemetry)} times; expected 0."
        }
        val runCreates = telemetry["run_creates"]?.jsonObject.orEmpty()
        check(runCreates.isEmpty()) { "The pending creation recorded a Run creation: $runCreates" }
        val sse = telemetry["sse_connections"]?.jsonObject.orEmpty()
        check(sse.isEmpty()) { "The pending creation opened a Run observation: $sse" }
        var writesSeen = 0
        val checkpoints =
            buildList {
                requests.forEach { (method, path) ->
                    if (method != "GET") {
                        writesSeen += 1
                    } else {
                        when (path) {
                            "/api/sessions" -> add("list" to writesSeen)
                        }
                    }
                }
            }
        check(checkpoints == listOf("list" to 0, "list" to 1)) {
            "Pending-creation reads recorded a dismissal reaction or a premature write: $checkpoints"
        }
    }
}

private object JourneySessionLifecycleInvariants {
    private val expectedSessionMutations =
        listOf(
            "POST" to "/api/sessions",
            "PATCH" to "/api/sessions/synthetic-created-session-1",
            "PATCH" to "/api/sessions/synthetic-created-session-1",
            "PATCH" to "/api/sessions/synthetic-created-session-1",
            "DELETE" to "/api/sessions/synthetic-created-session-1",
            "PATCH" to "/api/sessions/session-alpha",
            "PATCH" to "/api/sessions/session-alpha",
            "PATCH" to "/api/sessions/session-alpha",
            "DELETE" to "/api/sessions/session-alpha",
        )

    private val expectedSessionOperations =
        listOf(
            "create" to "synthetic-created-session-1",
            "rename" to "synthetic-created-session-1",
            "pin" to "synthetic-created-session-1",
            "unpin" to "synthetic-created-session-1",
            "delete" to "synthetic-created-session-1",
            "rename" to "session-alpha",
            "pin" to "session-alpha",
            "unpin" to "session-alpha",
            "delete" to "session-alpha",
        )

    private val expectedSessionCheckpoints =
        listOf(
            "list" to 0,
            "list" to 0,
            "list" to 0,
            "history" to 1,
            "list" to 1,
            "history" to 1,
            "list" to LIST_CHECKPOINT_AFTER_CREATED_SESSION_PIN,
            "list" to LIST_CHECKPOINT_AFTER_CREATED_SESSION_UNPIN,
            "list" to LIST_CHECKPOINT_AFTER_CREATED_SESSION_DELETE,
            "list" to LIST_CHECKPOINT_AFTER_ALPHA_SESSION_PIN,
            "list" to LIST_CHECKPOINT_AFTER_ALPHA_SESSION_UNPIN,
        )

    internal fun verifySessionLifecycle(telemetry: JsonObject) {
        requireConnectionAttempted(telemetry)
        val requests = recordedRequests(telemetry)
        val mutations = requests.filter { (method, _) -> method != "GET" }
        check(mutations == expectedSessionMutations) {
            "Session management writes did not match the explicitly confirmed journey actions: $mutations"
        }
        val operations = recordedSessionOperations(telemetry)
        check(operations == expectedSessionOperations) {
            "The confirmed Session operation types or targets did not match the journey."
        }
        val checkpoints = sessionReadCheckpoints(requests)
        check(checkpoints == expectedSessionCheckpoints) {
            "Session cancellation checkpoints recorded missing reads or premature writes: $checkpoints"
        }
    }

    private fun recordedRequests(telemetry: JsonObject): List<Pair<String, String>> =
        telemetry.getValue("requests").jsonArray.map { request ->
            val fields = request.jsonObject
            fields.getValue("method").jsonPrimitive.content to fields.getValue("path").jsonPrimitive.content
        }

    private fun recordedSessionOperations(telemetry: JsonObject): List<Pair<String, String>> =
        telemetry.getValue("session_mutations").jsonArray.map { entry ->
            val fields = entry.jsonObject
            fields.getValue("operation").jsonPrimitive.content to fields.getValue("session_id").jsonPrimitive.content
        }

    private fun sessionReadCheckpoints(requests: List<Pair<String, String>>): List<Pair<String, Int>> {
        var writesSeen = 0
        return buildList {
            requests.forEach { (method, path) ->
                if (method != "GET") {
                    writesSeen += 1
                } else {
                    when (path) {
                        "/api/sessions" -> add("list" to writesSeen)
                        "/api/sessions/synthetic-created-session-1/messages" -> add("history" to writesSeen)
                    }
                }
            }
        }
    }
}

private object JourneyRunInvariants {
    internal fun verifyActiveRunIsolation(telemetry: JsonObject) {
        val sse = sseFor(telemetry, JourneyInvariants.ACTIVE_RUN_ID)
        check(sse.opened == 2) {
            "Active Run was observed ${sse.opened} times; expected 2 (initial plus re-open, no refresh duplicate)."
        }
        check(sse.closed == 1) {
            "Active Run observation closed ${sse.closed} times; expected exactly 1 (Session switch)."
        }
        check(runStatusRequests(telemetry) >= 1) { "The client did not re-check the active Run status." }
        check(runCreatesFor(telemetry, JourneyInvariants.ACTIVE_SESSION_ID) == 1) {
            "The client submitted the active Run more than once."
        }
        check(recordedRequestPaths(telemetry).none { it.contains("cancel", ignoreCase = true) }) {
            "The client attempted to cancel the active Run while switching Sessions."
        }
        check(recordedRequestMethods(telemetry).none { it.equals("DELETE", ignoreCase = true) }) {
            "The client issued a DELETE request while switching Sessions."
        }
    }

    internal fun verifyStreaming(telemetry: JsonObject) {
        val sse = sseFor(telemetry, JourneyInvariants.STREAMING_RUN_ID)
        check(sse.opened == 1) { "Streaming Run was observed ${sse.opened} times; expected exactly 1." }
        check(runCreatesFor(telemetry, JourneyInvariants.ACTIVE_SESSION_ID) == 1) {
            "The client submitted the streaming Run more than once."
        }
    }

    internal fun verifyLiveStreaming(telemetry: JsonObject) {
        val sse = sseFor(telemetry, JourneyInvariants.LIVE_RUN_ID)
        check(sse.opened == 1) { "Live-framed Run was observed ${sse.opened} times; expected exactly 1." }
        check(runStatusRequests(telemetry) >= 1) { "The client did not reconcile the live-framed Run." }
        check(runCreatesFor(telemetry, JourneyInvariants.ACTIVE_SESSION_ID) == 1) {
            "The client submitted the live-framed Run more than once."
        }
    }

    internal fun verifyInterruptedSseRefetch(telemetry: JsonObject) {
        val sse = sseFor(telemetry, JourneyInvariants.INTERRUPTED_RUN_ID)
        check(sse.opened == 1) { "Interrupted Run was observed ${sse.opened} times; expected exactly 1." }
        check(JourneyInvariants.INTERRUPTED_RUN_ID in interruptedRunIds(telemetry)) {
            "The Gateway did not record the interrupted Run."
        }
        check(runStatusRequests(telemetry) >= 1) { "The client did not refetch authoritative Run status." }
        check(runCreatesFor(telemetry, JourneyInvariants.ACTIVE_SESSION_ID) == 1) {
            "The client re-submitted the interrupted Run."
        }
    }

    internal fun verifyTerminal(telemetry: JsonObject) {
        requireConnectionAttempted(telemetry)
        check(runStatusRequests(telemetry) >= 1) { "The client did not reconcile the terminal Run." }
        val submissions = runCreatesFor(telemetry, JourneyInvariants.ACTIVE_SESSION_ID)
        check(submissions == 1) {
            "The client submitted the terminal Run $submissions times; " +
                "expected exactly 1 (no duplicate retry submissions)."
        }
    }

    internal fun verifyExplicitRetry(telemetry: JsonObject) {
        check(runCreatesFor(telemetry, JourneyInvariants.RETRY_SESSION_ID) == 2) {
            "The client did not submit exactly two Run creations (initial run plus one explicit retry)."
        }
        check(runStatusRequests(telemetry) >= 1) { "The client did not reconcile Run status." }
    }

    internal fun verifyFailedOfflineSend(telemetry: JsonObject) {
        requireConnectionAttempted(telemetry)
        check(runCreatesFor(telemetry, OFFLINE_SEND_SESSION_ID) == 2) {
            "The client did not submit exactly two Run creations (one refused while offline plus one recovery retry)."
        }
        check(runStatusRequests(telemetry) >= 1) { "The client did not reconcile the recovered Run." }
    }

    private const val OFFLINE_SEND_SESSION_ID = "session-offline"
}

private const val MIN_ARGUMENT_COUNT = 2
private const val MAX_ARGUMENT_COUNT = 4
private const val KEYSTORE_PASSWORD_ARG_INDEX = 3
private const val TELEMETRY_TIMEOUT_MILLIS = 5_000
private const val HTTP_OK = 200
private const val LIST_CHECKPOINT_AFTER_CREATED_SESSION_PIN = 3
private const val LIST_CHECKPOINT_AFTER_CREATED_SESSION_UNPIN = 4
private const val LIST_CHECKPOINT_AFTER_CREATED_SESSION_DELETE = 5
private const val LIST_CHECKPOINT_AFTER_ALPHA_SESSION_PIN = 7
private const val LIST_CHECKPOINT_AFTER_ALPHA_SESSION_UNPIN = 8
