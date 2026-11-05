package org.mercurio.client.feature.entry.wiring

import android.content.Context
import io.sentry.SentryOptions.BeforeSendCallback
import io.sentry.android.core.SentryAndroid
import io.sentry.android.core.SentryAndroidOptions
import org.mercurio.client.feature.entry.application.ErrorReporter
import org.mercurio.client.feature.entry.application.NoOpErrorReporter
import org.mercurio.client.feature.entry.domain.ErrorReportingConsent
import java.util.concurrent.atomic.AtomicBoolean

fun interface SentryBackend {
    fun start(
        context: Context,
        config: SentryReportingConfig,
        consent: () -> ErrorReportingConsent,
    ): ErrorReporter
}

internal class AndroidSentryBackend(
    private val install: (Context, SentryReportingConfig, () -> ErrorReportingConsent) -> Unit =
        ::installSentryAndroid,
) : SentryBackend {
    private val started = AtomicBoolean(false)
    private val installed = AtomicBoolean(false)

    override fun start(
        context: Context,
        config: SentryReportingConfig,
        consent: () -> ErrorReportingConsent,
    ): ErrorReporter {
        if (!config.isEnabled) return NoOpErrorReporter
        if (started.compareAndSet(false, true)) {
            installed.set(runCatching { install(context, config, consent) }.isSuccess)
        }
        return if (installed.get()) SentryErrorReporter(config, consent = consent) else NoOpErrorReporter
    }
}

internal val defaultSentryBackend: SentryBackend = AndroidSentryBackend()

private fun installSentryAndroid(
    context: Context,
    config: SentryReportingConfig,
    consent: () -> ErrorReportingConsent,
) {
    val scrubber = ReportScrubber()
    SentryAndroid.init(context) { options -> options.applyReporting(config, scrubber, consent) }
}

internal fun SentryAndroidOptions.applyReporting(
    config: SentryReportingConfig,
    scrubber: ReportScrubber,
    consent: () -> ErrorReportingConsent,
) {
    dsn = config.dsn
    release = config.release
    environment = config.environment
    tracesSampleRate = config.tracesSampleRate
    isEnableAutoActivityLifecycleTracing = false
    isEnableUserInteractionTracing = false
    isEnableTimeToFullDisplayTracing = false
    isEnableAutoSessionTracking = config.sessionTrackingEnabled
    isAnrEnabled = true
    isAttachAnrThreadDump = false
    isAttachScreenshot = false
    isAttachViewHierarchy = false
    isSendDefaultPii = false
    setIgnoredErrors(config.ignoredErrors)
    isAttachStacktrace = true
    beforeSend =
        BeforeSendCallback { event, _ ->
            if (consent().isGranted) scrubber.scrub(event) else null
        }
}
