package org.mercurio.client.fixture.journey

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal object JourneySessionLifecycleInvariants {
    private val expectedSessionMutations =
        listOf(
            "POST" to "/api/sessions",
            "PATCH" to "/api/sessions/synthetic-created-session-1",
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
            "rename" to "synthetic-created-session-1",
            "delete" to "synthetic-created-session-1",
            "rename" to "session-alpha",
            "pin" to "session-alpha",
            "unpin" to "session-alpha",
            "delete" to "session-alpha",
        )

    private val expectedSessionCheckpoints =
        listOf(
            "list" to 0,
            "list" to 1,
            "history" to 2,
            "list" to LIST_CHECKPOINT_AFTER_CREATED_SESSION_PIN,
            "list" to LIST_CHECKPOINT_AFTER_CREATED_SESSION_UNPIN,
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

private const val LIST_CHECKPOINT_AFTER_CREATED_SESSION_PIN = 3

private const val LIST_CHECKPOINT_AFTER_CREATED_SESSION_UNPIN = 4

private const val LIST_CHECKPOINT_AFTER_ALPHA_SESSION_PIN = 8

private const val LIST_CHECKPOINT_AFTER_ALPHA_SESSION_UNPIN = 9
