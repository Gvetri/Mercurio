package org.mercurio.client.feature.entry.presentation

import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mercurio.client.feature.entry.data.DefaultRunSubmissionUncertaintyStore
import org.mercurio.client.feature.entry.domain.GatewayHistoryMessage
import org.mercurio.client.feature.entry.domain.Run
import org.mercurio.client.feature.entry.domain.RunEvent
import org.mercurio.client.feature.entry.domain.RunEventType
import org.mercurio.client.feature.entry.domain.RunId
import org.mercurio.client.feature.entry.domain.RunPresentationState
import org.mercurio.client.feature.entry.domain.RunRecoveryEntry
import org.mercurio.client.feature.entry.domain.Session
import org.mercurio.client.feature.entry.domain.SessionHistory

class RunReconciliationSubmissionTest {
    @Test
    fun submission_replaces_the_queued_observer_started_by_open_reconciliation() =
        runTest {
            val session = reconciliationSession()
            val run = Run(RunId("overlapping-run"), session.id, "running")
            val registry = ReconciliationBlockingRecoveryRegistry()
            val gateway = recoverySubmissionGateway(session, run)
            gateway.blockNextObservationOpen = true
            val holder =
                reconciliationHolder(
                    gateway,
                    settings =
                        ReconciliationHolderSettings(
                            recoveryRegistry = registry,
                            uncertaintyStore = DefaultRunSubmissionUncertaintyStore(ReconciliationUncertaintyStorage()),
                        ),
                )

            try {
                connectReconciliationGateway(holder, gateway)
                advanceUntilIdle()
                registry.blockLoads = true
                holder.onEvent(EntryUiEvent.SessionClicked(session.id))
                registry.loadStarted.await()
                registry.blockSaves = true
                holder.onEvent(EntryUiEvent.ComposerTextChanged("One submission"))
                holder.onEvent(EntryUiEvent.SendMessageClicked)
                registry.saveStarted.await()

                registry.releaseLoad.complete(Unit)
                advanceUntilIdle()
                gateway.observationOpenStarted.await()
                assertEquals(run.id, holder.uiState.value.sessionList?.openedSession?.latestRun?.id)
                assertTrue(registry.load().isEmpty())
                assertTrue(gateway.observedRunIds.isEmpty())

                gateway.releaseObservationOpen.complete(Unit)
                registry.releaseSave.complete(Unit)
                gateway.submissionCompleted.await()
                advanceUntilIdle()

                assertEquals(listOf(run.id, run.id), gateway.observedRunIds)
                assertEquals(
                    "Recovered observation",
                    holder.uiState.value.sessionList?.openedSession?.activeResponse?.content,
                )
                assertEquals(listOf(RunRecoveryEntry(session.id, run.id)), registry.load())
                assertEquals(listOf(session.id to "One submission"), gateway.runRequests)
            } finally {
                registry.releaseLoad.complete(Unit)
                registry.releaseSave.complete(Unit)
                gateway.releaseObservationOpen.complete(Unit)
                holder.close()
                advanceUntilIdle()
            }
        }

    @Test
    fun a_cancelled_queued_observer_cannot_remove_the_reopened_sessions_observer() =
        runTest {
            val session = reconciliationSession()
            val run = Run(RunId("reopened-run"), session.id, "running")
            val gateway =
                ReconciliationFakeGateway(session).apply {
                    runs.add(run)
                    statuses.add(run)
                    statuses.add(run)
                    blockNextObservationOpen = true
                    observation =
                        ReconciliationScriptedObservation(
                            listOf(RunEvent(RunEventType.MESSAGE_DELTA, run.id, "running", "Reopened event", "delta")),
                        )
                }
            val holder = reconciliationHolder(gateway)
            try {
                openReconciledSession(holder, gateway)
                holder.onEvent(EntryUiEvent.ComposerTextChanged("First"))
                holder.onEvent(EntryUiEvent.SendMessageClicked)
                advanceUntilIdle()
                gateway.observationOpenStarted.await()
                assertEquals(1, gateway.observationRequests.size)

                holder.onEvent(EntryUiEvent.ReturnToSessionListClicked)
                holder.onEvent(EntryUiEvent.SessionClicked(session.id))
                advanceUntilIdle()
                gateway.releaseObservationOpen.complete(Unit)
                advanceUntilIdle()

                assertEquals(listOf(run.id), gateway.observedRunIds)
                assertEquals("Reopened event", holder.uiState.value.sessionList?.openedSession?.activeResponse?.content)
                assertEquals(1, gateway.runRequests.size)
            } finally {
                gateway.releaseObservationOpen.complete(Unit)
                holder.close()
                advanceUntilIdle()
            }
        }

