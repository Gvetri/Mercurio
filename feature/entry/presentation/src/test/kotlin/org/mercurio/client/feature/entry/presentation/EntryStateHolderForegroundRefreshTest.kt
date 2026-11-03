package org.mercurio.client.feature.entry.presentation

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mercurio.client.feature.entry.domain.GatewayErrorCategory
import org.mercurio.client.feature.entry.domain.GatewayException
import org.mercurio.client.feature.entry.domain.GatewayHistoryMessage
import org.mercurio.client.feature.entry.domain.Run
import org.mercurio.client.feature.entry.domain.RunId
import org.mercurio.client.feature.entry.domain.SessionHistory
import org.mercurio.client.feature.entry.domain.SessionId
import org.mercurio.client.feature.entry.domain.SessionPage

class EntryStateHolderForegroundRefreshTest {
    @Test
    fun returning_to_the_foreground_replaces_a_stale_transcript() =
        runTest {
            val sessionId = SessionId("session-one")
            val gateway = FakeSessionGateway()
            gateway.enqueueList(SessionPage(listOf(entrySession(sessionId.value, "Session")), null))
            gateway.openedSession = entrySession(sessionId.value, "Session")
            gateway.openedHistory = historyWith("message-old", "user", "Older content")
            val holder = foregroundHolder(gateway)
            holder.onEvent(EntryUiEvent.AppEnteredForeground)
            verify(holder)
            awaitSessions(holder, sessionId.value)
            holder.onEvent(EntryUiEvent.SessionClicked(sessionId))
            awaitState(holder, "the open conversation") { it.sessionList?.openedSession != null }

            gateway.openFailure = GatewayException(GatewayErrorCategory.GATEWAY_REQUEST_FAILED)
            holder.onEvent(EntryUiEvent.RefreshSessionsClicked)
            assertTrue(requireNotNull(holder.uiState.value.sessionList?.openedSession).isStale)

            holder.onEvent(EntryUiEvent.AppEnteredBackground)
            gateway.openFailure = null
            gateway.openedHistory = historyWith("message-new", "assistant", "Latest content")
            holder.onEvent(EntryUiEvent.AppEnteredForeground)

            val opened = requireNotNull(holder.uiState.value.sessionList?.openedSession)
            assertEquals(listOf("Latest content"), opened.messages.map(SessionMessageUiState::content))
            assertFalse(opened.isStale)
            assertNull(opened.errorCategory)
            holder.close()
        }

    @Test
    fun a_failed_foreground_refresh_keeps_the_stale_transcript_and_its_message() =
        runTest {
            val sessionId = SessionId("session-one")
            val gateway = FakeSessionGateway()
            gateway.enqueueList(SessionPage(listOf(entrySession(sessionId.value, "Session")), null))
            gateway.openedSession = entrySession(sessionId.value, "Session")
            gateway.openedHistory = historyWith("message-old", "user", "Older content")
            val holder = foregroundHolder(gateway)
            holder.onEvent(EntryUiEvent.AppEnteredForeground)
            verify(holder)
            awaitSessions(holder, sessionId.value)
            holder.onEvent(EntryUiEvent.SessionClicked(sessionId))
            awaitState(holder, "the open conversation") { it.sessionList?.openedSession != null }

            gateway.openFailure = GatewayException(GatewayErrorCategory.GATEWAY_REQUEST_FAILED)
            holder.onEvent(EntryUiEvent.RefreshSessionsClicked)
            val opensBeforeForeground = gateway.operations.count { operation -> operation == "open" }

            holder.onEvent(EntryUiEvent.AppEnteredBackground)
            holder.onEvent(EntryUiEvent.AppEnteredForeground)

            assertEquals(opensBeforeForeground + 1, gateway.operations.count { operation -> operation == "open" })
            val opened = requireNotNull(holder.uiState.value.sessionList?.openedSession)
            assertEquals(listOf("Older content"), opened.messages.map(SessionMessageUiState::content))
            assertTrue(opened.isStale)
            assertEquals(SessionHistoryErrorCategory.GATEWAY_REQUEST_FAILED, opened.errorCategory)
            assertFalse(opened.isRefreshing)
            holder.close()
        }

    @Test
    fun a_foreground_transition_without_a_gateway_connection_starts_no_request() =
        runTest {
            val gateway = FakeSessionGateway()
            val holder = foregroundHolder(gateway)

            holder.onEvent(EntryUiEvent.AppEnteredForeground)
            holder.onEvent(EntryUiEvent.AppEnteredBackground)
            holder.onEvent(EntryUiEvent.AppEnteredForeground)

            assertTrue(gateway.listRequests.isEmpty())
            assertTrue(gateway.operations.isEmpty())
            holder.close()
        }

    @Test
    fun returning_to_the_foreground_reloads_the_session_list() =
        runTest {
            val gateway = FakeSessionGateway()
            gateway.enqueueList(SessionPage(listOf(entrySession("session-one", "First title")), null))
            val holder = foregroundHolder(gateway)
            holder.onEvent(EntryUiEvent.AppEnteredForeground)
            verify(holder)
            awaitSessions(holder, "session-one")

            gateway.enqueueList(SessionPage(listOf(entrySession("session-one", "Reloaded title")), null))
            holder.onEvent(EntryUiEvent.AppEnteredBackground)
            holder.onEvent(EntryUiEvent.AppEnteredForeground)
            awaitState(holder, "the reloaded Session list") {
                it.sessionList?.sessions?.singleOrNull()?.title == "Reloaded title"
            }

            assertEquals(2, gateway.listRequests.size)
            holder.close()
        }

