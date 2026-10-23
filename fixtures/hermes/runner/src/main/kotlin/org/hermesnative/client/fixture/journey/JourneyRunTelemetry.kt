package org.hermesnative.client.fixture.journey

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal fun sseFor(
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

internal fun runCreatesFor(
    telemetry: JsonObject,
    sessionId: String,
): Int =
    telemetry["run_creates"]?.jsonObject?.get(sessionId)?.jsonPrimitive?.int
        ?: error("Telemetry has no run-create record for session '$sessionId'.")

internal fun runStatusRequests(telemetry: JsonObject): Int {
    return telemetry.getValue("run_status_requests").jsonPrimitive.int
}

internal fun interruptedRunIds(telemetry: JsonObject): Set<String> =
    telemetry["interrupted_run_ids"]?.jsonArray?.map { it.jsonPrimitive.content }?.toSet().orEmpty()
