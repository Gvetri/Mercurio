package org.mercurio.client.feature.entry.application

import org.mercurio.client.feature.entry.domain.Session
import org.mercurio.client.feature.entry.domain.SessionGatewayPort

class CreateSession(
    private val gateway: SessionGatewayPort,
) {
    fun execute(title: String?): Session = gateway.createSession(title)
}
