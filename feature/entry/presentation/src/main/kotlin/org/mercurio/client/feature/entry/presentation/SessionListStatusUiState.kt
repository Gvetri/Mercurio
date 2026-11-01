package org.mercurio.client.feature.entry.presentation

internal enum class GatewayStatus { READY, WAITING, OFFLINE }

internal data class SessionListStatusUiState(
    val segments: List<String>,
    val gateway: String,
    val gatewayStatus: GatewayStatus,
)

internal fun SessionListUiState.statusUiState(): SessionListStatusUiState {
    val count = sessions.size
    val noun = if (count == 1) "session" else "sessions"
    val cacheLabel = if (isCached) "cached" else "connected"
    val countLabel = "$count $noun · $cacheLabel"
    return when {
        isUnavailable || isStale ->
            SessionListStatusUiState(
                segments = listOf(countLabel),
                gateway = "gateway :: offline",
                gatewayStatus = GatewayStatus.OFFLINE,
            )
        (isLoading && sessions.isEmpty()) || isRefreshing ->
            SessionListStatusUiState(
                segments = listOf("connecting…"),
                gateway = "gateway :: waiting",
                gatewayStatus = GatewayStatus.WAITING,
            )
        isLoadingMore ->
            SessionListStatusUiState(
                segments = listOf("loading more…", countLabel),
                gateway = "gateway :: ready",
                gatewayStatus = GatewayStatus.READY,
            )
        else ->
            SessionListStatusUiState(
                segments = listOf(countLabel),
                gateway = "gateway :: ready",
                gatewayStatus = GatewayStatus.READY,
            )
    }
}
