package org.mercurio.client.feature.entry.presentation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mercurio.client.feature.entry.domain.Run
import org.mercurio.client.feature.entry.domain.RunId
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.TimeUnit

class MessageSubmissionSettleOrderTest {
    @Test
    fun the_open_session_keeps_its_sending_state_until_the_submission_job_completes() =
        runTest {
            val session = session("session-1")
            val run = Run(RunId("run-1"), session.id, "starting")
            val gateway =
                FakeGateway(listOf(session)).apply {
                    enqueueRun(run)
                    blockRunCreation = true
                }
            val submissionSettled = CompletableDeferred<Unit>()
            val releaseCompletion = CompletableDeferred<Unit>()
            val holder =
                holder(
                    gateway,
                    onRunSubmissionSettled = {
                        submissionSettled.complete(Unit)
                        releaseCompletion.await()
                    },
                )
            try {
                open(holder, gateway, session.id)
                holder.onEvent(EntryUiEvent.ComposerTextChanged("Run once"))
                holder.onEvent(EntryUiEvent.SendMessageClicked)
                gateway.runStarted.await()

                gateway.releaseRun.complete(Unit)
                submissionSettled.await()

                val settled = requireNotNull(holder.uiState.value.sessionList?.openedSession)
                assertEquals(run, settled.latestRun)
                assertTrue(settled.isSending)

                releaseCompletion.complete(Unit)
                awaitState(holder) { it.sessionList?.openedSession?.isSending == false }

                holder.onEvent(EntryUiEvent.ReturnToSessionListClicked)
                holder.onEvent(EntryUiEvent.SessionClicked(session.id))
                var reopened: OpenSessionUiState? = null
                awaitState(holder) {
                    val opened = it.sessionList?.openedSession
                    val matching =
                        opened != null &&
                            opened.session.id == session.id &&
                            !opened.isRefreshing &&
                            opened.latestRun == run
                    if (matching) {
                        reopened = opened
                    }
                    matching
                }

                val stableReopen = requireNotNull(reopened)
                assertEquals(run, stableReopen.latestRun)
                assertFalse(stableReopen.isSending)
            } finally {
                releaseCompletion.complete(Unit)
                gateway.releaseRun.complete(Unit)
                holder.close()
            }
        }
}
