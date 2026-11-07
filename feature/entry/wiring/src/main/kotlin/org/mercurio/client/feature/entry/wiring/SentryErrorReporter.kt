package org.mercurio.client.feature.entry.wiring

import io.sentry.Sentry
import io.sentry.SentryLevel
import org.mercurio.client.feature.entry.application.ErrorReport
import org.mercurio.client.feature.entry.application.ErrorReporter
import org.mercurio.client.feature.entry.domain.ErrorReportingConsent

/**
 * The reporting gate. The build must carry an endpoint, and the stored answer is read for every
 * report instead of once at startup, so switching the Settings row off stops the next report at
 * once. Switching it on from a process that started without an answer still needs the next start:
 * the reporting SDK installs at most once per process, and no row can repeat that install.
 */
internal class SentryErrorReporter(
    private val config: SentryReportingConfig,
    private val capture: (ErrorReport) -> Unit = ::captureUnexpectedFailure,
    private val consent: () -> ErrorReportingConsent,
) : ErrorReporter {
    override fun report(report: ErrorReport) {
        if (!config.isConfigured || !consent().isGranted) return
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
