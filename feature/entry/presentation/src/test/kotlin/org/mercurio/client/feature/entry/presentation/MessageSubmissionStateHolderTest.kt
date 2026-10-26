package org.mercurio.client.feature.entry.presentation

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mercurio.client.feature.entry.data.DefaultGatewayConnectionRepository
import org.mercurio.client.feature.entry.data.InMemoryGatewayConnectionDataSource
import org.mercurio.client.feature.entry.domain.GatewayErrorCategory
import org.mercurio.client.feature.entry.domain.GatewayException
import org.mercurio.client.feature.entry.domain.GatewayHistoryMessage
import org.mercurio.client.feature.entry.domain.PendingRunSubmissionKey
import org.mercurio.client.feature.entry.domain.Run
import org.mercurio.client.feature.entry.domain.RunId
import org.mercurio.client.feature.entry.domain.SessionHistory
import org.mercurio.client.feature.entry.domain.SessionId
import java.util.concurrent.TimeUnit

private const val TEST_TIMEOUT_MILLIS = 5_000L

class MessageSubmissionStateHolderTest {
    @Test
    fun successful_send_creates_one_run_clears_only_the_submitted_draft_and_stores_the_returned_identity() {
        val session = session("session-1")
        val run = Run(RunId("run-1"), session.id, "starting")
        val gateway = FakeGateway(listOf(session)).apply { enqueueRun(run) }
        val holder = holder(gateway)

        try {
            open(holder, gateway, session.id)
            holder.onEvent(EntryUiEvent.ComposerTextChanged("Run this"))
            holder.onEvent(EntryUiEvent.SendMessageClicked)

            val opened = requireNotNull(requireNotNull(holder.uiState.value.sessionList).openedSession)
            assertEquals(listOf(session.id to "Run this"), gateway.runRequests)
            assertEquals(run, opened.latestRun)
            assertEquals("", opened.composerText)
            assertFalse(opened.isSending)
            assertNull(opened.sendErrorCategory)
        } finally {
            holder.close()
        }
    }

    @Test
    fun an_active_run_blocks_send_but_keeps_the_next_draft_editable() {
        val session = session("session-1")
        val gateway =
            FakeGateway(
                sessions = listOf(session),
                histories =
                    mapOf(
                        session.id to
                            SessionHistory(
                                session.id,
                                listOf(
                                    GatewayHistoryMessage("run-message", "user", "Run this", RunId("run-1"), "running"),
                                ),
                            ),
                    ),
            )
        val holder = holder(gateway)

        try {
            open(holder, gateway, session.id)
            holder.onEvent(EntryUiEvent.ComposerTextChanged("Do not accept this"))
            holder.onEvent(EntryUiEvent.SendMessageClicked)

            val opened = requireNotNull(requireNotNull(holder.uiState.value.sessionList).openedSession)
            assertEquals("Do not accept this", opened.composerText)
            assertEquals(emptyList<Pair<SessionId, String>>(), gateway.runRequests)
            assertTrue(opened.latestRun?.status == "running")
        } finally {
            holder.close()
        }
    }

    @Test
    fun duplicate_send_is_ignored_while_the_first_run_request_is_pending() =
        runTest {
            val session = session("session-1")
            val run = Run(RunId("run-1"), session.id, "starting")
            val gateway = FakeGateway(listOf(session)).apply { enqueueRun(run) }
            val holder = holder(gateway, StandardTestDispatcher(testScheduler))

            try {
                openWithVirtualTime(holder, gateway, session.id)
                holder.onEvent(EntryUiEvent.ComposerTextChanged("Run once"))
                holder.onEvent(EntryUiEvent.SendMessageClicked)
                assertTrue(requireNotNull(requireNotNull(holder.uiState.value.sessionList).openedSession).isSending)

                holder.onEvent(EntryUiEvent.SendMessageClicked)
                testScheduler.advanceUntilIdle()

                assertEquals(listOf(session.id to "Run once"), gateway.runRequests)
                assertFalse(requireNotNull(requireNotNull(holder.uiState.value.sessionList).openedSession).isSending)
            } finally {
                holder.close()
            }
        }

