package org.mercurio.client.feature.entry.presentation

enum class SessionHistoryErrorCategory(
    val messageRes: Int,
) {
    GATEWAY_REQUEST_FAILED(R.string.error_history_refresh_failed),
    RECONCILIATION_FAILED(R.string.error_reconciliation_failed),
}
