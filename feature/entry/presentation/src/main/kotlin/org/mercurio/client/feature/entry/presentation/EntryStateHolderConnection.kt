package org.mercurio.client.feature.entry.presentation

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

internal fun EntryStateHolder.releaseConnectionState(): ReleasedConnectionState =
    run {
        sessionRequestGeneration += 1
        connectionGeneration += 1
        sessionGateway = null
        runGateway = null
        catalogGateway = null
        val supersededJobs = listOfNotNull(verificationJob, sessionJob, recoveryJob)
        verificationJob = null
        sessionJob = null
        recoveryJob = null
        val requestJobs =
            listOf(
                mutationJobs.values,
                runJobs.values,
                runObservationJobs.values,
                connectionRecoveryJobs,
            ).flatten()
        val observationsToClose = runObservations.values.toList()
        val pendingCreates = pendingCreateSnapshot()
        clearSessionStateForClose()
        ReleasedConnectionState(
            jobs = requestJobs + supersededJobs,
            observations = observationsToClose,
            pendingCreates = pendingCreates,
        )
    }

internal fun EntryStateHolder.clearSessionStateForClose() {
    mutationJobs.clear()
    mutationOwners.clear()
    runJobs.clear()
    runObservationJobs.clear()
    runObservations.clear()
    runObservationRunIds.clear()
    pendingRunObservationRequests.clear()
    runObservationStates.clear()
    unresolvedSubmissionSessions.clear()
    ambiguousSubmissionSessions.clear()
    recoveryUnavailableSessions.clear()
    uncertainSendDrafts.clear()
    uncertainSubmissionRunIds.clear()
    uncertainSubmissionAttemptIds.clear()
    pendingCreateSessions.clear()
    pendingCreateStarted.clear()
    unresolvedLocalRunIds.clear()
    unresolvedLocalRunAttempts.clear()
    pendingTimedOutSends.clear()
    reconcilingSessions.clear()
    notifiedTerminalRunIds.clear()
    connectionRecoveryJobs.clear()
    connectionRecoverySessionCounts.clear()
    connectionRecoveryFailedSessions.clear()
    recoverySessionCounts.clear()
    recoveryClaims.clear()
    recoveryReconciledRunIds.clear()
    recoveryHandledEntries.clear()
    recoveryLoadPending = false
    recoveryLoadFailed = false
    sessionDrafts.clear()
    sessionDraftRevisions.clear()
    sessionSendErrors.clear()
    pendingRunDrafts.clear()
    sessionRuns.clear()
    submittedRunInputs.clear()
    submittedRunModelSelections.clear()
    authoritativeSessionHistoryGenerations.clear()
    authoritativeSessionRuns.clear()
}

internal fun EntryStateHolder.showConnectionSetup() {
    if (mutableUiState.value.isConnected) return
    mutableUiState.value = mutableUiState.value.connectionSetupState()
}

internal fun EntryStateHolder.showCredentialRotation() {
    val state = mutableUiState.value
    if (state.isChangingCredential || state.isVerifying || state.isSaving) return
    if (!state.isConnected && !state.isGatewayConnectionConfigured) return
    credentialBeforeRotation = state.bearerCredential
    mutableUiState.value =
        state.copy(
            title = UiText.Resource(R.string.entry_title_change_credential),
            supportingText = UiText.Resource(R.string.entry_supporting_change_credential),
            actionLabel = UiText.Resource(R.string.entry_action_verify_and_save),
            connectionSetupRequested = true,
            isChangingCredential = true,
            bearerCredential = "",
            errorCategory = null,
        )
}

internal fun EntryStateHolder.cancelCredentialRotation() {
    val state = mutableUiState.value
    if (!state.isChangingCredential || state.isVerifying || state.isSaving) return
    val titleRes = if (state.isConnected) R.string.entry_title_connected else R.string.entry_title_verify_saved
    val supportingRes =
        if (state.isConnected) R.string.entry_supporting_connected else R.string.entry_supporting_verify_saved
    val actionRes = if (state.isConnected) R.string.entry_action_connected else R.string.entry_action_verify
    val credential = credentialBeforeRotation ?: state.bearerCredential
    credentialBeforeRotation = null
    mutableUiState.value =
        state.copy(
            title = UiText.Resource(titleRes),
            supportingText = UiText.Resource(supportingRes),
            actionLabel = UiText.Resource(actionRes),
            isChangingCredential = false,
            bearerCredential = credential,
            errorCategory = null,
        )
}

internal fun EntryStateHolder.closeGatewayConnectionSetup() {
    val state = mutableUiState.value
    if (state.isVerifying || state.isSaving || state.isChangingCredential) return
    mutableUiState.value =
        state.copy(
            connectionSetupRequested = false,
            endpoint = if (state.isGatewayConnectionConfigured) state.endpoint else "",
            bearerCredential = if (state.isGatewayConnectionConfigured) state.bearerCredential else "",
            errorCategory = null,
        )
}

internal fun EntryStateHolder.updateEndpoint(value: String) {
    val state = mutableUiState.value
    if (state.isVerifying || state.isSaving || state.isConnected) return
    mutableUiState.value = state.copy(endpoint = value, errorCategory = null)
}

internal fun EntryStateHolder.updateBearerCredential(value: String) {
    val state = mutableUiState.value
    val isPending = state.isVerifying || state.isSaving
    if (isPending || (state.isConnected && !state.isChangingCredential)) return
    mutableUiState.value = state.copy(bearerCredential = value, errorCategory = null)
}

internal fun EntryStateHolder.verifyConnection() {
    val verifier = verifyGatewayConnection ?: return
    val job =
        run {
            val state = mutableUiState.value
            if (state.blocksVerification()) {
                null
            } else {
                verificationJob?.cancel()
                val requestConnectionGeneration = connectionGeneration
                mutableUiState.value = state.copy(isVerifying = true, errorCategory = null)
                val verification =
                    scope.launch(start = CoroutineStart.LAZY) {
                        runVerification(verifier, state, requestConnectionGeneration)
                    }
                verificationJob = verification
                verification
            }
        } ?: return
    job.start()
}
