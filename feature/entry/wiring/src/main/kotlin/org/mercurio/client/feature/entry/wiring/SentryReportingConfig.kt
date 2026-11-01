package org.mercurio.client.feature.entry.wiring

/** Every value the reporting backend is initialised with, resolved once at the wiring boundary. */
data class SentryReportingConfig(
    val dsn: String,
    val release: String,
    val environment: String,
    val tracesSampleRate: Double = SENTRY_TRACES_SAMPLE_RATE,
    val sessionTrackingEnabled: Boolean = SENTRY_SESSION_TRACKING_ENABLED,
    val ignoredErrors: List<String> = SENTRY_IGNORED_ERRORS,
) {
    /** True only when a DSN is configured, so a DSN-less build never initialises a backend. */
    val isEnabled: Boolean
        get() = dsn.isNotBlank()
}

internal const val SENTRY_TRACES_SAMPLE_RATE: Double = 0.01

internal const val SENTRY_SESSION_TRACKING_ENABLED: Boolean = true

internal val SENTRY_IGNORED_ERRORS: List<String> =
    listOf(
        "java.util.concurrent.CancellationException",
        "java.io.InterruptedIOException",
    )

internal fun sentryReportingConfig(clientVersion: String): SentryReportingConfig =
    SentryReportingConfig(
        dsn = BuildConfig.SENTRY_DSN,
        release = clientVersion,
        environment = BuildConfig.BUILD_TYPE,
    )
