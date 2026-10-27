package org.mercurio.client.feature.entry.presentation

import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mercurio.client.feature.entry.domain.GatewayHistoryMessage
import org.mercurio.client.feature.entry.domain.Run
import org.mercurio.client.feature.entry.domain.RunId
import org.mercurio.client.feature.entry.domain.RunPresentationState
import org.mercurio.client.feature.entry.domain.RunRecoveryEntry
import org.mercurio.client.feature.entry.domain.SessionHistory
import org.mercurio.client.feature.entry.domain.SessionId
import java.util.concurrent.TimeUnit

class RunReconciliationTerminalPollTest {
    @Test
    fun empty_event_stream_with_terminal_poll_reconciles_the_observation_and_renders_the_stored_reply() {
        val session = reconciliationSession()
        val run = Run(RunId("run-empty-stream"), session.id, "running")
        val finalHistory =
            SessionHistory(
                session.id,
                listOf(
                    GatewayHistoryMessage("user-1", "user", "Run this", run.id, "running"),
                    GatewayHistoryMessage("answer-1", "assistant", "Stored final reply", run.id, "completed"),
                ),
            )
        val gateway =
            ReconciliationFakeGateway(session).apply {
                histories.add(SessionHistory(session.id, emptyList()))
                histories.add(finalHistory)
                statuses.add(run.copy(status = "completed"))
                runs.add(run)
                observation = ReconciliationScriptedObservation(emptyList())
            }
        val holder = reconciliationHolder(gateway)

        try {
            openReconciledSession(holder, gateway)
            holder.onEvent(EntryUiEvent.ComposerTextChanged("Run this"))
            holder.onEvent(EntryUiEvent.SendMessageClicked)

            awaitReconciliationCondition {
                gateway.observedRunIds.isNotEmpty() && gateway.statusRequests.isNotEmpty()
            }
            assertEquals(listOf(run.id), gateway.observedRunIds)
            assertEquals(listOf(run.id), gateway.statusRequests)

            val opened = requireNotNull(requireNotNull(holder.uiState.value.sessionList).openedSession)
            assertEquals(RunPresentationState.SUCCEEDED, opened.latestRunState)
            assertTrue(opened.messages.any { message -> message.content == "Stored final reply" })
            assertEquals(null, opened.activeResponse)
        } finally {
            holder.close()
        }
    }

    @Test
    fun reopening_the_session_after_an_empty_stream_uncertain_state_clears_on_a_terminal_poll() {
        val session = reconciliationSession()
        val run = Run(RunId("run-reopen"), session.id, "running")
        val finalHistory =
            SessionHistory(
                session.id,
                listOf(
                    GatewayHistoryMessage("user-1", "user", "Run this", run.id, "running"),
                    GatewayHistoryMessage("answer-1", "assistant", "Stored final reply", run.id, "completed"),
                ),
            )
        val gateway =
            ReconciliationFakeGateway(session).apply {
                histories.add(SessionHistory(session.id, emptyList()))
                histories.add(SessionHistory(session.id, emptyList()))
                histories.add(finalHistory)
                histories.add(finalHistory)
                statuses.add(run.copy(status = "running"))
                statuses.add(run.copy(status = "completed"))
                runs.add(run)
                observation = ReconciliationScriptedObservation(emptyList())
            }
        val holder = reconciliationHolder(gateway)

        try {
            openReconciledSession(holder, gateway)
            holder.onEvent(EntryUiEvent.ComposerTextChanged("Run this"))
            holder.onEvent(EntryUiEvent.SendMessageClicked)

            awaitReconciliationState(holder) {
                it.sessionList?.openedSession?.latestRunState == RunPresentationState.UNCERTAIN
            }
            val uncertain =
                requireNotNull(requireNotNull(holder.uiState.value.sessionList).openedSession)
            assertTrue(requireNotNull(uncertain.activeResponse).streamInterrupted)

            holder.onEvent(EntryUiEvent.ReturnToSessionListClicked)
            holder.onEvent(EntryUiEvent.SessionClicked(session.id))
            awaitReconciliationState(holder) {
                it.sessionList?.openedSession?.isReconciliationInProgress == false &&
                    it.sessionList?.openedSession?.session?.id == session.id
            }
            awaitReconciliationCondition { gateway.statusRequests.size >= 2 }

            val opened = requireNotNull(requireNotNull(holder.uiState.value.sessionList).openedSession)
            assertEquals(RunPresentationState.SUCCEEDED, opened.latestRunState)
            assertTrue(opened.messages.any { message -> message.content == "Stored final reply" })
            assertEquals(null, opened.activeResponse)
        } finally {
            holder.close()
        }
    }

