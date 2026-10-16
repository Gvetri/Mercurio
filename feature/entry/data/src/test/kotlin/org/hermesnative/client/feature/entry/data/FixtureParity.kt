package org.hermesnative.client.feature.entry.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.URI

internal const val LIVE_PARITY_DIR_PROPERTY = "live.fixture.parity.artifactDir"
internal const val LIVE_PARITY_DEFAULT_DIR = "build/fixture-parity"
internal const val LIVE_PARITY_ARTIFACT_FILE_NAME = "fixture-parity.jsonl"
internal const val DRIFT_ISSUE_REPOSITORY = "Gvetri/hermes-native-client"
internal const val LIVE_SESSION_PARITY_CAPTURE_SINK = "live-session-matrix-parity-captures"
internal const val LIVE_RUN_PARITY_CAPTURE_SINK = "live-run-matrix-parity-captures"
internal const val PARITY_CONTRACTS_ROOT = "fixtures/hermes/contracts"

internal fun parityArtifactDirectory(): File =
    System
        .getProperty(LIVE_PARITY_DIR_PROPERTY)
        ?.let(::File)
        ?: File(System.getProperty("user.dir"), LIVE_PARITY_DEFAULT_DIR)

internal fun runFixtureParityStep(repositoryRoot: File): List<FixtureParityEntry> =
    runFixtureParityStep(repositoryRoot, parityArtifactDirectory())

internal fun runFixtureParityStep(
    repositoryRoot: File,
    artifactDirectory: File,
): List<FixtureParityEntry> {
    val captures =
        listOf(LIVE_SESSION_PARITY_CAPTURE_SINK, LIVE_RUN_PARITY_CAPTURE_SINK).flatMap { name ->
            val captureFile = File(artifactDirectory, "$name.jsonl")
            if (captureFile.isFile) FixtureParity.readCaptures(captureFile) else emptyList()
        }
    val entries = FixtureParity.compare(captures, repositoryRoot.resolve(PARITY_CONTRACTS_ROOT))
    FixtureParity.writeArtifact(entries, File(artifactDirectory, LIVE_PARITY_ARTIFACT_FILE_NAME))
    val ledger = LocalIssueLedger(File(artifactDirectory, DRIFT_LEDGER_FILE_NAME), repositoryRoot)
    DriftProtocol.reconcile(entries, ledger)
    if (System.getProperty(LIVE_FIXTURE_FILE_DRIFT_ISSUES_PROPERTY) == "true") {
        DriftIssueFiler(DRIFT_ISSUE_REPOSITORY, ledger).fileOpenIssues(::ghIssueRunner)
    }
    return entries
}

internal data class LiveParityCapture(
    val operation: String,
    val method: String,
    val path: String,
    val queryNames: List<String>,
    val requestBodyJson: String?,
    val status: Int?,
    val responseJson: String?,
    val sseLines: List<String>,
)

internal data class FixtureParityEntry(
    val operation: String,
    val fixture: String,
    val method: String,
    val pathShape: String,
    val expectedStatus: Int,
    val observedStatus: Int?,
    val verdict: String,
    val observedDifference: String?,
)

internal object FixtureParity {
    const val VERDICT_MATCH = "match"
    const val VERDICT_MISMATCH = "mismatch"

    fun compare(
        captures: List<LiveParityCapture>,
        contractsRoot: File,
    ): List<FixtureParityEntry> {
        val capturesByOperation = captures.groupBy { it.operation }
        return operationMappings.flatMap { (operation, mapping) ->
            compareOperation(operation, mapping, capturesByOperation[operation].orEmpty(), contractsRoot)
        }
    }

