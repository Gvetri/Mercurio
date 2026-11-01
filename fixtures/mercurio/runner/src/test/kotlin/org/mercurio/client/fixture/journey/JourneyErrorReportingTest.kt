package org.mercurio.client.fixture.journey

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class JourneyErrorReportingTest {
    @Test
    fun the_fixture_route_records_every_delivered_report() {
        val process = startGateway("error-reporting", tls = false)
        try {
            val response =
                post(
                    process.endpoint,
                    "/__fixture/error-report",
                    """[{"release":"0.1.0","environment":"debug"}]""",
                )

            assertEquals(202, response.status)
            val reports = telemetryReports(process)
            assertEquals(1, reports.size)
            assertEquals("0.1.0", reports.single()["release"])
            assertEquals("debug", reports.single()["environment"])
        } finally {
            process.stop()
        }
    }

    @Test
    fun the_fixture_route_rejects_a_malformed_delivery_and_records_nothing() {
        val process = startGateway("error-reporting", tls = false)
        try {
            val response = post(process.endpoint, "/__fixture/error-report", "not-json")

            assertEquals(400, response.status)
            assertTrue(telemetryReports(process).isEmpty())
        } finally {
            process.stop()
        }
    }

    @Test
    fun the_error_reporting_invariants_accept_exactly_one_tagged_report() {
        JourneyInvariants.verify(
            "error-reporting",
            Json.parseToJsonElement("""{"error_reports":[{"release":"0.1.0","environment":"debug"}]}""").jsonObject,
        )
    }

    @Test
    fun the_error_reporting_invariants_reject_repeated_or_untagged_reports() {
        assertRejected("""{"error_reports":[]}""")
        assertRejected(
            """{"error_reports":[{"release":"0.1.0","environment":"debug"},
                {"release":"0.1.0","environment":"debug"}]}""",
        )
        assertRejected("""{"error_reports":[{"release":"unknown","environment":"debug"}]}""")
        assertRejected("""{"error_reports":[{"release":"0.1.0","environment":"release"}]}""")
    }

    private fun assertRejected(telemetry: String) {
        try {
            JourneyInvariants.verify("error-reporting", Json.parseToJsonElement(telemetry).jsonObject)
            fail("The error-reporting invariants accepted $telemetry")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message.orEmpty().isNotBlank())
        }
    }

    private fun telemetryReports(process: JourneyGatewayProcess): List<Map<String, String>> {
        val telemetry = Json.parseToJsonElement(getBody(process.endpoint, "/__fixture/telemetry")).jsonObject
        return telemetry.getValue("error_reports").jsonArray.map { element ->
            val report = element.jsonObject
            mapOf(
                "release" to report.getValue("release").jsonPrimitive.content,
                "environment" to report.getValue("environment").jsonPrimitive.content,
            )
        }
    }
}