    @Test
    fun close_prevents_a_pending_submission_from_updating_state() =
        runTest {
            val session = session("session-1")
            val run = Run(RunId("run-1"), session.id, "starting")
            val gateway = FakeGateway(listOf(session)).apply { enqueueRun(run) }
            val holder = holder(gateway, StandardTestDispatcher(testScheduler))

            try {
                openWithVirtualTime(holder, gateway, session.id)
                holder.onEvent(EntryUiEvent.ComposerTextChanged("Run once"))
                holder.onEvent(EntryUiEvent.SendMessageClicked)
                val stateAtClose = holder.uiState.value

                holder.close()
                testScheduler.advanceUntilIdle()

                assertEquals(stateAtClose, holder.uiState.value)
            } finally {
                holder.close()
            }
        }

    @Test
    fun close_cancels_cleanup_even_when_the_uncertainty_marker_write_fails() =
        runTest {
            val session = session("session-1")
            val run = Run(RunId("run-1"), session.id, "starting")
            val gateway = FakeGateway(listOf(session)).apply { enqueueRun(run) }
            val holder =
                holder(
                    gateway = gateway,
                    dispatcher = StandardTestDispatcher(testScheduler),
                    uncertaintyStore = FailingShutdownUncertaintyStore(),
                )

            try {
                openWithVirtualTime(holder, gateway, session.id)
                holder.onEvent(EntryUiEvent.ComposerTextChanged("Run once"))
                holder.onEvent(EntryUiEvent.SendMessageClicked)

                holder.close()
                testScheduler.advanceUntilIdle()
            } finally {
                holder.close()
            }
        }

    @Test
    fun an_in_flight_draft_edit_back_to_the_submitted_text_clears_the_confirmed_composer() =
        runTest {
            val session = session("session-1")
            val run = Run(RunId("run-1"), session.id, "starting")
            val gateway = FakeGateway(listOf(session)).apply { enqueueRun(run) }
            val holder = holder(gateway, StandardTestDispatcher(testScheduler))

            try {
                openWithVirtualTime(holder, gateway, session.id)
                holder.onEvent(EntryUiEvent.ComposerTextChanged("Original draft"))
                holder.onEvent(EntryUiEvent.SendMessageClicked)
                holder.onEvent(EntryUiEvent.ComposerTextChanged("Different draft"))
                holder.onEvent(EntryUiEvent.ComposerTextChanged("Original draft"))
                testScheduler.advanceUntilIdle()

                val confirmed = requireNotNull(requireNotNull(holder.uiState.value.sessionList).openedSession)
                assertEquals("", confirmed.composerText)
                assertFalse(confirmed.isSending)
            } finally {
                holder.close()
            }
        }

    @Test
    fun reopening_a_pending_submission_preserves_a_changed_draft_and_blocks_duplicate_send() =
        runTest {
            val session = session("session-1")
            val run = Run(RunId("run-1"), session.id, "starting")
            val gateway = FakeGateway(listOf(session)).apply { enqueueRun(run) }
            val holder = holder(gateway, StandardTestDispatcher(testScheduler))

            try {
                openWithVirtualTime(holder, gateway, session.id)
                holder.onEvent(EntryUiEvent.ComposerTextChanged("Run once"))
                holder.onEvent(EntryUiEvent.SendMessageClicked)
                assertTrue(requireNotNull(requireNotNull(holder.uiState.value.sessionList).openedSession).isSending)

                holder.onEvent(EntryUiEvent.SendMessageClicked)
                holder.onEvent(EntryUiEvent.ComposerTextChanged("New draft"))
                holder.onEvent(EntryUiEvent.ReturnToSessionListClicked)
                holder.onEvent(EntryUiEvent.SessionClicked(session.id))
                testScheduler.advanceUntilIdle()

                assertEquals(listOf(session.id to "Run once"), gateway.runRequests)
                val reopened = requireNotNull(requireNotNull(holder.uiState.value.sessionList).openedSession)
                assertEquals("New draft", reopened.composerText)
                assertEquals(run, reopened.latestRun)
                assertFalse(reopened.isSending)
            } finally {
                holder.close()
            }
        }