    @Test
    fun refreshing_after_an_empty_stream_uncertain_state_clears_on_a_terminal_poll() {
        val session = reconciliationSession()
        val run = Run(RunId("run-refresh"), session.id, "running")
        val finalHistory =
            SessionHistory(
                session.id,
                listOf(
                    GatewayHistoryMessage("user-1", "user", "Run this", run.id, "running"),
                    GatewayHistoryMessage("answer-1", "assistant", "Stored final reply", run.id, "completed"),
                ),
            )
        val gateway =
            ReconciliationFakeGateway(session).apply {
                histories.add(SessionHistory(session.id, emptyList()))
                histories.add(SessionHistory(session.id, emptyList()))
                histories.add(finalHistory)
                histories.add(finalHistory)
                statuses.add(run.copy(status = "running"))
                statuses.add(run.copy(status = "completed"))
                runs.add(run)
                observation = ReconciliationScriptedObservation(emptyList())
            }
        val holder = reconciliationHolder(gateway)

        try {
            openReconciledSession(holder, gateway)
            holder.onEvent(EntryUiEvent.ComposerTextChanged("Run this"))
            holder.onEvent(EntryUiEvent.SendMessageClicked)

            awaitReconciliationState(holder) {
                it.sessionList?.openedSession?.latestRunState == RunPresentationState.UNCERTAIN
            }

            holder.onEvent(EntryUiEvent.RefreshSessionsClicked)
            awaitReconciliationCondition { gateway.statusRequests.size >= 2 }

            val opened = requireNotNull(requireNotNull(holder.uiState.value.sessionList).openedSession)
            assertEquals(RunPresentationState.SUCCEEDED, opened.latestRunState)
            assertTrue(opened.messages.any { message -> message.content == "Stored final reply" })
            assertEquals(null, opened.activeResponse)
        } finally {
            holder.close()
        }
    }

    @Test
    fun terminal_recovery_poll_reconciles_an_uncertain_observation_even_when_the_history_generation_moved() {
        val session = reconciliationSession()
        val run = Run(RunId("run-recovery-poll"), session.id, "running")
        val replyHistory =
            SessionHistory(
                session.id,
                listOf(
                    GatewayHistoryMessage("user-1", "user", "Run this"),
                    GatewayHistoryMessage("answer-1", "assistant", "Stored final reply"),
                ),
            )
        val endpoint = "https://gateway.example/profile"
        val gateway =
            ReconciliationFakeGateway(session).apply {
                histories.add(replyHistory)
                histories.add(SessionHistory(session.id, emptyList()))
                statuses.add(run.copy(status = "running"))
                statuses.add(run.copy(status = "completed"))
                runs.add(run)
                observation = ReconciliationScriptedObservation(emptyList())
            }
        val holder =
            reconciliationHolder(
                gateway,
                settings =
                    ReconciliationHolderSettings(
                        dispatcher = Dispatchers.Default,
                    ),
            )

        try {
            openReconciledSession(holder, gateway)
            holder.onEvent(EntryUiEvent.ComposerTextChanged("Run this"))
            holder.onEvent(EntryUiEvent.SendMessageClicked)

            awaitReconciliationState(holder) {
                it.sessionList?.openedSession?.latestRunState == RunPresentationState.UNCERTAIN
            }
            gateway.blockNextStatus = true
            invokePersistedRecoveryEntry(
                holder,
                endpoint,
                RunRecoveryEntry(sessionId = session.id, runId = run.id),
            )
            assertTrue(gateway.statusStarted.await(RECONCILIATION_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))

            @Suppress("UNCHECKED_CAST")
            val historyGenerations =
                privateField(holder, "authoritativeSessionHistoryGenerations") as MutableMap<SessionId, Long>
            historyGenerations[session.id] = (historyGenerations[session.id] ?: 0L) + 1L

            gateway.releaseStatus.countDown()
            awaitReconciliationCondition { gateway.statusRequests.size >= 2 }

            val opened = requireNotNull(requireNotNull(holder.uiState.value.sessionList).openedSession)
            assertEquals(RunPresentationState.SUCCEEDED, opened.latestRunState)
            assertEquals(null, opened.activeResponse)
            assertTrue(opened.messages.any { message -> message.content == "Stored final reply" })
        } finally {
            gateway.releaseStatus.countDown()
            holder.close()
        }
    }
}
