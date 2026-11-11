package org.mercurio.client.feature.entry.presentation

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import org.mercurio.client.feature.entry.application.ReconcileRun
import org.mercurio.client.feature.entry.domain.PendingRunSubmissionKey
import org.mercurio.client.feature.entry.domain.Run
import org.mercurio.client.feature.entry.domain.RunEventStateTransition
import org.mercurio.client.feature.entry.domain.RunRecoveryEntry
import org.mercurio.client.feature.entry.domain.isActive

internal suspend fun EntryStateHolder.recoverRun(
    entry: RunRecoveryEntry,
    context: RecoveryRunContext,
): Run? {
    val reconciliation =
        try {
            runInterruptible {
                ReconcileRun(context.runGateway, context.sessionGateway).execute(entry.runId, entry.sessionId)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            return null
        }
    applyAuthoritativeRunReconciliation(
        request =
            AuthoritativeReconciliationRequest(
                sessionId = entry.sessionId,
                connectionGeneration = context.connectionGeneration,
                sessionGeneration = context.sessionGeneration,
                historyGeneration = context.historyGeneration,
                updateVisibleUi = context.updateVisibleUi,
            ),
        reconciliation = reconciliation,
    )
    return reconciliation.run
}

internal fun EntryStateHolder.rememberRecoveredRun(
    entry: RunRecoveryEntry,
    run: Run,
    updateVisibleState: Boolean = true,
) {
    val pendingKey = PendingRunSubmissionKey(mutableUiState.value.endpoint, entry.sessionId)
    val uncertaintySnapshot = runSubmissionUncertaintyStore.snapshot(pendingKey)
    if (uncertaintySnapshot != null && entry.runId !in uncertaintySnapshot.knownRunIds) {
        val currentAttemptId = uncertainSubmissionAttemptIds[entry.sessionId] ?: uncertaintySnapshot.attemptId
        if (
            uncertaintySnapshot.attemptId == currentAttemptId &&
            uncertaintySnapshot.boundRunId == entry.runId
        ) {
            uncertainSubmissionRunIds[entry.sessionId] = entry.runId
            uncertainSubmissionAttemptIds[entry.sessionId] = uncertaintySnapshot.attemptId
        } else {
            if (uncertainSubmissionRunIds[entry.sessionId] == entry.runId) {
                uncertainSubmissionRunIds.remove(entry.sessionId)
                uncertainSubmissionAttemptIds.remove(entry.sessionId)
            }
            unresolvedSubmissionSessions += entry.sessionId
            ambiguousSubmissionSessions += entry.sessionId
            runCatching {
                runSubmissionUncertaintyStore.markAmbiguous(
                    pendingKey,
                    uncertaintySnapshot.attemptId,
                )
            }
        }
    }
    sessionRuns[entry.sessionId] =
        mergeRuns(sessionRuns[entry.sessionId].orEmpty(), listOf(run))
    val previous = observationStateFor(entry.sessionId, entry.runId)
    val state =
        if (run.isActive()) {
            val initial = RunEventStateTransition.initial(run)
            previous?.let {
                initial.copy(
                    responseText = it.responseText,
                    processedEventIds = it.processedEventIds,
                )
            } ?: initial
        } else {
            uncertainObservationState(run, previous)
        }
    rememberObservationState(state)
    if (updateVisibleState) updateVisibleRunState(entry.sessionId)
}

internal fun EntryStateHolder.persistDisconnectedRecoveryEntry(
    endpoint: String,
    entry: RunRecoveryEntry,
): Boolean {
    val persisted = persistOrQueueRecoveryEntry(endpoint, entry)
    if (persisted) {
        val reconcileNow =
            run {
                mutableUiState.value.isConnected &&
                    mutableUiState.value.endpoint == endpoint &&
                    sessionGateway != null &&
                    runGateway != null
            }
        if (!reconcileNow) {
            run {
                pendingDisconnectedRecoveryEntries
                    .getOrPut(endpoint, ::mutableSetOf)
                    .add(entry)
            }
        }
        reconcilePersistedRecoveryEntryIfConnected(endpoint, entry)
    }
    return persisted
}

internal fun EntryStateHolder.reconcilePersistedRecoveryEntryIfConnected(
    endpoint: String,
    entry: RunRecoveryEntry,
) {
    val (recoveryContext, claim) = claimPersistedRecoveryRun(endpoint, entry) ?: return
    val job = beginRecoveryJob(endpoint, entry, recoveryContext, claim) ?: return
    job.start()
}

internal fun EntryStateHolder.claimPersistedRecoveryRun(
    endpoint: String,
    entry: RunRecoveryEntry,
): Pair<RecoveryRunContext, RecoveryClaim>? =
    recoveryRunContextFor(endpoint, entry)?.let { recoveryContext ->
        claimRecoveryEntryForRun(endpoint, entry, recoveryContext.connectionGeneration)?.let { claim ->
            recoveryContext to claim
        }
    }

internal fun EntryStateHolder.recoveryRunContextFor(
    endpoint: String,
    entry: RunRecoveryEntry,
): RecoveryRunContext? =
    run {
        val currentSessionGateway = sessionGateway
        val currentRunGateway = runGateway
        if (
            !mutableUiState.value.isConnected ||
            mutableUiState.value.endpoint != endpoint
        ) {
            null
        } else if (currentSessionGateway == null || currentRunGateway == null) {
            null
        } else {
            val opened =
                mutableUiState.value.sessionList?.openedSession?.takeIf { it.session.id == entry.sessionId }
            RecoveryRunContext(
                sessionGateway = currentSessionGateway,
                runGateway = currentRunGateway,
                connectionGeneration = connectionGeneration,
                historyGeneration = authoritativeSessionHistoryGenerations[entry.sessionId] ?: 0L,
                sessionGeneration = opened?.let { sessionRequestGeneration },
                updateVisibleUi = opened != null,
            )
        }
    }

internal fun EntryStateHolder.claimRecoveryEntryForRun(
    endpoint: String,
    entry: RunRecoveryEntry,
    requestConnectionGeneration: Long,
): RecoveryClaim? =
    run {
        if (
            connectionGeneration != requestConnectionGeneration ||
            mutableUiState.value.endpoint != endpoint
        ) {
            null
        } else {
            claimRecoveryEntry(endpoint, entry, requestConnectionGeneration)
        }
    }

internal fun EntryStateHolder.beginRecoveryJob(
    endpoint: String,
    entry: RunRecoveryEntry,
    recoveryContext: RecoveryRunContext,
    claim: RecoveryClaim,
): Job? =
    run {
        if (
            connectionGeneration != recoveryContext.connectionGeneration ||
            mutableUiState.value.endpoint != endpoint ||
            recoveryClaims[claim.key] != claim
        ) {
            return@run null
        }
        rememberRecoveredRun(
            entry = entry,
            run = Run(entry.runId, entry.sessionId, RECOVERY_PENDING_STATUS),
            updateVisibleState = false,
        )
        if (connectionRecoverySessionCounts[entry.sessionId] == null) {
            connectionRecoveryFailedSessions.remove(entry.sessionId)
        }
        connectionRecoverySessionCounts[entry.sessionId] =
            (connectionRecoverySessionCounts[entry.sessionId] ?: 0) + 1
        if (recoveryContext.updateVisibleUi) {
            markRecoveryUiRefreshing(entry, recoveryContext)
        }
        val job = startRecoveryObservationJob(entry, recoveryContext, claim)
        connectionRecoveryJobs += job
        job
    }

internal fun EntryStateHolder.markRecoveryUiRefreshing(
    entry: RunRecoveryEntry,
    recoveryContext: RecoveryRunContext,
) {
    submitStateChange {
        run {
            if (connectionGeneration != recoveryContext.connectionGeneration) return@run
            val (current, opened) = openedSessionFor(entry.sessionId) ?: return@run
            mutableUiState.value =
                mutableUiState.value.copy(
                    sessionList =
                        current.copy(
                            openedSession =
                                opened.copy(
                                    isRefreshing = true,
                                    isReconciliationInProgress = true,
                                ),
                        ),
                )
        }
    }
}

internal fun EntryStateHolder.startRecoveryObservationJob(
    entry: RunRecoveryEntry,
    recoveryContext: RecoveryRunContext,
    claim: RecoveryClaim,
): Job {
    lateinit var job: Job
    job =
        scope.launch(start = CoroutineStart.LAZY) {
            var recoveryFinished = false
            var recoveryCompleted = false
            try {
                val recoveredRun =
                    recoverRun(
                        entry = entry,
                        context = recoveryContext,
                    )
                recoveryFinished = true
                recoveryCompleted = recoveredRun != null
                recoveredRun?.takeIf(Run::isActive)?.let { run ->
                    startRunObservation(
                        sessionId = entry.sessionId,
                        run = run,
                        expectedConnectionGeneration = recoveryContext.connectionGeneration,
                        expectedSessionGeneration = recoveryContext.sessionGeneration,
                        expectedHistoryGeneration = recoveryContext.historyGeneration,
                    )
                }
            } finally {
                run {
                    if (
                        connectionGeneration == recoveryContext.connectionGeneration &&
                        releaseRecoveryClaim(claim)
                    ) {
                        connectionRecoveryJobs.remove(job)
                        recoveryReconciledRunIds.remove(entry.runId)
                        if (recoveryFinished) {
                            recoveryHandledEntries += claim.key
                        }
                        if (!recoveryCompleted) {
                            connectionRecoveryFailedSessions += entry.sessionId
                        }
                        releaseRecoverySessionCount(entry, recoveryContext)
                    }
                }
            }
        }
    return job
}
