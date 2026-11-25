package org.mercurio.client.feature.entry.application

import org.mercurio.client.feature.entry.domain.SessionGatewayPort
import org.mercurio.client.feature.entry.domain.SessionId
import org.mercurio.client.feature.entry.domain.SessionPinResult

class PinSession(
    private val gateway: SessionGatewayPort,
) {
    suspend fun execute(sessionId: SessionId): SessionPinResult = gateway.pinSession(sessionId)
}
