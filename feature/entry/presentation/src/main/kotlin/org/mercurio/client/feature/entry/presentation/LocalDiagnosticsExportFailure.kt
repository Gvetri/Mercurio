package org.mercurio.client.feature.entry.presentation

/** Recoverable failure shown after an export that produced no snapshot. */
enum class LocalDiagnosticsExportFailure(
    val messageRes: Int,
) {
    EXPORT_FAILED(R.string.error_diagnostics_export_failed),
}
