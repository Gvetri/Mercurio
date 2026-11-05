package org.mercurio.client.feature.entry.wiring

import org.mercurio.client.feature.entry.domain.ErrorReportingConsent

/** Every value the reporting backend is initialised with, resolved once at the wiring boundary. */
data class SentryReportingConfig(
    val dsn: String,
    val release: String,
    val environment: String,
    val consent: ErrorReportingConsent = ErrorReportingConsent.Undecided,
    val tracesSampleRate: Double? = null,
    val sessionTrackingEnabled: Boolean = SENTRY_SESSION_TRACKING_ENABLED,
    val ignoredErrors: List<String> = SENTRY_IGNORED_ERRORS,
) {
    val isConfigured: Boolean
        get() = dsn.isNotBlank()

    val isEnabled: Boolean
        get() = isConfigured && consent.isGranted
}

internal const val SENTRY_SESSION_TRACKING_ENABLED: Boolean = false

internal val SENTRY_IGNORED_ERRORS: List<String> =
    listOf(
        "java.util.concurrent.CancellationException",
        "java.io.InterruptedIOException",
    )

internal fun sentryReportingConfig(
    clientVersion: String,
    consent: ErrorReportingConsent,
    dsn: String? = null,
): SentryReportingConfig =
    SentryReportingConfig(
        dsn = resolvedSentryDsn(dsn),
        release = clientVersion,
        environment = BuildConfig.BUILD_TYPE,
        consent = consent,
    )

/** The endpoint this build reports to, resolved once so the gate and the Settings row agree. */
internal fun resolvedSentryDsn(dsn: String? = null): String = dsn ?: BuildConfig.SENTRY_DSN
