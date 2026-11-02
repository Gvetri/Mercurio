package org.mercurio.client.feature.entry.application

import org.mercurio.client.feature.entry.domain.Session
import org.mercurio.client.feature.entry.domain.SessionGatewayPort
import org.mercurio.client.feature.entry.domain.SessionId

class RenameSession(
    private val gateway: SessionGatewayPort,
) {
    fun execute(
        sessionId: SessionId,
        title: String,
    ): Session = gateway.renameSession(sessionId, title)
}
