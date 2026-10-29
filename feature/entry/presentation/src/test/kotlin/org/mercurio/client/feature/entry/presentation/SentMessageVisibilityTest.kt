package org.mercurio.client.feature.entry.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mercurio.client.feature.entry.domain.GatewayHistoryMessage
import org.mercurio.client.feature.entry.domain.Run
import org.mercurio.client.feature.entry.domain.RunId
import org.mercurio.client.feature.entry.domain.SessionHistory

class SentMessageVisibilityTest {
    @Test
    fun a_sent_user_message_is_visible_in_the_transcript_before_the_run_answers() {
        val session = session("session-1")
        val history =
            SessionHistory(
                session.id,
                listOf(
                    GatewayHistoryMessage("message-1", "user", "First message", RunId("run-1"), "completed"),
                    GatewayHistoryMessage("message-2", "assistant", "First answer", RunId("run-1"), "completed"),
                ),
            )
        val run = Run(RunId("run-2"), session.id, "starting")
        val gateway =
            FakeGateway(listOf(session), histories = mapOf(session.id to history)).apply {
                enqueueRun(run)
                setRunStatus(Run(RunId("run-1"), session.id, "completed"))
            }
        val holder = holder(gateway)

        try {
            open(holder, gateway, session.id)
            holder.onEvent(EntryUiEvent.ComposerTextChanged("Second message"))
            holder.onEvent(EntryUiEvent.SendMessageClicked)

            val opened = requireNotNull(requireNotNull(holder.uiState.value.sessionList).openedSession)
            assertEquals(
                "the send must reach the Gateway for this transcript to mean anything",
                listOf(session.id to "Second message"),
                gateway.runRequests,
            )
            val displayedMessages = opened.messages + listOfNotNull(opened.activeResponse)
            assertTrue(
                "sent message must be displayed while the Run is starting: $displayedMessages",
                displayedMessages.any { it.role == "user" && it.content == "Second message" },
            )
        } finally {
            holder.close()
        }
    }

    @Test
    fun remote_history_replaces_the_local_row_without_duplicating_the_sent_message() {
        val session = session("session-1")
        val historyBefore =
            SessionHistory(
                session.id,
                listOf(
                    GatewayHistoryMessage("message-1", "user", "First message", RunId("run-1"), "completed"),
                    GatewayHistoryMessage("message-2", "assistant", "First answer", RunId("run-1"), "completed"),
                ),
            )
        val historyAfter =
            SessionHistory(
                session.id,
                historyBefore.messages +
                    listOf(
                        GatewayHistoryMessage("message-3", "user", "Second message", RunId("run-2"), "completed"),
                        GatewayHistoryMessage("message-4", "assistant", "Second answer", RunId("run-2"), "completed"),
                    ),
            )
        val run = Run(RunId("run-2"), session.id, "completed")
        val gateway =
            FakeGateway(listOf(session), histories = mapOf(session.id to historyBefore)).apply {
                enqueueRun(run)
                setRunStatus(Run(RunId("run-1"), session.id, "completed"))
                setRunStatus(run)
            }
        val holder = holder(gateway)

        try {
            open(holder, gateway, session.id)
            gateway.setHistory(historyAfter)
            holder.onEvent(EntryUiEvent.ComposerTextChanged("Second message"))
            holder.onEvent(EntryUiEvent.SendMessageClicked)

            awaitState(holder) { state ->
                state.sessionList?.openedSession
                    ?.messages
                    ?.any { it.role == "user" && it.content == "Second message" } == true
            }

            val messages =
                requireNotNull(requireNotNull(holder.uiState.value.sessionList).openedSession).messages
            val sentRows = messages.filter { it.content == "Second message" }
            assertEquals(
                "the remote history must replace the local row, not add a second copy: $sentRows",
                1,
                sentRows.size,
            )
            assertEquals(
                "the surviving row must be the authoritative remote message",
                "message-3",
                sentRows.single().id,
            )
        } finally {
            holder.close()
        }
    }
}