    @Test
    fun an_older_active_run_blocks_submission_when_the_latest_run_is_terminal() {
        val session = session("session-1")
        val gateway =
            FakeGateway(
                sessions = listOf(session),
                histories =
                    mapOf(
                        session.id to
                            SessionHistory(
                                session.id,
                                listOf(
                                    GatewayHistoryMessage(
                                        "run-1-message",
                                        "user",
                                        "First",
                                        RunId("run-1"),
                                        "running",
                                    ),
                                    GatewayHistoryMessage(
                                        "run-2-message",
                                        "user",
                                        "Second",
                                        RunId("run-2"),
                                        "succeeded",
                                    ),
                                ),
                            ),
                    ),
            )
        val holder = holder(gateway)

        try {
            open(holder, gateway, session.id)
            holder.onEvent(EntryUiEvent.ComposerTextChanged("Do not send"))
            holder.onEvent(EntryUiEvent.SendMessageClicked)

            val opened = requireNotNull(requireNotNull(holder.uiState.value.sessionList).openedSession)
            assertTrue(opened.activeRuns.any { it.id == RunId("run-1") })
            assertEquals(emptyList<Pair<SessionId, String>>(), gateway.runRequests)
        } finally {
            holder.close()
        }
    }

    @Test
    fun a_returned_active_run_is_retained_when_reopened_history_omits_it() {
        val session = session("session-1")
        val returnedRun = Run(RunId("run-new"), session.id, "starting")
        val gateway =
            FakeGateway(
                sessions = listOf(session),
                histories =
                    mapOf(
                        session.id to
                            SessionHistory(
                                session.id,
                                listOf(
                                    GatewayHistoryMessage(
                                        "old-run-message",
                                        "user",
                                        "Previous",
                                        RunId("run-old"),
                                        "succeeded",
                                    ),
                                ),
                            ),
                    ),
                runStatuses = mapOf(RunId("run-old") to Run(RunId("run-old"), session.id, "succeeded")),
            ).apply { enqueueRun(returnedRun) }
        val holder = holder(gateway)

        try {
            open(holder, gateway, session.id)
            holder.onEvent(EntryUiEvent.ComposerTextChanged("Create active run"))
            holder.onEvent(EntryUiEvent.SendMessageClicked)
            holder.onEvent(EntryUiEvent.ReturnToSessionListClicked)
            holder.onEvent(EntryUiEvent.SessionClicked(session.id))
            awaitState(holder) { it.sessionList?.openedSession?.latestRun?.id == returnedRun.id }

            val reopened = requireNotNull(requireNotNull(holder.uiState.value.sessionList).openedSession)
            assertTrue(reopened.activeRuns.any { it.id == returnedRun.id })
            holder.onEvent(EntryUiEvent.ComposerTextChanged("Do not send twice"))
            holder.onEvent(EntryUiEvent.SendMessageClicked)
            assertEquals(listOf(session.id to "Create active run"), gateway.runRequests)
        } finally {
            holder.close()
        }
    }

