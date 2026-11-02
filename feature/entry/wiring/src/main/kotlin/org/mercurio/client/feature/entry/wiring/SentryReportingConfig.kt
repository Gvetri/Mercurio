package org.mercurio.client.feature.entry.wiring

/** Every value the reporting backend is initialised with, resolved once at the wiring boundary. */
data class SentryReportingConfig(
    val dsn: String,
    val release: String,
    val environment: String,
    val consent: ErrorReportingConsent = ErrorReportingConsent.Undecided,
    val tracesSampleRate: Double = SENTRY_TRACES_SAMPLE_RATE,
    val sessionTrackingEnabled: Boolean = SENTRY_SESSION_TRACKING_ENABLED,
    val ignoredErrors: List<String> = SENTRY_IGNORED_ERRORS,
) {
    /**
     * True only when a DSN is configured and the stored decision grants reporting, so neither a
     * DSN-less build nor an unanswered or declined decision ever initialises a backend.
     */
    val isEnabled: Boolean
        get() = dsn.isNotBlank() && consent.isGranted
}

internal const val SENTRY_TRACES_SAMPLE_RATE: Double = 0.0

internal const val SENTRY_SESSION_TRACKING_ENABLED: Boolean = false

internal val SENTRY_IGNORED_ERRORS: List<String> =
    listOf(
        "java.util.concurrent.CancellationException",
        "java.io.InterruptedIOException",
    )

internal fun sentryReportingConfig(
    clientVersion: String,
    consent: ErrorReportingConsent,
    dsn: String = BuildConfig.SENTRY_DSN,
): SentryReportingConfig =
    SentryReportingConfig(
        dsn = dsn,
        release = clientVersion,
        environment = BuildConfig.BUILD_TYPE,
        consent = consent,
    )
