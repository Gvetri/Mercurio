package org.hermesnative.client.feature.entry.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.util.TreeMap

class FixtureParityStepTest {
    private val repositoryRoot =
        File(
            requireNotNull(System.getProperty("fixture.repositoryRoot")) {
                "fixture.repositoryRoot must identify the repository root"
            },
        )
    private val contractsRoot = repositoryRoot.resolve("fixtures/hermes/contracts")

    @Test
    fun parity_step_reports_match_for_every_lane_operation_capture_set() {
        val entries = FixtureParity.compare(laneCaptureSet(), contractsRoot)

        assertEquals(LANE_PARITY_ENTRY_COUNT, entries.size)
        assertEquals(EXPECTED_OPERATION_FIXTURE_PAIRS, operationFixturePairs(entries))
        val mismatches = entries.filter { it.verdict != FixtureParity.VERDICT_MATCH }
        val mismatchReport = mismatches.map { "${it.operation}/${it.fixture}: ${it.observedDifference}" }
        assertTrue(
            "Unexpected parity mismatches: $mismatchReport",
            mismatches.isEmpty(),
        )
        val discovery = entries.first { it.operation == "capability discovery" }
        assertEquals("GET", discovery.method)
        assertEquals("/v1/capabilities", discovery.pathShape)
        assertEquals(200, discovery.expectedStatus)
        assertEquals(200, discovery.observedStatus)
        assertEquals("capabilities/success.json", discovery.fixture)
    }

    @Test
    fun parity_verdicts_are_stable_across_repeated_runs() {
        val first = FixtureParity.compare(laneCaptureSet(), contractsRoot)
        val second = FixtureParity.compare(laneCaptureSet(), contractsRoot)

        assertEquals(first, second)
    }

    @Test
    fun seeded_mismatch_is_reported_and_changes_no_fixture_parser_or_manifest() {
        val guardedPaths = guardedSourcePaths()
        val before = digests(guardedPaths)
        val mismatched =
            laneCaptureSet().map { capture ->
                if (capture.operation == "session open") {
                    val wrongObject =
                        LIVE_SESSION_OPEN_ENVELOPE.replace("\"hermes.session\"", "\"hermes.session.wrong\"")
                    capture.copy(responseJson = wrongObject)
                } else {
                    capture
                }
            }

        val entries = FixtureParity.compare(mismatched, contractsRoot)

        val reported = entries.first { it.operation == "session open" }
        assertEquals(FixtureParity.VERDICT_MISMATCH, reported.verdict)
        assertEquals("sessions/open-response.json", reported.fixture)
        assertTrue(reported.observedDifference.orEmpty().contains("object"))
        assertEquals(LANE_PARITY_ENTRY_COUNT, entries.size)
        assertEquals(EXPECTED_OPERATION_FIXTURE_PAIRS, operationFixturePairs(entries))
        assertEquals(before, digests(guardedPaths))
    }

    @Test
    fun normalization_equates_volatile_shapes_between_live_and_fixture_envelopes() {
        val liveShapedCapture =
            LiveParityCapture(
                operation = "session open",
                method = "GET",
                path = "/api/sessions/synthetic-created-session-1",
                queryNames = emptyList(),
                requestBodyJson = null,
                status = 200,
                responseJson = LIVE_SESSION_OPEN_ENVELOPE,
                sseLines = emptyList(),
            )

        val entries = FixtureParity.compare(listOf(liveShapedCapture), contractsRoot)

        assertEquals(FixtureParity.VERDICT_MATCH, entries.single().verdict)
        val typedMismatch =
            LiveParityCapture(
                operation = "session open",
                method = "GET",
                path = "/api/sessions/synthetic-created-session-1",
                queryNames = emptyList(),
                requestBodyJson = null,
                status = 200,
                responseJson = LIVE_SESSION_OPEN_ENVELOPE.replace("\"pinned\":false", "\"pinned\":\"false\""),
                sseLines = emptyList(),
            )
        val mismatchEntries = FixtureParity.compare(listOf(typedMismatch), contractsRoot)

        assertEquals(FixtureParity.VERDICT_MISMATCH, mismatchEntries.single().verdict)
        assertTrue(mismatchEntries.single().observedDifference.orEmpty().contains("pinned"))
    }

    @Test
    fun parity_step_persists_and_reloads_captures_without_loss() {
        val captureFile = File(scratchDirectory(), "parity-captures.jsonl")
        val recorder = LiveParityCaptureRecorder(captureFile, repositoryRoot)
        val captures = laneCaptureSet()

        captures.forEach(recorder::record)
        val reloaded = FixtureParity.readCaptures(captureFile)

        assertEquals(captures, reloaded)
    }

