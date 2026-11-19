package org.mercurio.client.feature.entry.presentation

import kotlinx.coroutines.Job
import org.mercurio.client.feature.entry.domain.RunRecoveryEntry

internal fun EntryStateHolder.clearRecoveryState() {
    connectionRecoveryJobs.clear()
    connectionRecoverySessionCounts.clear()
    connectionRecoveryFailedSessions.clear()
    recoverySessionCounts.clear()
    recoveryClaims.clear()
    recoveryReconciledRunIds.clear()
    recoveryHandledEntries.clear()
}

internal fun EntryStateHolder.clearSessionDraftAndRunState() {
    sessionDrafts.clear()
    pendingSharedText = null
    sessionDraftRevisions.clear()
    sessionSendErrors.clear()
    pendingRunDrafts.clear()
    sessionRuns.clear()
    submittedRunInputs.clear()
    submittedRunModelSelections.clear()
    authoritativeSessionHistoryGenerations.clear()
    authoritativeSessionRuns.clear()
}

internal fun EntryStateHolder.clearEndpointStores(endpoint: String) {
    sessionDraftStore.clearEndpoint(endpoint)
    modelSelectionStore.clearEndpoint(endpoint)
    historyCacheStore.clearEndpoint(endpoint)
}

internal fun EntryStateHolder.collectAndClearRequestJobs(): List<Job> =
    (mutationJobs.values + runJobs.values + runObservationJobs.values).toList().also {
        mutationJobs.clear()
        runJobs.clear()
        runObservationJobs.clear()
        runObservations.clear()
        runObservationRunIds.clear()
        pendingRunObservationRequests.clear()
        runObservationStates.clear()
        unresolvedSubmissionSessions.clear()
        ambiguousSubmissionSessions.clear()
        recoveryUnavailableSessions.clear()
        unresolvedLocalRunIds.clear()
        unresolvedLocalRunAttempts.clear()
        uncertainSendDrafts.clear()
        uncertainSubmissionRunIds.clear()
        uncertainSubmissionAttemptIds.clear()
        pendingTimedOutSends.clear()
        reconcilingSessions.clear()
        notifiedTerminalRunIds.clear()
    }

internal fun EntryStateHolder.releaseRecoverySessionCount(
    entry: RunRecoveryEntry,
    recoveryContext: RecoveryRunContext,
) {
    val remaining =
        connectionRecoverySessionCounts.compute(entry.sessionId) { _, count ->
            ((count ?: 1) - 1).takeIf { it > 0 }
        }
    if (remaining != null) return
    val recoveryFailed = connectionRecoveryFailedSessions.remove(entry.sessionId)
    if (
        recoveryContext.updateVisibleUi &&
        sessionRequestGeneration == recoveryContext.sessionGeneration
    ) {
        if (recoveryFailed) {
            markRecoveryFailure(entry.sessionId, recoveryContext.connectionGeneration)
        } else {
            clearRecoveryUiIfIdle(entry.sessionId)
        }
    }
}