    @Test
    fun a_cancelled_queued_observer_cannot_restart_after_connection_removal_or_close() =
        runTest {
            listOf(false, true).forEach { closeHolder ->
                val session = reconciliationSession()
                val run = Run(RunId("abandoned-run"), session.id, "running")
                val gateway =
                    ReconciliationFakeGateway(session).apply {
                        runs.add(run)
                        blockNextObservationOpen = true
                    }
                val holder = reconciliationHolder(gateway)
                try {
                    openReconciledSession(holder, gateway)
                    holder.onEvent(EntryUiEvent.ComposerTextChanged("First"))
                    holder.onEvent(EntryUiEvent.SendMessageClicked)
                    advanceUntilIdle()
                    gateway.observationOpenStarted.await()
                    assertEquals(run.id, holder.uiState.value.sessionList?.openedSession?.latestRun?.id)
                    assertTrue(holder.testSurface.runObservationJobs.isNotEmpty())

                    val removalEvent = EntryUiEvent.ConfirmRemoveGatewayConnectionClicked
                    if (closeHolder) holder.close() else holder.onEvent(removalEvent)
                    advanceUntilIdle()

                    assertTrue(gateway.observedRunIds.isEmpty())
                    assertEquals(1, gateway.observationRequests.size)
                    assertEquals(1, gateway.runRequests.size)
                } finally {
                    gateway.releaseObservationOpen.complete(Unit)
                    holder.close()
                    advanceUntilIdle()
                }
            }
        }

    @Test(timeout = RECONCILIATION_TIMEOUT_MILLIS)
    fun direct_parent_cancellation_releases_a_queued_observer_without_restarting() =
        runTest {
            assertParentCancellationReleasesObserver(cancelDuringRegistration = false)
        }

    @Test(timeout = RECONCILIATION_TIMEOUT_MILLIS)
    fun direct_parent_cancellation_during_observer_registration_still_releases_ownership() =
        runTest {
            assertParentCancellationReleasesObserver(cancelDuringRegistration = true)
        }

