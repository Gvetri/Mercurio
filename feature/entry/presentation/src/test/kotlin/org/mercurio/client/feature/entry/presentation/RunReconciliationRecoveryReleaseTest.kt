package org.mercurio.client.feature.entry.presentation

import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mercurio.client.feature.entry.domain.GatewayHistoryMessage
import org.mercurio.client.feature.entry.domain.Run
import org.mercurio.client.feature.entry.domain.RunId
import org.mercurio.client.feature.entry.domain.RunRecoveryEntry
import org.mercurio.client.feature.entry.domain.Session
import org.mercurio.client.feature.entry.domain.SessionHistory

class RunReconciliationRecoveryReleaseTest {
    @Test
    fun a_recovery_release_that_races_a_new_entry_keeps_the_session_settled() =
        runTest {
            val session = reconciliationSession()
            val failedEntry = RunRecoveryEntry(session.id, RunId("run-recovery-failed"))
            val successfulRun = Run(RunId("run-recovery-success"), session.id, "succeeded")
            val successfulEntry = RunRecoveryEntry(session.id, successfulRun.id)
            val gateway = racingRecoveryGateway(session, failedEntry, successfulRun)
            val holder = reconciliationHolder(gateway)

            try {
                openReconciledSession(holder, gateway)
                gateway.blockNextStatus = true
                invokePersistedRecoveryEntry(holder, "https://gateway.example/profile", failedEntry)
                advanceUntilIdle()

                invokePersistedRecoveryEntry(holder, "https://gateway.example/profile", successfulEntry)
                advanceUntilIdle()
                gateway.statusStarted.await()

                assertTrue(
                    "An in-flight recovery entry must still hold the session's recovery count.",
                    holder.testSurface.connectionRecoverySessionCounts.containsKey(session.id),
                )
                val inFlight = requireNotNull(requireNotNull(holder.uiState.value.sessionList).openedSession)
                assertTrue(inFlight.hasUnresolvedSubmission)

                gateway.releaseStatus.complete(Unit)
                awaitReconciliationState(holder) {
                    it.sessionList?.openedSession?.let { opened ->
                        !opened.isRefreshing &&
                            !opened.isReconciliationInProgress &&
                            opened.hasUnresolvedSubmission &&
                            opened.sendErrorCategory == MessageSendErrorCategory.UNCERTAIN
                    } == true
                }
                assertTrue(holder.testSurface.connectionRecoverySessionCounts.isEmpty())
                assertTrue(holder.testSurface.connectionRecoveryJobs.isEmpty())
            } finally {
                gateway.releaseStatus.complete(Unit)
                holder.close()
            }
        }

    private fun racingRecoveryGateway(
        session: Session,
        failedEntry: RunRecoveryEntry,
        successfulRun: Run,
    ): ReconciliationFakeGateway =
        ReconciliationFakeGateway(session).apply {
            failStatusRunIds += failedEntry.runId
            statuses.add(successfulRun)
            histories.add(SessionHistory(session.id, emptyList()))
            histories.add(
                SessionHistory(
                    session.id,
                    listOf(
                        GatewayHistoryMessage(
                            "recovery-result",
                            "assistant",
                            "Recovered result",
                            successfulRun.id,
                            "succeeded",
                        ),
                    ),
                ),
            )
        }
}
