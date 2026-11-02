package org.mercurio.client.fixture.journey

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal object JourneySessionCreationInvariants {
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
        check(checkpoints == listOf("list" to 0, "list" to 1)) {
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
