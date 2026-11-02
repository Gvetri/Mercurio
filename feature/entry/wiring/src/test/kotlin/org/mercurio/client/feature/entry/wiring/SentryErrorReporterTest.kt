package org.mercurio.client.feature.entry.wiring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mercurio.client.feature.entry.application.ErrorReport
import org.mercurio.client.feature.entry.domain.ErrorReportingConsent

class SentryErrorReporterTest {
    @Test
    fun reports_one_event_carrying_the_reports_release_and_environment() {
        val captured = mutableListOf<ErrorReport>()
        val reporter = SentryErrorReporter(reportingConfig(), captured::add)

        reporter.report(ErrorReport(release = "nightly-42-abc", environment = "debug"))

        assertEquals(
            listOf(ErrorReport(release = "nightly-42-abc", environment = "debug")),
            captured,
        )
    }

    @Test
    fun a_blank_dsn_keeps_the_backend_disabled_and_reports_nothing() {
        val captured = mutableListOf<ErrorReport>()
        val config = reportingConfig().copy(dsn = " ")
        val reporter = SentryErrorReporter(config, captured::add)

        reporter.report(ErrorReport(release = "0.1.0", environment = "release"))

        assertFalse(config.isEnabled)
        assertTrue(captured.isEmpty())
    }

    @Test
    fun a_decision_that_grants_nothing_reports_nothing() {
        for (consent in listOf(ErrorReportingConsent.Undecided, ErrorReportingConsent.Declined)) {
            val captured = mutableListOf<ErrorReport>()
            val reporter = SentryErrorReporter(reportingConfig().copy(consent = consent), captured::add)

            reporter.report(ErrorReport(release = "0.1.0", environment = "debug"))

            assertTrue("A $consent decision reported an event.", captured.isEmpty())
        }
    }

    private fun reportingConfig(): SentryReportingConfig =
        SentryReportingConfig(
            dsn = "https://synthetic-key@ingest.example.invalid/1",
            release = "0.1.0",
            environment = "debug",
            consent = ErrorReportingConsent.Granted,
        )
}
