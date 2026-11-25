package org.mercurio.client.feature.entry.presentation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mercurio.client.feature.entry.data.InMemoryRunRecoveryRegistry
import org.mercurio.client.feature.entry.domain.GatewayHistoryMessage
import org.mercurio.client.feature.entry.domain.Run
import org.mercurio.client.feature.entry.domain.RunEvent
import org.mercurio.client.feature.entry.domain.RunEventType
import org.mercurio.client.feature.entry.domain.RunId
import org.mercurio.client.feature.entry.domain.RunPresentationState
import org.mercurio.client.feature.entry.domain.RunRecoveryEntry
import org.mercurio.client.feature.entry.domain.SessionHistory
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

class RunObservationRecoveryTest {
    @Test
    fun one_foreground_observer_appends_deltas_and_reaches_succeeded_without_duplicate_content() =
        runTest {
            val session = observationSession("session-1")
            val run = Run(RunId("run-1"), session.id, "starting")
            val recoveryRegistry = InMemoryRunRecoveryRegistry()
            val gateway =
                ObservationScriptedGateway(session).apply {
                    enqueueRun(run)
                    enqueueHistory(SessionHistory(session.id, emptyList()))
                    enqueueHistory(
                        SessionHistory(
                            session.id,
                            listOf(
                                GatewayHistoryMessage("assistant-1", "assistant", "Hello world", run.id, "succeeded"),
                            ),
                        ),
                    )
                    enqueueStatus(run.copy(status = "succeeded"))
                    observation =
                        ObservationScriptedObservation(
                            listOf(
                                RunEvent(RunEventType.STARTED, run.id, "starting", eventId = "started"),
                                RunEvent(RunEventType.RUNNING, run.id, "running", eventId = "running"),
                                RunEvent(RunEventType.MESSAGE_DELTA, run.id, "running", "Hello", "delta-1"),
                                RunEvent(RunEventType.MESSAGE_DELTA, run.id, "running", "Hello", "delta-1"),
                                RunEvent(RunEventType.MESSAGE_DELTA, run.id, "running", " world", "delta-2"),
                                RunEvent(RunEventType.COMPLETING, run.id, "completing", eventId = "completing"),
                                RunEvent(RunEventType.SUCCEEDED, run.id, "succeeded", eventId = "succeeded"),
                            ),
                        )
                }
            val holder = observationHolder(gateway, recoveryRegistry = recoveryRegistry)

            try {
                openObservedSession(holder, gateway, session.id)
                holder.onEvent(EntryUiEvent.ComposerTextChanged("Run this"))
                holder.onEvent(EntryUiEvent.SendMessageClicked)
                advanceUntilIdle()

                val opened = requireNotNull(requireNotNull(holder.uiState.value.sessionList).openedSession)
                assertEquals(listOf(run.id), gateway.observedRunIds)
                assertEquals(RunPresentationState.SUCCEEDED, opened.latestRunState)
                assertEquals(listOf("Hello world"), opened.messages.map { it.content })
                assertTrue(opened.activeResponse == null)
                assertTrue(recoveryRegistry.load().isEmpty())
            } finally {
                holder.close()
            }
        }

    @Test
    fun disconnect_during_create_persists_the_remote_run_in_its_original_endpoint_scope() =
        runTest {
            val session = observationSession("disconnect-during-create")
            val run = Run(RunId("run-disconnected"), session.id, "running")
            val gateway =
                ObservationScriptedGateway(session).apply {
                    enqueueRun(run)
                    blockCreate = true
                }
            val persisted = CopyOnWriteArrayList<Pair<String, RunRecoveryEntry>>()
            val holder =
                observationHolder(
                    gateway = gateway,
                    persistRunRecoveryEntry = { endpoint, entry -> persisted += endpoint to entry },
                )

            try {
                openObservedSession(holder, gateway, session.id)
                holder.onEvent(EntryUiEvent.ComposerTextChanged("Run this"))
                holder.onEvent(EntryUiEvent.SendMessageClicked)
                gateway.createStarted.await()

                holder.onEvent(EntryUiEvent.ConfirmRemoveGatewayConnectionClicked)
                gateway.createRelease.complete(Unit)

                awaitObservationCondition { persisted.isNotEmpty() }
                assertEquals(
                    listOf("https://gateway.example/profile" to RunRecoveryEntry(session.id, run.id)),
                    persisted.toList(),
                )
                assertTrue(gateway.cancelledRunIds.isEmpty())
            } finally {
                gateway.createRelease.complete(Unit)
                holder.close()
            }
        }

