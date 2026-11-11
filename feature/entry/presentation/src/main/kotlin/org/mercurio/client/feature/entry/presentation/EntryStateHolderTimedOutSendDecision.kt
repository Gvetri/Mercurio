package org.mercurio.client.feature.entry.presentation

import org.mercurio.client.feature.entry.domain.PendingRunSubmissionKey
import org.mercurio.client.feature.entry.domain.Run
import org.mercurio.client.feature.entry.domain.RunId
import org.mercurio.client.feature.entry.domain.RunReconciliationDecision
import org.mercurio.client.feature.entry.domain.RunSubmissionUncertaintySnapshot
import org.mercurio.client.feature.entry.domain.SessionId
import org.mercurio.client.feature.entry.domain.SessionReconciliation
import org.mercurio.client.feature.entry.domain.decideRunReconciliation

internal fun EntryStateHolder.confirmedTerminalRunIds(
    reconciliation: SessionReconciliation,
    submissionRun: Run?,
): Set<RunId> =
    reconciliation.discoveredRuns
        .filter { run ->
            decideRunReconciliation(run) ==
                RunReconciliationDecision.CONFIRMED
        }
        .map { it.id }
        .plus(
            submissionRun
                ?.takeIf { run -> decideRunReconciliation(run) == RunReconciliationDecision.CONFIRMED }
                ?.id,
        )
        .filterNotNull()
        .toSet()

internal fun EntryStateHolder.submissionConfirmed(
    submissionRun: Run?,
    belongsToThisSubmission: Boolean,
): Boolean =
    submissionRun != null &&
        decideRunReconciliation(submissionRun) == RunReconciliationDecision.CONFIRMED &&
        belongsToThisSubmission

internal fun EntryStateHolder.recoveryStoreAllowsNoNewRun(
    uncertaintySnapshot: RunSubmissionUncertaintySnapshot?,
    belongsToThisSubmission: Boolean,
): Boolean =
    uncertaintySnapshot == null ||
        (
            belongsToThisSubmission &&
                uncertaintySnapshot.settled &&
                !uncertaintySnapshot.requiresRunMatch
        )

internal fun EntryStateHolder.noNewRunConfirmsNoSubmission(
    sessionId: SessionId,
    resolveWhenNoNewRun: Boolean,
    discoveredUnattributedRun: Boolean,
    recoveryStoreAllowsNoNewRun: Boolean,
): Boolean =
    resolveWhenNoNewRun &&
        sessionId !in ambiguousSubmissionSessions &&
        !discoveredUnattributedRun &&
        recoveryStoreAllowsNoNewRun &&
        uncertainSubmissionRunIds[sessionId] == null

internal fun EntryStateHolder.uncertaintyCanBeRemoved(
    pendingKey: PendingRunSubmissionKey,
    uncertaintySnapshot: RunSubmissionUncertaintySnapshot?,
    canResolveUncertainty: Boolean,
): Boolean =
    !canResolveUncertainty ||
        !runSubmissionUncertaintyStore.contains(pendingKey) ||
        runCatching {
            uncertaintySnapshot != null &&
                runSubmissionUncertaintyStore.removeIfSnapshotMatches(pendingKey, uncertaintySnapshot)
        }.getOrDefault(false)

internal fun EntryStateHolder.uncertaintyIdentityIsStable(
    pendingKey: PendingRunSubmissionKey,
    uncertaintySnapshot: RunSubmissionUncertaintySnapshot?,
    canClearUncertainty: Boolean,
    belongsToThisSubmission: Boolean,
): Boolean {
    val currentSnapshot = runSubmissionUncertaintyStore.snapshot(pendingKey)
    val stable =
        if (canClearUncertainty) {
            currentSnapshot == null
        } else if (uncertaintySnapshot == null) {
            currentSnapshot == null
        } else {
            currentSnapshot?.let { current ->
                current.attemptId == uncertaintySnapshot.attemptId &&
                    current.knownRunIds == uncertaintySnapshot.knownRunIds &&
                    current.boundRunId == uncertaintySnapshot.boundRunId
            } ?: false
        }
    return belongsToThisSubmission && stable
}

internal fun EntryStateHolder.evaluateTimedOutSendUncertainty(
    sessionId: SessionId,
    reconciliation: SessionReconciliation,
    resolveWhenNoNewRun: Boolean,
): TimedOutSendUncertainty? {
    val pendingRecovery = pendingTimedOutSends[sessionId]
    val pendingKey = pendingRunSubmissionKey(sessionId)
    val uncertaintySnapshot = runSubmissionUncertaintyStore.snapshot(pendingKey)
    val attemptId =
        pendingRecovery?.attemptId ?: uncertaintySnapshot?.attemptId ?: LEGACY_ATTEMPT_ID
    val authoritativeRuns = authoritativeRunsFor(reconciliation)
    val retainedAuthoritativeRuns = retainedAuthoritativeRuns(sessionId, authoritativeRuns)
    val belongsToThisSubmission =
        uncertaintyBelongsToSubmission(uncertaintySnapshot, pendingRecovery, attemptId)
    val durableBoundRunId =
        uncertaintySnapshot
            ?.boundRunId
            ?.takeIf { belongsToThisSubmission }
    val localBoundRunId = localBoundRunIdFor(sessionId, attemptId, uncertaintySnapshot)
    val boundSubmissionRun = boundSubmissionRun(sessionId, durableBoundRunId, localBoundRunId, reconciliation)
    val discoveredLocalRun: Run? = null
    val discoveredUnattributedRun =
        discoveredUnattributedRun(boundSubmissionRun, reconciliation, belongsToThisSubmission)
    val submissionRun = boundSubmissionRun ?: discoveredLocalRun
    val terminalRunIds = confirmedTerminalRunIds(reconciliation, submissionRun)
    val recoveryAllowsNoNewRun =
        recoveryStoreAllowsNoNewRun(uncertaintySnapshot, belongsToThisSubmission)
    val noNewRunConfirms =
        noNewRunConfirmsNoSubmission(
            sessionId = sessionId,
            resolveWhenNoNewRun = resolveWhenNoNewRun,
            discoveredUnattributedRun = discoveredUnattributedRun,
            recoveryStoreAllowsNoNewRun = recoveryAllowsNoNewRun,
        )
    val submissionConfirms = submissionConfirmed(submissionRun, belongsToThisSubmission)
    val canResolveUncertainty = belongsToThisSubmission && (submissionConfirms || noNewRunConfirms)
    val uncertaintyRemoved = uncertaintyCanBeRemoved(pendingKey, uncertaintySnapshot, canResolveUncertainty)
    val canClearUncertainty = canResolveUncertainty && uncertaintyRemoved
    val identityIsStable =
        uncertaintyIdentityIsStable(pendingKey, uncertaintySnapshot, canClearUncertainty, belongsToThisSubmission)
    return if (identityIsStable) {
        TimedOutSendUncertainty(
            pendingKey = pendingKey,
            pendingRecovery = pendingRecovery,
            recoveryDraft = pendingRecovery?.draft,
            attemptId = attemptId,
            authoritativeMessages = toMessageUiStates(reconciliation.history),
            retainedAuthoritativeRuns = retainedAuthoritativeRuns,
            authoritativeRuns = authoritativeRuns,
            durableBoundRunId = durableBoundRunId,
            discoveredUnattributedRun = discoveredUnattributedRun,
            boundSubmissionRun = boundSubmissionRun,
            discoveredLocalRun = discoveredLocalRun,
            submissionRun = submissionRun,
            terminalRunIds = terminalRunIds,
            canClearUncertainty = canClearUncertainty,
        )
    } else {
        null
    }
}
