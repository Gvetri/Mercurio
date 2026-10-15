package org.hermesnative.client.feature.entry.data

import org.hermesnative.client.feature.entry.domain.GatewayErrorCategory
import org.hermesnative.client.feature.entry.domain.Run
import org.hermesnative.client.feature.entry.domain.RunEvent
import org.hermesnative.client.feature.entry.domain.RunEventObservation
import org.hermesnative.client.feature.entry.domain.RunEventType
import org.hermesnative.client.feature.entry.domain.RunId
import org.hermesnative.client.feature.entry.domain.SessionId
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
        val run = laneRun(repositoryRoot, RUN_LANE_BEARER_TOKEN, ::runLaneScenario)

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
                streamSummary = runStreamSummary(RUN_LANE_DELTA_RUN_ID, events),
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
                streamSummary = runStreamSummary(RUN_LANE_FRAMING_RUN_ID, events),
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
                streamSummary = runStreamSummary(RUN_LANE_HOLD_OPEN_RUN_ID, events),
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
                streamSummary = runStreamSummary(RUN_LANE_UNMAPPED_RUN_ID, events),
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

    private fun runStreamSummary(
        runId: String,
        events: List<RunEvent>,
    ): RunStreamSummary {
        val script =
            requireNotNull(runLaneScripts().singleOrNull { laneScript -> laneScript.runId == runId }) {
                "The run lane scenario has no script for run $runId."
            }
        val deltaWireName = RUN_LANE_EVENT_WIRE_NAMES.getValue(RunEventType.MESSAGE_DELTA)
        val deliveredEventsByType = eventsByWireName(events)
        val deliveredDeltaCount = deliveredDeltas(events).size
        val unmappedFramesSkipped =
            script.observation.count { scriptedEvent -> scriptedEvent.type !in RUN_LANE_EVENT_WIRE_NAMES.values }
        assertEquals(
            script.observation.count { scriptedEvent -> scriptedEvent.type == deltaWireName },
            deliveredDeltaCount,
        )
        assertEquals(script.observation.size - unmappedFramesSkipped, deliveredEventsByType.values.sum())
        return RunStreamSummary(
            eventsByType = deliveredEventsByType,
            deltaCount = deliveredDeltaCount,
            terminalEvent = TERMINAL_EVENT_NAME,
            unmappedFramesSkipped = unmappedFramesSkipped,
            idLines = if (script.liveFraming) script.observation.size else 0,
            boundaryComments = if (script.liveFraming) LIVE_FRAMING_BOUNDARY_COMMENTS else 0,
            keepAliveComments = script.holdOpen,
        )
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

    private fun seamClient(transport: GatewayTransport): DefaultGatewayClient =
        DefaultGatewayClient(
            endpoint = "https://gateway.example",
            bearerToken = "test-token",
            transport = transport,
        )

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
        const val LIVE_FRAMING_BOUNDARY_COMMENTS = 2
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
