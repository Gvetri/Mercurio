package org.mercurio.client.feature.entry.domain

interface SessionGatewayPort {
    suspend fun listSessions(request: SessionListRequest): SessionPage

    suspend fun listSessions(): SessionPage = listSessions(SessionListRequest())

    suspend fun createSession(title: String?): Session

    suspend fun openSession(sessionId: SessionId): Session

    suspend fun loadSessionHistory(sessionId: SessionId): SessionHistory

    suspend fun renameSession(
        sessionId: SessionId,
        title: String,
    ): Session

    suspend fun deleteSession(sessionId: SessionId)

    suspend fun pinSession(sessionId: SessionId): SessionPinResult

    suspend fun unpinSession(sessionId: SessionId): SessionPinResult
}
