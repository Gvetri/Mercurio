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
                .filterNot { field -> field.isSynthetic || Modifier.isStatic(field.modifiers) }
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
    fun the_default_sink_holds_no_state_and_accepts_a_report() {
        val stateFields =
            NoOpErrorReporter::class.java.declaredFields
                .filterNot { field -> Modifier.isStatic(field.modifiers) }

        assertEquals(emptyList<Any>(), stateFields.toList())
        ErrorReporting().reportUnexpectedFailure()
    }
}