    @Test
    fun a_second_foreground_event_without_a_background_transition_does_not_refresh_again() =
        runTest {
            val gateway = FakeSessionGateway()
            gateway.enqueueList(SessionPage(listOf(entrySession("session-one", "First title")), null))
            val holder = foregroundHolder(gateway)
            holder.onEvent(EntryUiEvent.AppEnteredForeground)
            verify(holder)
            awaitSessions(holder, "session-one")

            gateway.enqueueList(SessionPage(listOf(entrySession("session-one", "Reloaded title")), null))
            holder.onEvent(EntryUiEvent.AppEnteredBackground)
            holder.onEvent(EntryUiEvent.AppEnteredForeground)
            awaitState(holder, "the reloaded Session list") {
                it.sessionList?.sessions?.singleOrNull()?.title == "Reloaded title"
            }
            holder.onEvent(EntryUiEvent.AppEnteredForeground)

            assertEquals(2, gateway.listRequests.size)
            holder.close()
        }

    @Test
    fun a_foreground_transition_during_an_in_flight_refresh_starts_no_second_request() =
        runTest {
            val gateway = FakeSessionGateway()
            gateway.enqueueList(SessionPage(listOf(entrySession("session-one", "First title")), null))
            gateway.enqueueList(SessionPage(listOf(entrySession("session-one", "Second title")), null))
            val holder = foregroundHolder(gateway, dispatcher = StandardTestDispatcher(testScheduler))
            holder.onEvent(EntryUiEvent.AppEnteredForeground)
            verify(holder)
            advanceUntilIdle()
            assertEquals(1, gateway.listRequests.size)

            holder.onEvent(EntryUiEvent.AppEnteredBackground)
            holder.onEvent(EntryUiEvent.AppEnteredForeground)
            holder.onEvent(EntryUiEvent.AppEnteredBackground)
            holder.onEvent(EntryUiEvent.AppEnteredForeground)
            advanceUntilIdle()

            assertEquals(2, gateway.listRequests.size)
            holder.close()
        }

    @Test
    fun returning_to_the_foreground_resolves_a_run_left_uncertain_while_backgrounded() =
        runTest {
            val sessionId = SessionId("session-one")
            val runId = RunId("run-one")
            val gateway = FakeSessionGateway()
            gateway.enqueueList(SessionPage(listOf(entrySession(sessionId.value, "Session")), null))
            gateway.openedSession = entrySession(sessionId.value, "Session")
            gateway.openedHistory = runHistory(sessionId, runId, "Partial answer", "running")
            val runGateway =
                FakeRunGateway().apply {
                    statusFailure = GatewayException(GatewayErrorCategory.GATEWAY_REQUEST_FAILED)
                }
            val holder = foregroundHolder(gateway, runGateway)
            holder.onEvent(EntryUiEvent.AppEnteredForeground)
            verify(holder)
            awaitSessions(holder, sessionId.value)
            holder.onEvent(EntryUiEvent.SessionClicked(sessionId))
            awaitState(holder, "the uncertain open conversation") {
                it.sessionList?.openedSession?.isStale == true
            }
            val requestsBeforeForeground = runGateway.statusRequests.size
            assertTrue(requestsBeforeForeground > 0)

            holder.onEvent(EntryUiEvent.AppEnteredBackground)
            runGateway.statusFailure = null
            runGateway.statuses += Run(runId, sessionId, "succeeded")
            gateway.openedHistory = runHistory(sessionId, runId, "Latest content", "succeeded")
            holder.onEvent(EntryUiEvent.AppEnteredForeground)

            val opened = requireNotNull(holder.uiState.value.sessionList?.openedSession)
            assertEquals(listOf("Latest content"), opened.messages.map(SessionMessageUiState::content))
            assertFalse(opened.isStale)
            assertNull(opened.errorCategory)
            assertTrue(runGateway.statusRequests.size > requestsBeforeForeground)
            holder.close()
        }

    private fun foregroundHolder(
        gateway: FakeSessionGateway,
        runGateway: FakeRunGateway? = null,
        dispatcher: CoroutineDispatcher = Dispatchers.Unconfined,
    ): EntryStateHolder =
        scriptedHolder(
            gateway = gateway,
            dispatcher = dispatcher,
            runGateway = runGateway,
        )
}

private fun historyWith(
    messageId: String,
    role: String,
    content: String,
): SessionHistory =
    SessionHistory(
        SessionId("session-one"),
        listOf(GatewayHistoryMessage(messageId, role, content)),
    )

private fun runHistory(
    sessionId: SessionId,
    runId: RunId,
    content: String,
    status: String,
): SessionHistory =
    SessionHistory(
        sessionId,
        listOf(GatewayHistoryMessage("message-$status", "assistant", content, runId = runId, runStatus = status)),
    )
