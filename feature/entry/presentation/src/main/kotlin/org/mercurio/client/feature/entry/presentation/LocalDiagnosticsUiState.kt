package org.mercurio.client.feature.entry.presentation

/**
 * State of the Local diagnostics surface: a private, redacted buffer of approved
 * operational records with explicit export and clear actions.
 */
data class LocalDiagnosticsUiState(
    val isOpen: Boolean = false,
    val isLoadingRecords: Boolean = false,
    val recordCount: Int = 0,
    val isExporting: Boolean = false,
    val exportFailure: LocalDiagnosticsExportFailure? = null,
    val isClearConfirmationOpen: Boolean = false,
) {
    /** True when the buffer holds records, so an export can produce a snapshot. */
    val hasRecords: Boolean
        get() = recordCount > 0

    /** Export stays disabled until the buffer holds records and no export is running. */
    val isExportEnabled: Boolean
        get() = hasRecords && !isExporting
}
