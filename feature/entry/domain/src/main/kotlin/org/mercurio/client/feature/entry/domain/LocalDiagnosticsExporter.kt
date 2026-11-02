package org.mercurio.client.feature.entry.domain

/**
 * Writes an explicit export snapshot of the buffered records and offers it to
 * the user through the platform Sharesheet.
 */
interface LocalDiagnosticsExporter {
    /**
     * Exports the current buffer.
     *
     * The snapshot never clears, mutates, or appends a second copy to the
     * buffer, so a failed export preserves it for another attempt.
     */
    fun export(): LocalDiagnosticsExportResult
}
