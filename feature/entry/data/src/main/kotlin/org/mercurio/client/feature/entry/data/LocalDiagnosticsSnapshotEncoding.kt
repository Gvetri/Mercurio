package org.mercurio.client.feature.entry.data

/** Outcome of encoding the explicit export snapshot of the retained records. */
sealed interface LocalDiagnosticsSnapshotEncoding {
    /** The encoded snapshot text, for the file named by [LocalDiagnosticsSnapshot.fileName]. */
    data class Encoded(
        val text: String,
    ) : LocalDiagnosticsSnapshotEncoding

    /** The buffer held no records, so no snapshot may be written. */
    data object NoRecords : LocalDiagnosticsSnapshotEncoding

    /** A retained record or the client version is not exportable; the buffer is unchanged. */
    data object Rejected : LocalDiagnosticsSnapshotEncoding
}