    @Test
    fun a_blocked_observer_open_keeps_ownership_until_cancellation_finishes_before_next_run() =
        runTest {
            val session = reconciliationSession()
            val firstRun = Run(RunId("run-blocked-open-1"), session.id, "starting")
            val secondRun = Run(RunId("run-blocked-open-2"), session.id, "starting")
            val firstLateObservation = ReconciliationBlockingObservation()
            val secondObservation = ReconciliationBlockingObservation()
            val authoritativeHistory =
                SessionHistory(
                    session.id,
                    listOf(GatewayHistoryMessage("authoritative", "assistant", "Confirmed", firstRun.id, "succeeded")),
                )
            val gateway =
                ReconciliationFakeGateway(session).apply {
                    histories.add(SessionHistory(session.id, emptyList()))
                    histories.add(SessionHistory(session.id, emptyList()))
                    histories.add(authoritativeHistory)
                    statuses.add(firstRun.copy(status = "succeeded"))
                    runs.add(firstRun)
                    runs.add(secondRun)
                    observations.add(firstLateObservation)
                    observations.add(secondObservation)
                    blockNextObservationOpen = true
                    openIgnoresCancellation = true
                }
            val holder =
                reconciliationHolder(
                    gateway,
                    settings =
                        ReconciliationHolderSettings(),
                )

            try {
                openReconciledSession(holder, gateway)
                awaitRunSubmissionAllowed(holder, session.id)
                holder.onEvent(EntryUiEvent.ComposerTextChanged("First"))
                holder.onEvent(EntryUiEvent.SendMessageClicked)
                gateway.observationOpenStarted.await()

                holder.onEvent(EntryUiEvent.RefreshSessionsClicked)
                awaitReconciliationState(holder) {
                    it.sessionList?.openedSession?.latestRunState == RunPresentationState.SUCCEEDED &&
                        !it.sessionList.openedSession.isRefreshing
                }

                awaitRunSubmissionAllowed(holder, session.id)
                holder.onEvent(EntryUiEvent.ComposerTextChanged("Second"))
                holder.onEvent(EntryUiEvent.SendMessageClicked)
                advanceUntilIdle()
                assertFalse(
                    "a second observation must not open while the first one still owns the screen",
                    gateway.secondObservationRequested.isCompleted,
                )
                assertEquals(1, gateway.observationRequests.size)
                val ownershipJob =
                    requireNotNull(holder.testSurface.runObservationJobs[session.id]) {
                        "the blocked open keeps the only registered observation job"
                    }
                assertEquals(1, holder.testSurface.runObservationJobs.size)
                assertTrue("the next run has already asked for the open's ownership", ownershipJob.isCancelled)
                assertFalse(
                    "the cancelled open must not release ownership before its cancellation finishes",
                    ownershipJob.isCompleted,
                )
                assertEquals(
                    "the next run waits in the pending slot while the open is blocked",
                    secondRun.id,
                    holder.testSurface.pendingRunObservationRequests[session.id]?.run?.id,
                )

                gateway.releaseObservationOpen.complete(Unit)
                advanceUntilIdle()
                assertTrue("the released open must finish", gateway.observationOpenFinished.isCompleted)
                assertTrue("the cancelled late observation must close", firstLateObservation.closed.isCompleted)
                assertTrue(
                    "the released ownership must hand off to the next run",
                    gateway.secondObservationRequested.isCompleted,
                )
                assertTrue(secondObservation.started.isCompleted)
                assertEquals(listOf(firstRun.id, secondRun.id), gateway.observedRunIds)
                assertEquals(secondRun.id, holder.testSurface.runObservationRunIds[session.id])
                assertTrue(holder.testSurface.pendingRunObservationRequests.isEmpty())
            } finally {
                gateway.releaseObservationOpen.complete(Unit)
                secondObservation.release.complete(Unit)
                holder.close()
            }
        }

    @Test
    fun reopening_an_observed_run_reconciles_before_resuming_observation() =
        runTest {
            val session = reconciliationSession()
            val run = Run(RunId("run-1"), session.id, "running")
            val activeHistory =
                SessionHistory(
                    session.id,
                    listOf(GatewayHistoryMessage("message-1", "user", "Run this", run.id, "running")),
                )
            val firstObservation = ReconciliationBlockingObservation()
            val secondObservation = ReconciliationBlockingObservation()
            val gateway =
                ReconciliationFakeGateway(session).apply {
                    histories.add(activeHistory)
                    histories.add(activeHistory)
                    histories.add(activeHistory)
                    histories.add(activeHistory)
                    statuses.add(run)
                    statuses.add(run)
                    observations.add(firstObservation)
                    observations.add(secondObservation)
                }
            val holder =
                reconciliationHolder(
                    gateway,
                    settings =
                        ReconciliationHolderSettings(),
                )

            try {
                openReconciledSession(holder, gateway)
                awaitReconciliationState(holder) {
                    it.sessionList?.openedSession?.latestRunState == RunPresentationState.UNCERTAIN &&
                        gateway.statusRequests.size == 1
                }
                firstObservation.started.await()

                holder.onEvent(EntryUiEvent.ReturnToSessionListClicked)
                awaitReconciliationState(holder) { it.sessionList?.openedSession == null }
                firstObservation.finished.await()

                holder.onEvent(EntryUiEvent.SessionClicked(session.id))
                awaitReconciliationState(holder) {
                    it.sessionList?.openedSession?.latestRunState == RunPresentationState.UNCERTAIN &&
                        gateway.statusRequests.size == 2 &&
                        gateway.historyRequests == 4
                }
                secondObservation.started.await()
                assertEquals(2, gateway.observedRunIds.size)

                assertEquals(listOf(run.id, run.id), gateway.observedRunIds)
            } finally {
                firstObservation.release.complete(Unit)
                secondObservation.release.complete(Unit)
                holder.close()
            }
        }

