package org.mercurio.client.feature.entry.presentation

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.mercurio.client.feature.entry.application.EntryState
import org.mercurio.client.feature.entry.application.VerifyGatewayConnection
import org.mercurio.client.feature.entry.application.normalizeGatewayEndpoint
import org.mercurio.client.feature.entry.domain.GatewayCapabilities
import org.mercurio.client.feature.entry.domain.GatewayConnectionPersistenceException
import org.mercurio.client.feature.entry.domain.GatewayException
import org.mercurio.client.feature.entry.domain.LocalDiagnosticEventType
import org.mercurio.client.feature.entry.domain.LocalDiagnosticStatus
import org.mercurio.client.feature.entry.domain.ModelGatewayPort
import org.mercurio.client.feature.entry.domain.RunEventObservation
import org.mercurio.client.feature.entry.domain.RunGatewayPort
import org.mercurio.client.feature.entry.domain.SessionGatewayPort
import org.mercurio.client.feature.entry.domain.supportsModelCatalog

internal fun EntryUiState.blocksVerification(): Boolean {
    return !connectionSetupRequested || isVerifying || isSaving || (isConnected && !isChangingCredential)
}

internal suspend fun EntryStateHolder.runVerification(
    verifier: VerifyGatewayConnection,
    state: EntryUiState,
    requestConnectionGeneration: Long,
) {
    try {
        pendingRemovalJob?.join()
        val capabilities =
            verifier.executeWithoutPersistence(
                endpoint = state.endpoint,
                bearerCredential = state.bearerCredential,
            )
        val normalizedEndpoint = normalizeGatewayEndpoint(state.endpoint)
        if (!persistVerifiedCredential(verifier, state, requestConnectionGeneration, normalizedEndpoint)) return
        val gateway = connectVerifiedGateway(state, normalizedEndpoint, requestConnectionGeneration, capabilities)
        recordDiagnostic(
            LocalDiagnosticEventType.GATEWAY_CONNECTION_VERIFICATION,
            LocalDiagnosticStatus.SUCCEEDED,
        )
        flushPendingRecoveryEntries(normalizedEndpoint)
        startRunRecovery(requestConnectionGeneration)
        gateway?.let(::loadInitialSessions)
    } catch (error: CancellationException) {
        throw error
    } catch (error: GatewayException) {
        showFailure(error.category.toUserFacingCategory(), requestConnectionGeneration)
    } catch (_: GatewayConnectionPersistenceException) {
        showFailure(
            EntryErrorCategory.CREDENTIAL_STORAGE_FAILED,
            requestConnectionGeneration,
        )
    } catch (_: Exception) {
        showFailure(EntryErrorCategory.GATEWAY_REQUEST_FAILED, requestConnectionGeneration)
    }
}

internal fun EntryStateHolder.persistVerifiedCredential(
    verifier: VerifyGatewayConnection,
    state: EntryUiState,
    requestConnectionGeneration: Long,
    normalizedEndpoint: String,
): Boolean {
    val canPersist =
        synchronized(sessionRequestLock) {
            if (connectionGeneration == requestConnectionGeneration) {
                mutableUiState.value = mutableUiState.value.copy(isVerifying = false, isSaving = true)
                true
            } else {
                false
            }
        }
    if (!canPersist) return false
    return synchronized(connectionPersistenceLock) {
        val stillCurrent =
            synchronized(sessionRequestLock) {
                connectionGeneration == requestConnectionGeneration
            }
        if (!stillCurrent) {
            false
        } else {
            verifier.persist(
                endpoint = normalizedEndpoint,
                bearerCredential = state.bearerCredential,
            )
            true
        }
    }
}