    @Test
    fun late_create_result_reconciles_after_reconnecting_before_recovery_metadata_is_written() =
        runTest {
            val session = observationSession("disconnect-reconnect-race")
            val run = Run(RunId("run-reconnect-race"), session.id, "running")
            val gateway =
                ObservationScriptedGateway(session).apply {
                    enqueueRun(run)
                    statusByRun[run.id] = run
                    blockCreate = true
                }
            val persisted = CopyOnWriteArrayList<Pair<String, RunRecoveryEntry>>()
            val holder =
                observationHolder(
                    gateway = gateway,
                    persistRunRecoveryEntry = { endpoint, entry -> persisted += endpoint to entry },
                )

            try {
                openObservedSession(holder, gateway, session.id)
                holder.onEvent(EntryUiEvent.ComposerTextChanged("Run this"))
                holder.onEvent(EntryUiEvent.SendMessageClicked)
                gateway.createStarted.await()

                holder.onEvent(EntryUiEvent.ConfirmRemoveGatewayConnectionClicked)
                awaitObservationState(holder) { it.sessionList == null }
                holder.onEvent(EntryUiEvent.AddGatewayConnectionClicked)
                holder.onEvent(EntryUiEvent.EndpointChanged("https://gateway.example/profile"))
                holder.onEvent(EntryUiEvent.BearerCredentialChanged("test-only-credential"))
                holder.onEvent(EntryUiEvent.VerifyGatewayConnectionClicked)
                awaitObservationState(holder) { it.sessionList?.sessions == listOf(session.toSessionItemUiState()) }

                gateway.createRelease.complete(Unit)

                awaitObservationCondition {
                    persisted.isNotEmpty() &&
                        gateway.statusRequests.contains(run.id)
                }
            } finally {
                gateway.createRelease.complete(Unit)
                holder.close()
            }
        }

    @Test
    fun a_create_result_persisted_before_reconnect_reconciles_when_the_connection_returns() =
        runTest {
            val session = observationSession("disconnect-persist-before-connect")
            val run = Run(RunId("run-persist-before-connect"), session.id, "running")
            val gateway =
                ObservationScriptedGateway(session).apply {
                    enqueueRun(run)
                    statusByRun[run.id] = run
                    blockCreate = true
                }
            val persisted = CopyOnWriteArrayList<Pair<String, RunRecoveryEntry>>()
            val holder =
                observationHolder(
                    gateway = gateway,
                    persistRunRecoveryEntry = { endpoint, entry -> persisted += endpoint to entry },
                )

            try {
                openObservedSession(holder, gateway, session.id)
                holder.onEvent(EntryUiEvent.ComposerTextChanged("Run this"))
                holder.onEvent(EntryUiEvent.SendMessageClicked)
                gateway.createStarted.await()

                holder.onEvent(EntryUiEvent.ConfirmRemoveGatewayConnectionClicked)
                awaitObservationState(holder) { it.sessionList == null }
                gateway.createRelease.complete(Unit)
                awaitObservationCondition { persisted.isNotEmpty() }
                assertTrue(gateway.statusRequests.isEmpty())

                holder.onEvent(EntryUiEvent.AddGatewayConnectionClicked)
                holder.onEvent(EntryUiEvent.EndpointChanged("https://gateway.example/profile"))
                holder.onEvent(EntryUiEvent.BearerCredentialChanged("test-only-credential"))
                holder.onEvent(EntryUiEvent.VerifyGatewayConnectionClicked)

                awaitObservationCondition { gateway.statusRequests.contains(run.id) }
            } finally {
                gateway.createRelease.complete(Unit)
                holder.close()
            }
        }