    private fun operationFixturePairs(entries: List<FixtureParityEntry>): Set<Pair<String, String>> =
        entries.map { it.operation to it.fixture }.toSet()

    private fun guardedSourcePaths(): List<File> =
        buildList {
            addAll(contractsRoot.walkTopDown().filter { it.isFile })
            add(repositoryRoot.resolve(PARSER_SOURCE_PATH))
            add(repositoryRoot.resolve(MANIFEST_SOURCE_PATH))
        }.sortedBy { it.path }

    private fun digests(paths: List<File>) =
        TreeMap(
            paths.associate { path ->
                path.relativeTo(repositoryRoot).path to
                    MessageDigest.getInstance("SHA-256").digest(path.readBytes()).joinToString("") { "%02x".format(it) }
            },
        )

    private fun scratchDirectory(): File {
        val directory = File(System.getProperty("java.io.tmpdir"), "fixture-parity-step-test")
        directory.mkdirs()
        return directory
    }

    private fun laneCaptureSet(): List<LiveParityCapture> = sessionHalfCaptures() + runHalfCaptures()

    private fun sessionHalfCaptures(): List<LiveParityCapture> = sessionDiscoveryCaptures() + sessionLifecycleCaptures()

    private fun sessionDiscoveryCaptures(): List<LiveParityCapture> =
        listOf(
            capture("capability discovery", "GET", "/v1/capabilities", 200, LIVE_CAPABILITIES_ENVELOPE),
            capture("session list", "GET", "/api/sessions", 200, LIVE_SESSION_LIST_PAGE_ONE)
                .copy(queryNames = listOf("limit", "offset")),
            capture("session list", "GET", "/api/sessions", 200, LIVE_SESSION_LIST_PAGE_TWO)
                .copy(queryNames = listOf("limit", "offset")),
        )

    private fun sessionLifecycleCaptures(): List<LiveParityCapture> =
        listOf(
            capture("session create", "POST", "/api/sessions", 201, LIVE_SESSION_CREATED_ENVELOPE)
                .copy(requestBodyJson = SESSION_CREATE_REQUEST_BODY),
            capture("session open", "GET", CREATED_SESSION_PATH, 200, LIVE_SESSION_OPEN_ENVELOPE),
            capture("session rename", "PATCH", CREATED_SESSION_PATH, 200, LIVE_SESSION_RENAMED_ENVELOPE)
                .copy(requestBodyJson = """{"title":"Live lane renamed session"}"""),
            capture("session pin", "PATCH", CREATED_SESSION_PATH, 200, LIVE_SESSION_PINNED_ENVELOPE)
                .copy(requestBodyJson = """{"pinned":true}"""),
            capture("session unpin", "PATCH", CREATED_SESSION_PATH, 200, LIVE_SESSION_UNPINNED_ENVELOPE)
                .copy(requestBodyJson = """{"pinned":false}"""),
            capture("session history", "GET", CREATED_HISTORY_PATH, 200, LIVE_SESSION_HISTORY_EMPTY),
            capture("session history", "GET", SEEDED_HISTORY_PATH, 200, LIVE_SESSION_HISTORY_POPULATED),
            capture("session delete", "DELETE", CREATED_SESSION_PATH, 200, LIVE_SESSION_DELETED_ENVELOPE),
        )

    private fun runHalfCaptures(): List<LiveParityCapture> = runCreationCaptures() + runObservationCaptures()

    private fun runCreationCaptures(): List<LiveParityCapture> =
        listOf(
            capture("run create", "POST", "/v1/runs", 202, RUN_DELTA_ADMISSION_ENVELOPE)
                .copy(requestBodyJson = RUN_LANE_REQUEST_BODY),
            capture("run status", "GET", "/v1/runs/lane-delta-run", 200, LIVE_RUN_STATUS_STARTED),
            capture("run create", "POST", "/v1/runs", 202, RUN_FRAMING_ADMISSION_ENVELOPE)
                .copy(requestBodyJson = RUN_LANE_REQUEST_BODY),
            capture("run create", "POST", "/v1/runs", 202, RUN_HOLD_OPEN_ADMISSION_ENVELOPE)
                .copy(requestBodyJson = RUN_LANE_REQUEST_BODY),
            capture("run create", "POST", "/v1/runs", 202, RUN_UNMAPPED_ADMISSION_ENVELOPE)
                .copy(requestBodyJson = RUN_LANE_REQUEST_BODY),
        )

