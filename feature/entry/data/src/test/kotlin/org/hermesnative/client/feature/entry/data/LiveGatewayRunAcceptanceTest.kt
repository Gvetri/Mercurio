package org.hermesnative.client.feature.entry.data

import org.hermesnative.client.feature.entry.domain.GatewayErrorCategory
import org.hermesnative.client.feature.entry.domain.GatewayException
import org.hermesnative.client.feature.entry.domain.Run
import org.hermesnative.client.feature.entry.domain.RunEvent
import org.hermesnative.client.feature.entry.domain.RunEventObservation
import org.hermesnative.client.feature.entry.domain.RunEventType
import org.hermesnative.client.feature.entry.domain.RunId
import org.hermesnative.client.feature.entry.domain.SessionId
import org.hermesnative.client.fixture.DeterministicGatewayFixture
import org.hermesnative.client.fixture.GatewayProcessFactory
import org.hermesnative.client.fixture.journey.JourneyGatewayProcess
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LiveGatewayRunAcceptanceTest {
    private val repositoryRoot =
        File(
            requireNotNull(System.getProperty("fixture.repositoryRoot")) {
                "fixture.repositoryRoot must identify the repository root"
            },
        )

    @Test
    fun run_matrix_executes_every_run_operation_against_the_started_gateway() {
        val evidence = LiveSessionMatrixRecorder(evidenceSink("live-run-matrix"), repositoryRoot)
        val run = liveRunMatrixRun()

        run.fixture.execute { context ->
            StartedGatewayGuard.requireStartedLoopbackEndpoint(context.endpoint, run.process.endpoint)
            val client = laneClient(run, RUN_LANE_BEARER_TOKEN)
            val ephemeralSession = client.createSession(RUN_LANE_SESSION_TITLE)
            assertEquals(RUN_LANE_CREATED_SESSION_ID, ephemeralSession.id.value)

            verifyDeltaRun(client, evidence)
            verifyLiveFramingRun(client, evidence)
            verifyHeldOpenRun(client, evidence)
            verifyUnmappedEventRun(client, evidence)
            verifyRefusedRunCreation(client, evidence)
        }

        val evidenceText = evidence.file.readText()
        assertEquals(RUN_MATRIX_ENTRY_COUNT, evidence.file.readLines().size)
        assertTrue(evidenceText.contains("\"outcome\":\"verified\""))
        assertTrue(evidenceText.contains("\"outcome\":\"failed\""))
        assertTrue(evidenceText.contains("\"errorCategory\":\"${GatewayErrorCategory.GATEWAY_REQUEST_FAILED.name}\""))
        assertTrue(evidenceText.contains("\"streamSummary\":{\"eventsByType\""))
        assertTrue(
            evidenceText.lines().filter { it.isNotBlank() }.all {
                it.contains("\"operation\":\"run ") && it.contains("\"expectedStatus\":")
            },
        )
        assertFalse(evidenceText.contains(RUN_LANE_BEARER_TOKEN))
        assertFalse(evidenceText.contains(RUN_LANE_SESSION_TITLE))
        assertFalse(evidenceText.contains(RUN_LANE_INPUT))
        assertFalse(evidenceText.contains(RUN_LANE_EXPECTED_RESPONSE))
        assertFalse(evidenceText.contains(RUN_LANE_FRAMING_DELTA))
        assertFalse(evidenceText.contains(RUN_LANE_HOLD_OPEN_DELTA))
    }

    @Test
    fun malformed_run_streams_surface_invalid_response_at_the_client_seam() {
        val evidence =
            LiveSessionMatrixRecorder(evidenceSink("live-run-matrix-malformed-streams"), repositoryRoot)

        val missingDeltaTransport =
            SeamedEventStreamTransport(
                listOf(
                    "data: {\"event\":\"message.delta\",\"run_id\":\"$RUN_LANE_SEAM_RUN_ID\",\"status\":\"running\"}",
                    "",
                ),
            )
        val missingDeltaFailure =
            gatewayFailure { seamClient(missingDeltaTransport).observeRun(RunId(RUN_LANE_SEAM_RUN_ID)).toList() }
        assertEquals(GatewayErrorCategory.INVALID_RESPONSE, missingDeltaFailure.category)
        assertTrue(missingDeltaTransport.eventStreamClosed)
        evidence.record(
            failedEvidence(
                operation = "run observation",
                method = "GET",
                pathShape = "/v1/runs/{run_id}/events",
                expectedStatus = HTTP_OK,
                envelope = RUN_OBSERVATION_ENVELOPE,
                errorCategory = GatewayErrorCategory.INVALID_RESPONSE.name,
            ),
        )

        val mixedRecordTransport =
            SeamedEventStreamTransport(
                listOf(
                    "event: message.delta",
                    "data: {\"event\":\"message.delta\",\"run_id\":\"$RUN_LANE_SEAM_RUN_ID\"}",
                    "",
                ),
            )
        val mixedRecordFailure =
            gatewayFailure { seamClient(mixedRecordTransport).observeRun(RunId(RUN_LANE_SEAM_RUN_ID)).toList() }
        assertEquals(GatewayErrorCategory.INVALID_RESPONSE, mixedRecordFailure.category)
        assertTrue(mixedRecordTransport.eventStreamClosed)
        evidence.record(
            failedEvidence(
                operation = "run observation",
                method = "GET",
                pathShape = "/v1/runs/{run_id}/events",
                expectedStatus = HTTP_OK,
                envelope = RUN_OBSERVATION_ENVELOPE,
                errorCategory = GatewayErrorCategory.INVALID_RESPONSE.name,
            ),
        )

        val evidenceText = evidence.file.readText()
        assertEquals(MALFORMED_STREAM_ENTRY_COUNT, evidence.file.readLines().size)
        assertTrue(
            evidenceText.lines().filter { it.isNotBlank() }.all {
                it.contains("\"errorCategory\":\"${GatewayErrorCategory.INVALID_RESPONSE.name}\"")
            },
        )
        assertFalse(evidenceText.contains(RUN_LANE_SEAM_RUN_ID))
    }

    private fun verifyDeltaRun(
        client: DefaultGatewayClient,
        evidence: LiveSessionMatrixRecorder,
    ) {
        val admitted = createRun(client, evidence)
        assertEquals(RUN_LANE_DELTA_RUN_ID, admitted.id.value)

        verifyRunStatus(client, admitted, RUN_LANE_CREATED_SESSION_ID, "started", evidence)

        val events = client.observeRun(admitted.id).toList()
        assertEquals(RUN_LANE_EXPECTED_RESPONSE, deliveredDeltas(events).joinToString(separator = ""))
        assertEquals(RunEventType.COMPLETED, events.last().type)
        evidence.record(
            verifiedRunObservationEvidence(
                operation = "run observation",
                method = "GET",
                pathShape = "/v1/runs/{run_id}/events",
                expectedStatus = HTTP_OK,
                envelope = RUN_OBSERVATION_ENVELOPE,
                streamSummary =
                    RunStreamSummary(
                        eventsByType = eventsByWireName(events),
                        deltaCount = DELTA_RUN_DELTA_COUNT,
                        terminalEvent = TERMINAL_EVENT_NAME,
                        unmappedFramesSkipped = 0,
                        idLines = 0,
                        boundaryComments = 0,
                        keepAliveComments = false,
                    ),
            ),
        )

        verifyRunStatus(client, admitted, RUN_LANE_CREATED_SESSION_ID, "succeeded", evidence)
    }

    private fun verifyLiveFramingRun(
        client: DefaultGatewayClient,
        evidence: LiveSessionMatrixRecorder,
    ) {
        val admitted = createRun(client, evidence)
        assertEquals(RUN_LANE_FRAMING_RUN_ID, admitted.id.value)

        val events = client.observeRun(admitted.id).toList()
        assertEquals(listOf(RUN_LANE_FRAMING_DELTA), deliveredDeltas(events))
        assertEquals(RunEventType.COMPLETED, events.last().type)
        evidence.record(
            verifiedRunObservationEvidence(
                operation = "run observation",
                method = "GET",
                pathShape = "/v1/runs/{run_id}/events",
                expectedStatus = HTTP_OK,
                envelope = RUN_OBSERVATION_ENVELOPE,
                streamSummary =
                    RunStreamSummary(
                        eventsByType = eventsByWireName(events),
                        deltaCount = FRAMING_RUN_DELTA_COUNT,
                        terminalEvent = TERMINAL_EVENT_NAME,
                        unmappedFramesSkipped = 0,
                        idLines = FRAMING_RUN_EVENT_COUNT,
                        boundaryComments = FRAMING_RUN_BOUNDARY_COMMENTS,
                        keepAliveComments = false,
                    ),
            ),
        )
    }

    private fun verifyHeldOpenRun(
        client: DefaultGatewayClient,
        evidence: LiveSessionMatrixRecorder,
    ) {
        val admitted = createRun(client, evidence)
        assertEquals(RUN_LANE_HOLD_OPEN_RUN_ID, admitted.id.value)

        val observation = client.observeRun(admitted.id)
        val events = collectEventsUntilTerminal(observation)
        observation.close()
        assertEquals(listOf(RUN_LANE_HOLD_OPEN_DELTA), deliveredDeltas(events))
        assertEquals(RunEventType.COMPLETED, events.last().type)
        evidence.record(
            verifiedRunObservationEvidence(
                operation = "run observation",
                method = "GET",
                pathShape = "/v1/runs/{run_id}/events",
                expectedStatus = HTTP_OK,
                envelope = RUN_OBSERVATION_ENVELOPE,
                streamSummary =
                    RunStreamSummary(
                        eventsByType = eventsByWireName(events),
                        deltaCount = HOLD_OPEN_RUN_DELTA_COUNT,
                        terminalEvent = TERMINAL_EVENT_NAME,
                        unmappedFramesSkipped = 0,
                        idLines = 0,
                        boundaryComments = 0,
                        keepAliveComments = true,
                    ),
            ),
        )
    }

    private fun verifyUnmappedEventRun(
        client: DefaultGatewayClient,
        evidence: LiveSessionMatrixRecorder,
    ) {
        val admitted = createRun(client, evidence)
        assertEquals(RUN_LANE_UNMAPPED_RUN_ID, admitted.id.value)

        val events = client.observeRun(admitted.id).toList()
        assertEquals(listOf(RunEventType.COMPLETED), events.map(RunEvent::type))
        assertEquals(TERMINAL_EVENT_NAME, wireEventName(events.single().type))
        evidence.record(
            verifiedRunObservationEvidence(
                operation = "run observation",
                method = "GET",
                pathShape = "/v1/runs/{run_id}/events",
                expectedStatus = HTTP_OK,
                envelope = RUN_OBSERVATION_ENVELOPE,
                streamSummary =
                    RunStreamSummary(
                        eventsByType = eventsByWireName(events),
                        deltaCount = 0,
                        terminalEvent = TERMINAL_EVENT_NAME,
                        unmappedFramesSkipped = 1,
                        idLines = 0,
                        boundaryComments = 0,
                        keepAliveComments = false,
                    ),
            ),
        )
    }

    private fun verifyRefusedRunCreation(
        client: DefaultGatewayClient,
        evidence: LiveSessionMatrixRecorder,
    ) {
        val refused = gatewayFailure { client.createRun(SessionId(RUN_LANE_CREATED_SESSION_ID), RUN_LANE_INPUT) }
        assertEquals(GatewayErrorCategory.GATEWAY_REQUEST_FAILED, refused.category)
        evidence.record(
            failedEvidence(
                operation = "run create",
                method = "POST",
                pathShape = "/v1/runs",
                expectedStatus = HTTP_ACCEPTED,
                envelope = RUN_ADMISSION_ENVELOPE,
                errorCategory = GatewayErrorCategory.GATEWAY_REQUEST_FAILED.name,
            ),
        )
    }

    private fun createRun(
        client: DefaultGatewayClient,
        evidence: LiveSessionMatrixRecorder,
    ): Run {
        val admitted = client.createRun(SessionId(RUN_LANE_CREATED_SESSION_ID), RUN_LANE_INPUT)
        evidence.record(
            verifiedEvidence(
                operation = "run create",
                method = "POST",
                pathShape = "/v1/runs",
                expectedStatus = HTTP_ACCEPTED,
                envelope = RUN_ADMISSION_ENVELOPE,
            ),
        )
        return admitted
    }

    private fun verifyRunStatus(
        client: DefaultGatewayClient,
        admitted: Run,
        sessionId: String,
        expectedStatus: String,
        evidence: LiveSessionMatrixRecorder,
    ) {
        val status = client.getRunStatus(admitted.id)
        evidence.record(
            verifiedEvidence(
                operation = "run status",
                method = "GET",
                pathShape = "/v1/runs/{run_id}",
                expectedStatus = HTTP_OK,
                envelope = RUN_STATUS_ENVELOPE,
            ),
        )
        assertEquals(admitted.id, status.id)
        assertEquals(sessionId, status.sessionId.value)
        assertEquals(expectedStatus, status.status)
    }

    private fun collectEventsUntilTerminal(observation: RunEventObservation): List<RunEvent> {
        val iterator = observation.iterator()
        val events = mutableListOf<RunEvent>()
        while (events.lastOrNull()?.type != RunEventType.COMPLETED) {
            events += iterator.next()
        }
        return events
    }

    private fun deliveredDeltas(events: List<RunEvent>): List<String> =
        events.filter { it.type == RunEventType.MESSAGE_DELTA }.mapNotNull(RunEvent::text)

    private fun eventsByWireName(events: List<RunEvent>): Map<String, Int> =
        events.groupingBy { observedEvent -> wireEventName(observedEvent.type) }.eachCount()

    private fun wireEventName(type: RunEventType): String =
        requireNotNull(RUN_LANE_EVENT_WIRE_NAMES[type]) {
            "The run lane observed an event type outside its scenario: $type."
        }

    private fun laneClient(
        run: LiveSessionMatrixRun,
        bearerToken: String,
    ): DefaultGatewayClient =
        DefaultGatewayClient(
            endpoint = run.process.endpoint.toString(),
            bearerToken = bearerToken,
            transport = OkHttpGatewayTransport(run.tlsClient),
        )

    private fun seamClient(transport: GatewayTransport): DefaultGatewayClient =
        DefaultGatewayClient(
            endpoint = "https://gateway.example",
            bearerToken = "test-token",
            transport = transport,
        )

    private fun liveRunMatrixRun(): LiveSessionMatrixRun {
        var startedProcess: JourneyGatewayProcess? = null
        val tlsClient = journeyTrustedClient(repositoryRoot.resolve(JOURNEY_CA_PEM_PATH))
        val fixture =
            DeterministicGatewayFixture(
                descriptorFile = repositoryRoot.resolve(PINNED_DESCRIPTOR_PATH),
                processFactory =
                    GatewayProcessFactory { descriptor ->
                        val scenario =
                            runLaneScenario(descriptor, port = freeLoopbackPort(), bearerToken = RUN_LANE_BEARER_TOKEN)
                        val process =
                            JourneyGatewayProcess.start(
                                scenario,
                                repositoryRoot.resolve(JOURNEY_KEYSTORE_PATH),
                                JOURNEY_KEYSTORE_PASSWORD,
                            )
                        startedProcess = process
                        LoopbackHttpViewProcess(process)
                    },
                readinessChecker = TlsLoopbackReadinessChecker(tlsClient, RUN_LANE_BEARER_TOKEN),
            )
        return LiveSessionMatrixRun(fixture, tlsClient) {
            requireNotNull(startedProcess) { "The run lane journey process has not started." }
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

    private class SeamedEventStreamTransport(
        private val lines: List<String>,
    ) : GatewayTransport {
        var eventStreamClosed = false

        override fun execute(request: GatewayHttpRequest): GatewayHttpResponse {
            return GatewayHttpResponse(statusCode = HTTP_OK, body = "")
        }

        override fun openEventStream(request: GatewayHttpRequest): GatewayEventStream =
            GatewayEventStream(statusCode = HTTP_OK, lines = lines.asSequence()) {
                eventStreamClosed = true
            }
    }

    private companion object {
        const val RUN_LANE_BEARER_TOKEN = "live-run-matrix-bearer"
        const val RUN_LANE_SESSION_TITLE = "Run lane ephemeral session"
        const val RUN_LANE_INPUT = "Run lane input for the run matrix"
        const val RUN_LANE_EXPECTED_RESPONSE = "Hello from the lane."
        const val RUN_LANE_FRAMING_DELTA = "Framed delta"
        const val RUN_LANE_HOLD_OPEN_DELTA = "Held delta"
        const val RUN_LANE_SEAM_RUN_ID = "lane-seam-run"
        const val TERMINAL_EVENT_NAME = "run.completed"
        const val RUN_MATRIX_ENTRY_COUNT = 11
        const val MALFORMED_STREAM_ENTRY_COUNT = 2
        const val DELTA_RUN_DELTA_COUNT = 3
        const val FRAMING_RUN_DELTA_COUNT = 1
        const val FRAMING_RUN_EVENT_COUNT = 2
        const val FRAMING_RUN_BOUNDARY_COMMENTS = 2
        const val HOLD_OPEN_RUN_DELTA_COUNT = 1
        const val HTTP_OK = 200
        const val HTTP_ACCEPTED = 202

        val RUN_LANE_EVENT_WIRE_NAMES =
            mapOf(
                RunEventType.MESSAGE_DELTA to "message.delta",
                RunEventType.COMPLETED to "run.completed",
            )

        val RUN_ADMISSION_ENVELOPE =
            mapOf(
                "run_id" to "string",
                "status" to "string",
                "replayed" to "boolean",
            )

        val RUN_STATUS_ENVELOPE =
            mapOf(
                "object" to "string",
                "run_id" to "string",
                "session_id" to "string",
                "status" to "string",
                "updated_at" to "number",
            )

        val RUN_OBSERVATION_ENVELOPE =
            mapOf(
                "frames[].event" to "string",
                "frames[].run_id" to "string",
                "frames[].delta" to "string-or-absent",
            )
    }
}
