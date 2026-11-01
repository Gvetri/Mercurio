package org.mercurio.client

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.mercurio.client.feature.entry.application.ErrorReport
import org.mercurio.client.feature.entry.application.RecordingErrorReporter
import org.mercurio.client.feature.entry.presentation.HermesTheme
import org.mercurio.client.feature.entry.wiring.EntryWiring
import org.mercurio.client.feature.entry.wiring.EntryWiringOverrides
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/**
 * Debug-only probe for the error-reporting journey. It binds the recording sink at the
 * port, reports exactly one unexpected failure through the wiring, and delivers what the
 * sink captured to the journey Gateway, so the host-side verifier observes the seam
 * instead of the shipped backend.
 *
 * The flow reaches it over the `mercurio-journey://error-reporting` deep link; a release
 * build carries neither this activity nor the deep link.
 */
class ErrorReportingProbeActivity : ComponentActivity() {
    private var status by mutableStateOf(PROBE_PENDING)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HermesTheme {
                Text(status, modifier = Modifier.padding(24.dp))
            }
        }
        thread(name = "error-reporting-probe") {
            val live = intent?.data?.getQueryParameter(LIVE_PARAMETER) == LIVE_VALUE
            val captured = runCatching { if (live) captureLive() else captureAndDeliver() }.getOrDefault(false)
            runOnUiThread {
                status =
                    when {
                        live && captured -> LIVE_CAPTURED
                        live -> LIVE_FAILED
                        captured -> PROBE_CAPTURED
                        else -> PROBE_FAILED
                    }
            }
        }
    }

    private fun captureLive(): Boolean {
        val holder = EntryWiring.createEntryStateHolder(context = applicationContext)
        return try {
            holder.reportUnexpectedFailure()
            true
        } finally {
            holder.close()
        }
    }

    private fun captureAndDeliver(): Boolean {
        val sink = RecordingErrorReporter()
        val holder =
            EntryWiring.createEntryStateHolder(
                context = applicationContext,
                overrides = EntryWiringOverrides(errorReporter = sink),
            )
        return try {
            holder.reportUnexpectedFailure()
            sink.reports().takeIf(List<ErrorReport>::isNotEmpty)?.let(::deliver)
                ?: false
        } finally {
            holder.close()
        }
    }

    private fun deliver(reports: List<ErrorReport>): Boolean {
        val body = reports.joinToString(",", prefix = "[", postfix = "]") { report ->
            """{"release":${report.release.asJsonString()},"environment":${report.environment.asJsonString()}}"""
        }
        val connection = URL(JOURNEY_ERROR_REPORT_URL).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.connectTimeout = DELIVERY_TIMEOUT_MILLIS
            connection.readTimeout = DELIVERY_TIMEOUT_MILLIS
            connection.outputStream.use { output -> output.write(body.toByteArray(Charsets.UTF_8)) }
            connection.responseCode == HttpURLConnection.HTTP_ACCEPTED
        } finally {
            connection.disconnect()
        }
    }
}

private fun String.asJsonString(): String =
    buildString {
        append('"')
        this@asJsonString.forEach { character ->
            when (character) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                else -> append(character)
            }
        }
        append('"')
    }

private const val JOURNEY_ERROR_REPORT_URL = "https://10.0.2.2:18443/__fixture/error-report"
private const val LIVE_PARAMETER = "live"
private const val LIVE_VALUE = "1"
private const val DELIVERY_TIMEOUT_MILLIS = 10_000
private const val PROBE_PENDING = "Capturing error report"
private const val PROBE_CAPTURED = "Error report captured"
private const val PROBE_FAILED = "Error report capture failed"
private const val LIVE_CAPTURED = "Live error report raised"
private const val LIVE_FAILED = "Live error report not raised"
