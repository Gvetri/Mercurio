package org.mercurio.client.feature.entry.wiring

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mercurio.client.feature.entry.application.ErrorReporter
import org.mercurio.client.feature.entry.application.NoOpErrorReporter
import org.mercurio.client.feature.entry.domain.ErrorReportingConsent
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SentryBackendTest {
    private val context: Application = RuntimeEnvironment.getApplication()

    @Test
    fun a_configured_dsn_and_a_granted_decision_install_the_backend_with_the_declared_options() {
        val installed = mutableListOf<SentryReportingConfig>()
        val backend = AndroidSentryBackend { _, config, _ -> installed += config }
        val config = reportingConfig()

        val reporter = backend.startWithStoredAnswer(context, config)

        assertEquals(listOf(config), installed)
        assertTrue(reporter is SentryErrorReporter)
        assertTrue(config.isEnabled)
        assertFalse(config.sessionTrackingEnabled)
        assertNull(config.tracesSampleRate)
        assertEquals(
            listOf(
                "java.util.concurrent.CancellationException",
                "java.io.InterruptedIOException",
            ),
            config.ignoredErrors,
        )
    }

    @Test
    fun a_decision_that_grants_nothing_never_installs_the_backend() {
        for (consent in listOf(ErrorReportingConsent.Undecided, ErrorReportingConsent.Declined)) {
            val installed = mutableListOf<SentryReportingConfig>()
            val backend = AndroidSentryBackend { _, config, _ -> installed += config }

            val reporter = backend.startWithStoredAnswer(context, reportingConfig().copy(consent = consent))

            assertTrue("A $consent decision installed the backend.", installed.isEmpty())
            assertSame(NoOpErrorReporter, reporter)
        }
    }

    @Test
    fun an_absent_dsn_never_installs_a_backend() {
        var installed: SentryReportingConfig? = null
        val backend = AndroidSentryBackend { _, config, _ -> installed = config }

        val reporter = backend.startWithStoredAnswer(context, reportingConfig().copy(dsn = ""))

        assertNull(installed)
        assertSame(NoOpErrorReporter, reporter)
    }

    @Test
    fun a_reporting_config_that_stores_no_decision_stays_disabled() {
        val config = reportingConfig().copy(consent = ErrorReportingConsent.Undecided)

        assertEquals(ErrorReportingConsent.Undecided, config.consent)
        assertFalse(config.isEnabled)
    }

    @Test
    fun the_dsn_the_environment_and_the_decision_come_from_the_wiring_inputs() {
        val config = sentryReportingConfig(clientVersion = "0.1.0", consent = ErrorReportingConsent.Granted)

        assertEquals(BuildConfig.SENTRY_DSN, config.dsn)
        assertEquals(BuildConfig.BUILD_TYPE, config.environment)
        assertEquals("0.1.0", config.release)
        assertEquals(ErrorReportingConsent.Granted, config.consent)
    }

    @Test
    fun a_backend_that_cannot_install_degrades_to_the_no_op_sink() {
        val backend = AndroidSentryBackend { _, _, _ -> error("InvalidDsnException") }

        val reporter = backend.startWithStoredAnswer(context, reportingConfig())

        assertSame(NoOpErrorReporter, reporter)
    }

    @Test
    fun a_second_start_installs_nothing_further() {
        var installs = 0
        val backend = AndroidSentryBackend { _, _, _ -> installs++ }

        backend.startWithStoredAnswer(context, reportingConfig())
        backend.startWithStoredAnswer(context, reportingConfig())

        assertEquals(1, installs)
    }

    @Test
    fun a_failed_install_keeps_every_later_start_on_the_no_op_sink() {
        val backend = AndroidSentryBackend { _, _, _ -> error("InvalidDsnException") }

        backend.startWithStoredAnswer(context, reportingConfig())

        assertSame(NoOpErrorReporter, backend.startWithStoredAnswer(context, reportingConfig()))
    }

    private fun AndroidSentryBackend.startWithStoredAnswer(
        context: Application,
        config: SentryReportingConfig,
    ): ErrorReporter = start(context, config) { config.consent }

    private fun reportingConfig(): SentryReportingConfig =
        SentryReportingConfig(
            dsn = "https://synthetic-key@ingest.example.invalid/1",
            release = "0.1.0",
            environment = "debug",
            consent = ErrorReportingConsent.Granted,
        )
}
