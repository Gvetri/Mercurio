package org.mercurio.client.feature.entry.wiring

import io.sentry.Breadcrumb
import io.sentry.Hint
import io.sentry.SentryEvent
import io.sentry.android.core.SentryAndroidOptions
import io.sentry.protocol.SentryException
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
    fun the_declared_defaults_leave_session_tracking_and_tracing_off() {
        val config = reportingConfig()

        assertFalse(config.sessionTrackingEnabled)
        assertEquals(0.0, config.tracesSampleRate, 0.0)
    }

    @Test
    fun the_applied_options_carry_crash_error_and_not_responding_reports_only() {
        val options = SentryAndroidOptions()

        options.applyReporting(reportingConfig(), ReportScrubber())

        assertFalse(options.isEnableAutoSessionTracking)
        assertEquals(0.0, options.tracesSampleRate ?: 0.0, 0.0)
        assertFalse(options.isProfilingEnabled)
        assertFalse(options.isSendDefaultPii)
        assertFalse(options.isAttachScreenshot)
        assertFalse(options.isAttachViewHierarchy)
        assertTrue(options.isAnrEnabled)
        assertFalse(options.isAttachAnrThreadDump)
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

    @Test
    fun an_anr_event_keeps_its_thread_stacks_and_loses_only_its_context() {
        val options = SentryAndroidOptions()
        options.applyReporting(reportingConfig(), ReportScrubber())
        val beforeSend = requireNotNull(options.beforeSend) { "The reporting options install no before-send hook." }
        val notResponding =
            SentryException().apply {
                type = "ANR"
                value = "Application Not Responding"
            }
        val event =
            SentryEvent().apply {
                exceptions = listOf(notResponding)
                breadcrumbs = listOf(Breadcrumb("draft text typed into the composer"))
            }

        val scrubbed = beforeSend.execute(event, Hint())

        assertEquals(listOf(notResponding), scrubbed?.exceptions)
        assertNull(scrubbed?.breadcrumbs)
    }

    private fun reportingConfig(): SentryReportingConfig =
        SentryReportingConfig(
            dsn = "https://synthetic-key@ingest.example.invalid/1",
            release = "0.1.0",
            environment = "debug",
            consent = ErrorReportingConsent.Granted,
        )
}
