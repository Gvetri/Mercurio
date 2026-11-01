package org.mercurio.client.feature.entry.wiring

import io.sentry.Hint
import io.sentry.SentryEvent
import io.sentry.android.core.SentryAndroidOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SentryReportingOptionsTest {
    @Test
    fun the_reporting_config_maps_onto_the_android_options() {
        val options = SentryAndroidOptions()
        val config = reportingConfig()

        options.applyReporting(config, ReportScrubber())

        assertEquals(config.dsn, options.dsn)
        assertEquals(config.release, options.release)
        assertEquals(config.environment, options.environment)
        assertEquals(config.tracesSampleRate, options.tracesSampleRate ?: 0.0, 0.0)
        assertEquals(config.sessionTrackingEnabled, options.isEnableAutoSessionTracking)
        assertEquals(
            config.ignoredErrors,
            options.ignoredErrors?.map { pattern -> pattern.filterString },
        )
        assertTrue(options.isAttachStacktrace)
    }

    @Test
    fun the_session_tracking_toggle_reaches_the_options() {
        val options = SentryAndroidOptions()

        options.applyReporting(reportingConfig().copy(sessionTrackingEnabled = false), ReportScrubber())

        assertFalse(options.isEnableAutoSessionTracking)
    }

    @Test
    fun before_send_routes_every_event_through_the_scrubber() {
        val options = SentryAndroidOptions()
        options.applyReporting(reportingConfig(), ReportScrubber())
        val beforeSend = requireNotNull(options.beforeSend) { "The reporting options install no before-send hook." }
        val event = SentryEvent().apply { serverName = "gateway.internal.example" }

        val scrubbed = beforeSend.execute(event, Hint())

        assertNull(scrubbed?.serverName)
    }

    private fun reportingConfig(): SentryReportingConfig =
        SentryReportingConfig(
            dsn = "https://synthetic-key@ingest.example.invalid/1",
            release = "0.1.0",
            environment = "debug",
        )
}
