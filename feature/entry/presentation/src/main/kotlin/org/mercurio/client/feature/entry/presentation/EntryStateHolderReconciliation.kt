package org.mercurio.client.feature.entry.presentation

import org.mercurio.client.feature.entry.domain.PendingRunSubmissionKey
import org.mercurio.client.feature.entry.domain.Run
import org.mercurio.client.feature.entry.domain.RunId
import org.mercurio.client.feature.entry.domain.RunPresentationState
import org.mercurio.client.feature.entry.domain.RunRecoveryEntry
import org.mercurio.client.feature.entry.domain.SessionId
import org.mercurio.client.feature.entry.domain.isActive
import org.mercurio.client.feature.entry.domain.isTerminal

internal fun EntryStateHolder.recordTerminalPollReconciliation(
    sessionId: SessionId,
    run: Run,
) {
    authoritativeSessionRuns[sessionId] =
        mergeRuns(authoritativeSessionRuns[sessionId].orEmpty(), listOf(run))
    if (isLocallyOwnedRun(sessionId, run.id)) {
        sessionRuns[sessionId] = mergeRuns(sessionRuns[sessionId].orEmpty(), listOf(run))
    }
    forgetObservationState(sessionId, run.id)
    updateVisibleRunState(sessionId)
}

internal suspend fun EntryStateHolder.reconcileOpenedRun(context: OpenedRunReconcileContext) {
    val recoveryEntries =
        try {
            runRecoveryRegistry?.load().orEmpty()
        } catch (_: Exception) {
            null
        }
    val recoveryEntriesLoadFailed = recoveryEntries == null
    val sessionRecoveryEntries =
        recoveryEntries.orEmpty().filter { entry -> entry.sessionId == context.sessionId }
    val runIds = reconciliationRunIds(context, sessionRecoveryEntries, recoveryEntriesLoadFailed) ?: return
    if (runIds.isEmpty()) {
        if (context.clearRefreshWhenNoRun) {
            finishReconciliation(
                context.sessionId,
                context.requestConnectionGeneration,
                context.requestSessionGeneration,
            )
        }
        if (recoveryLoadFailed) {
            markRecoveryUnavailable(
                context.sessionId,
                context.requestConnectionGeneration,
                context.requestSessionGeneration,
            )
        }
        return
    }
    var activeRunToObserve: Run? = null
    runIds.forEach { runId ->
        reconcileRunObservationCandidate(context, runId, activeRunToObserve != null)
            ?.let { candidate -> activeRunToObserve = candidate }
    }
    val runToObserve = activeRunToObserve
    if (
        context.restartObservation &&
        runToObserve != null &&
        shouldReconcileCurrentSession(
            context.requestConnectionGeneration,
            context.requestSessionGeneration,
            context.sessionId,
        )
    ) {
        startRunObservation(
            sessionId = context.sessionId,
            run = runToObserve,
            expectedConnectionGeneration = context.requestConnectionGeneration,
            expectedSessionGeneration = context.requestSessionGeneration,
        )
    }
    if (recoveryLoadFailed) {
        markRecoveryUnavailable(
            context.sessionId,
            context.requestConnectionGeneration,
            context.requestSessionGeneration,
        )
    }
}

internal fun EntryStateHolder.reconciliationRunIds(
    context: OpenedRunReconcileContext,
    sessionRecoveryEntries: List<RunRecoveryEntry>,
    recoveryEntriesLoadFailed: Boolean,
): List<RunId>? =
    synchronized(sessionRequestLock) {
        if (
            connectionGeneration != context.requestConnectionGeneration ||
            sessionRequestGeneration != context.requestSessionGeneration ||
            sessionGateway !== context.sessionGateway
        ) {
            null
        } else {
            val ids = linkedSetOf<RunId>()
            context.runIdToReconcile?.let(ids::add)
            val pendingKey = PendingRunSubmissionKey(mutableUiState.value.endpoint, context.sessionId)
            runSubmissionUncertaintyStore
                .boundRunId(pendingKey)
                ?.let { boundRunId ->
                    uncertainSubmissionRunIds[context.sessionId] = boundRunId
                    uncertainSubmissionAttemptIds[context.sessionId] =
                        runSubmissionUncertaintyStore.attemptId(pendingKey) ?: LEGACY_ATTEMPT_ID
                }
            recoveryLoadPending = false
            recoveryLoadFailed = recoveryEntriesLoadFailed
            if (recoveryEntriesLoadFailed) {
                recoveryUnavailableSessions.add(context.sessionId)
            } else {
                recoveryUnavailableSessions.remove(context.sessionId)
            }
            sessionRecoveryEntries.forEach { entry ->
                ids += entry.runId
                if (
                    sessionRuns[context.sessionId].orEmpty().none { it.id == entry.runId } &&
                    observationStateFor(context.sessionId, entry.runId) == null
                ) {
                    rememberRecoveredRun(
                        entry = entry,
                        run = Run(entry.runId, entry.sessionId, RECOVERY_PENDING_STATUS),
                        updateVisibleState = false,
                    )
                }
            }
            sessionRuns[context.sessionId].orEmpty().filter(Run::isActive).mapTo(ids) { it.id }
            unresolvedLocalRunIds[recoverySessionKey(context.sessionId)].orEmpty().forEach(ids::add)
            uncertainSubmissionRunIds[context.sessionId]?.let(ids::add)
            visibleSessionRuns(context.sessionId).latestActiveRun()?.id?.let(ids::add)
            if (ids.isEmpty()) {
                latestObservedObservationState(context.sessionId, visibleSessionRuns(context.sessionId))
                    ?.takeIf { state -> !state.state.isTerminal() }
                    ?.run
                    ?.id
                    ?.let(ids::add)
            }
            ids.removeAll(unreconciledRecoveryRunIds(context.sessionId))
            ids.toList()
        }
    }

internal fun EntryStateHolder.unreconciledRecoveryRunIds(sessionId: SessionId): Set<RunId> {
    val endpoint = mutableUiState.value.endpoint
    return recoveryClaims.keys
        .asSequence()
        .filter { key ->
            key.sessionId == sessionId &&
                key.endpoint == endpoint &&
                key.runId !in recoveryReconciledRunIds
        }.mapTo(mutableSetOf()) { key -> key.runId }
}

internal fun EntryStateHolder.markRecoveryUnavailable(
    sessionId: SessionId,
    requestConnectionGeneration: Long,
    requestSessionGeneration: Long,
) {
    synchronized(sessionRequestLock) {
        if (
            connectionGeneration != requestConnectionGeneration ||
            sessionRequestGeneration != requestSessionGeneration
        ) {
            return@synchronized
        }
        submitStateChange {
            synchronized(sessionRequestLock) {
                if (
                    connectionGeneration != requestConnectionGeneration ||
                    sessionRequestGeneration != requestSessionGeneration
                ) {
                    return@submitStateChange
                }
                val (current, opened) = openedSessionFor(sessionId) ?: return@submitStateChange
                mutableUiState.value =
                    mutableUiState.value.copy(
                        sessionList =
                            current.copy(
                                openedSession =
                                    opened.copy(
                                        isRefreshing = false,
                                        isStale = true,
                                        errorCategory = SessionHistoryErrorCategory.RECONCILIATION_FAILED,
                                        sendErrorCategory = MessageSendErrorCategory.UNCERTAIN,
                                        hasUnresolvedSubmission = true,
                                        latestRunState = opened.latestRunState ?: RunPresentationState.UNCERTAIN,
                                        isReconciliationInProgress = false,
                                    ),
                            ),
                    )
            }
        }
    }
}
