package org.hermesnative.client.feature.entry.data

import org.hermesnative.client.feature.entry.domain.GatewayErrorCategory
import org.hermesnative.client.feature.entry.domain.GatewayException
import org.hermesnative.client.feature.entry.domain.PublicBetaGatewayCapabilityManifest
import org.hermesnative.client.feature.entry.domain.Session
import org.hermesnative.client.feature.entry.domain.SessionId
import org.hermesnative.client.feature.entry.domain.SessionListRequest
import org.hermesnative.client.fixture.DeterministicGatewayFixture
import org.hermesnative.client.fixture.GatewayProcessFactory
import org.hermesnative.client.fixture.journey.JourneyGatewayProcess
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.URI

class LiveGatewaySessionAcceptanceTest {
    private val repositoryRoot =
        File(
            requireNotNull(System.getProperty("fixture.repositoryRoot")) {
                "fixture.repositoryRoot must identify the repository root"
            },
        )

    @Test
    fun refuses_to_target_a_gateway_other_than_the_one_it_started() {
        val startedEndpoint = URI.create("https://127.0.0.1:49152")

        StartedGatewayGuard.requireStartedLoopbackEndpoint(URI.create("http://127.0.0.1:49152"), startedEndpoint)

        val foreignContext = URI.create("http://192.0.2.1:49152")
        val foreignHostFailure =
            assertThrows(IllegalArgumentException::class.java) {
                StartedGatewayGuard.requireStartedLoopbackEndpoint(foreignContext, startedEndpoint)
            }
        assertTrue(foreignHostFailure.message.orEmpty().contains("loopback"))

        val foreignPortContext = URI.create("http://127.0.0.1:49153")
        val foreignPortFailure =
            assertThrows(IllegalArgumentException::class.java) {
                StartedGatewayGuard.requireStartedLoopbackEndpoint(foreignPortContext, startedEndpoint)
            }
        assertTrue(foreignPortFailure.message.orEmpty().contains("port"))

        val insecureProcessFailure =
            assertThrows(IllegalArgumentException::class.java) {
                StartedGatewayGuard.requireStartedLoopbackEndpoint(
                    URI.create("http://127.0.0.1:49152"),
                    URI.create("http://127.0.0.1:49152"),
                )
            }
        assertTrue(insecureProcessFailure.message.orEmpty().contains("https"))
    }

    @Test
    fun session_matrix_executes_every_operation_against_the_started_gateway() {
        val evidence = LiveSessionMatrixRecorder(evidenceSink("live-session-matrix"), repositoryRoot)
        val run = liveMatrixRun()

        run.fixture.execute { context ->
            StartedGatewayGuard.requireStartedLoopbackEndpoint(context.endpoint, run.process.endpoint)
            val client = laneClient(run, LANE_BEARER_TOKEN)

            val capabilities = client.discoverCapabilities()
            evidence.record(
                verifiedEvidence(
                    operation = "capability discovery",
                    method = "GET",
                    pathShape = "/v1/capabilities",
                    expectedStatus = HTTP_OK,
                    envelope = CAPABILITY_DISCOVERY_ENVELOPE,
                ),
            )
            val advertisedEndpoints = capabilities.endpoints.keys
            val requiredEndpoints = PublicBetaGatewayCapabilityManifest.current.requiredEndpoints.keys
            assertTrue(advertisedEndpoints.containsAll(requiredEndpoints))

            verifyPagedSessionList(client, evidence)
            verifyCreatedSessionLifecycle(client, evidence)
        }

        val evidenceText = evidence.file.readText()
        assertEquals(MATRIX_ENTRY_COUNT, evidence.file.readLines().size)
        assertTrue(evidenceText.contains("\"outcome\":\"verified\""))
        assertTrue(evidenceText.lines().filter { it.isNotBlank() }.all { it.contains("\"expectedStatus\":") })
        assertFalse(evidenceText.contains(LANE_BEARER_TOKEN))
        assertFalse(evidenceText.contains(CREATED_SESSION_ID))
        assertFalse(evidenceText.contains(SEEDED_PINNED_SESSION_ID))
        assertFalse(evidenceText.contains(LANE_CREATED_TITLE))
        assertFalse(evidenceText.contains(SEEDED_FIRST_MESSAGE_CONTENT))
    }

