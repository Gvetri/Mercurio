package org.mercurio.client.fixture.journey

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

internal object JourneyScenarioJson {
    internal fun parseMessages(
        container: JsonObject,
        key: String,
        context: String,
    ): List<JourneyMessage> =
        container[key]?.jsonArray.orEmpty().mapIndexed { index, element ->
            val message = element.jsonObject
            rejectUnknownKeys(message, allowedMessageKeys, "$context[$index]")
            JourneyMessage(
                id = requiredString(message, "id"),
                role = optionalString(message, "role"),
                content = optionalString(message, "content"),
                timestamp = optionalString(message, "timestamp"),
            )
        }

    internal fun parseRuns(root: JsonObject): List<JourneyRunScript> =
        root["runs"]?.jsonArray.orEmpty().mapIndexed { index, element ->
            val run = element.jsonObject
            rejectUnknownKeys(run, allowedRunKeys, "run[$index]")
            JourneyRunScript(
                runId = requiredString(run, "run_id"),
                sessionId = requiredString(run, "session_id"),
                createStatus = optionalString(run, "create_status") ?: "started",
                observation = parseObservation(run, index),
                interruptAfterEvents = optionalInt(run, "interrupt_after_events"),
                holdOpen = optionalBoolean(run, "hold_open", default = false),
                finalizeOnDisconnect = optionalBoolean(run, "finalize_on_disconnect", default = false),
                refuseCreate = optionalBoolean(run, "refuse_create", default = false),
                admissionRefusalCode = optionalString(run, "admission_refusal_code"),
                liveFraming = optionalBoolean(run, "live_framing", default = false),
                finalStatus = optionalString(run, "final_status"),
                terminalHistory = parseMessages(run, "terminal_history", "run[$index].terminal_history"),
                idleGapMillis = optionalInt(run, "idle_gap_ms"),
            )
        }

    private fun parseObservation(
        run: JsonObject,
        runIndex: Int,
    ): List<JourneyRunEvent> =
        run["observation"]?.jsonArray.orEmpty().mapIndexed { index, element ->
            val event = element.jsonObject
            rejectUnknownKeys(event, allowedEventKeys, "run[$runIndex].observation[$index]")
            val type = requiredString(event, "type")
            if (type !in supportedEventTypes) {
                throw JourneyScenarioFormatException(
                    "Journey scenario declares unsupported SSE event type '$type'.",
                )
            }
            JourneyRunEvent(
                type = type,
                delta = optionalString(event, "delta"),
            )
        }

    internal fun requiredString(
        container: JsonObject,
        key: String,
    ): String {
        val value = container[key] as? JsonPrimitive ?: throw JourneyScenarioFormatException("Missing field '$key'.")
        return value.content.takeIf(String::isNotBlank)
            ?: throw JourneyScenarioFormatException("Field '$key' must not be blank.")
    }

    internal fun optionalString(
        container: JsonObject,
        key: String,
    ): String? {
        val value = container[key] ?: return null
        return if (value == JsonNull) {
            null
        } else {
            (value as? JsonPrimitive)?.content
                ?: throw JourneyScenarioFormatException("Field '$key' must be a string or null.")
        }
    }

    internal fun requiredInt(
        container: JsonObject,
        key: String,
    ): Int =
        (container[key] as? JsonPrimitive)?.intOrNull
            ?: throw JourneyScenarioFormatException("Field '$key' must be an integer.")

    internal fun optionalInt(
        container: JsonObject,
        key: String,
    ): Int? {
        val value = container[key] ?: return null
        return if (value == JsonNull) {
            null
        } else {
            (value as? JsonPrimitive)?.intOrNull
                ?: throw JourneyScenarioFormatException("Field '$key' must be an integer or null.")
        }
    }

    internal fun requiredBoolean(
        container: JsonObject,
        key: String,
    ): Boolean =
        (container[key] as? JsonPrimitive)?.booleanOrNull
            ?: throw JourneyScenarioFormatException("Field '$key' must be a boolean.")

    internal fun optionalBoolean(
        container: JsonObject,
        key: String,
        default: Boolean,
    ): Boolean {
        val value = container[key] ?: return default
        return (value as? JsonPrimitive)?.booleanOrNull
            ?: throw JourneyScenarioFormatException("Field '$key' must be a boolean.")
    }

    internal fun requiredStringList(
        container: JsonObject,
        key: String,
    ): List<String> =
        container[key]?.jsonArray.orEmpty().map { element ->
            (element as? JsonPrimitive)?.content
                ?: throw JourneyScenarioFormatException("Field '$key' must be a list of strings.")
        }
}

private val supportedEventTypes =
    setOf(
        "message.delta",
        "run.completed",
        "run.failed",
        "run.cancelled",
        "run.stopping",
        "tool.started",
        "tool.completed",
        "reasoning.available",
        "approval.request",
    )
