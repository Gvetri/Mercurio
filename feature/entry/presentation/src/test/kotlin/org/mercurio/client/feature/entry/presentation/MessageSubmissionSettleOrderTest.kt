package org.mercurio.client.feature.entry.presentation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mercurio.client.feature.entry.domain.Run
import org.mercurio.client.feature.entry.domain.RunId
import java.util.concurrent.CountDownLatch
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
            val submissionSettled = CountDownLatch(1)
            val releaseCompletion = CountDownLatch(1)
            val holder =
                holder(
                    gateway,
                    Dispatchers.Default,
                    onRunSubmissionSettled = {
                        submissionSettled.countDown()
                        check(releaseCompletion.await(ASYNC_TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                            "The completion hook was not released."
                        }
                    },
                )
            try {
                open(holder, gateway, session.id)
                holder.onEvent(EntryUiEvent.ComposerTextChanged("Run once"))
                holder.onEvent(EntryUiEvent.SendMessageClicked)
                assertTrue(gateway.runStarted.await(ASYNC_TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))

                gateway.releaseRun.countDown()
                assertTrue(submissionSettled.await(ASYNC_TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))

                val settled = requireNotNull(holder.uiState.value.sessionList?.openedSession)
                assertEquals(run, settled.latestRun)
                assertTrue(settled.isSending)

                releaseCompletion.countDown()
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
                releaseCompletion.countDown()
                gateway.releaseRun.countDown()
                holder.close()
            }
        }
}