    fun readCaptures(captureFile: File): List<LiveParityCapture> =
        captureFile.readLines().filter(String::isNotBlank).map { line ->
            val root = Json.parseToJsonElement(line).jsonObject
            LiveParityCapture(
                operation = root.getValue("operation").jsonPrimitive.content,
                method = root.getValue("method").jsonPrimitive.content,
                path = root.getValue("path").jsonPrimitive.content,
                queryNames = root.getValue("queryNames").jsonArray.map { it.jsonPrimitive.content },
                requestBodyJson = root["requestBody"]?.jsonPrimitive?.contentOrNull,
                status = root["status"]?.jsonPrimitive?.contentOrNull?.toIntOrNull(),
                responseJson = root["responseBody"]?.jsonPrimitive?.contentOrNull,
                sseLines = root.getValue("sseLines").jsonArray.map { it.jsonPrimitive.content },
            )
        }

    fun writeArtifact(
        entries: List<FixtureParityEntry>,
        artifactFile: File,
    ) {
        artifactFile.parentFile.mkdirs()
        artifactFile.writeText(entries.joinToString(separator = "") { artifactLine(it) })
    }

    fun requireMatchVerdicts(
        entries: List<FixtureParityEntry>,
        expectedPairs: Set<Pair<String, String>>,
    ) {
        expectedPairs.forEach { (operation, fixture) ->
            val entry = entries.firstOrNull { it.operation == operation && it.fixture == fixture }
            requireNotNull(entry) { "The parity artifact has no entry for $operation against $fixture." }
            check(entry.verdict == VERDICT_MATCH) {
                "Parity mismatch for $operation against $fixture: ${entry.observedDifference.orEmpty()}"
            }
        }
    }

    private fun compareOperation(
        operation: String,
        mapping: OperationParityMapping,
        captures: List<LiveParityCapture>,
        contractsRoot: File,
    ): List<FixtureParityEntry> =
        when {
            captures.isEmpty() -> emptyList()
            mapping.responseFixtures.size == 1 ->
                listOf(parityEntry(operation, mapping, mapping.responseFixtures.single(), captures, contractsRoot))
            captures.size == mapping.responseFixtures.size ->
                mapping.responseFixtures.mapIndexed { index, fixture ->
                    parityEntry(operation, mapping, fixture, listOf(captures[index]), contractsRoot)
                }
            else ->
                mapping.responseFixtures.map { fixture ->
                    captureCountMismatchEntry(operation, mapping, fixture, captures, contractsRoot)
                }
        }

    private fun captureCountMismatchEntry(
        operation: String,
        mapping: OperationParityMapping,
        fixture: String,
        captures: List<LiveParityCapture>,
        contractsRoot: File,
    ): FixtureParityEntry =
        FixtureParityEntry(
            operation = operation,
            fixture = fixture,
            method = requestMethod(mapping, contractsRoot),
            pathShape = normalizedPathShape(requestPathShape(mapping, contractsRoot)),
            expectedStatus = expectedStatus(mapping, contractsRoot),
            observedStatus = null,
            verdict = VERDICT_MISMATCH,
            observedDifference =
                "capture count ${captures.size} does not match the " +
                    "${mapping.responseFixtures.size} fixture variants",
        )

    private fun parityEntry(
        operation: String,
        mapping: OperationParityMapping,
        fixturePath: String,
        captures: List<LiveParityCapture>,
        contractsRoot: File,
    ): FixtureParityEntry {
        val firstDifference =
            captures
                .mapNotNull { capture -> captureDifference(capture, mapping, fixturePath, contractsRoot) }
                .firstOrNull()
        return FixtureParityEntry(
            operation = operation,
            fixture = fixturePath,
            method = requestMethod(mapping, contractsRoot),
            pathShape = normalizedPathShape(requestPathShape(mapping, contractsRoot)),
            expectedStatus = expectedStatus(mapping, contractsRoot),
            observedStatus = captures.first().status,
            verdict = if (firstDifference == null) VERDICT_MATCH else VERDICT_MISMATCH,
            observedDifference = firstDifference,
        )
    }

