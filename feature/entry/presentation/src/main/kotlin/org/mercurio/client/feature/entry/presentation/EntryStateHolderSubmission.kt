package org.mercurio.client.feature.entry.presentation

import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.mercurio.client.feature.entry.domain.Run
import org.mercurio.client.feature.entry.domain.RunGatewayPort
import org.mercurio.client.feature.entry.domain.RunId
import org.mercurio.client.feature.entry.domain.RunPresentationState
import org.mercurio.client.feature.entry.domain.RunSubmissionState
import org.mercurio.client.feature.entry.domain.SessionId
import org.mercurio.client.feature.entry.domain.isActive
import org.mercurio.client.feature.entry.domain.isRunRetryEligible
import org.mercurio.client.feature.entry.domain.runs
import org.mercurio.client.feature.entry.domain.toRunPresentationState
import java.util.UUID

internal fun EntryStateHolder.retryUncertainSubmissionOrSend() {
    val retryContext =
        run {
            val opened = mutableUiState.value.sessionList?.openedSession ?: return@run null
            val sessionId = opened.session.id
            val key = pendingRunSubmissionKey(sessionId)
            if (!opened.hasUnresolvedSubmission ||
                (runSubmissionUncertaintyStore.contains(key) && !runSubmissionUncertaintyStore.isSettled(key))
            ) {
                null
            } else {
                Triple(
                    sessionId,
                    connectionGeneration,
                    runSubmissionUncertaintyStore.knownRunIds(key) to
                        (runSubmissionUncertaintyStore.attemptId(key) ?: LEGACY_ATTEMPT_ID),
                )
            }
        }
    if (retryContext == null) {
        sendMessage()
        return
    }
    val (sessionId, requestConnectionGeneration, recovery) = retryContext
    val (knownRunIds, attemptId) = recovery
    scope.launch {
        val reconciled =
            reconcileTimedOutSend(
                TimedOutSendReconciliationRequest(
                    sessionId = sessionId,
                    connectionGeneration = requestConnectionGeneration,
                    knownRunIds = knownRunIds,
                    attemptId = attemptId,
                    resolveWhenNoNewRun = true,
                ),
            )
        if (reconciled) {
            val canRetry =
                run {
                    !hasUnresolvedSubmission(sessionId)
                }
            if (canRetry) {
                awaitStateChangesApplied()
                sendMessage()
            }
        }
    }
}

internal fun EntryStateHolder.sendMessage() {
    val input =
        run {
            mutableUiState.value.sessionList?.openedSession?.composerText?.takeIf { it.isNotBlank() }
        } ?: return
    launchRunSubmission(input = input, recordDraft = true)
}

internal fun EntryStateHolder.dispatchSendMessage() {
    val retryPending =
        run {
            mutableUiState.value.sessionList?.openedSession?.hasUnresolvedSubmission == true
        }
    if (retryPending) {
        scope.launch { retryUncertainSubmissionOrSend() }
    } else {
        sendMessage()
    }
}

internal fun EntryStateHolder.retryRun(runId: RunId) {
    val originalMessage =
        run {
            val opened = mutableUiState.value.sessionList?.openedSession ?: return@run null
            val sessionId = opened.session.id
            val failedState =
                visibleSessionRuns(sessionId).lastOrNull { it.id == runId }?.toRunPresentationState()
                    ?: observationStateFor(sessionId, runId)?.state
                    ?: opened.messages.lastOrNull { it.runId == runId }?.runState
                    ?: opened.messages
                        .lastOrNull { it.runId == runId && it.isFailedRun }
                        ?.let { RunPresentationState.FAILED }
            val original =
                submittedRunInputs[runId]
                    ?: opened.messages
                        .lastOrNull { it.runId == runId && it.role == "user" }
                        ?.content
                        ?.takeIf { it.isNotBlank() }
            if (!isRunRetryEligible(failedState, original)) return@run null
            original
        } ?: return
    launchRunSubmission(input = originalMessage, recordDraft = false, rejectRunId = runId)
}

internal fun EntryStateHolder.latestRunRetryAvailable(
    runs: List<Run>,
    latestRunState: RunPresentationState?,
): Boolean {
    val latestRun = runs.latestRun() ?: return false
    return isRunRetryEligible(latestRunState, submittedRunInputs[latestRun.id])
}

