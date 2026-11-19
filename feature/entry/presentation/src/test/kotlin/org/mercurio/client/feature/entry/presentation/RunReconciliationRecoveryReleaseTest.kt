package org.mercurio.client.feature.entry.presentation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mercurio.client.feature.entry.domain.GatewayHistoryMessage
import org.mercurio.client.feature.entry.domain.Run
import org.mercurio.client.feature.entry.domain.RunId
import org.mercurio.client.feature.entry.domain.RunRecoveryEntry
import org.mercurio.client.feature.entry.domain.Session
import org.mercurio.client.feature.entry.domain.SessionHistory
import java.util.concurrent.TimeUnit

class RunReconciliationRecoveryReleaseTest {
    @Test
    fun a_recovery_release_that_races_a_new_entry_keeps_the_session_settled() =
        runTest {
            val session = reconciliationSession()
            val failedEntry = RunRecoveryEntry(session.id, RunId("run-recovery-failed"))
            val successfulRun = Run(RunId("run-recovery-success"), session.id, "succeeded")
            val successfulEntry = RunRecoveryEntry(session.id, successfulRun.id)
            val gateway = racingRecoveryGateway(session, failedEntry, successfulRun)
            val holder =
                reconciliationHolder(
                    gateway,
                    settings = ReconciliationHolderSettings(Dispatchers.Default),
                )
            val racingCounts = ReconciliationRacingCountMap()
            privateField(holder, "connectionRecoverySessionCounts", racingCounts)

            try {
                openReconciledSession(holder, gateway)
                invokePersistedRecoveryEntry(holder, "https://gateway.example/profile", failedEntry)
                val secondEntry =
                    holder.scope.launch {
                        racingCounts.armForCurrentThread()
                        invokePersistedRecoveryEntry(holder, "https://gateway.example/profile", successfulEntry)
                    }
                assertTrue(
                    "The second recovery entry never reached its count write.",
                    racingCounts.entered.await(RECONCILIATION_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS),
                )
                awaitReconciliationCondition { racingCounts.isEmpty() }
                racingCounts.release.countDown()
                secondEntry.join()
                awaitReconciliationState(holder) {
                    it.sessionList?.openedSession?.let { opened ->
                        !opened.isRefreshing &&
                            !opened.isReconciliationInProgress &&
                            opened.hasUnresolvedSubmission &&
                            opened.sendErrorCategory == MessageSendErrorCategory.UNCERTAIN
                    } == true
                }
            } finally {
                racingCounts.release.countDown()
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
