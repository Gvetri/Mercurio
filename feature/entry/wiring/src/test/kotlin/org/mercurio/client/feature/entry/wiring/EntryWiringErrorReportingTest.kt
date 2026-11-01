package org.mercurio.client.feature.entry.wiring

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mercurio.client.feature.entry.application.ErrorReport
import org.mercurio.client.feature.entry.application.RecordingErrorReporter
import org.mercurio.client.feature.entry.domain.NoOpHistoryCacheStore
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
        val holder =
            EntryWiring.createEntryStateHolder(
                context = context,
                overrides =
                    EntryWiringOverrides(
                        coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
                        historyCacheStore = NoOpHistoryCacheStore,
                        errorReporter = sink,
                    ),
                clientVersion = "nightly-42-abc",
            )

        try {
            holder.reportUnexpectedFailure()

            assertEquals(
                listOf(ErrorReport(release = "nightly-42-abc", environment = BuildConfig.BUILD_TYPE)),
                sink.reports(),
            )
        } finally {
            holder.close()
        }
    }
}
