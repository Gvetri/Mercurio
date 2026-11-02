package org.mercurio.client.feature.entry.domain

/** Outcome of an explicit diagnostics export. */
enum class LocalDiagnosticsExportResult {
    /** The snapshot was written and offered through the Sharesheet. */
    SHARED,

    /** The buffer held no records, so no snapshot was written. */
    EMPTY,

    /** No snapshot could be shared; the buffer is unchanged. */
    FAILED,
}
