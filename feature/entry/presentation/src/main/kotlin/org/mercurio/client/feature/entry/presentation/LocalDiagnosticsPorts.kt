package org.mercurio.client.feature.entry.presentation

import org.mercurio.client.feature.entry.domain.LocalDiagnosticsExporter
import org.mercurio.client.feature.entry.domain.LocalDiagnosticsRecorder
import org.mercurio.client.feature.entry.domain.LocalDiagnosticsStore

/**
 * The ports the Local diagnostics surface needs, grouped so one buffer collection
 * serves all of them: the recorder appends approved events, the store reports and
 * clears them, and the exporter shares a snapshot of what the buffer holds.
 */
data class LocalDiagnosticsPorts(
    val recorder: LocalDiagnosticsRecorder,
    val store: LocalDiagnosticsStore,
    val exporter: LocalDiagnosticsExporter,
)