    @Test
    fun recovery_persistence_failure_keeps_the_created_run_and_marks_its_outcome_uncertain() =
        runTest {
            val session = observationSession("session-1")
            val run = Run(RunId("run-1"), session.id, "running")
            val observation = ObservationBlockingObservation()
            val gateway =
                ObservationScriptedGateway(session).apply {
                    enqueueRun(run)
                    this.observation = observation
                }
            val holder = observationHolder(gateway, ObservationFailingRunRecoveryRegistry())

            try {
                openObservedSession(holder, gateway, session.id)
                holder.onEvent(EntryUiEvent.ComposerTextChanged("Run this"))
                holder.onEvent(EntryUiEvent.SendMessageClicked)
                awaitObservationState(holder) {
                    val opened = it.sessionList?.openedSession
                    opened?.latestRun?.id == run.id &&
                        opened.latestRunState == RunPresentationState.UNCERTAIN &&
                        opened.sendErrorCategory == MessageSendErrorCategory.UNCERTAIN &&
                        opened.hasUnresolvedSubmission &&
                        opened.composerText == "Run this"
                }
                observation.started.await()
            } finally {
                observation.release.complete(Unit)
                holder.close()
            }
        }

    @Test
    fun recovery_persistence_failure_is_retried_after_connection_recreation() =
        runTest {
            val session = observationSession("recovery-retry")
            val run = Run(RunId("run-recovery-retry"), session.id, "running")
            val observation = ObservationBlockingObservation()
            val gateway =
                ObservationScriptedGateway(session).apply {
                    enqueueRun(run)
                    this.observation = observation
                }
            val recoveryRegistry = ObservationFlakyRunRecoveryRegistry()
            val holder = observationHolder(gateway, recoveryRegistry)

            try {
                openObservedSession(holder, gateway, session.id)
                holder.onEvent(EntryUiEvent.ComposerTextChanged("Retry this"))
                holder.onEvent(EntryUiEvent.SendMessageClicked)
                awaitObservationState(holder) {
                    it.sessionList?.openedSession?.hasUnresolvedSubmission == true
                }

                holder.onEvent(EntryUiEvent.ConfirmRemoveGatewayConnectionClicked)
                recoveryRegistry.failSave = false
                holder.onEvent(EntryUiEvent.AddGatewayConnectionClicked)
                holder.onEvent(EntryUiEvent.EndpointChanged("https://gateway.example/profile"))
                holder.onEvent(EntryUiEvent.BearerCredentialChanged("test-only-credential"))
                holder.onEvent(EntryUiEvent.VerifyGatewayConnectionClicked)

                awaitObservationCondition {
                    recoveryRegistry.load() == listOf(RunRecoveryEntry(session.id, run.id))
                }
                assertEquals(1, gateway.createRunCount)
            } finally {
                observation.release.complete(Unit)
                holder.close()
            }
        }

    @Test
    fun recovery_load_failure_blocks_submission_until_authoritative_recovery_is_available() =
        runTest {
            val gateway = ObservationScriptedGateway(observationSession("recovery-load-failure"))
            val holder = observationHolder(gateway, recoveryRegistry = ObservationFailingLoadRunRecoveryRegistry())

            try {
                openObservedSession(holder, gateway, gateway.session.id)
                awaitObservationState(holder) {
                    val opened = it.sessionList?.openedSession
                    opened?.hasUnresolvedSubmission == true &&
                        opened.sendErrorCategory == MessageSendErrorCategory.UNCERTAIN
                }
                holder.onEvent(EntryUiEvent.ComposerTextChanged("Do not duplicate"))
                holder.onEvent(EntryUiEvent.SendMessageClicked)
                assertEquals(0, gateway.createRunCount)
            } finally {
                holder.close()
            }
        }

