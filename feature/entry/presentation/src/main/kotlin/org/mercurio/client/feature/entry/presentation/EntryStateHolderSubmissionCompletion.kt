package org.mercurio.client.feature.entry.presentation

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.mercurio.client.feature.entry.application.SubmitMessage
import org.mercurio.client.feature.entry.domain.GatewayErrorCategory
import org.mercurio.client.feature.entry.domain.GatewayException
import org.mercurio.client.feature.entry.domain.LocalDiagnosticEventType
import org.mercurio.client.feature.entry.domain.LocalDiagnosticStatus
import org.mercurio.client.feature.entry.domain.PendingRunSubmissionKey
import org.mercurio.client.feature.entry.domain.Run
import org.mercurio.client.feature.entry.domain.RunId
import org.mercurio.client.feature.entry.domain.SessionId
import org.mercurio.client.feature.entry.domain.isActive

internal fun EntryStateHolder.abortUnclaimedRun(work: RunSubmissionWork) {
    synchronized(sessionRequestLock) {
        if (
            pendingCreateSessions[work.recoverySessionKey] ==
            PendingCreateState(work.knownRunIds, work.attemptId)
        ) {
            pendingCreateSessions.remove(work.recoverySessionKey)
            pendingCreateStarted.remove(work.recoverySessionKey)
            unresolvedSubmissionSessions += work.sessionId
            pendingRunDrafts.remove(work.sessionId)?.let { draft ->
                uncertainSendDrafts[work.sessionId] = draft
            }
            sessionSendErrors[work.sessionId] = MessageSendErrorCategory.UNCERTAIN
            val (current, opened) = openedSessionFor(work.sessionId) ?: return@synchronized
            mutableUiState.value =
                mutableUiState.value.copy(
                    sessionList =
                        current.copy(
                            openedSession =
                                opened.copy(
                                    isSending = false,
                                    sendErrorCategory = MessageSendErrorCategory.UNCERTAIN,
                                    hasUnresolvedSubmission = true,
                                ),
                        ),
                )
        }
    }
}

internal fun EntryStateHolder.prepareClaimedSubmission(work: RunSubmissionWork): RunSubmissionWork? {
    if (work.recordDraft) {
        runCatching { sessionDraftStore.write(sessionDraftKey(work.sessionId, work.requestEndpoint), work.input) }
    }
    val claimed =
        runCatching {
            runSubmissionUncertaintyStore.add(
                PendingRunSubmissionKey(work.requestEndpoint, work.sessionId),
                work.knownRunIds,
                work.attemptId,
            )
        }.getOrDefault(false)
    if (!claimed) {
        abortUnclaimedRun(work)
        return null
    }
    val selection = runCatching { modelSelectionStore.read(work.requestEndpoint) }.getOrNull()
    return work.copy(selection = selection)
}

internal suspend fun EntryStateHolder.submitRun(work: RunSubmissionWork) {
    if (
        !markPendingCreateStarted(
            work.recoverySessionKey,
            work.pendingCreate,
            work.requestConnectionGeneration,
        )
    ) {
        return
    }
    val submission = prepareClaimedSubmission(work) ?: return
    try {
        val run =
            withContext(NonCancellable) {
                withTimeout(sendTimeoutMillis) {
                    runInterruptible {
                        SubmitMessage(submission.gateway)
                            .execute(submission.sessionId, submission.input, submission.selection)
                    }
                }
            }
        val applied = completeSuccessfulSubmission(submission, run)
        if (applied) {
            if (!run.isActive()) {
                reconcileInactiveSubmittedRun(submission.sessionId, run.id, submission.requestConnectionGeneration)
            }
            onRunSubmissionCompleted?.invoke()
        }
    } catch (_: TimeoutCancellationException) {
        if (reportUncertainRunSubmission(submission)) {
            onRunSubmissionCompleted?.invoke()
        }
    } catch (error: CancellationException) {
        runCatching {
            runSubmissionUncertaintyStore.markAmbiguous(
                PendingRunSubmissionKey(submission.requestEndpoint, submission.sessionId),
                submission.attemptId,
            )
        }
        throw error
    } catch (error: GatewayException) {
        val sendCategory =
            error.category.toSendFailureCategoryOrNull() ?: MessageSendErrorCategory.GATEWAY_REQUEST_FAILED
        if (reportUncertainRunSubmission(submission, sendCategory)) {
            onRunSubmissionCompleted?.invoke()
        }
    } catch (_: Exception) {
        if (reportUncertainRunSubmission(submission, MessageSendErrorCategory.GATEWAY_REQUEST_FAILED)) {
            onRunSubmissionCompleted?.invoke()
        }
    } finally {
        forgetPendingCreate(submission.recoverySessionKey, submission.pendingCreate)
        onRunSubmissionSettled?.invoke()
    }
}

