package org.mercurio.client.feature.entry.presentation

import org.mercurio.client.feature.entry.domain.SessionGatewayPort
import org.mercurio.client.feature.entry.domain.SessionId

internal data class SessionMutationRequest(
    val sessionId: SessionId,
    val gateway: SessionGatewayPort,
    val connectionGeneration: Long,
    val attemptId: Long,
)
