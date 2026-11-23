package org.mercurio.client.feature.entry.domain

/**
 * Records approved operational metadata in the private local diagnostics buffer.
 *
 * The buffer lives only in application-private storage, is excluded from
 * Android backup, and is never uploaded automatically.
 */
interface LocalDiagnosticsRecorder {
    /**
     * Records [event] best-effort.
     *
     * An event whose encoded fields fail safe-field validation is discarded
     * entirely: the buffer never retains, truncates, or repairs a partial
     * record. Recording must never surface a failure to the caller; cancellation is not a failure
     * and propagates. Cancellation is observed at suspension points, so a write that already
     * entered the storage may still complete.
     */
    suspend fun record(event: LocalDiagnosticEvent)
}
