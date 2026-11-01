package org.mercurio.client.feature.entry.wiring

import io.sentry.Sentry
import io.sentry.SentryLevel
import org.mercurio.client.feature.entry.application.ErrorReport
import org.mercurio.client.feature.entry.application.ErrorReporter

internal class SentryErrorReporter(
    private val config: SentryReportingConfig,
    private val capture: (ErrorReport) -> Unit = ::captureUnexpectedFailure,
) : ErrorReporter {
    override fun report(report: ErrorReport) {
        if (!config.isEnabled) return
        capture(report)
    }
}

private const val UNEXPECTED_FAILURE_MESSAGE = "Unexpected client failure"
private const val RELEASE_TAG = "release"
private const val ENVIRONMENT_TAG = "environment"

private fun captureUnexpectedFailure(report: ErrorReport) {
    Sentry.captureMessage(UNEXPECTED_FAILURE_MESSAGE, SentryLevel.WARNING) { scope ->
        scope.setTag(RELEASE_TAG, report.release)
        scope.setTag(ENVIRONMENT_TAG, report.environment)
    }
}
