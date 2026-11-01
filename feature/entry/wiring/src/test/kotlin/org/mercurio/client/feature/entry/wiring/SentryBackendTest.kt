package org.mercurio.client.feature.entry.wiring

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mercurio.client.feature.entry.application.NoOpErrorReporter
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SentryBackendTest {
    private val context: Application = RuntimeEnvironment.getApplication()

    @Test
    fun a_configured_dsn_installs_the_backend_with_the_declared_options() {
        val installed = mutableListOf<SentryReportingConfig>()
        val backend = AndroidSentryBackend { _, config -> installed += config }
        val config = reportingConfig()

        val reporter = backend.start(context, config)

        assertEquals(listOf(config), installed)
        assertTrue(reporter is SentryErrorReporter)
        assertTrue(config.isEnabled)
        assertTrue(config.tracesSampleRate < 1.0)
        assertTrue(config.sessionTrackingEnabled)
        assertEquals(
            listOf(
                "java.util.concurrent.CancellationException",
                "java.io.InterruptedIOException",
            ),
            config.ignoredErrors,
        )
    }

    @Test
    fun an_absent_dsn_never_installs_a_backend() {
        var installed: SentryReportingConfig? = null
        val backend = AndroidSentryBackend { _, config -> installed = config }

        val reporter = backend.start(context, reportingConfig().copy(dsn = ""))

        assertNull(installed)
        assertSame(NoOpErrorReporter, reporter)
    }

    @Test
    fun the_dsn_and_the_environment_come_from_the_build_configuration_value() {
        val config = sentryReportingConfig(clientVersion = "0.1.0")

        assertEquals(BuildConfig.SENTRY_DSN, config.dsn)
        assertEquals(BuildConfig.BUILD_TYPE, config.environment)
        assertEquals("0.1.0", config.release)
        assertFalse(config.isEnabled)
    }

    private fun reportingConfig(): SentryReportingConfig =
        SentryReportingConfig(
            dsn = "https://synthetic-key@ingest.example.invalid/1",
            release = "0.1.0",
            environment = "debug",
        )
}