    private fun runObservationCaptures(): List<LiveParityCapture> =
        listOf(
            capture("run status", "GET", "/v1/runs/lane-delta-run", 200, LIVE_RUN_STATUS_SUCCEEDED),
            capture("run observation", "GET", "/v1/runs/lane-delta-run/events", 200)
                .copy(
                    sseLines =
                        listOf(
                            """data: {"event":"message.delta","run_id":"lane-delta-run","delta":"Hello "}""",
                            "",
                            """data: {"event":"message.delta","run_id":"lane-delta-run","delta":"from "}""",
                            "",
                            """data: {"event":"message.delta","run_id":"lane-delta-run","delta":"the lane."}""",
                            "",
                            """data: {"event":"run.completed","run_id":"lane-delta-run"}""",
                            "",
                        ),
                ),
            capture("run observation", "GET", "/v1/runs/lane-framing-run/events", 200)
                .copy(
                    sseLines =
                        listOf(
                            ": open",
                            "id: 0",
                            """data: {"event":"message.delta","run_id":"lane-framing-run","delta":"Framed delta"}""",
                            "",
                            "id: 1",
                            """data: {"event":"run.completed","run_id":"lane-framing-run"}""",
                            "",
                            ": stream closed",
                        ),
                ),
            capture("run observation", "GET", "/v1/runs/lane-hold-open-run/events", 200)
                .copy(
                    sseLines =
                        listOf(
                            """data: {"event":"message.delta","run_id":"lane-hold-open-run","delta":"Held delta"}""",
                            "",
                            """data: {"event":"run.completed","run_id":"lane-hold-open-run"}""",
                            "",
                            ": keep-alive",
                        ),
                ),
            capture("run observation", "GET", "/v1/runs/lane-unmapped-run/events", 200)
                .copy(
                    sseLines =
                        listOf(
                            """data: {"event":"metrics.heartbeat","run_id":"lane-unmapped-run"}""",
                            "",
                            """data: {"event":"run.completed","run_id":"lane-unmapped-run"}""",
                            "",
                        ),
                ),
        )

    private fun capture(
        operation: String,
        method: String,
        path: String,
        status: Int,
        responseJson: String? = null,
    ) = LiveParityCapture(
        operation = operation,
        method = method,
        path = path,
        queryNames = emptyList(),
        requestBodyJson = null,
        status = status,
        responseJson = responseJson,
        sseLines = emptyList(),
    )

