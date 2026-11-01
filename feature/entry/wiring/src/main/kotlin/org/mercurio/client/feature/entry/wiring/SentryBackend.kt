package org.mercurio.client.feature.entry.wiring

import android.content.Context
import io.sentry.SentryOptions.BeforeSendCallback
import io.sentry.android.core.SentryAndroid
import io.sentry.android.core.SentryAndroidOptions
import org.mercurio.client.feature.entry.application.ErrorReporter
import org.mercurio.client.feature.entry.application.NoOpErrorReporter
import java.util.concurrent.atomic.AtomicBoolean

fun interface SentryBackend {
    fun start(
        context: Context,
        config: SentryReportingConfig,
    ): ErrorReporter
}

internal class AndroidSentryBackend(
    private val install: (Context, SentryReportingConfig) -> Unit = ::installSentryAndroid,
) : SentryBackend {
    private val started = AtomicBoolean(false)
    private val installed = AtomicBoolean(false)

    override fun start(
        context: Context,
        config: SentryReportingConfig,
    ): ErrorReporter {
        if (!config.isEnabled) return NoOpErrorReporter
        if (started.compareAndSet(false, true)) {
            installed.set(runCatching { install(context, config) }.isSuccess)
        }
        return if (installed.get()) SentryErrorReporter(config) else NoOpErrorReporter
    }
}

internal val defaultSentryBackend: SentryBackend = AndroidSentryBackend()

private fun installSentryAndroid(
    context: Context,
    config: SentryReportingConfig,
) {
    val scrubber = ReportScrubber()
    SentryAndroid.init(context) { options -> options.applyReporting(config, scrubber) }
}

internal fun SentryAndroidOptions.applyReporting(
    config: SentryReportingConfig,
    scrubber: ReportScrubber,
) {
    dsn = config.dsn
    release = config.release
    environment = config.environment
    tracesSampleRate = config.tracesSampleRate
    isEnableAutoSessionTracking = config.sessionTrackingEnabled
    setIgnoredErrors(config.ignoredErrors)
    isAttachStacktrace = true
    beforeSend =
        BeforeSendCallback { event, _ ->
            scrubber.scrub(event)
        }
}