internal fun EntryStateHolder.launchRunSubmission(
    input: String,
    recordDraft: Boolean,
    rejectRunId: RunId? = null,
) {
    val gateway = runGateway ?: return
    prepareRunSubmission(gateway, input, recordDraft, rejectRunId)?.start()
}

internal fun EntryStateHolder.prepareRunSubmission(
    gateway: RunGatewayPort,
    input: String,
    recordDraft: Boolean,
    rejectRunId: RunId?,
): Job? =
    run {
        val current = mutableUiState.value.sessionList ?: return@run null
        val opened = current.openedSession ?: return@run null
        val sessionId = opened.session.id
        val requestConnectionGeneration = connectionGeneration
        val (knownRuns, _) = submissionRunsAndState(opened, sessionId)
        if (blocksRunSubmissionNow(current, sessionId)) {
            return@run null
        }
        mutableUiState.value =
            mutableUiState.value.copy(
                sessionList =
                    current.copy(
                        openedSession =
                            opened.copy(
                                isSending = true,
                                sendErrorCategory = null,
                            ),
                    ),
            )
        sessionSendErrors.remove(sessionId)
        if (recordDraft) {
            recordSubmissionDraft(sessionId, input)
        }
        val requestEndpoint = mutableUiState.value.endpoint
        val recoverySessionKey = RecoverySessionKey(requestEndpoint, sessionId)
        val knownRunIds = knownRuns.mapTo(mutableSetOf()) { it.id }
        val attemptId = UUID.randomUUID().toString()
        val pendingCreate = PendingCreateState(knownRunIds, attemptId)
        pendingCreateSessions[recoverySessionKey] = pendingCreate
        val work =
            RunSubmissionWork(
                gateway = gateway,
                input = input,
                rejectRunId = rejectRunId,
                sessionId = sessionId,
                requestConnectionGeneration = requestConnectionGeneration,
                requestEndpoint = requestEndpoint,
                recoverySessionKey = recoverySessionKey,
                pendingCreate = pendingCreate,
                attemptId = attemptId,
                knownRunIds = knownRunIds,
                selection = null,
                recordDraft = recordDraft,
            )
        createRunJob(sessionId) { submitRun(work) }
    }

internal fun EntryStateHolder.submissionPersisted(
    run: Run,
    persistBeforeApply: Boolean,
    recoveryEntryPersisted: Boolean,
    submissionBindingSucceeded: Boolean,
): Boolean = run.isActive() && persistBeforeApply && recoveryEntryPersisted && submissionBindingSucceeded

internal fun EntryStateHolder.submissionRunsAndState(
    opened: OpenSessionUiState,
    sessionId: SessionId,
): Pair<List<Run>, RunSubmissionState> {
    val knownRuns =
        visibleSessionRuns(sessionId).ifEmpty {
            (opened.activeRuns + listOfNotNull(opened.latestRun)).distinctBy { it.id }
        }
    val latestRun = knownRuns.latestRun() ?: opened.latestRun
    val state =
        RunSubmissionState(
            latestRun = latestRun,
            activeRuns = knownRuns.activeRuns(),
            isSubmissionPending = opened.isSending || runJobs.containsKey(sessionId),
        )
    return knownRuns to state
}

internal fun EntryStateHolder.blocksRunSubmissionNow(
    current: SessionListUiState,
    sessionId: SessionId,
): Boolean {
    val opened = current.openedSession?.takeIf { it.session.id == sessionId }
    if (opened == null) return true
    val (_, submissionState) = submissionRunsAndState(opened, sessionId)
    return !submissionState.canSubmit ||
        current.hasPendingMutation ||
        blocksRunSubmission(opened, sessionId)
}

internal fun EntryStateHolder.recordSubmissionDraft(
    sessionId: SessionId,
    input: String,
) {
    val draft =
        PendingDraft(
            text = input,
            revision = sessionDraftRevisions[sessionId] ?: 0L,
        )
    pendingRunDrafts[sessionId] = draft
    sessionDrafts[sessionId] = input
}
