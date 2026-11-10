package org.mercurio.client.feature.entry.presentation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mercurio.client.feature.entry.domain.Run
import org.mercurio.client.feature.entry.domain.RunId
import org.mercurio.client.feature.entry.domain.RunRecoveryEntry
import org.mercurio.client.feature.entry.domain.SessionHistory
import java.util.concurrent.TimeUnit

class RunReconciliationRecoveryOwnershipTest {
    @Test
    fun an_opened_run_reconcile_skips_a_run_a_persisted_recovery_already_owns() =
        runTest {
            val session = reconciliationSession()
            val run = Run(RunId("run-recovery-claimed"), session.id, "running")
            val entry = RunRecoveryEntry(session.id, run.id)
            val gateway =
                ReconciliationFakeGateway(session).apply {
                    histories.add(SessionHistory(session.id, emptyList()))
                    histories.add(SessionHistory(session.id, emptyList()))
                    statuses.add(run)
                    blockNextStatus = true
                }
            val holder =
                reconciliationHolder(
                    gateway,
                    settings =
                        ReconciliationHolderSettings(
                            Dispatchers.Default,
                        ),
                )
            try {
                openReconciledSession(holder, gateway)
                invokePersistedRecoveryEntry(holder, "https://gateway.example/profile", entry)
                assertTrue(gateway.statusStarted.await(RECONCILIATION_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))

                holder.reconcileOpenedRun(
                    OpenedRunReconcileContext(
                        sessionId = session.id,
                        requestSessionGeneration = holder.sessionRequestGeneration,
                        requestConnectionGeneration = holder.connectionGeneration,
                        sessionGateway = gateway,
                    ),
                )

                assertEquals(listOf(run.id), gateway.statusRequests)
            } finally {
                gateway.releaseStatus.countDown()
                holder.close()
            }
        }
}
