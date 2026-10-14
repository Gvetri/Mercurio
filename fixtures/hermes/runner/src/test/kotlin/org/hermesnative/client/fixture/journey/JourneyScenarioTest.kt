package org.hermesnative.client.fixture.journey

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class JourneyScenarioTest {
    @Test
    fun every_checked_in_scenario_parses_with_the_pinned_revision() {
        val scenarioFiles = scenariosDir.listFiles { file -> file.extension == "json" }.orEmpty()
        assertTrue("No journey scenarios found.", scenarioFiles.isNotEmpty())
        scenarioFiles.forEach { file ->
            val scenario = JourneyScenarioParser.parse(file, pinnedDescriptor.provenance.value)
            assertEquals(file.nameWithoutExtension, scenario.name)
        }
    }

    @Test
    fun connection_recovery_flow_uses_the_visible_verification_action() {
        val flow = File(repositoryRoot, "fixtures/hermes/journey/flows/connection.yaml").readText()
        val correctedCredential = "synthetic-token"
        assertTrue(flow.contains(correctedCredential))
        val recoverySteps = flow.substringAfter(correctedCredential)
        assertTrue(recoverySteps.contains("Verify Gateway Connection"))
        assertFalse(recoverySteps.contains("Try again"))
    }

    @Test
    fun retry_journey_uses_targeted_scroll_and_retries_unregistered_taps() {
        val flow = File(repositoryRoot, "fixtures/hermes/journey/flows/explicit-retry.yaml").readText()
        assertTrue(flow.contains("text: \"Try again\"\n    retryTapIfNoChange: true"))
        assertTrue(flow.contains("scrollUntilVisible:"))
        assertFalse(flow.contains("- swipe:"))
    }

    @Test
    fun provenance_mismatch_is_rejected() {
        val mismatched = File(repositoryRoot, "fixtures/hermes/journey/scenarios/connection.json")
        val error =
            runCatching {
                JourneyScenarioParser.parse(mismatched, "0000000000000000000000000000000000000000")
            }.exceptionOrNull()
        assertTrue(error is JourneyScenarioFormatException)
        assertTrue(error!!.message.orEmpty().contains("provenance"))
    }

    @Test
    fun unknown_top_level_field_is_rejected() {
        val revision = pinnedDescriptor.provenance.value
        val file =
            tempScenario(
                """{"name":"unknown-field","hermes_revision":"$revision",""" +
                    """"port":18443,"tls":false,"capabilities":["sessions"],"mutable":"latest"}""",
            )
        val error =
            runCatching { JourneyScenarioParser.parse(file, pinnedDescriptor.provenance.value) }.exceptionOrNull()
        assertTrue(error is JourneyScenarioFormatException)
        assertTrue(error!!.message.orEmpty().contains("unsupported"))
    }

    @Test
    fun unknown_capability_endpoint_is_rejected() {
        val revision = pinnedDescriptor.provenance.value
        val file =
            tempScenario(
                """{"name":"unknown-endpoint","hermes_revision":"$revision","port":18443,"tls":false,""" +
                    """"capabilities":["sessions","not_an_endpoint"]}""",
            )
        val error = runCatching { JourneyScenarioParser.parse(file, revision) }.exceptionOrNull()
        assertTrue(error is JourneyScenarioFormatException)
        assertTrue(error!!.message.orEmpty().contains("capability"))
    }

    @Test
    fun live_framing_run_script_parses_from_the_scenario_file() {
        val revision = pinnedDescriptor.provenance.value
        val file =
            tempScenario(
                """{"name":"live-framed","hermes_revision":"$revision","port":18443,"tls":false,""" +
                    """"capabilities":["runs","run_events"],"sessions":[],"runs":[{"run_id":"live-run",""" +
                    """"session_id":"session-alpha","live_framing":true,"observation":[],""" +
                    """"final_status":"completed"}]}""",
            )
        val scenario = JourneyScenarioParser.parse(file, revision)
        assertTrue(scenario.runs.single().liveFraming)
    }

    @Test
    fun interrupted_and_held_open_run_scripts_are_rejected() {
        val revision = pinnedDescriptor.provenance.value
        val file =
            tempScenario(
                """
                {"name":"invalid-run","hermes_revision":"$revision","port":18443,"tls":false,"capabilities":["sessions"],
                 "sessions":[{"id":"s1","title":"S","preview":null,"pinned":false}],
                 "runs":[{"run_id":"r1","session_id":"s1","hold_open":true,"interrupt_after_events":1,"observation":[{"type":"tool.started"}]}]}
                """.trimIndent(),
            )
        val error = runCatching { JourneyScenarioParser.parse(file, revision) }.exceptionOrNull()
        assertTrue(error is JourneyScenarioFormatException)
    }

    @Test
    fun disconnect_finalized_run_script_parses_from_the_scenario_file() {
        val revision = pinnedDescriptor.provenance.value
        val file =
            tempScenario(
                """{"name":"composer-stopped","hermes_revision":"$revision","port":18443,"tls":false,""" +
                    """"capabilities":["runs","run_events"],"sessions":[],"runs":[{"run_id":"stop-run",""" +
                    """"session_id":"session-alpha","hold_open":true,"finalize_on_disconnect":true,""" +
                    """"observation":[],"final_status":"completed"}]}""",
            )
        val scenario = JourneyScenarioParser.parse(file, revision)
        assertTrue(scenario.runs.single().finalizeOnDisconnect)
    }

    @Test
    fun disconnect_finalized_run_script_without_a_held_open_stream_is_rejected() {
        val revision = pinnedDescriptor.provenance.value
        val file =
            tempScenario(
                """{"name":"invalid-disconnect-run","hermes_revision":"$revision","port":18443,"tls":false,""" +
                    """"capabilities":["sessions"],"sessions":[],"runs":[{"run_id":"r1","session_id":"s1",""" +
                    """"finalize_on_disconnect":true,"observation":[],"final_status":"completed"}]}""",
            )
        val error = runCatching { JourneyScenarioParser.parse(file, revision) }.exceptionOrNull()
        assertTrue(error is JourneyScenarioFormatException)
    }

    @Test
    fun disconnect_finalized_run_script_without_a_final_status_is_rejected() {
        val revision = pinnedDescriptor.provenance.value
        val file =
            tempScenario(
                """{"name":"invalid-disconnect-run","hermes_revision":"$revision","port":18443,"tls":false,""" +
                    """"capabilities":["sessions"],"sessions":[],"runs":[{"run_id":"r1","session_id":"s1",""" +
                    """"hold_open":true,"finalize_on_disconnect":true,"observation":[]}]}""",
            )
        val error = runCatching { JourneyScenarioParser.parse(file, revision) }.exceptionOrNull()
        assertTrue(error is JourneyScenarioFormatException)
    }

    @Test
    fun refused_run_creation_with_scripted_outcome_is_rejected() {
        val revision = pinnedDescriptor.provenance.value
        val file =
            tempScenario(
                """
                {"name":"invalid-refused-run","hermes_revision":"$revision","port":18443,"tls":false,"capabilities":["sessions"],
                 "sessions":[{"id":"s1","title":"S","preview":null,"pinned":false}],
                 "runs":[{"run_id":"r1","session_id":"s1","refuse_create":true,"observation":[{"type":"run.completed"}]}]}
                """.trimIndent(),
            )
        val error = runCatching { JourneyScenarioParser.parse(file, revision) }.exceptionOrNull()
        assertTrue(error is JourneyScenarioFormatException)
    }

    @Test
    fun duplicate_provenance_keys_are_rejected() {
        val file = File.createTempFile("journey-scenario", ".json")
        try {
            val pinned = pinnedDescriptor.provenance.value
            file.writeText(
                """{"name":"duplicate-provenance","hermes_revision":"$pinned","hermes_revision":"$pinned"}""",
            )
            val error =
                assertThrows(JourneyScenarioFormatException::class.java) {
                    JourneyScenarioParser.parse(file, pinned)
                }
            assertTrue(error.message.orEmpty().contains("exactly one hermes_revision"))
        } finally {
            file.delete()
        }
    }

    @Test
    fun duplicate_provenance_keys_with_unicode_escapes_are_rejected() {
        val file = File.createTempFile("journey-scenario", ".json")
        try {
            val pinned = pinnedDescriptor.provenance.value
            val escapedRevision = "hermes_revision".replaceFirst("h", "\\u0068")
            file.writeText(
                """{"name":"duplicate-provenance","hermes_revision":"$pinned","$escapedRevision":"$pinned"}""",
            )
            val error =
                assertThrows(JourneyScenarioFormatException::class.java) {
                    JourneyScenarioParser.parse(file, pinned)
                }
            assertTrue(error.message.orEmpty().contains("exactly one hermes_revision"))
        } finally {
            file.delete()
        }
    }

    @Test
    fun pending_creation_scenario_parses_with_the_bounded_hold() {
        val scenarioFile = File(scenariosDir, "pending-creation.json")
        val scenario = JourneyScenarioParser.parse(scenarioFile, pinnedDescriptor.provenance.value)
        assertEquals("pending-creation", scenario.name)
        assertEquals(8000, scenario.holdCreateMs)
    }

    @Test
    fun hold_create_millis_must_stay_positive_and_inside_the_client_read_timeout() {
        val revision = pinnedDescriptor.provenance.value

        fun scenarioWithHold(hold: String): File =
            tempScenario(
                """{"name":"hold-bounds","hermes_revision":"$revision","port":18443,"tls":false,""" +
                    """"capabilities":["sessions"],"hold_create_ms":$hold}""",
            )
        listOf("0", "-1", "9001").forEach { hold ->
            val error = runCatching { JourneyScenarioParser.parse(scenarioWithHold(hold), revision) }.exceptionOrNull()
            assertTrue("hold_create_ms=$hold should be rejected", error is JourneyScenarioFormatException)
        }
        val accepted = JourneyScenarioParser.parse(scenarioWithHold("9000"), revision)
        assertEquals(9000, accepted.holdCreateMs)
    }

    @Test
    fun pending_creation_flow_exercises_duplicate_back_and_outside_while_held() {
        val flow = File(repositoryRoot, "fixtures/hermes/journey/flows/pending-creation.yaml").readText()
        val pendingWindow =
            flow.substringAfter("takeScreenshot: pending-creation-02-pending")
                .substringBefore("extendedWaitUntil")
        assertTrue("The duplicate confirm tap is missing.", pendingWindow.contains("- tapOn: \"Create\""))
        assertTrue("The Back dismissal attempt is missing.", pendingWindow.contains("- back"))
        assertTrue("The outside tap is missing.", pendingWindow.contains("point:"))
        assertTrue("The still-pending assertion is missing.", pendingWindow.contains("\"Creating Session…\""))
    }

    @Test
    fun pending_creation_flow_asserts_dialog_absence_after_result_open() {
        val flow = File(repositoryRoot, "fixtures/hermes/journey/flows/pending-creation.yaml").readText()
        val settledWindow = flow.substringAfter("takeScreenshot: pending-creation-04-result-open")
        val settleIndex = settledWindow.indexOf("waitForAnimationToEnd")
        val absentIndex = settledWindow.indexOf("assertNotVisible: \"Creating Session…\"")
        val settledScreenshotIndex = settledWindow.indexOf("takeScreenshot: pending-creation-05-result-settled")
        assertTrue("The post-result settle is missing.", settleIndex >= 0)
        assertTrue("The dialog-absence assertion does not follow the settle.", absentIndex > settleIndex)
        assertTrue("The settled result frame does not follow the assertion.", settledScreenshotIndex > absentIndex)
        assertTrue(
            "The dialog field label absence assertion is missing.",
            settledWindow.contains("assertNotVisible: \"Title (optional)\""),
        )
    }
}