    @Test
    fun failing_session_operation_surfaces_its_gateway_error_category() {
        val evidence = LiveSessionMatrixRecorder(evidenceSink("live-session-matrix-failing-operations"), repositoryRoot)
        val run = liveMatrixRun()

        run.fixture.execute { _ ->
            val rejectedCredentials =
                gatewayFailure { laneClient(run, WRONG_BEARER_TOKEN).discoverCapabilities() }
            assertEquals(GatewayErrorCategory.AUTHENTICATION_FAILED, rejectedCredentials.category)
            evidence.record(
                failedEvidence(
                    operation = "capability discovery",
                    method = "GET",
                    pathShape = "/v1/capabilities",
                    expectedStatus = HTTP_OK,
                    envelope = CAPABILITY_DISCOVERY_ENVELOPE,
                    errorCategory = GatewayErrorCategory.AUTHENTICATION_FAILED.name,
                ),
            )

            val client = laneClient(run, LANE_BEARER_TOKEN)
            val created = client.createSession(LANE_CREATED_TITLE)
            client.deleteSession(created.id)
            val deletedSessionFailure = gatewayFailure { client.openSession(created.id) }
            assertEquals(GatewayErrorCategory.GATEWAY_REQUEST_FAILED, deletedSessionFailure.category)
            evidence.record(
                failedEvidence(
                    operation = "session open",
                    method = "GET",
                    pathShape = "/api/sessions/{session_id}",
                    expectedStatus = HTTP_OK,
                    envelope = SESSION_ENVELOPE,
                    errorCategory = GatewayErrorCategory.GATEWAY_REQUEST_FAILED.name,
                ),
            )
        }

        val evidenceText = evidence.file.readText()
        assertEquals(FAILED_OPERATION_ENTRY_COUNT, evidence.file.readLines().size)
        assertTrue(evidenceText.contains("\"outcome\":\"failed\""))
        assertTrue(evidenceText.contains("\"errorCategory\":\"${GatewayErrorCategory.AUTHENTICATION_FAILED.name}\""))
        assertTrue(evidenceText.contains("\"errorCategory\":\"${GatewayErrorCategory.GATEWAY_REQUEST_FAILED.name}\""))
    }

    @Test
    fun evidence_for_failed_operations_never_contains_the_bearer_value() {
        val evidence = LiveSessionMatrixRecorder(evidenceSink("live-session-matrix-redaction"), repositoryRoot)
        val run = liveMatrixRun()

        run.fixture.execute { _ ->
            val client = laneClient(run, LANE_BEARER_TOKEN)
            client.createSession(LANE_CREATED_TITLE)
            evidence.record(
                verifiedEvidence(
                    operation = "session create",
                    method = "POST",
                    pathShape = "/api/sessions",
                    expectedStatus = HTTP_CREATED,
                    envelope = SESSION_ENVELOPE,
                ),
            )

            val rejectedCredentials =
                gatewayFailure { laneClient(run, WRONG_BEARER_TOKEN).discoverCapabilities() }
            assertEquals(GatewayErrorCategory.AUTHENTICATION_FAILED, rejectedCredentials.category)
            evidence.record(
                failedEvidence(
                    operation = "capability discovery",
                    method = "GET",
                    pathShape = "/v1/capabilities",
                    expectedStatus = HTTP_OK,
                    envelope = CAPABILITY_DISCOVERY_ENVELOPE,
                    errorCategory = GatewayErrorCategory.AUTHENTICATION_FAILED.name,
                ),
            )
        }

        val evidenceText = evidence.file.readText()
        assertTrue(evidenceText.contains("\"outcome\":\"failed\""))
        assertTrue(evidenceText.contains("\"errorCategory\":\"${GatewayErrorCategory.AUTHENTICATION_FAILED.name}\""))
        assertFalse(evidenceText.contains(LANE_BEARER_TOKEN))
        assertFalse(evidenceText.contains(WRONG_BEARER_TOKEN))
    }

    @Test
    fun evidence_sink_redacts_sensitive_values_before_writing() {
        val recorder = LiveSessionMatrixRecorder(evidenceSink("live-session-matrix-redaction-seam"), repositoryRoot)

        recorder.record(
            verifiedEvidence(
                operation = "redaction sanity probe",
                method = "GET",
                pathShape = "/api/sessions",
                expectedStatus = HTTP_OK,
                envelope =
                    mapOf(
                        "authorization" to "Bearer $LANE_BEARER_TOKEN",
                        "workspace" to repositoryRoot.absolutePath,
                    ),
            ),
        )

        val sinkText = recorder.file.readText()
        assertFalse(sinkText.contains(LANE_BEARER_TOKEN))
        assertFalse(sinkText.contains(repositoryRoot.absolutePath))
        assertTrue(sinkText.contains("\"authorization\":\"<redacted>\""))
        assertTrue(sinkText.contains("\"workspace\":\"<workspace>\""))
    }

