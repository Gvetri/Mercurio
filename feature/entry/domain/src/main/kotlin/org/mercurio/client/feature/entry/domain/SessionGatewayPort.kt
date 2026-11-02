package org.mercurio.client.feature.entry.domain

interface SessionGatewayPort {
    fun listSessions(request: SessionListRequest): SessionPage

    fun listSessions(): SessionPage = listSessions(SessionListRequest())

    fun createSession(title: String?): Session

    fun openSession(sessionId: SessionId): Session

    fun loadSessionHistory(sessionId: SessionId): SessionHistory

    fun renameSession(
        sessionId: SessionId,
        title: String,
    ): Session

    fun deleteSession(sessionId: SessionId)

    fun pinSession(sessionId: SessionId): SessionPinResult

    fun unpinSession(sessionId: SessionId): SessionPinResult
}