    @Test
    fun returning_after_terminal_event_resolves_the_reopened_outcome_from_the_authoritative_status() =
        runTest {
            val session = reconciliationSession()
            val run = Run(RunId("run-1"), session.id, "starting")
            val gateway =
                ReconciliationFakeGateway(session).apply {
                    histories.add(SessionHistory(session.id, emptyList()))
                    histories.add(SessionHistory(session.id, emptyList()))
                    histories.add(SessionHistory(session.id, emptyList()))
                    histories.add(SessionHistory(session.id, emptyList()))
                    statuses.add(run.copy(status = "succeeded"))
                    statuses.add(run.copy(status = "succeeded"))
                    runs.add(run)
                    observation =
                        ReconciliationScriptedObservation(
                            listOf(RunEvent(RunEventType.SUCCEEDED, run.id, "succeeded", eventId = "succeeded")),
                        )
                }
            val holder =
                reconciliationHolder(
                    gateway,
                    settings =
                        ReconciliationHolderSettings(),
                )

            try {
                openReconciledSession(holder, gateway)
                gateway.blockNextStatus = true
                holder.onEvent(EntryUiEvent.ComposerTextChanged("Run this"))
                holder.onEvent(EntryUiEvent.SendMessageClicked)
                gateway.statusStarted.await()

                holder.onEvent(EntryUiEvent.ReturnToSessionListClicked)
                awaitReconciliationState(holder) { it.sessionList?.openedSession == null }

                gateway.blockNextStatus = true
                holder.onEvent(EntryUiEvent.SessionClicked(session.id))
                awaitReconciliationState(holder) {
                    val opened = it.sessionList?.openedSession
                    opened?.latestRun?.id == run.id &&
                        opened.latestRunState == RunPresentationState.UNCERTAIN
                }
                awaitReconciliationCondition { gateway.statusRequests.size >= 2 }
                holder.onEvent(EntryUiEvent.SendMessageClicked)
                assertEquals(1, gateway.runRequests.size)

                gateway.releaseStatus.complete(Unit)
                gateway.statusFinished.await()
                awaitReconciliationState(holder) {
                    it.sessionList?.openedSession?.latestRunState == RunPresentationState.SUCCEEDED
                }
            } finally {
                gateway.releaseStatus.complete(Unit)
                holder.close()
            }
        }

    @Test
    fun send_timeout_applies_authoritative_history_while_preserving_the_uncertain_draft() =
        runTest {
            val session = reconciliationSession()
            val otherRun = Run(RunId("other-run"), session.id, "succeeded")
            val otherHistory = otherRunHistory(session, otherRun)
            val gateway =
                ReconciliationFakeGateway(session).apply {
                    histories.add(SessionHistory(session.id, emptyList()))
                    histories.add(otherHistory)
                    histories.add(refreshedRunHistory(session, otherRun))
                    repeat(2) { histories.add(otherHistory) }
                    repeat(2) { statuses.add(otherRun) }
                    blockRunCreation = true
                }
            val holder =
                reconciliationHolder(
                    gateway,
                    ReconciliationHolderSettings(sendTimeoutMillis = 50L),
                )

            try {
                openReconciledSession(holder, gateway)
                holder.onEvent(EntryUiEvent.ComposerTextChanged("Keep this draft"))
                holder.onEvent(EntryUiEvent.SendMessageClicked)
                gateway.runStarted.await()

                awaitReconciliationState(holder, timedOutSendResolvedState(otherRun))
                assertTimedOutSendKeptDraft(holder, gateway, session, otherRun)

                holder.onEvent(EntryUiEvent.ComposerTextChanged("Edited after timeout"))
                acceptRefreshAfterTimeout(holder, gateway)
                awaitReconciliationState(holder, refreshedAfterTimeoutState(otherRun))
                holder.onEvent(EntryUiEvent.ReturnToSessionListClicked)
                awaitReconciliationState(holder) { it.sessionList?.openedSession == null }
                holder.onEvent(EntryUiEvent.SessionClicked(session.id))
                awaitReconciliationState(holder, reopenedAfterTimeoutState(otherRun))
                awaitReconciliationCondition { gateway.historyRequests == 4 }
                assertEquals(4, gateway.historyRequests)
                holder.onEvent(EntryUiEvent.SendMessageClicked)
                awaitReconciliationCondition { gateway.historyRequests == 5 }
                awaitReconciliationState(holder, sendAfterTimeoutState())
                assertReloadedTimeoutKeptDraft(holder, gateway, otherRun)
            } finally {
                gateway.releaseRun.complete(Unit)
                holder.close()
            }
        }