    private fun verifyPagedSessionList(
        client: DefaultGatewayClient,
        evidence: LiveSessionMatrixRecorder,
    ) {
        val firstPage = client.listSessions(SessionListRequest(limit = LANE_PAGE_LIMIT))
        evidence.record(
            verifiedEvidence(
                operation = "session list",
                method = "GET",
                pathShape = "/api/sessions",
                expectedStatus = HTTP_OK,
                envelope = SESSION_LIST_ENVELOPE,
                queryShape = listOf("limit", "offset"),
            ),
        )
        assertEquals(
            listOf(SEEDED_PINNED_SESSION_ID, SEEDED_OPEN_SESSION_ID),
            firstPage.sessions.map { it.id.value },
        )
        assertEquals(LANE_PAGE_SIZE, firstPage.nextOffset)

        val secondPage =
            client.listSessions(
                SessionListRequest(limit = LANE_PAGE_LIMIT, offset = requireNotNull(firstPage.nextOffset)),
            )
        evidence.record(
            verifiedEvidence(
                operation = "session list",
                method = "GET",
                pathShape = "/api/sessions",
                expectedStatus = HTTP_OK,
                envelope = SESSION_LIST_ENVELOPE,
                queryShape = listOf("limit", "offset"),
            ),
        )
        assertEquals(listOf(SEEDED_THIRD_SESSION_ID), secondPage.sessions.map { it.id.value })
        assertNull(secondPage.nextOffset)
    }

    private fun verifyCreatedSessionLifecycle(
        client: DefaultGatewayClient,
        evidence: LiveSessionMatrixRecorder,
    ) {
        val created = client.createSession(LANE_CREATED_TITLE)
        evidence.record(
            verifiedEvidence(
                operation = "session create",
                method = "POST",
                pathShape = "/api/sessions",
                expectedStatus = HTTP_CREATED,
                envelope = SESSION_ENVELOPE,
            ),
        )
        assertEquals(CREATED_SESSION_ID, created.id.value)
        assertEquals(LANE_CREATED_TITLE, created.title)
        assertNull(created.preview)
        assertFalse(created.pinned)

        val opened = client.openSession(created.id)
        evidence.record(sessionResourceEvidence("session open"))
        assertEquals(CREATED_SESSION_ID, opened.id.value)
        assertEquals(LANE_CREATED_TITLE, opened.title)

        val renamed = client.renameSession(created.id, LANE_RENAMED_TITLE)
        evidence.record(sessionResourceEvidence("session rename"))
        assertEquals(LANE_RENAMED_TITLE, renamed.title)

        val pinned = client.pinSession(created.id)
        evidence.record(sessionResourceEvidence("session pin"))
        assertEquals(created.id, pinned.sessionId)
        assertTrue(pinned.pinned)

        val unpinned = client.unpinSession(created.id)
        evidence.record(sessionResourceEvidence("session unpin"))
        assertFalse(unpinned.pinned)

        verifySessionHistory(client, created, evidence)

        client.deleteSession(created.id)
        evidence.record(
            verifiedEvidence(
                operation = "session delete",
                method = "DELETE",
                pathShape = "/api/sessions/{session_id}",
                expectedStatus = HTTP_OK,
                envelope = SESSION_DELETED_ENVELOPE,
            ),
        )
    }

    private fun verifySessionHistory(
        client: DefaultGatewayClient,
        created: Session,
        evidence: LiveSessionMatrixRecorder,
    ) {
        val createdHistory = client.loadSessionHistory(created.id)
        evidence.record(
            verifiedEvidence(
                operation = "session history",
                method = "GET",
                pathShape = "/api/sessions/{session_id}/messages",
                expectedStatus = HTTP_OK,
                envelope = EMPTY_SESSION_HISTORY_ENVELOPE,
            ),
        )
        assertEquals(created.id, createdHistory.sessionId)
        assertTrue(createdHistory.messages.isEmpty())

        val seededHistory = client.loadSessionHistory(SessionId(SEEDED_PINNED_SESSION_ID))
        evidence.record(
            verifiedEvidence(
                operation = "session history",
                method = "GET",
                pathShape = "/api/sessions/{session_id}/messages",
                expectedStatus = HTTP_OK,
                envelope = POPULATED_SESSION_HISTORY_ENVELOPE,
            ),
        )
        assertEquals(SEEDED_PINNED_SESSION_ID, seededHistory.sessionId.value)
        assertEquals(
            listOf(SEEDED_FIRST_MESSAGE_ID, SEEDED_SECOND_MESSAGE_ID),
            seededHistory.messages.map { it.id },
        )
        assertEquals(listOf("user", "assistant"), seededHistory.messages.map { it.role })
        assertEquals(SEEDED_SECOND_MESSAGE_CONTENT, seededHistory.messages.last().content)
    }

