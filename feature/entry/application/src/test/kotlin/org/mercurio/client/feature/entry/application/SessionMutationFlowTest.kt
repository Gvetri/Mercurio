package org.mercurio.client.feature.entry.application

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mercurio.client.feature.entry.domain.Session
import org.mercurio.client.feature.entry.domain.SessionGatewayPort
import org.mercurio.client.feature.entry.domain.SessionHistory
import org.mercurio.client.feature.entry.domain.SessionId
import org.mercurio.client.feature.entry.domain.SessionListRequest
import org.mercurio.client.feature.entry.domain.SessionPage
import org.mercurio.client.feature.entry.domain.SessionPinResult

class SessionMutationFlowTest {
    @Test
    fun session_mutation_use_cases_forward_the_exact_gateway_operations() =
        runBlocking {
            val sessionId = SessionId("session-one")
            val renamed = session(sessionId, "Renamed")
            val gateway = RecordingSessionGateway(renamed)

            assertEquals(renamed, RenameSession(gateway).execute(sessionId, "Renamed"))
            assertEquals(Unit, DeleteSession(gateway).execute(sessionId))
            assertEquals(SessionPinResult(sessionId, pinned = true), PinSession(gateway).execute(sessionId))
            assertEquals(SessionPinResult(sessionId, pinned = false), UnpinSession(gateway).execute(sessionId))
            assertEquals(
                listOf(
                    "rename:session-one:Renamed",
                    "delete:session-one",
                    "pin:session-one",
                    "unpin:session-one",
                ),
                gateway.operations,
            )
        }

    private class RecordingSessionGateway(
        private val renamed: Session,
    ) : SessionGatewayPort {
        val operations = mutableListOf<String>()

        override suspend fun listSessions(request: SessionListRequest): SessionPage = SessionPage(emptyList(), null)

        override suspend fun createSession(title: String?): Session = error("not used")

        override suspend fun openSession(sessionId: SessionId): Session = error("not used")

        override suspend fun loadSessionHistory(sessionId: SessionId): SessionHistory = error("not used")

        override suspend fun renameSession(
            sessionId: SessionId,
            title: String,
        ): Session {
            operations += "rename:${sessionId.value}:$title"
            return renamed
        }

        override suspend fun deleteSession(sessionId: SessionId) {
            operations += "delete:${sessionId.value}"
        }

        override suspend fun pinSession(sessionId: SessionId): SessionPinResult {
            operations += "pin:${sessionId.value}"
            return SessionPinResult(sessionId, pinned = true)
        }

        override suspend fun unpinSession(sessionId: SessionId): SessionPinResult {
            operations += "unpin:${sessionId.value}"
            return SessionPinResult(sessionId, pinned = false)
        }
    }

    private fun session(
        id: SessionId,
        title: String,
    ): Session =
        Session(
            id = id,
            title = title,
            preview = "Preview",
            pinned = false,
        )
}