    private fun captureDifference(
        capture: LiveParityCapture,
        mapping: OperationParityMapping,
        fixturePath: String,
        contractsRoot: File,
    ): String? {
        val requestDifference =
            compareRequest(capture, requestFixtureObject(mapping, contractsRoot), mapping.requestPathShape)
        val statusDifference = statusDifference(capture, mapping, contractsRoot).takeIf { requestDifference == null }
        val bodyDifference =
            responseDifference(capture, mapping, fixturePath, contractsRoot)
                .takeIf { requestDifference == null && statusDifference == null }
        return requestDifference ?: statusDifference ?: bodyDifference
    }

    private fun statusDifference(
        capture: LiveParityCapture,
        mapping: OperationParityMapping,
        contractsRoot: File,
    ): String? {
        val expected = expectedStatus(mapping, contractsRoot)
        return if (capture.status == expected) null else "response.status: fixture $expected, live ${capture.status}"
    }

    private fun responseDifference(
        capture: LiveParityCapture,
        mapping: OperationParityMapping,
        fixturePath: String,
        contractsRoot: File,
    ): String? =
        if (fixturePath.endsWith(SSE_FIXTURE_SUFFIX)) {
            compareStreamShapes(capture.sseLines, contractsRoot.resolve(fixturePath).readLines())
        } else {
            compareJsonResponse(capture, fixturePath, contractsRoot, mapping.comparedResponseKeys)
        }

    private fun compareJsonResponse(
        capture: LiveParityCapture,
        fixturePath: String,
        contractsRoot: File,
        comparedKeys: Set<String>?,
    ): String? {
        val fixtureBody =
            Json
                .parseToJsonElement(contractsRoot.resolve(fixturePath).readText())
                .jsonObject
                .getValue("response")
                .jsonObject
                .getValue("body")
        val liveBody =
            capture.responseJson
                ?.let { body -> runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull() }
        return when {
            liveBody == null -> "response.body: live body is not a JSON object"
            else ->
                compareShape(
                    comparableBody(liveBody, comparedKeys),
                    comparableBody(fixtureBody, comparedKeys),
                    "response.body",
                    "",
                )
        }
    }

    private fun comparableBody(
        body: JsonElement,
        comparedKeys: Set<String>?,
    ): JsonElement =
        if (comparedKeys == null || body !is JsonObject) {
            body
        } else {
            JsonObject(body.filterKeys { key -> key in comparedKeys })
        }

    private fun compareRequest(
        capture: LiveParityCapture,
        fixtureRequest: JsonObject?,
        requestPathShape: String?,
    ): String? {
        val method = fixtureRequest?.get("method")?.jsonPrimitive?.content ?: "GET"
        val fixturePath = requestPathShape ?: fixtureRequest?.get("path")?.jsonPrimitive?.content.orEmpty()
        val expectedQuery = fixtureRequest?.get("query")?.jsonObject?.keys?.sorted().orEmpty()
        val observedQuery = capture.queryNames.sorted()
        val methodDifference =
            if (capture.method == method) {
                null
            } else {
                "request.method: fixture $method, live ${capture.method}"
            }
        val pathDifference =
            if (normalizedPathShape(capture.path) == normalizedPathShape(fixturePath)) {
                null
            } else {
                "request.path: fixture $fixturePath, live ${capture.path}"
            }
        val queryDifference =
            if (expectedQuery == observedQuery) {
                null
            } else {
                "request.query: fixture $expectedQuery, live $observedQuery"
            }
        val bodyDifference = requestBodyDifference(capture, fixtureRequest)
        return methodDifference ?: pathDifference ?: queryDifference ?: bodyDifference
    }

    private fun requestBodyDifference(
        capture: LiveParityCapture,
        fixtureRequest: JsonObject?,
    ): String? {
        val fixtureBody = fixtureRequest?.get("body")?.jsonObject
        val liveBody =
            capture.requestBodyJson
                ?.let { body -> runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull() }
        return when {
            fixtureBody == null && liveBody == null -> null
            fixtureBody == null -> "request.body: fixture absent, live present"
            liveBody == null -> "request.body: fixture present, live absent"
            else -> compareShape(liveBody, fixtureBody, "request.body", "")
        }
    }

