package org.mercurio.client.feature.entry.application

import org.mercurio.client.feature.entry.domain.SessionGatewayPort
import org.mercurio.client.feature.entry.domain.SessionId

class DeleteSession(
    private val gateway: SessionGatewayPort,
) {
    fun execute(sessionId: SessionId) = gateway.deleteSession(sessionId)
}