    @Test
    fun repeated_history_statuses_use_the_latest_status_for_one_run() {
        val session = session("session-1")
        val gateway =
            FakeGateway(
                sessions = listOf(session),
                histories =
                    mapOf(
                        session.id to
                            SessionHistory(
                                session.id,
                                listOf(
                                    GatewayHistoryMessage(
                                        "run-message-started",
                                        "user",
                                        "Run",
                                        RunId("run-1"),
                                        "running",
                                    ),
                                    GatewayHistoryMessage(
                                        "run-message-completed",
                                        "assistant",
                                        "Done",
                                        RunId("run-1"),
                                        "succeeded",
                                    ),
                                ),
                            ),
                    ),
                runStatuses = mapOf(RunId("run-1") to Run(RunId("run-1"), session.id, "succeeded")),
            )
        val holder = holder(gateway)

        try {
            open(holder, gateway, session.id)
            val opened = requireNotNull(requireNotNull(holder.uiState.value.sessionList).openedSession)
            assertEquals("succeeded", opened.latestRun?.status)
            assertTrue(opened.activeRuns.isEmpty())
            holder.onEvent(EntryUiEvent.ComposerTextChanged("Send after completion"))
            holder.onEvent(EntryUiEvent.SendMessageClicked)
            assertEquals(listOf(session.id to "Send after completion"), gateway.runRequests)
        } finally {
            holder.close()
        }
    }

    @Test
    fun late_run_completion_after_removal_cannot_pollute_a_reconnected_gateway() =
        runTest {
            val session = session("session-1")
            val oldRun = Run(RunId("run-old"), session.id, "starting")
            val newRun = Run(RunId("run-new"), session.id, "starting")
            val gateway =
                FakeGateway(listOf(session)).apply {
                    enqueueRun(oldRun)
                    enqueueRun(newRun)
                }
            val holder = holder(gateway, StandardTestDispatcher(testScheduler))

            try {
                openWithVirtualTime(holder, gateway, session.id)
                holder.onEvent(EntryUiEvent.ComposerTextChanged("Old request"))
                holder.onEvent(EntryUiEvent.SendMessageClicked)
                testScheduler.advanceUntilIdle()

                holder.onEvent(EntryUiEvent.ConfirmRemoveGatewayConnectionClicked)
                gateway.setRunStatus(oldRun.copy(status = "succeeded"))
                gateway.setHistory(
                    SessionHistory(
                        session.id,
                        listOf(GatewayHistoryMessage("old-result", "assistant", "Old result", oldRun.id, "succeeded")),
                    ),
                )

                holder.onEvent(EntryUiEvent.AddGatewayConnectionClicked)
                holder.onEvent(EntryUiEvent.EndpointChanged("https://gateway.example/profile"))
                holder.onEvent(EntryUiEvent.BearerCredentialChanged("memory-only-token"))
                holder.onEvent(EntryUiEvent.VerifyGatewayConnectionClicked)
                testScheduler.advanceUntilIdle()
                holder.onEvent(EntryUiEvent.SessionClicked(session.id))
                testScheduler.advanceUntilIdle()

                val reopened = requireNotNull(requireNotNull(holder.uiState.value.sessionList).openedSession)
                assertEquals(session.id, reopened.session.id)
                assertFalse(reopened.isReconciliationInProgress)
                assertFalse(reopened.hasUnresolvedSubmission)
                assertNull(reopened.sendErrorCategory)

                holder.onEvent(EntryUiEvent.ComposerTextChanged("New request"))
                holder.onEvent(EntryUiEvent.SendMessageClicked)
                testScheduler.advanceUntilIdle()

                assertFalse(requireNotNull(requireNotNull(holder.uiState.value.sessionList).openedSession).isSending)
                assertEquals(
                    listOf(session.id to "Old request", session.id to "New request"),
                    gateway.runRequests,
                )
            } finally {
                holder.close()
            }
        }

