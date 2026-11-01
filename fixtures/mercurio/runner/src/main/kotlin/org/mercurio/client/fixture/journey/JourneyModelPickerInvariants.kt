package org.mercurio.client.fixture.journey

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal object JourneyModelPickerInvariants {
    const val MODEL_CATALOG_PATH = "/api/models"
    const val CATALOG_SESSION_ID = "session-alpha"
    const val CATALOG_MODEL_ID = "deepseek-v4-flash"
    const val SECOND_MODEL_ID = "hermes-agent"

    internal fun verifyModelPickerAdvertised(telemetry: JsonObject) {
        requireConnectionAttempted(telemetry)
        val fetches = catalogRequests(telemetry)
        check(fetches.size == EXPECTED_ADVERTISED_FETCHES) {
            "The advertised catalog was fetched ${fetches.size} times; " +
                "expected $EXPECTED_ADVERTISED_FETCHES (one per verified connection)."
        }
        check(
            runSelections(telemetry) ==
                listOf(
                    RunSelectionTelemetry(CATALOG_SESSION_ID, SECOND_MODEL_ID, null, accepted = true),
                    RunSelectionTelemetry(CATALOG_SESSION_ID, CATALOG_MODEL_ID, "low", accepted = true),
                ),
        ) {
            "The advertised picker submissions did not carry the scripted overrides: ${runSelections(telemetry)}"
        }
        check(runCreatesFor(telemetry, CATALOG_SESSION_ID) == 2) {
            "The advertised picker journey did not submit exactly two Runs."
        }
        check(runStatusRequests(telemetry) >= 2) {
            "The client did not reconcile both terminal Runs of the advertised picker journey."
        }
    }

    internal fun verifyModelPickerAbsent(telemetry: JsonObject) {
        verifyNoCatalogTraffic(telemetry)
        check(runCreatesFor(telemetry, CATALOG_SESSION_ID) == 1) {
            "The absent-capability journey did not submit exactly one Run."
        }
    }

    internal fun verifyModelPickerRefusal(telemetry: JsonObject) {
        requireConnectionAttempted(telemetry)
        check(catalogRequests(telemetry).size == 1) {
            "The refusal journey fetched the catalog ${catalogRequests(telemetry).size} times; expected 1."
        }
        check(
            runSelections(telemetry) ==
                listOf(
                    RunSelectionTelemetry(CATALOG_SESSION_ID, CATALOG_MODEL_ID, "high", accepted = false),
                    RunSelectionTelemetry(CATALOG_SESSION_ID, null, null, accepted = true),
                ),
        ) {
            "The refusal journey did not record a refused selection followed by an accepted retry: " +
                runSelections(telemetry)
        }
        check(runCreatesFor(telemetry, CATALOG_SESSION_ID) == 2) {
            "The refusal journey did not submit exactly two Run creations (one refused, one retried)."
        }
    }

    internal fun verifyModelCatalogRowMismatch(telemetry: JsonObject) {
        verifyNoCatalogTraffic(telemetry)
        check(runCreatesFor(telemetry, CATALOG_SESSION_ID) == 1) {
            "The mismatched-row journey did not submit exactly one Run."
        }
    }

    private fun verifyNoCatalogTraffic(telemetry: JsonObject) {
        requireConnectionAttempted(telemetry)
        check(catalogRequests(telemetry).isEmpty()) {
            "The client fetched the model catalog although the capability row was not advertised as usable."
        }
        val selections = runSelections(telemetry)
        check(selections == listOf(RunSelectionTelemetry(CATALOG_SESSION_ID, null, null, accepted = true))) {
            "The no-catalog journey submitted an unexpected Run selection: $selections"
        }
    }

    private const val EXPECTED_ADVERTISED_FETCHES = 2
}

internal fun catalogRequests(telemetry: JsonObject): List<String> =
    telemetry["requests"]?.jsonArray.orEmpty().mapNotNull { request ->
        val fields = request.jsonObject
        val path = fields["path"]?.jsonPrimitive?.content
        path?.takeIf { it == JourneyModelPickerInvariants.MODEL_CATALOG_PATH }
    }

private fun runSelections(telemetry: JsonObject): List<RunSelectionTelemetry> =
    telemetry["run_selections"]?.jsonArray?.map { entry ->
        val fields = entry.jsonObject
        RunSelectionTelemetry(
            sessionId = fields.getValue("session_id").jsonPrimitive.content,
            model = fields["model"]?.let(::stringOrNull),
            effort = fields["effort"]?.let(::stringOrNull),
            accepted = fields.getValue("accepted").jsonPrimitive.content.toBooleanStrict(),
        )
    } ?: error("Telemetry has no run-selection records.")
