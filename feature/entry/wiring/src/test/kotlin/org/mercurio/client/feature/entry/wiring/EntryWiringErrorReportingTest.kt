package org.mercurio.client.feature.entry.wiring

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mercurio.client.feature.entry.application.ErrorReport
import org.mercurio.client.feature.entry.application.NoOpErrorReporter
import org.mercurio.client.feature.entry.application.RecordingErrorReporter
import org.mercurio.client.feature.entry.domain.ErrorReportingConsent
import org.mercurio.client.feature.entry.domain.NoOpHistoryCacheStore
import org.mercurio.client.feature.entry.presentation.EntryUiEvent
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EntryWiringErrorReportingTest {
    private val context: Application = RuntimeEnvironment.getApplication()

    @Test
    fun the_bound_sink_receives_one_report_carrying_the_client_version() {
        val sink = RecordingErrorReporter()

        val reporting =
            EntryWiring.createErrorReporting(
                context = context,
                overrides =
                    EntryWiringOverrides(
                        errorReporter = sink,
                        installedBuildProvider = FakeInstalledBuildProvider(versionName = "nightly-42-abc"),
                    ),
            )
        reporting.reportUnexpectedFailure()

        assertEquals(
            listOf(ErrorReport(release = "nightly-42-abc", environment = BuildConfig.BUILD_TYPE)),
            sink.reports(),
        )
    }

    @Test
    fun a_granted_decision_starts_the_backend_with_the_stored_answer() {
        val consult = mutableListOf<SentryReportingConfig>()
        val store = FakeErrorReportingConsentStore(ErrorReportingConsent.Granted)
        val backend =
            SentryBackend { _, config ->
                consult += config
                NoOpErrorReporter
            }

        EntryWiring.createErrorReporting(
            context = context,
            overrides =
                EntryWiringOverrides(
                    sentryBackend = backend,
                    errorReportingConsentStore = store,
                    installedBuildProvider = FakeInstalledBuildProvider(versionName = "0.1.0"),
                    sentryDsn = SYNTHETIC_DSN,
                ),
        )

        assertEquals(1, store.reads)
        assertEquals(
            listOf(
                SentryReportingConfig(
                    dsn = SYNTHETIC_DSN,
                    release = "0.1.0",
                    environment = BuildConfig.BUILD_TYPE,
                    consent = ErrorReportingConsent.Granted,
                ),
            ),
            consult,
        )
    }

    @Test
    fun a_decision_that_grants_nothing_never_reaches_the_backend() {
        for (consent in listOf(ErrorReportingConsent.Undecided, ErrorReportingConsent.Declined)) {
            val started = mutableListOf<SentryReportingConfig>()
            val store = FakeErrorReportingConsentStore(consent)
            val backend =
                SentryBackend { _, config ->
                    started += config
                    NoOpErrorReporter
                }

            val reporting =
                EntryWiring.createErrorReporting(
                    context = context,
                    overrides =
                        EntryWiringOverrides(
                            sentryBackend = backend,
                            errorReportingConsentStore = store,
                            installedBuildProvider = FakeInstalledBuildProvider(versionName = "0.1.0"),
                            sentryDsn = SYNTHETIC_DSN,
                        ),
                )
            reporting.reportUnexpectedFailure()

            assertEquals(1, store.reads)
            assertTrue("A $consent decision started the backend.", started.isEmpty())
        }
    }

    @Test
    fun the_composition_root_binds_the_port_for_every_installation() {
        val consult = mutableListOf<SentryReportingConfig>()
        val store = FakeErrorReportingConsentStore(ErrorReportingConsent.Granted)
        val backend =
            SentryBackend { _, config ->
                consult += config
                NoOpErrorReporter
            }

        val holder =
            EntryWiring.createEntryStateHolder(
                context = context,
                overrides =
                    EntryWiringOverrides(
                        coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
                        historyCacheStore = NoOpHistoryCacheStore,
                        sentryBackend = backend,
                        errorReportingConsentStore = store,
                        installedBuildProvider = FakeInstalledBuildProvider(versionName = "0.1.0"),
                        sentryDsn = SYNTHETIC_DSN,
                    ),
            )

        try {
            assertTrue(store.reads >= 1)
            assertFalse(holder.uiState.value.isErrorReportingConsentRequired)
            assertEquals(
                listOf(
                    SentryReportingConfig(
                        dsn = SYNTHETIC_DSN,
                        release = "0.1.0",
                        environment = BuildConfig.BUILD_TYPE,
                        consent = ErrorReportingConsent.Granted,
                    ),
                ),
                consult,
            )
        } finally {
            holder.close()
        }
    }

    @Test
    fun a_fresh_install_asks_the_question_and_the_answer_survives_a_restart() {
        val first = EntryWiring.createEntryStateHolder(context = context)
        try {
            assertTrue(first.uiState.value.isErrorReportingConsentRequired)
            first.onEvent(EntryUiEvent.ErrorReportingConsentDeclined)
        } finally {
            first.close()
        }

        assertEquals(
            ErrorReportingConsent.Declined,
            SharedPreferencesErrorReportingConsentStore(context).read(),
        )

        val restarted = EntryWiring.createEntryStateHolder(context = context)
        try {
            assertFalse(restarted.uiState.value.isErrorReportingConsentRequired)
        } finally {
            restarted.close()
        }
    }
}

private const val SYNTHETIC_DSN = "https://synthetic-key@ingest.example.invalid/1"