    @Test
    fun a_late_completion_cannot_replace_a_newer_attempt_or_its_draft() =
        runTest {
            val session = session("session-late-attempt")
            val run = Run(RunId("run-late-attempt"), session.id, "starting")
            val gateway = FakeGateway(listOf(session)).apply { enqueueRun(run) }
            val store = ProcessRunSubmissionUncertaintyStore
            val key = PendingRunSubmissionKey("https://gateway.example/profile", session.id)
            store.remove(key)
            val holder = holder(gateway, StandardTestDispatcher(testScheduler), uncertaintyStore = store)

            try {
                holder.onEvent(EntryUiEvent.AddGatewayConnectionClicked)
                holder.onEvent(EntryUiEvent.EndpointChanged("https://gateway.example/profile"))
                holder.onEvent(EntryUiEvent.BearerCredentialChanged("memory-only-token"))
                holder.onEvent(EntryUiEvent.VerifyGatewayConnectionClicked)
                testScheduler.advanceUntilIdle()
                assertEquals(
                    gateway.sessions.map { listed -> listed.toSessionItemUiState() },
                    requireNotNull(holder.uiState.value.sessionList).sessions,
                )
                holder.onEvent(EntryUiEvent.SessionClicked(session.id))
                testScheduler.advanceUntilIdle()
                assertEquals(
                    session.id,
                    requireNotNull(requireNotNull(holder.uiState.value.sessionList).openedSession).session.id,
                )

                holder.onEvent(EntryUiEvent.ComposerTextChanged("Original attempt"))
                holder.onEvent(EntryUiEvent.SendMessageClicked)
                val staleAttemptId = requireNotNull(store.attemptId(key))

                holder.onEvent(EntryUiEvent.ComposerTextChanged("Newer draft"))
                assertTrue(store.remove(key, staleAttemptId))
                assertTrue(store.add(key, emptySet(), "new-attempt"))

                testScheduler.advanceUntilIdle()

                val settled = requireNotNull(requireNotNull(holder.uiState.value.sessionList).openedSession)
                assertEquals("Newer draft", settled.composerText)
                assertFalse(settled.isSending)
                assertTrue(settled.hasUnresolvedSubmission)
                assertEquals(listOf(session.id to "Original attempt"), gateway.runRequests)
                assertEquals("new-attempt", store.attemptId(key))
                assertNull(store.boundRunId(key))

                gateway.setRunStatus(run.copy(status = "succeeded"))
                gateway.setHistory(
                    SessionHistory(
                        session.id,
                        listOf(GatewayHistoryMessage("late-result", "assistant", "Late result", run.id, "succeeded")),
                    ),
                )
                holder.onEvent(EntryUiEvent.RefreshSessionsClicked)
                testScheduler.advanceUntilIdle()

                val refreshed = requireNotNull(requireNotNull(holder.uiState.value.sessionList).openedSession)
                assertEquals(run.id, refreshed.latestRun?.id)
                assertEquals("succeeded", refreshed.latestRun?.status)
                assertFalse(refreshed.isRefreshing)
                assertEquals("new-attempt", store.attemptId(key))
                assertTrue(requireNotNull(holder.uiState.value.sessionList).openedSession!!.hasUnresolvedSubmission)
            } finally {
                store.remove(key)
                holder.close()
            }
        }

    @Test
    fun an_active_run_in_one_session_does_not_disable_a_different_session() {
        val first = session("session-1")
        val second = session("session-2")
        val firstRun = Run(RunId("run-1"), first.id, "starting")
        val secondRun = Run(RunId("run-2"), second.id, "starting")
        val gateway =
            FakeGateway(listOf(first, second)).apply {
                enqueueRun(firstRun)
                enqueueRun(secondRun)
            }
        val holder = holder(gateway)

        try {
            open(holder, gateway, first.id)
            holder.onEvent(EntryUiEvent.ComposerTextChanged("First"))
            holder.onEvent(EntryUiEvent.SendMessageClicked)
            holder.onEvent(EntryUiEvent.ReturnToSessionListClicked)
            holder.onEvent(EntryUiEvent.SessionClicked(second.id))
            holder.onEvent(EntryUiEvent.ComposerTextChanged("Second"))
            holder.onEvent(EntryUiEvent.SendMessageClicked)

            assertEquals(
                listOf(first.id to "First", second.id to "Second"),
                gateway.runRequests,
            )
        } finally {
            holder.close()
        }
    }

