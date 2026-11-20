package org.mercurio.client.feature.entry.presentation

import org.mercurio.client.feature.entry.domain.RunId
import org.mercurio.client.feature.entry.domain.SessionGatewayPort
import org.mercurio.client.feature.entry.domain.SessionId

internal data class OpenedRunReconcileContext(
    val sessionId: SessionId,
    val requestSessionGeneration: Long,
    val requestConnectionGeneration: Long,
    val sessionGateway: SessionGatewayPort,
    val runIdToReconcile: RunId? = null,
    val restartObservation: Boolean = false,
    val clearRefreshWhenNoRun: Boolean = false,
)