    @Test
    fun restart_reconciles_only_known_local_nonterminal_runs() =
        runTest {
            val session = observationSession("session-1")
            val run = Run(RunId("run-local"), session.id, "running")
            val recoveryEntry = RunRecoveryEntry(session.id, run.id)
            val recoveryRegistry = InMemoryRunRecoveryRegistry(listOf(recoveryEntry))
            val gateway =
                ObservationScriptedGateway(session).apply {
                    enqueueStatus(run)
                }
            val holder = observationHolder(gateway, recoveryRegistry = recoveryRegistry)

            try {
                holder.onEvent(EntryUiEvent.AddGatewayConnectionClicked)
                holder.onEvent(EntryUiEvent.EndpointChanged("https://gateway.example/profile"))
                holder.onEvent(EntryUiEvent.BearerCredentialChanged("memory-only-token"))
                holder.onEvent(EntryUiEvent.VerifyGatewayConnectionClicked)

                awaitObservationState(holder) {
                    it.sessionList?.sessions == listOf(session.toSessionItemUiState()) &&
                        gateway.statusRequests == listOf(run.id)
                }
                assertEquals(listOf(recoveryEntry), recoveryRegistry.load())
                assertTrue(gateway.observedRunIds.isEmpty())
            } finally {
                holder.close()
            }
        }

    @Test
    fun recovery_removal_failure_keeps_metadata_without_failing_the_recovery_job() =
        runTest {
        val session = observationSession("session-remove-failure")
        val run = Run(RunId("run-remove-failure"), session.id, "succeeded")
        val entry = RunRecoveryEntry(session.id, run.id)
        val recoveryRegistry = ObservationFailingRemoveRunRecoveryRegistry(entry)
        val gateway =
            ObservationScriptedGateway(session).apply {
                enqueueStatus(run)
                enqueueHistory(
                    SessionHistory(
                        session.id,
                        listOf(GatewayHistoryMessage("result", "assistant", "Done", run.id, "succeeded")),
                    ),
                )
            }
        val holder = observationHolder(gateway, recoveryRegistry = recoveryRegistry)

        try {
            holder.onEvent(EntryUiEvent.AddGatewayConnectionClicked)
            holder.onEvent(EntryUiEvent.EndpointChanged("https://gateway.example/profile"))
            holder.onEvent(EntryUiEvent.BearerCredentialChanged("test-only-credential"))
            holder.onEvent(EntryUiEvent.VerifyGatewayConnectionClicked)
            awaitObservationCondition { gateway.statusRequests.contains(run.id) }
            assertEquals(listOf(entry), runBlocking { recoveryRegistry.load() })
        } finally {
            holder.close()
        }
    }

    @Test
    fun terminal_observation_forgets_a_recovery_write_queued_after_save_failure() =
        runTest {
            val session = observationSession("session-queued-removal")
            val run = Run(RunId("run-queued-removal"), session.id, "running")
            val observation =
                ObservationDelayedTerminalObservation(
                    RunEvent(RunEventType.SUCCEEDED, run.id, "succeeded", eventId = "succeeded"),
                )
            val recoveryRegistry = ObservationFlakyRunRecoveryRegistry()
            val gateway =
                ObservationScriptedGateway(session).apply {
                    enqueueRun(run)
                    enqueueHistory(SessionHistory(session.id, emptyList()))
                    enqueueHistory(
                        SessionHistory(
                            session.id,
                            listOf(GatewayHistoryMessage("result", "assistant", "Done", run.id, "succeeded")),
                        ),
                    )
                    enqueueStatus(run.copy(status = "succeeded"))
                    this.observation = observation
                }
            val holder = observationHolder(gateway, recoveryRegistry = recoveryRegistry)

            try {
                openObservedSession(holder, gateway, session.id)
                holder.onEvent(EntryUiEvent.ComposerTextChanged("Run this"))
                holder.onEvent(EntryUiEvent.SendMessageClicked)
                observation.started.await()
                assertEquals(1, recoveryRegistry.saveAttempts)

                recoveryRegistry.failSave = false
                observation.release.complete(Unit)
                awaitObservationState(holder) {
                    val opened = it.sessionList?.openedSession
                    opened?.latestRunState == RunPresentationState.SUCCEEDED &&
                        opened.hasUnresolvedSubmission == false
                }
                assertTrue(recoveryRegistry.load().isEmpty())

                holder.onEvent(EntryUiEvent.RefreshSessionsClicked)
                awaitObservationState(holder) { it.sessionList?.isRefreshing == false }
                assertEquals(1, recoveryRegistry.saveAttempts)
                assertTrue(recoveryRegistry.load().isEmpty())
            } finally {
                observation.release.complete(Unit)
                holder.close()
            }
        }