    private fun compareStreamShapes(
        liveLines: List<String>,
        fixtureLines: List<String>,
    ): String? {
        val liveByEvent = visibleFrames(liveLines).groupBy { it.first }
        val fixtureByEvent = visibleFrames(fixtureLines).groupBy { it.first }
        val absentEvent = liveByEvent.keys.sorted().firstOrNull { eventName -> eventName !in fixtureByEvent }
        if (absentEvent != null) {
            return "response.stream: event type '$absentEvent' is delivered to the client but absent from the fixture"
        }
        return liveByEvent.keys.sorted().firstNotNullOfOrNull { eventName ->
            compareEventFrames(
                eventName,
                liveByEvent.getValue(eventName).map { it.second },
                fixtureByEvent.getValue(eventName).map { it.second },
            )
        }
    }

    private fun compareEventFrames(
        eventName: String,
        liveFrames: List<JsonObject>,
        fixtureFrames: List<JsonObject>,
    ): String? =
        liveFrames.withIndex().firstNotNullOfOrNull { (liveIndex, liveFrame) ->
            val matchesFixture =
                fixtureFrames.any { fixtureFrame -> compareShape(liveFrame, fixtureFrame, eventName, "") == null }
            if (matchesFixture) {
                null
            } else {
                val firstDifference = compareShape(liveFrame, fixtureFrames.first(), eventName, "")
                "response.stream.$eventName: live frame $liveIndex " +
                    "does not match any fixture frame shape: $firstDifference"
            }
        }

    private fun visibleFrames(lines: List<String>): List<Pair<String, JsonObject>> =
        GatewaySseParser
            .frames(lines.asSequence(), PARITY_OBSERVATION_OPERATION)
            .mapNotNull { frame ->
                val root = runCatching { Json.parseToJsonElement(frame.data).jsonObject }.getOrNull()
                val eventName = root?.get("event")?.jsonPrimitive?.contentOrNull
                val delivered =
                    runCatching { GatewayJsonParser.parseRunEvent(PARITY_OBSERVATION_OPERATION, frame) }.getOrNull()
                if (root == null || eventName == null || delivered == null) null else eventName to root
            }.toList()

    private fun compareShape(
        live: JsonElement,
        fixture: JsonElement,
        path: String,
        key: String,
    ): String? =
        when {
            live is JsonObject && fixture is JsonObject -> compareObjects(live, fixture, path)
            live is JsonArray && fixture is JsonArray -> compareArrays(live, fixture, path, key)
            live is JsonObject || fixture is JsonObject || live is JsonArray || fixture is JsonArray ->
                "$path: fixture ${describe(fixture)}, live ${describe(live)}"
            else -> {
                val liveShape = normalizedLeaf(key, live)
                val fixtureShape = normalizedLeaf(key, fixture)
                if (liveShape == fixtureShape) null else "$path: fixture $fixtureShape, live $liveShape"
            }
        }

    private fun compareObjects(
        live: JsonObject,
        fixture: JsonObject,
        path: String,
    ): String? {
        val liveOnlyKey =
            live.keys.sorted().firstOrNull { key ->
                fixture[key] == null && !toleratesMissingFixtureKey(key, live.getValue(key))
            }
        val fixtureOnlyKey =
            fixture.keys.sorted().firstOrNull { key -> live[key] == null && key !in FIXTURE_ONLY_EXEMPT_FIELDS }
        val nestedDifference =
            live.keys.sorted().firstNotNullOfOrNull { key ->
                fixture[key]?.let { fixtureValue -> compareShape(live.getValue(key), fixtureValue, "$path.$key", key) }
            }
        return when {
            liveOnlyKey != null -> "$path: live-only key '$liveOnlyKey'"
            fixtureOnlyKey != null -> "$path: fixture-only key '$fixtureOnlyKey'"
            else -> nestedDifference
        }
    }

