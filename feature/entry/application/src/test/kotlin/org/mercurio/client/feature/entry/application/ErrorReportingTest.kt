package org.mercurio.client.feature.entry.application

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Test
import java.lang.reflect.Modifier

class ErrorReportingTest {
    @Test
    fun the_event_contract_carries_only_the_release_and_environment_tags() {
        val fields =
            ErrorReport::class.java.declaredFields
                .filter { field -> Modifier.isPrivate(field.modifiers) && !field.isSynthetic }
                .map { field -> field.name }
                .toSet()

        assertEquals(setOf("release", "environment"), fields)
    }

    @Test
    fun the_recording_sink_keeps_every_report_in_order() {
        val sink = RecordingErrorReporter()

        sink.report(ErrorReport(release = "1.2.3", environment = "debug"))
        sink.report(ErrorReport(release = "1.2.4", environment = "release"))

        assertEquals(
            listOf(
                ErrorReport(release = "1.2.3", environment = "debug"),
                ErrorReport(release = "1.2.4", environment = "release"),
            ),
            sink.reports(),
        )
    }

    @Test
    fun the_recording_sink_returns_a_snapshot_that_is_not_the_live_store() {
        val sink = RecordingErrorReporter()
        sink.report(ErrorReport(release = "1.2.3", environment = "debug"))

        assertNotSame(sink.reports(), sink.reports())

        assertEquals(listOf(ErrorReport(release = "1.2.3", environment = "debug")), sink.reports())
    }

    @Test
    fun clearing_the_recording_sink_discards_every_report() {
        val sink = RecordingErrorReporter()
        sink.report(ErrorReport(release = "1.2.3", environment = "debug"))

        sink.clear()

        assertEquals(emptyList<ErrorReport>(), sink.reports())
    }

    @Test
    fun the_seam_reports_the_configured_release_and_environment() {
        val sink = RecordingErrorReporter()

        ErrorReporting(
            reporter = sink,
            release = "1.2.3",
            environment = "debug",
        ).reportUnexpectedFailure()

        assertEquals(listOf(ErrorReport(release = "1.2.3", environment = "debug")), sink.reports())
    }

    @Test
    fun the_default_binding_is_the_silent_no_op() {
        ErrorReporting().reportUnexpectedFailure()

        NoOpErrorReporter.report(ErrorReport(release = UNKNOWN_CLIENT_VERSION, environment = UNKNOWN_ENVIRONMENT))
    }
}