    private fun sessionResourceEvidence(operation: String): LiveSessionMatrixEvidence =
        verifiedEvidence(
            operation = operation,
            method = "PATCH",
            pathShape = "/api/sessions/{session_id}",
            expectedStatus = HTTP_OK,
            envelope = SESSION_ENVELOPE,
        )

    private fun laneClient(
        run: LiveSessionMatrixRun,
        bearerToken: String,
    ): DefaultGatewayClient =
        DefaultGatewayClient(
            endpoint = run.process.endpoint.toString(),
            bearerToken = bearerToken,
            transport = OkHttpGatewayTransport(run.tlsClient),
        )

    private fun liveMatrixRun(): LiveSessionMatrixRun {
        var startedProcess: JourneyGatewayProcess? = null
        val tlsClient = journeyTrustedClient(repositoryRoot.resolve(JOURNEY_CA_PEM_PATH))
        val fixture =
            DeterministicGatewayFixture(
                descriptorFile = repositoryRoot.resolve(PINNED_DESCRIPTOR_PATH),
                processFactory =
                    GatewayProcessFactory { descriptor ->
                        val scenario =
                            laneScenario(descriptor, port = freeLoopbackPort(), bearerToken = LANE_BEARER_TOKEN)
                        val process =
                            JourneyGatewayProcess.start(
                                scenario,
                                repositoryRoot.resolve(JOURNEY_KEYSTORE_PATH),
                                JOURNEY_KEYSTORE_PASSWORD,
                            )
                        startedProcess = process
                        LoopbackHttpViewProcess(process)
                    },
                readinessChecker = TlsLoopbackReadinessChecker(tlsClient, LANE_BEARER_TOKEN),
            )
        return LiveSessionMatrixRun(fixture, tlsClient) {
            requireNotNull(startedProcess) { "The lane journey process has not started." }
        }
    }

    private fun gatewayFailure(block: () -> Unit): GatewayException =
        try {
            block()
            error("Expected the operation to fail with a GatewayException.")
        } catch (failure: GatewayException) {
            failure
        }

    private fun evidenceSink(name: String): File = File(evidenceDirectory(), "$name.jsonl")

    private companion object {
        const val LANE_BEARER_TOKEN = "live-session-matrix-bearer"
        const val WRONG_BEARER_TOKEN = "wrong-bearer-value"
        const val LANE_PAGE_LIMIT = 10
        const val LANE_PAGE_SIZE = 2
        const val LANE_CREATED_TITLE = "Live lane created session"
        const val LANE_RENAMED_TITLE = "Live lane renamed session"
        const val CREATED_SESSION_ID = "synthetic-created-session-1"
        private const val MATRIX_ENTRY_COUNT = 11
        const val FAILED_OPERATION_ENTRY_COUNT = 2
        const val HTTP_OK = 200
        const val HTTP_CREATED = 201

        val CAPABILITY_DISCOVERY_ENVELOPE =
            mapOf(
                "object" to "string",
                "platform" to "string",
                "model" to "string",
                "auth" to "object",
                "features" to "object",
                "endpoints" to "object",
            )

        val SESSION_LIST_ENVELOPE =
            mapOf(
                "object" to "string",
                "data" to "array",
                "limit" to "number",
                "offset" to "number",
                "has_more" to "boolean",
            )

        val SESSION_ENVELOPE =
            mapOf(
                "object" to "string",
                "session.id" to "string",
                "session.source" to "string",
                "session.title" to "string-or-null",
                "session.preview" to "string-or-null",
                "session.pinned" to "boolean",
                "session.message_count" to "number",
            )

        val EMPTY_SESSION_HISTORY_ENVELOPE =
            mapOf(
                "object" to "string",
                "session_id" to "string",
                "data" to "array",
                "pagination" to "object",
            )

        val POPULATED_SESSION_HISTORY_ENVELOPE =
            mapOf(
                "object" to "string",
                "session_id" to "string",
                "data" to "array",
                "data[].id" to "string",
                "data[].role" to "string-or-null",
                "data[].content" to "string-or-null",
                "data[].timestamp" to "string-or-null",
                "pagination" to "object",
            )

        val SESSION_DELETED_ENVELOPE =
            mapOf(
                "object" to "string",
                "id" to "string",
                "deleted" to "boolean",
            )
    }
}
