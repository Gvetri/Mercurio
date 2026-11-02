package org.mercurio.client.feature.entry.domain

data class GatewayHistoryMessage(
    val id: String,
    val role: String?,
    val content: String?,
    val runId: RunId? = null,
    val runStatus: String? = null,
    val runResult: String? = null,
    val timestamp: String? = null,
)
