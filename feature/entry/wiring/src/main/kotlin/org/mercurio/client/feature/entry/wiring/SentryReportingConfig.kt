package org.mercurio.client.feature.entry.wiring

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
    val isEnabled: Boolean
        get() = dsn.isNotBlank() && consent.isGranted
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
        dsn = dsn ?: BuildConfig.SENTRY_DSN,
        release = clientVersion,
        environment = BuildConfig.BUILD_TYPE,
        consent = consent,
    )
