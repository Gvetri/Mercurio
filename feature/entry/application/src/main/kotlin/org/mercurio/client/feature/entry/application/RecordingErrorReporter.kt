package org.mercurio.client.feature.entry.application

import java.util.concurrent.CopyOnWriteArrayList

/** Deterministic sink that records every report so tests and journeys can assert on it. */
class RecordingErrorReporter : ErrorReporter {
    private val recorded = CopyOnWriteArrayList<ErrorReport>()

    override fun report(report: ErrorReport) {
        recorded += report
    }

    /** A snapshot of the reports received so far, in report order. */
    fun reports(): List<ErrorReport> = recorded.toList()
}
