package org.mercurio.client.feature.entry.application

import java.util.concurrent.CopyOnWriteArrayList

/** The environment tag reported when the composition does not select one. */
const val UNKNOWN_ENVIRONMENT: String = "unknown"

/** One error event the application reports. */
data class ErrorReport(
    val release: String,
    val environment: String,
)

/** Reports one closed [ErrorReport] to the bound sink. */
interface ErrorReporter {
    fun report(report: ErrorReport)
}

/** Default sink: keeps the seam silent until a real backend is bound. */
object NoOpErrorReporter : ErrorReporter {
    override fun report(report: ErrorReport) = Unit
}

/** Deterministic sink that records every report so tests and journeys can assert on it. */
class RecordingErrorReporter : ErrorReporter {
    private val recorded = CopyOnWriteArrayList<ErrorReport>()

    override fun report(report: ErrorReport) {
        recorded += report
    }

    /** A snapshot of the reports received so far, in report order. */
    fun reports(): List<ErrorReport> = recorded.toList()
}

/** The application seam that reports an unexpected failure. */
class ErrorReporting(
    private val reporter: ErrorReporter = NoOpErrorReporter,
    private val release: String = UNKNOWN_CLIENT_VERSION,
    private val environment: String = UNKNOWN_ENVIRONMENT,
) {
    /** Reports one unexpected failure through the bound sink. */
    fun reportUnexpectedFailure() {
        reporter.report(ErrorReport(release = release, environment = environment))
    }
}
