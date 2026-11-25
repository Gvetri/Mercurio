package org.mercurio.client.feature.entry.presentation

import org.mercurio.client.feature.entry.domain.RunId
import org.mercurio.client.feature.entry.domain.RunPresentationState
import org.mercurio.client.feature.entry.domain.SessionId
import org.mercurio.client.feature.entry.domain.isRunRetryEligible
import org.mercurio.client.feature.entry.domain.toRunPresentationState

internal fun EntryStateHolder.retryRun(runId: RunId) {
    val originalMessage = retryOriginalMessage(runId) ?: return
    launchRunSubmission(input = originalMessage, recordDraft = false, rejectRunId = runId)
}

private fun EntryStateHolder.retryOriginalMessage(runId: RunId): String? =
    mutableUiState.value.sessionList?.openedSession?.let { opened ->
        originalRunInput(opened, runId)?.takeIf { original ->
            isRunRetryEligible(failedRunState(opened, opened.session.id, runId), original)
        }
    }

private fun EntryStateHolder.failedRunState(
    opened: OpenSessionUiState,
    sessionId: SessionId,
    runId: RunId,
): RunPresentationState? =
    visibleSessionRuns(sessionId).lastOrNull { it.id == runId }?.toRunPresentationState()
        ?: observationStateFor(sessionId, runId)?.state
        ?: opened.messages.lastOrNull { it.runId == runId }?.runState
        ?: opened.messages
            .lastOrNull { it.runId == runId && it.isFailedRun }
            ?.let { RunPresentationState.FAILED }

private fun EntryStateHolder.originalRunInput(
    opened: OpenSessionUiState,
    runId: RunId,
): String? =
    submittedRunInputs[runId]
        ?: opened.messages
            .lastOrNull { it.runId == runId && it.role == "user" }
            ?.content
            ?.takeIf { it.isNotBlank() }
