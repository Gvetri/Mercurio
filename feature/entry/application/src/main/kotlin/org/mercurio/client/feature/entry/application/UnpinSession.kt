package org.mercurio.client.feature.entry.application

import org.mercurio.client.feature.entry.domain.SessionGatewayPort
import org.mercurio.client.feature.entry.domain.SessionId
import org.mercurio.client.feature.entry.domain.SessionPinResult

class UnpinSession(
    private val gateway: SessionGatewayPort,
) {
    fun execute(sessionId: SessionId): SessionPinResult = gateway.unpinSession(sessionId)
}