    @Test
    fun response_loss_with_one_terminal_discovered_run_remains_uncertain() =
        runTest {
            val session = reconciliationSession()
            val run = Run(RunId("run-terminal-after-response-loss"), session.id, "succeeded")
            val history =
                SessionHistory(
                    session.id,
                    listOf(GatewayHistoryMessage("terminal", "assistant", "Succeeded", run.id, "succeeded")),
                )
            val gateway =
                ReconciliationFakeGateway(session).apply {
                    histories.add(SessionHistory(session.id, emptyList()))
                    histories.add(history)
                    statuses.add(run)
                    failRunCreation = true
                }
            val holder = reconciliationHolder(gateway)

            try {
                openReconciledSession(holder, gateway)
                holder.onEvent(EntryUiEvent.ComposerTextChanged("Keep this draft"))
                holder.onEvent(EntryUiEvent.SendMessageClicked)

                awaitReconciliationState(holder) {
                    val opened = it.sessionList?.openedSession
                    opened != null &&
                        opened.latestRun?.id == run.id &&
                        opened.latestRunState == RunPresentationState.SUCCEEDED &&
                        opened.sendErrorCategory == MessageSendErrorCategory.GATEWAY_REQUEST_FAILED &&
                        opened.hasUnresolvedSubmission &&
                        opened.composerText == "Keep this draft" &&
                        !opened.isReconciliationInProgress
                }
                assertEquals(1, gateway.runRequests.size)
                assertEquals(listOf(run.id), gateway.statusRequests)
                assertTrue(gateway.observedRunIds.isEmpty())
            } finally {
                holder.close()
            }
        }

    private fun otherRunHistory(
        session: Session,
        otherRun: Run,
    ): SessionHistory =
        SessionHistory(
            session.id,
            listOf(
                GatewayHistoryMessage(
                    "other",
                    "assistant",
                    "Existing result",
                    otherRun.id,
                    "succeeded",
                    "External result",
                    "2026-09-08T21:00:00Z",
                ),
            ),
        )

    private fun refreshedRunHistory(
        session: Session,
        otherRun: Run,
    ): SessionHistory =
        SessionHistory(
            session.id,
            listOf(
                GatewayHistoryMessage(
                    "refreshed",
                    "assistant",
                    "Refreshed result",
                    otherRun.id,
                    "succeeded",
                    "Refreshed result",
                    "2026-09-08T21:30:00Z",
                ),
            ),
        )

    private suspend fun TestScope.acceptRefreshAfterTimeout(
        holder: EntryStateHolder,
        gateway: ReconciliationFakeGateway,
    ) {
        awaitReconciliationState(holder) { state ->
            val list = state.sessionList
            val opened = list?.openedSession
            list != null &&
                opened != null &&
                !list.hasActiveRequest &&
                !opened.isRefreshing &&
                !opened.isReconciliationInProgress
        }
        holder.awaitStateChangesApplied()
        val beforeRefresh = gateway.historyRequests
        holder.onEvent(EntryUiEvent.RefreshSessionsClicked)
        awaitReconciliationCondition { gateway.historyRequests == beforeRefresh + 1 }
    }

    private fun timedOutSendResolvedState(otherRun: Run): (EntryUiState) -> Boolean =
        { state ->
            val opened = state.sessionList?.openedSession
            opened?.sendErrorCategory == MessageSendErrorCategory.UNCERTAIN &&
                !opened.isRefreshing &&
                !opened.isReconciliationInProgress &&
                opened.latestRun?.id == otherRun.id &&
                opened.latestRunState == RunPresentationState.SUCCEEDED &&
                opened.messages.map { message -> message.id } == listOf("other") &&
                opened.messages.single().runResult == "External result" &&
                opened.messages.single().timestamp?.toString() == "2026-09-08T21:00:00Z" &&
                opened.composerText == "Keep this draft"
        }

