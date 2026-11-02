package org.mercurio.client.feature.entry.application

import org.mercurio.client.feature.entry.domain.GatewayErrorCategory
import org.mercurio.client.feature.entry.domain.GatewayException
import org.mercurio.client.feature.entry.domain.Session
import org.mercurio.client.feature.entry.domain.SessionGatewayPort
import org.mercurio.client.feature.entry.domain.SessionHistory
import org.mercurio.client.feature.entry.domain.SessionId

class OpenSession(
    private val gateway: SessionGatewayPort,
) {
    fun execute(sessionId: SessionId): OpenedSession =
        OpenedSession(
            session = executeSession(sessionId),
            history = executeHistory(sessionId),
        )

    fun executeSession(sessionId: SessionId): Session {
        val session = gateway.openSession(sessionId)
        if (session.id != sessionId) {
            throw invalidResponse("session.id does not match the requested Session")
        }
        return session
    }

    fun executeHistory(sessionId: SessionId): SessionHistory {
        val history = gateway.loadSessionHistory(sessionId)
        if (history.sessionId != sessionId) {
            throw invalidResponse("session_id does not match the requested Session")
        }
        return history
    }

    private fun invalidResponse(detail: String): GatewayException =
        GatewayException(
            GatewayErrorCategory.INVALID_RESPONSE,
            "Invalid Gateway response while opening a Session: $detail.",
        )
}
