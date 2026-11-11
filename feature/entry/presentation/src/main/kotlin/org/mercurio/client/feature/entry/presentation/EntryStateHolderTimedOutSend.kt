package org.mercurio.client.feature.entry.presentation

import org.mercurio.client.feature.entry.domain.SessionId

internal fun EntryStateHolder.markTimedOutSendUncertain(
    sessionId: SessionId,
    ui: TimedOutSendUiContext,
    errorCategory: MessageSendErrorCategory = MessageSendErrorCategory.UNCERTAIN,
): Boolean {
    unresolvedSubmissionSessions += sessionId
    sessionSendErrors[sessionId] = errorCategory
    val pendingKey = pendingRunSubmissionKey(sessionId)
    if (runSubmissionUncertaintyStore.contains(pendingKey)) {
        uncertainSubmissionAttemptIds[sessionId] =
            pendingTimedOutSends[sessionId]?.attemptId
                ?: runSubmissionUncertaintyStore.attemptId(pendingKey)
                ?: LEGACY_ATTEMPT_ID
    }
    pendingRunDrafts.remove(sessionId)?.let { uncertainSendDrafts[sessionId] = it }
        ?: ui.recoveryDraft?.let { uncertainSendDrafts[sessionId] = it }
    val knownRuns = visibleSessionRuns(sessionId)
    val latestObservation = latestObservationState(sessionId, knownRuns)
    mutableUiState.value =
        mutableUiState.value.copy(
            sessionList =
                ui.current.copy(
                    openedSession =
                        ui.opened.withRunSnapshot(knownRuns, latestObservation).copy(
                            messages = ui.authoritativeMessages ?: ui.opened.messages,
                            isSending = false,
                            sendErrorCategory = errorCategory,
                            hasUnresolvedSubmission = true,
                            errorCategory = SessionHistoryErrorCategory.RECONCILIATION_FAILED,
                            isStale = true,
                            isCached = ui.authoritativeMessages == null && ui.opened.isCached,
                        ),
                ),
        )
    return true
}

internal fun EntryStateHolder.clearTimedOutSendUncertainty(
    sessionId: SessionId,
    current: SessionListUiState,
    opened: OpenSessionUiState,
    evaluation: TimedOutSendUncertainty,
    resolveWhenNoNewRun: Boolean,
) {
    unresolvedSubmissionSessions.remove(sessionId)
    ambiguousSubmissionSessions.remove(sessionId)
    uncertainSubmissionRunIds.remove(sessionId)
    uncertainSubmissionAttemptIds.remove(sessionId)
    evaluation.submissionRun?.let { run -> forgetObservationState(sessionId, run.id) }
    uncertainSendDrafts.remove(sessionId)
    val clearedSendErrorCategory =
        if (resolveWhenNoNewRun) {
            MessageSendErrorCategory.GATEWAY_REQUEST_FAILED
        } else {
            evaluation.pendingRecovery?.errorCategory ?: MessageSendErrorCategory.GATEWAY_REQUEST_FAILED
        }
    sessionSendErrors[sessionId] = clearedSendErrorCategory
    val knownRuns = visibleSessionRuns(sessionId)
    val latestObservation = latestObservationState(sessionId, knownRuns)
    mutableUiState.value =
        mutableUiState.value.copy(
            sessionList =
                current.copy(
                    openedSession =
                        opened.withRunSnapshot(knownRuns, latestObservation).copy(
                            messages = evaluation.authoritativeMessages,
                            composerText = sessionDrafts[sessionId].orEmpty(),
                            isSending = false,
                            sendErrorCategory = clearedSendErrorCategory,
                            hasUnresolvedSubmission = false,
                            errorCategory = null,
                            isStale = false,
                            isCached = false,
                        ),
                ),
        )
}

internal suspend fun EntryStateHolder.showTimedOutSendFailure(
    sessionId: SessionId,
    requestConnectionGeneration: Long,
    requestSessionGeneration: Long,
    refusalCategory: MessageSendErrorCategory? = null,
) {
    applyStateChange {
        synchronized(sessionRequestLock) {
            if (
                connectionGeneration != requestConnectionGeneration ||
                sessionRequestGeneration != requestSessionGeneration
            ) {
                return@synchronized
            }
            val (current, opened) = openedSessionFor(sessionId) ?: return@synchronized
            val recoveryDraft = pendingTimedOutSends[sessionId]?.draft
            markTimedOutSendUncertain(
                sessionId,
                TimedOutSendUiContext(current, opened, recoveryDraft),
                errorCategory =
                    refusalCategory
                        ?: pendingTimedOutSends[sessionId]?.errorCategory
                        ?: MessageSendErrorCategory.UNCERTAIN,
            )
            pendingTimedOutSends.remove(sessionId)
        }
    }
}