    private fun compareArrays(
        live: JsonArray,
        fixture: JsonArray,
        path: String,
        key: String,
    ): String? {
        val unmatched =
            live.withIndex().firstOrNull { (index, liveItem) ->
                live.isNotEmpty() &&
                    fixture.isNotEmpty() &&
                    fixture.none { fixtureItem -> compareShape(liveItem, fixtureItem, "$path[$index]", key) == null }
            }
        val firstDifference =
            unmatched?.let { (index, liveItem) ->
                compareShape(liveItem, fixture.first(), "$path[$index]", key)
            }
        val difference =
            unmatched?.let { (index, _) ->
                "$path: live item $index does not match any fixture item shape: $firstDifference"
            }
        return difference
    }

    private fun toleratesMissingFixtureKey(
        key: String,
        value: JsonElement,
    ): Boolean =
        when {
            key in CONTENT_FIELDS -> value is JsonNull || (value is JsonPrimitive && value.isString)
            key in PROVENANCE_FIELDS -> value is JsonPrimitive && value.isString
            key in IDENTITY_FIELDS -> value is JsonPrimitive
            key in TIMESTAMP_FIELDS -> value is JsonPrimitive
            else -> false
        }

    private fun normalizedLeaf(
        key: String,
        element: JsonElement,
    ): String =
        when {
            element is JsonPrimitive && !element.isString && element.content in BOOLEAN_STATES -> BOOLEAN_PLACEHOLDER
            element is JsonPrimitive -> normalizedPrimitive(key, element)
            else -> element.toString()
        }

    private fun normalizedPrimitive(
        key: String,
        element: JsonPrimitive,
    ): String =
        when {
            key in IDENTITY_FIELDS -> IDENTITY_PLACEHOLDER
            key in TIMESTAMP_FIELDS -> TIMESTAMP_PLACEHOLDER
            key in COUNT_FIELDS && !element.isString -> COUNT_PLACEHOLDER
            key in CONTENT_FIELDS && (element is JsonNull || element.isString) -> CONTENT_PLACEHOLDER
            key in PROVENANCE_FIELDS && element.isString -> PROVENANCE_PLACEHOLDER
            else -> element.toString()
        }

    private fun describe(element: JsonElement): String =
        when (element) {
            is JsonObject -> "object"
            is JsonArray -> "array"
            is JsonPrimitive -> "scalar"
        }

    private fun normalizedPathShape(path: String): String =
        path
            .split('/')
            .joinToString("/") { segment ->
                if (segment.isEmpty() || segment in STATIC_PATH_SEGMENTS) segment else PATH_PARAMETER_PLACEHOLDER
            }

    private fun requestFixtureObject(
        mapping: OperationParityMapping,
        contractsRoot: File,
    ): JsonObject? =
        mapping.requestFixture?.let { path ->
            Json
                .parseToJsonElement(contractsRoot.resolve(path).readText())
                .jsonObject
                .getValue("request")
                .jsonObject
        }

    private fun requestMethod(
        mapping: OperationParityMapping,
        contractsRoot: File,
    ): String = requestFixtureObject(mapping, contractsRoot)?.get("method")?.jsonPrimitive?.content ?: "GET"

    private fun requestPathShape(
        mapping: OperationParityMapping,
        contractsRoot: File,
    ): String =
        mapping.requestPathShape
            ?: requestFixtureObject(mapping, contractsRoot)?.get("path")?.jsonPrimitive?.content.orEmpty()

    private fun expectedStatus(
        mapping: OperationParityMapping,
        contractsRoot: File,
    ): Int =
        mapping.expectedStatus
            ?: mapping.responseFixtures
                .first()
                .let { path ->
                    Json
                        .parseToJsonElement(contractsRoot.resolve(path).readText())
                        .jsonObject
                        .getValue("response")
                        .jsonObject
                        .getValue("status")
                        .jsonPrimitive
                        .content
                }.toInt()