    private companion object {
        const val LANE_PARITY_ENTRY_COUNT = 14
        const val PARSER_SOURCE_PATH =
            "feature/entry/data/src/main/kotlin/org/hermesnative/client/feature/entry/data/GatewayJsonParser.kt"
        const val MANIFEST_SOURCE_PATH =
            "feature/entry/domain/src/main/kotlin/org/hermesnative/client/feature/entry/domain" +
                "/GatewayCapabilityManifest.kt"

        const val SESSION_CREATE_REQUEST_BODY = """{"title":"Live lane created session"}"""

        const val CREATED_SESSION_PATH = "/api/sessions/synthetic-created-session-1"
        const val CREATED_HISTORY_PATH = "$CREATED_SESSION_PATH/messages"
        const val SEEDED_HISTORY_PATH = "/api/sessions/seeded-pinned-session/messages"

        const val RUN_LANE_REQUEST_BODY =
            """{"input":"Run lane input for the run matrix","session_id":"synthetic-created-session-1"}"""

        const val RUN_DELTA_ADMISSION_ENVELOPE =
            """{"run_id":"lane-delta-run","status":"started","replayed":false}"""
        const val RUN_FRAMING_ADMISSION_ENVELOPE =
            """{"run_id":"lane-framing-run","status":"started","replayed":false}"""
        const val RUN_HOLD_OPEN_ADMISSION_ENVELOPE =
            """{"run_id":"lane-hold-open-run","status":"running","replayed":false}"""
        const val RUN_UNMAPPED_ADMISSION_ENVELOPE =
            """{"run_id":"lane-unmapped-run","status":"started","replayed":false}"""

        val EXPECTED_OPERATION_FIXTURE_PAIRS =
            setOf(
                "capability discovery" to "capabilities/success.json",
                "session list" to "sessions/list-response-page-1.json",
                "session list" to "sessions/list-response-page-2.json",
                "session create" to "sessions/create-response.json",
                "session open" to "sessions/open-response.json",
                "session rename" to "sessions/rename-response.json",
                "session pin" to "sessions/pin-response.json",
                "session unpin" to "sessions/unpin-response.json",
                "session history" to "sessions/history-response.json",
                "session history" to "sessions/history-response-populated.json",
                "session delete" to "sessions/delete-response.json",
                "run create" to "runs/create-response.json",
                "run status" to "runs/status-response.json",
                "run observation" to "runs/observation.sse",
            )

        val LIVE_CAPABILITIES_ENVELOPE =
            """
            {"object":"hermes.api_server.capabilities","platform":"hermes-agent","model":"synthetic",
            "auth":{"type":"bearer","required":true},
            "features":{"run_submission":true,"run_status":true,"run_events_sse":true,"session_resources":true},
            "endpoints":{
            "health":{"method":"GET","path":"/health"},
            "sessions":{"method":"GET","path":"/api/sessions"},
            "session_create":{"method":"POST","path":"/api/sessions"},
            "session":{"method":"GET","path":"/api/sessions/{session_id}"},
            "session_update":{"method":"PATCH","path":"/api/sessions/{session_id}"},
            "session_delete":{"method":"DELETE","path":"/api/sessions/{session_id}"},
            "session_messages":{"method":"GET","path":"/api/sessions/{session_id}/messages"},
            "runs":{"method":"POST","path":"/v1/runs"},
            "run_status":{"method":"GET","path":"/v1/runs/{run_id}"},
            "run_events":{"method":"GET","path":"/v1/runs/{run_id}/events"}}}
            """.trimIndent()

        val LIVE_SESSION_LIST_PAGE_ONE =
            """
            {"object":"list","data":[
            {"id":"seeded-session-1","source":"api_server","title":null,"preview":null,"pinned":false,"message_count":0}],
            "limit":20,"offset":0,"has_more":true}
            """.trimIndent()

        val LIVE_SESSION_LIST_PAGE_TWO =
            """
            {"object":"list","data":[],"limit":20,"offset":20,"has_more":false}
            """.trimIndent()

        val LIVE_SESSION_CREATED_ENVELOPE =
            """
            {"object":"hermes.session","session":{
            "id":"synthetic-created-session-1","source":"api_server","title":"Live lane created session","preview":null,"pinned":false,"message_count":0}}
            """.trimIndent()

        val LIVE_SESSION_OPEN_ENVELOPE =
            """
            {"object":"hermes.session","session":{
            "id":"synthetic-created-session-1","source":"api_server","title":"Live lane created session","preview":null,"pinned":false,"message_count":0}}
            """.trimIndent()

        val LIVE_SESSION_RENAMED_ENVELOPE =
            """
            {"object":"hermes.session","session":{
            "id":"synthetic-created-session-1","source":"api_server","title":"Live lane renamed session","preview":null,"pinned":false,"message_count":0}}
            """.trimIndent()

        val LIVE_SESSION_PINNED_ENVELOPE =
            """
            {"object":"hermes.session","session":{
            "id":"synthetic-created-session-1","source":"api_server","title":"Live lane renamed session","preview":null,"pinned":true,"message_count":0}}
            """.trimIndent()

        val LIVE_SESSION_UNPINNED_ENVELOPE =
            """
            {"object":"hermes.session","session":{
            "id":"synthetic-created-session-1","source":"api_server","title":"Live lane renamed session","preview":null,"pinned":false,"message_count":0}}
            """.trimIndent()

        val LIVE_SESSION_HISTORY_EMPTY =
            """
            {"object":"list","session_id":"synthetic-created-session-1","data":[],
            "pagination":{"limit":500,"offset":0,"order":"latest","returned":0}}
            """.trimIndent()

        val LIVE_SESSION_HISTORY_POPULATED =
            """
            {"object":"list","session_id":"seeded-pinned-session","data":[
            {"id":"seeded-message-1","role":"user","content":"Seeded user message for the live lane"},
            {"id":"seeded-message-2","role":"assistant","content":"Seeded assistant reply for the live lane","timestamp":"1757325600.0"}],
            "pagination":{"limit":500,"offset":0,"order":"latest","returned":2}}
            """.trimIndent()

        val LIVE_SESSION_DELETED_ENVELOPE =
            """
            {"object":"hermes.session.deleted","id":"synthetic-created-session-1","deleted":true}
            """.trimIndent()

        val LIVE_RUN_STATUS_STARTED =
            """
            {"object":"hermes.run","run_id":"lane-delta-run","session_id":"synthetic-created-session-1","status":"started","updated_at":1757325600.0}
            """.trimIndent()

        val LIVE_RUN_STATUS_SUCCEEDED =
            """
            {"object":"hermes.run","run_id":"lane-delta-run","session_id":"synthetic-created-session-1","status":"succeeded","updated_at":1757325600.0}
            """.trimIndent()
    }
}