internal suspend fun EntryStateHolder.completeSuccessfulSubmission(
    work: RunSubmissionWork,
    run: Run,
): Boolean {
    if (work.rejectRunId != null && run.id == work.rejectRunId) {
        throw GatewayException(
            GatewayErrorCategory.INVALID_RESPONSE,
            "Retry response reused the failed Run identity.",
        )
    }
    synchronized(sessionRequestLock) {
        if (connectionGeneration == work.requestConnectionGeneration) {
            submittedRunInputs[run.id] = work.input
            work.selection?.let { selection ->
                submittedRunModelSelections[run.id] = selection
                modelSelectionStore.recordUse(work.requestEndpoint, selection.model)
            }
        }
    }
    val applied =
        applySubmittedRun(
            sessionId = work.sessionId,
            run = run,
            requestConnectionGeneration = work.requestConnectionGeneration,
            requestEndpoint = work.requestEndpoint,
            attemptId = work.attemptId,
        )
    forgetPendingCreate(work.recoverySessionKey, work.pendingCreate)
    recordDiagnostic(LocalDiagnosticEventType.RUN_SUBMISSION, LocalDiagnosticStatus.SUCCEEDED)
    return applied
}

internal suspend fun EntryStateHolder.reportUncertainRunSubmission(
    work: RunSubmissionWork,
    uncertaintyErrorCategory: MessageSendErrorCategory = MessageSendErrorCategory.UNCERTAIN,
): Boolean {
    recordDiagnostic(LocalDiagnosticEventType.RUN_SUBMISSION, LocalDiagnosticStatus.UNCERTAIN)
    runCatching {
        runSubmissionUncertaintyStore.markSettled(
            PendingRunSubmissionKey(work.requestEndpoint, work.sessionId),
            work.attemptId,
        )
    }
    forgetPendingCreate(work.recoverySessionKey, work.pendingCreate)
    return reconcileTimedOutSend(
        TimedOutSendReconciliationRequest(
            sessionId = work.sessionId,
            connectionGeneration = work.requestConnectionGeneration,
            knownRunIds = work.knownRunIds,
            attemptId = work.attemptId,
            uncertaintyErrorCategory = uncertaintyErrorCategory,
        ),
    )
}

internal suspend fun EntryStateHolder.reconcileInactiveSubmittedRun(
    sessionId: SessionId,
    runId: RunId,
    requestConnectionGeneration: Long,
) {
    val reconciliationContext =
        synchronized(sessionRequestLock) {
            sessionGateway
                ?.takeIf {
                    connectionGeneration == requestConnectionGeneration &&
                        mutableUiState.value.sessionList?.openedSession?.session?.id == sessionId
                }?.let { it to sessionRequestGeneration }
        }
    if (reconciliationContext != null) {
        reconcileRun(
            sessionId = sessionId,
            runId = runId,
            requestConnectionGeneration = requestConnectionGeneration,
            requestSessionGeneration = reconciliationContext.second,
            sessionGateway = reconciliationContext.first,
        )
    }
}

internal fun EntryStateHolder.prepareSettledSubmissionRecovery(sessionId: SessionId): TimedOutSendRecovery? {
    return synchronized(sessionRequestLock) {
        val key = pendingRunSubmissionKey(sessionId)
        if (
            !runSubmissionUncertaintyStore.contains(key) ||
            !runSubmissionUncertaintyStore.isSettled(key) ||
            pendingTimedOutSends.containsKey(sessionId)
        ) {
            null
        } else {
            TimedOutSendRecovery(
                knownRunIds = runSubmissionUncertaintyStore.knownRunIds(key),
                attemptId = runSubmissionUncertaintyStore.attemptId(key) ?: LEGACY_ATTEMPT_ID,
                draft =
                    PendingDraft(
                        text = sessionDrafts[sessionId].orEmpty(),
                        revision = sessionDraftRevisions[sessionId] ?: 0L,
                    ),
            ).also { pendingTimedOutSends[sessionId] = it }
        }
    }
}