    @Test
    fun recovery_continues_after_switching_sessions_while_authoritative_queries_are_blocked() =
        runTest {
            val firstSession = observationSession("session-recovery-1")
            val secondSession = observationSession("session-recovery-2")
            val firstRun = Run(RunId("run-recovery-1"), firstSession.id, "running")
            val secondRun = Run(RunId("run-recovery-2"), secondSession.id, "running")
            val recoveryRegistry =
                InMemoryRunRecoveryRegistry(
                    listOf(
                        RunRecoveryEntry(firstSession.id, firstRun.id),
                        RunRecoveryEntry(secondSession.id, secondRun.id),
                    ),
                )
            val gateway =
                ObservationScriptedGateway(firstSession, listOf(secondSession)).apply {
                    blockStatus = true
                    statusByRun[firstRun.id] = firstRun.copy(status = "succeeded")
                    statusByRun[secondRun.id] = secondRun.copy(status = "succeeded")
                    historyBySession[firstSession.id] =
                        SessionHistory(
                            firstSession.id,
                            listOf(
                                GatewayHistoryMessage("first-result", "assistant", "Done", firstRun.id, "succeeded"),
                            ),
                        )
                    historyBySession[secondSession.id] =
                        SessionHistory(
                            secondSession.id,
                            listOf(
                                GatewayHistoryMessage("second-result", "assistant", "Done", secondRun.id, "succeeded"),
                            ),
                        )
                }
            val holder = observationHolder(gateway, recoveryRegistry)

            try {
                holder.onEvent(EntryUiEvent.AddGatewayConnectionClicked)
                holder.onEvent(EntryUiEvent.EndpointChanged("https://gateway.example/profile"))
                holder.onEvent(EntryUiEvent.BearerCredentialChanged("test-only-credential"))
                holder.onEvent(EntryUiEvent.VerifyGatewayConnectionClicked)
                awaitObservationState(holder) { it.sessionList?.sessions?.size == 2 }
                gateway.statusStarted.await()

                holder.onEvent(EntryUiEvent.SessionClicked(firstSession.id))
                awaitObservationState(holder) { it.sessionList?.openedSession?.session?.id == firstSession.id }
                holder.onEvent(EntryUiEvent.ReturnToSessionListClicked)
                holder.onEvent(EntryUiEvent.SessionClicked(secondSession.id))
                awaitObservationState(holder) { it.sessionList?.openedSession?.session?.id == secondSession.id }

                gateway.statusRelease.complete(Unit)
                awaitObservationCondition {
                    recoveryRegistry.load().isEmpty() &&
                        gateway.statusRequests.containsAll(listOf(firstRun.id, secondRun.id))
                }
            } finally {
                gateway.statusRelease.complete(Unit)
                holder.close()
            }
        }

    @Test
    fun restart_removes_a_recovery_entry_only_after_terminal_history_confirms_the_run() =
        runTest {
            val session = observationSession("session-1")
            val run = Run(RunId("run-local"), session.id, "succeeded")
            val recoveryEntry = RunRecoveryEntry(session.id, run.id)
            val recoveryRegistry = InMemoryRunRecoveryRegistry(listOf(recoveryEntry))
            val gateway =
                ObservationScriptedGateway(session).apply {
                    enqueueStatus(run)
                    enqueueHistory(
                        SessionHistory(
                            session.id,
                            listOf(GatewayHistoryMessage("result", "assistant", "Done", run.id, "succeeded")),
                        ),
                    )
                }
            val holder = observationHolder(gateway, recoveryRegistry = recoveryRegistry)

            try {
                holder.onEvent(EntryUiEvent.AddGatewayConnectionClicked)
                holder.onEvent(EntryUiEvent.EndpointChanged("https://gateway.example/profile"))
                holder.onEvent(EntryUiEvent.BearerCredentialChanged("memory-only-token"))
                holder.onEvent(EntryUiEvent.VerifyGatewayConnectionClicked)

                awaitObservationState(holder) {
                    it.sessionList?.sessions == listOf(session.toSessionItemUiState()) &&
                        gateway.statusRequests == listOf(run.id) &&
                        recoveryRegistry.load().isEmpty()
                }
                assertTrue(gateway.observedRunIds.isEmpty())
            } finally {
                holder.close()
            }
        }
}
