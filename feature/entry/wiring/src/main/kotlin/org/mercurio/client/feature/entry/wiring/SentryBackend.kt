package org.mercurio.client.feature.entry.wiring

import android.content.Context
import io.sentry.SentryOptions.BeforeSendCallback
import io.sentry.android.core.SentryAndroid
import org.mercurio.client.feature.entry.application.ErrorReporter
import org.mercurio.client.feature.entry.application.NoOpErrorReporter

fun interface SentryBackend {
    fun start(
        context: Context,
        config: SentryReportingConfig,
    ): ErrorReporter
}

internal class AndroidSentryBackend(
    private val install: (Context, SentryReportingConfig) -> Unit = ::installSentryAndroid,
) : SentryBackend {
    override fun start(
        context: Context,
        config: SentryReportingConfig,
    ): ErrorReporter {
        if (!config.isEnabled) return NoOpErrorReporter
        install(context, config)
        return SentryErrorReporter(config)
    }
}

internal val defaultSentryBackend: SentryBackend = AndroidSentryBackend()

private fun installSentryAndroid(
    context: Context,
    config: SentryReportingConfig,
) {
    val scrubber = ReportScrubber()
    SentryAndroid.init(context) { options ->
        options.dsn = config.dsn
        options.release = config.release
        options.environment = config.environment
        options.tracesSampleRate = config.tracesSampleRate
        options.isEnableAutoSessionTracking = config.sessionTrackingEnabled
        options.setIgnoredErrors(config.ignoredErrors)
        options.isAttachStacktrace = true
        options.beforeSend =
            BeforeSendCallback { event, _ ->
                scrubber.scrub(event)
            }
    }
}
