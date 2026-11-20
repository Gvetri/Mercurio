package org.mercurio.client.feature.entry.presentation

import org.mercurio.client.feature.entry.domain.PendingRunSubmissionKey
import org.mercurio.client.feature.entry.domain.Run
import org.mercurio.client.feature.entry.domain.RunId

internal data class TimedOutSendUncertainty(
    val pendingKey: PendingRunSubmissionKey,
    val pendingRecovery: TimedOutSendRecovery?,
    val recoveryDraft: PendingDraft?,
    val attemptId: String,
    val authoritativeMessages: List<SessionMessageUiState>,
    val retainedAuthoritativeRuns: List<Run>,
    val authoritativeRuns: List<Run>,
    val durableBoundRunId: RunId?,
    val discoveredUnattributedRun: Boolean,
    val boundSubmissionRun: Run?,
    val discoveredLocalRun: Run?,
    val submissionRun: Run?,
    val terminalRunIds: Set<RunId>,
    val canClearUncertainty: Boolean,
)