internal fun EntryStateHolder.connectVerifiedGateway(
    state: EntryUiState,
    normalizedEndpoint: String,
    requestConnectionGeneration: Long,
    capabilities: GatewayCapabilities,
): SessionGatewayPort? =
    synchronized(sessionRequestLock) {
        if (connectionGeneration != requestConnectionGeneration) {
            null
        } else {
            val gateway =
                sessionGatewayFactory?.invoke(
                    normalizedEndpoint,
                    state.bearerCredential,
                )
            updateRunRecoveryEndpoint?.invoke(normalizedEndpoint)
            sessionGateway = gateway
            runGateway =
                runGatewayFactory?.invoke(
                    normalizedEndpoint,
                    state.bearerCredential,
                ) ?: (gateway as? RunGatewayPort)
            catalogGateway = (runGateway as? ModelGatewayPort) ?: (gateway as? ModelGatewayPort)
            mutableUiState.value =
                mutableUiState.value.copy(
                    endpoint = normalizedEndpoint,
                    title = UiText.Resource(R.string.entry_title_connected),
                    supportingText = UiText.Resource(R.string.entry_supporting_connected),
                    actionLabel = UiText.Resource(R.string.entry_action_connected),
                    isChangingCredential = false,
                    isVerifying = false,
                    isSaving = false,
                    isConnected = true,
                    isGatewayConnectionConfigured = true,
                    errorCategory = null,
                    sessionList =
                        gateway?.let {
                            SessionListUiState(
                                isLoading = true,
                                showFirstUseGuidance = true,
                                modelPicker =
                                    ModelPickerUiState(
                                        capabilityKnown = true,
                                        capabilityAdvertised = capabilities.supportsModelCatalog(),
                                    ),
                            )
                        },
                )
            gateway
        }
    }

internal fun EntryStateHolder.showFailure(
    category: EntryErrorCategory,
    requestConnectionGeneration: Long,
) {
    recordDiagnostic(
        LocalDiagnosticEventType.GATEWAY_CONNECTION_VERIFICATION,
        LocalDiagnosticStatus.FAILED,
    )
    synchronized(sessionRequestLock) {
        if (connectionGeneration == requestConnectionGeneration) {
            mutableUiState.value =
                mutableUiState.value.copy(
                    isVerifying = false,
                    isSaving = false,
                    errorCategory = category,
                )
        }
    }
}

internal fun EntryStateHolder.removeGatewayConnection() {
    val released = releaseGatewayConnection()
    released.jobs.forEach(Job::cancel)
    released.observations.forEach(RunEventObservation::close)
}

internal fun EntryStateHolder.releaseGatewayConnection(): ReleasedConnectionState {
    val endpoint = mutableUiState.value.endpoint
    val released =
        synchronized(connectionPersistenceLock) {
            val releasedState = releaseGatewayConnectionLocked()
            updateRunRecoveryEndpoint?.invoke(null)
            val current = mutableUiState.value
            mutableUiState.value =
                EntryState()
                    .toUiState()
                    .copy(
                        clientVersion = current.clientVersion,
                        buildNumber = current.buildNumber,
                        installSource = current.installSource,
                        autoLoadImages = current.autoLoadImages,
                        runStatusNotifications = current.runStatusNotifications,
                    )
            releasedState
        }
    val persistence =
        persistenceScope.launch(start = CoroutineStart.LAZY) {
            runCatching { writePendingCreateMarkers(released.pendingCreates) }
            runCatching { removeGatewayConnectionUseCase?.execute() }
            runCatching { clearEndpointStores(endpoint) }
        }
    pendingRemovalJob = persistence
    persistence.start()
    return released
}

internal fun EntryStateHolder.releaseGatewayConnectionLocked(): ReleasedConnectionState =
    synchronized(sessionRequestLock) {
        rememberUnresolvedSessionMutations(mutableUiState.value.endpoint)
        sessionRequestGeneration += 1
        connectionGeneration += 1
        val supersededJobs = listOfNotNull(verificationJob, sessionJob, recoveryJob)
        verificationJob = null
        sessionJob = null
        recoveryJob = null
        val pendingCreates = pendingCreateSnapshotLocked()
        pendingCreateStarted.clear()
        pendingCreateSessions.clear()
        sessionGateway = null
        runGateway = null
        catalogGateway = null
        mutationOwners.clear()
        val connectionRecoveryJobsToCancel = connectionRecoveryJobs.toList()
        clearRecoveryState()
        clearSessionDraftAndRunState()
        val observationsToClose = runObservations.values.toList()
        val requestJobs = collectAndClearRequestJobs()
        ReleasedConnectionState(
            jobs = requestJobs + supersededJobs + connectionRecoveryJobsToCancel,
            observations = observationsToClose,
            pendingCreates = pendingCreates,
        )
    }