    private fun artifactLine(entry: FixtureParityEntry): String =
        JsonObject(
            mapOf(
                "operation" to JsonPrimitive(entry.operation),
                "fixture" to JsonPrimitive(entry.fixture),
                "method" to JsonPrimitive(entry.method),
                "pathShape" to JsonPrimitive(entry.pathShape),
                "expectedStatus" to JsonPrimitive(entry.expectedStatus),
                "observedStatus" to (entry.observedStatus?.let(::JsonPrimitive) ?: JsonNull),
                "verdict" to JsonPrimitive(entry.verdict),
                "observedDifference" to (entry.observedDifference?.let(::JsonPrimitive) ?: JsonNull),
            ),
        ).toString() + "\n"

    private data class OperationParityMapping(
        val responseFixtures: List<String>,
        val requestFixture: String?,
        val requestPathShape: String? = null,
        val expectedStatus: Int? = null,
        val comparedResponseKeys: Set<String>? = null,
    )

    private const val SSE_FIXTURE_SUFFIX = ".sse"
    private const val PARITY_OBSERVATION_OPERATION = "fixture parity run observation"
    private const val IDENTITY_PLACEHOLDER = "<id>"
    private const val TIMESTAMP_PLACEHOLDER = "<timestamp>"
    private const val COUNT_PLACEHOLDER = "<count>"
    private const val CONTENT_PLACEHOLDER = "<text>"
    private const val PROVENANCE_PLACEHOLDER = "<provenance>"
    private const val BOOLEAN_PLACEHOLDER = "<bool>"
    private const val PATH_PARAMETER_PLACEHOLDER = "{param}"

    private val BOOLEAN_STATES = setOf("true", "false")

    private val STATIC_PATH_SEGMENTS = setOf("api", "sessions", "v1", "runs", "messages", "capabilities", "health")

    private val IDENTITY_FIELDS = setOf("id", "run_id", "session_id")
    private val TIMESTAMP_FIELDS = setOf("last_active", "created_at", "updated_at", "timestamp")
    private val COUNT_FIELDS =
        setOf("limit", "offset", "returned", "message_count", "input_tokens", "output_tokens", "total_tokens")
    private val CONTENT_FIELDS = setOf("title", "preview", "content", "delta", "output", "input")
    private val FIXTURE_ONLY_EXEMPT_FIELDS =
        setOf(
            "last_active",
            "created_at",
            "output",
            "usage",
            "session_id",
            "runtime",
            "tool_progress_events",
            "approval_events",
            "session_continuity_header",
            "session_key_header",
        )
    private val PROVENANCE_FIELDS = setOf("model")

    private val operationMappings =
        linkedMapOf(
            "capability discovery" to
                OperationParityMapping(
                    listOf("capabilities/success.json"),
                    "capabilities/request.json",
                ),
            "session list" to
                OperationParityMapping(
                    listOf("sessions/list-response-page-1.json", "sessions/list-response-page-2.json"),
                    "sessions/list-request.json",
                ),
            "session create" to
                OperationParityMapping(
                    listOf("sessions/create-response.json"),
                    "sessions/create-request.json",
                ),
            "session open" to
                OperationParityMapping(
                    listOf("sessions/open-response.json"),
                    "sessions/open-request.json",
                ),
            "session rename" to
                OperationParityMapping(
                    listOf("sessions/rename-response.json"),
                    "sessions/rename-request.json",
                ),
            "session pin" to
                OperationParityMapping(
                    listOf("sessions/pin-response.json"),
                    "sessions/pin-request.json",
                ),
            "session unpin" to
                OperationParityMapping(
                    listOf("sessions/unpin-response.json"),
                    "sessions/unpin-request.json",
                ),
            "session history" to
                OperationParityMapping(
                    listOf("sessions/history-response.json", "sessions/history-response-populated.json"),
                    "sessions/history-request.json",
                ),
            "session delete" to
                OperationParityMapping(
                    listOf("sessions/delete-response.json"),
                    "sessions/delete-request.json",
                ),
            "run create" to
                OperationParityMapping(
                    listOf("runs/create-response.json"),
                    "runs/create-request.json",
                ),
            "run status" to
                OperationParityMapping(
                    listOf("runs/status-response.json"),
                    "runs/status-request.json",
                ),
            "run observation" to
                OperationParityMapping(
                    listOf("runs/observation.sse"),
                    requestFixture = null,
                    requestPathShape = "/v1/runs/{run_id}/events",
                    expectedStatus = 200,
                ),
        )
}

