package org.mercurio.client.feature.entry.application

/** The environment tag reported when the composition does not select one. */
const val UNKNOWN_ENVIRONMENT: String = "unknown"

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