    @Test
    fun confirmed_failure_preserves_the_draft_and_only_an_explicit_retry_submits_again() {
        val session = session("session-1")
        val run = Run(RunId("run-1"), session.id, "starting")
        val gateway =
            FakeGateway(listOf(session)).apply {
                enqueueRunFailure(GatewayException(GatewayErrorCategory.GATEWAY_REQUEST_FAILED))
                enqueueRun(run)
            }
        val holder = holder(gateway)

        try {
            open(holder, gateway, session.id)
            holder.onEvent(EntryUiEvent.ComposerTextChanged("Keep this draft"))
            holder.onEvent(EntryUiEvent.SendMessageClicked)

            val failed = requireNotNull(requireNotNull(holder.uiState.value.sessionList).openedSession)
            assertEquals("Keep this draft", failed.composerText)
            assertEquals(MessageSendErrorCategory.GATEWAY_REQUEST_FAILED, failed.sendErrorCategory)
            assertFalse(failed.isSending)
            assertEquals(1, gateway.runRequests.size)
            assertTrue(gateway.runFinished.await(TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))

            holder.onEvent(EntryUiEvent.SendMessageClicked)
            awaitState(holder) {
                it.sessionList?.openedSession?.latestRun == run &&
                    it.sessionList?.openedSession?.composerText == ""
            }

            val retried = requireNotNull(requireNotNull(holder.uiState.value.sessionList).openedSession)
            assertEquals(2, gateway.runRequests.size)
            assertEquals("", retried.composerText)
            assertEquals(run, retried.latestRun)
        } finally {
            holder.close()
        }
    }

    @Test
    fun failed_submission_after_leaving_session_restores_try_again_when_reopened() =
        runTest {
            val session = session("session-1")
            val gateway =
                FakeGateway(listOf(session)).apply {
                    enqueueRunFailure(GatewayException(GatewayErrorCategory.GATEWAY_REQUEST_FAILED))
                }
            val holder = holder(gateway, StandardTestDispatcher(testScheduler))

            try {
                openWithVirtualTime(holder, gateway, session.id)
                holder.onEvent(EntryUiEvent.ComposerTextChanged("Keep this draft"))
                holder.onEvent(EntryUiEvent.SendMessageClicked)
                holder.onEvent(EntryUiEvent.ReturnToSessionListClicked)
                testScheduler.advanceUntilIdle()

                holder.onEvent(EntryUiEvent.SessionClicked(session.id))
                testScheduler.advanceUntilIdle()

                val reopened = requireNotNull(requireNotNull(holder.uiState.value.sessionList).openedSession)
                assertEquals("Keep this draft", reopened.composerText)
                assertEquals(MessageSendErrorCategory.GATEWAY_REQUEST_FAILED, reopened.sendErrorCategory)
                assertFalse(reopened.isSending)
            } finally {
                holder.close()
            }
        }

    @Test
    fun removing_the_gateway_clears_persisted_connection_and_in_memory_drafts() {
        val session = session("session-1")
        val dataSource = InMemoryGatewayConnectionDataSource()
        val repository = DefaultGatewayConnectionRepository(dataSource)
        val gateway = FakeGateway(listOf(session))
        val holder = holder(gateway, repository = repository)

        try {
            connect(holder, gateway)
            holder.onEvent(EntryUiEvent.SessionClicked(session.id))
            holder.onEvent(EntryUiEvent.ComposerTextChanged("Forget me"))
            holder.onEvent(EntryUiEvent.ConfirmRemoveGatewayConnectionClicked)

            assertNull(repository.load())
            assertFalse(holder.uiState.value.isConnected)
            assertNull(holder.uiState.value.sessionList)
        } finally {
            holder.close()
        }
    }
}