internal class LiveParityCaptureTransport(
    private val delegate: GatewayTransport,
) : GatewayTransport {
    private val pending = ArrayDeque<PendingExchange>()

    override fun execute(request: GatewayHttpRequest): GatewayHttpResponse {
        val response = delegate.execute(request)
        pending += pendingExchange(request, response.statusCode, response.body)
        return response
    }

    override fun openEventStream(request: GatewayHttpRequest): GatewayEventStream {
        val stream = delegate.openEventStream(request)
        val exchange = pendingExchange(request, stream.statusCode, null)
        pending += exchange
        val teedLines = stream.lines.onEach { line -> exchange.sseLines += line }
        return GatewayEventStream(stream.statusCode, teedLines) { stream.close() }
    }

    fun drain(operation: String): LiveParityCapture {
        check(pending.size == 1) {
            "The lane drains exactly one wire exchange per operation, found ${pending.size} for $operation."
        }
        val exchange = pending.removeFirst()
        val uri = URI(exchange.url)
        return LiveParityCapture(
            operation = operation,
            method = exchange.method,
            path = uri.path,
            queryNames = uri.rawQuery?.split('&')?.map { it.substringBefore('=') }.orEmpty(),
            requestBodyJson = exchange.requestBody,
            status = exchange.status,
            responseJson = exchange.responseBody,
            sseLines = exchange.sseLines.toList(),
        )
    }

    private fun pendingExchange(
        request: GatewayHttpRequest,
        status: Int,
        responseBody: String?,
    ): PendingExchange =
        PendingExchange(
            method = request.method,
            url = request.url,
            requestBody = request.body,
            status = status,
            responseBody = responseBody,
            sseLines = mutableListOf(),
        )

    private data class PendingExchange(
        val method: String,
        val url: String,
        val requestBody: String?,
        val status: Int,
        val responseBody: String?,
        val sseLines: MutableList<String>,
    )
}

internal class LiveParityCaptureRecorder(
    sink: File,
    private val workspaceRoot: File,
) {
    private val sinkFile: File

    init {
        sink.parentFile.mkdirs()
        sink.delete()
        sinkFile = sink
    }

    fun record(capture: LiveParityCapture) {
        sinkFile.appendText(EvidenceRedaction.redact(captureLine(capture), workspaceRoot) + "\n")
    }

    private fun captureLine(capture: LiveParityCapture): String =
        JsonObject(
            mapOf(
                "operation" to JsonPrimitive(capture.operation),
                "method" to JsonPrimitive(capture.method),
                "path" to JsonPrimitive(capture.path),
                "queryNames" to JsonArray(capture.queryNames.map(::JsonPrimitive)),
                "requestBody" to (capture.requestBodyJson?.let(::JsonPrimitive) ?: JsonNull),
                "status" to (capture.status?.let(::JsonPrimitive) ?: JsonNull),
                "responseBody" to (capture.responseJson?.let(::JsonPrimitive) ?: JsonNull),
                "sseLines" to JsonArray(capture.sseLines.map(::JsonPrimitive)),
            ),
        ).toString()
}
