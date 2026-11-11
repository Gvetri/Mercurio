package org.mercurio.client.feature.entry.presentation

internal data class SessionListStatusUiState(
    val segments: List<UiText>,
    val gateway: UiText,
    val gatewayStatus: GatewayStatus,
)

internal fun SessionListUiState.statusUiState(): SessionListStatusUiState {
    val count = sessions.size
    val cacheLabel = UiText.Resource(if (isCached) R.string.sessions_cached else R.string.sessions_connected)
    val countLabel = UiText.Plural(R.plurals.sessions_count, count, listOf(count, cacheLabel))
    return when {
        isUnavailable || isStale ->
            SessionListStatusUiState(
                segments = listOf(countLabel),
                gateway = UiText.Resource(R.string.sessions_gateway_offline),
                gatewayStatus = GatewayStatus.OFFLINE,
            )
        (isLoading && sessions.isEmpty()) || isRefreshing ->
            SessionListStatusUiState(
                segments = listOf(UiText.Resource(R.string.sessions_connecting)),
                gateway = UiText.Resource(R.string.sessions_gateway_waiting),
                gatewayStatus = GatewayStatus.WAITING,
            )
        isLoadingMore ->
            SessionListStatusUiState(
                segments = listOf(UiText.Resource(R.string.sessions_loading_more), countLabel),
                gateway = UiText.Resource(R.string.sessions_gateway_ready),
                gatewayStatus = GatewayStatus.READY,
            )
        else ->
            SessionListStatusUiState(
                segments = listOf(countLabel),
                gateway = UiText.Resource(R.string.sessions_gateway_ready),
                gatewayStatus = GatewayStatus.READY,
            )
    }
}
