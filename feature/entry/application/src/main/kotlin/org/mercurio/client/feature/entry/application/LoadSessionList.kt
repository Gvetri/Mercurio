package org.mercurio.client.feature.entry.application

import org.mercurio.client.feature.entry.domain.SessionGatewayPort
import org.mercurio.client.feature.entry.domain.SessionListRequest
import org.mercurio.client.feature.entry.domain.SessionPage

class LoadSessionList(
    private val gateway: SessionGatewayPort,
) {
    fun execute(request: SessionListRequest = SessionListRequest()): SessionPage = gateway.listSessions(request)
}