    private fun refreshedAfterTimeoutState(otherRun: Run): (EntryUiState) -> Boolean =
        { state ->
            val refreshed = state.sessionList?.openedSession
            refreshed?.isRefreshing == false &&
                refreshed.latestRun?.id == otherRun.id &&
                refreshed.latestRunState == RunPresentationState.SUCCEEDED &&
                refreshed.sendErrorCategory == MessageSendErrorCategory.UNCERTAIN &&
                refreshed.hasUnresolvedSubmission &&
                refreshed.composerText == "Edited after timeout" &&
                refreshed.messages.map { message -> message.id } == listOf("refreshed") &&
                refreshed.messages.single().runResult == "Refreshed result" &&
                refreshed.messages.single().timestamp?.toString() == "2026-09-08T21:30:00Z"
        }

    private fun reopenedAfterTimeoutState(otherRun: Run): (EntryUiState) -> Boolean =
        { state ->
            state.sessionList?.openedSession?.isRefreshing == false &&
                state.sessionList.openedSession.latestRun?.id == otherRun.id &&
                state.sessionList.openedSession.latestRunState == RunPresentationState.SUCCEEDED &&
                state.sessionList.openedSession.sendErrorCategory == MessageSendErrorCategory.UNCERTAIN &&
                state.sessionList.openedSession.hasUnresolvedSubmission == true &&
                state.sessionList.openedSession.isReconciliationInProgress == false &&
                state.sessionList.openedSession.composerText == "Edited after timeout" &&
                state.sessionList.openedSession.messages.map { message -> message.id } == listOf("other") &&
                state.sessionList.openedSession.messages.single().runResult == "External result" &&
                state.sessionList.openedSession.messages.single().timestamp?.toString() == "2026-09-08T21:00:00Z"
        }

    private fun sendAfterTimeoutState(): (EntryUiState) -> Boolean =
        { state ->
            state.sessionList?.openedSession?.isReconciliationInProgress == false &&
                state.sessionList.openedSession.hasUnresolvedSubmission == true
        }

    private fun assertTimedOutSendKeptDraft(
        holder: EntryStateHolder,
        gateway: ReconciliationFakeGateway,
        session: Session,
        otherRun: Run,
    ): OpenSessionUiState {
        val opened = requireNotNull(requireNotNull(holder.uiState.value.sessionList).openedSession)
        assertEquals("Keep this draft", opened.composerText)
        assertFalse(opened.isSending)
        assertEquals(otherRun.id, opened.latestRun?.id)
        assertEquals(RunPresentationState.SUCCEEDED, opened.latestRunState)
        assertTrue(opened.activeRuns.isEmpty())
        assertTrue(opened.hasUnresolvedSubmission)
        assertTrue(gateway.observedRunIds.isEmpty())
        val syntheticId = RunId("uncertain-send:${session.id.value}")
        assertFalse(gateway.statusRequests.contains(syntheticId))
        assertFalse(gateway.observedRunIds.contains(syntheticId))
        assertEquals(1, gateway.runRequests.size)
        assertEquals(2, gateway.historyRequests)
        assertEquals(listOf(otherRun.id), gateway.statusRequests)
        return opened
    }

    private fun assertReloadedTimeoutKeptDraft(
        holder: EntryStateHolder,
        gateway: ReconciliationFakeGateway,
        otherRun: Run,
    ) {
        assertEquals(1, gateway.runRequests.size)
        assertFalse(requireNotNull(requireNotNull(holder.uiState.value.sessionList).openedSession).isSending)
        assertEquals(listOf(otherRun.id, otherRun.id), gateway.statusRequests)
        assertTrue(gateway.observedRunIds.isEmpty())
        assertEquals(5, gateway.historyRequests)
    }

    private fun recoverySubmissionGateway(
        session: Session,
        run: Run,
    ): ReconciliationFakeGateway =
        ReconciliationFakeGateway(session).apply {
            runs.add(run)
            statuses.add(run)
            statuses.add(run)
            repeat(2) { observations.add(recoveredDeltaObservation(run)) }
        }
}
