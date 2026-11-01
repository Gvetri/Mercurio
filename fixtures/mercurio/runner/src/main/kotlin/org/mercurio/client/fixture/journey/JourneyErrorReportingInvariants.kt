package org.mercurio.client.fixture.journey

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal object JourneyErrorReportingInvariants {
    private const val EXPECTED_ENVIRONMENT = "debug"
    private const val UNKNOWN_RELEASE = "unknown"

    internal fun verifyErrorReporting(telemetry: JsonObject) {
        val reports =
            telemetry["error_reports"]?.jsonArray
                ?: error("Telemetry has no error-report record.")
        check(reports.size == 1) {
            "The fake sink captured ${reports.size} error reports, expected exactly one."
        }
        val report = reports.single().jsonObject
        val release = report.getValue("release").jsonPrimitive.content
        val environment = report.getValue("environment").jsonPrimitive.content
        check(release.isNotBlank() && release != UNKNOWN_RELEASE) {
            "The captured error report carries no release tag."
        }
        check(environment == EXPECTED_ENVIRONMENT) {
            "The captured error report carries environment '$environment', expected '$EXPECTED_ENVIRONMENT'."
        }
    }
}
