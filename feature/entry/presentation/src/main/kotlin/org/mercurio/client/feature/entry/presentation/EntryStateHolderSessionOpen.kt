package org.mercurio.client.feature.entry.presentation

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import org.mercurio.client.feature.entry.application.OpenSession
import org.mercurio.client.feature.entry.application.OpenedSession
import org.mercurio.client.feature.entry.domain.GatewayException
import org.mercurio.client.feature.entry.domain.RunEventObservation
import org.mercurio.client.feature.entry.domain.Session
import org.mercurio.client.feature.entry.domain.SessionGatewayPort
import org.mercurio.client.feature.entry.domain.SessionHistory
import org.mercurio.client.feature.entry.domain.SessionId

internal fun EntryStateHolder.openSession(sessionId: SessionId) {
    startSessionOpen(sessionId, linked = false)
}

internal fun EntryStateHolder.startSessionOpen(
    sessionId: SessionId,
    linked: Boolean,
): Boolean {
    val gateway = sessionGateway ?: return false
    val job =
        synchronized(sessionRequestLock) {
            val state = mutableUiState.value
            val sessionList = state.sessionList
            if (sessionList == null || !sessionList.allowsSessionOpen(sessionId, linked)) {
                return@synchronized null
            }
            val interrupted = sessionList.interruptsListRequest()
            inboundLinkListRequestInterrupted = linked && (interrupted || sessionList.isStale)
            val replacedSessionId = sessionList.openedSession?.session?.id?.takeIf { it != sessionId }
            val request =
                SessionRequestContext(
                    generation = beginSessionRequest(),
                    query = sessionList.searchQuery,
                    offset = null,
                    linked = linked,
                )
            val requestConnectionGeneration = connectionGeneration
            mutableUiState.value =
                state.copy(
                    inboundLinkNotice = null,
                    sessionList =
                        sessionList.copy(
                            openingSessionId = sessionId,
                            isLoading = false,
                            isRefreshing = false,
                            isSearching = false,
                            isLoadingMore = false,
                            isStale = sessionList.isStale || (linked && interrupted),
                            errorCategory = null,
                            creationErrorCategory = null,
                        ),
                )
            createSessionJob {
                openSessionJob(
                    gateway = gateway,
                    request = request,
                    requestConnectionGeneration = requestConnectionGeneration,
                    sessionId = sessionId,
                    replacedSessionId = replacedSessionId,
                )
            }
        }
    job?.start()
    return job != null
}

internal suspend fun EntryStateHolder.openSessionJob(
    gateway: SessionGatewayPort,
    request: SessionRequestContext,
    requestConnectionGeneration: Long,
    sessionId: SessionId,
    replacedSessionId: SessionId?,
) {
    val openedSessionBeforeAttempt = mutableUiState.value.sessionList?.openedSession
    try {
        val opener = OpenSession(gateway)
        val session = opener.executeSession(sessionId)
        val metadataApplied =
            updateCurrentSessionRequest(request.generation) { current ->
                applyOpenedSessionMetadata(current, session, sessionId)
            }
        if (!metadataApplied) {
            return
        }
        val history = opener.executeHistory(sessionId)
        val openedSession = OpenedSession(session = session, history = history)
        cacheOpenedSessionTranscript(openedSession)
        val applied =
            updateCurrentSessionRequest(request.generation) { current ->
                applyOpenedSessionResult(current, openedSession, sessionId)
            }
        if (applied) {
            applyOpenedSessionOutcome(
                gateway = gateway,
                request = request,
                requestConnectionGeneration = requestConnectionGeneration,
                openedSession = openedSession,
                replacedSessionId = replacedSessionId,
            )
            maybeFetchModelCatalog()
        }
    } catch (error: CancellationException) {
        throw error
    } catch (_: GatewayException) {
        showOpenFailure(request.generation, sessionId, openedSessionBeforeAttempt, request.linked)
    } catch (_: Exception) {
        showOpenFailure(request.generation, sessionId, openedSessionBeforeAttempt, request.linked)
    }
}

private suspend fun EntryStateHolder.showOpenFailure(
    requestGeneration: Long,
    sessionId: SessionId,
    openedSessionToRestore: OpenSessionUiState?,
    linked: Boolean,
) {
    if (linked) {
        showLinkedSessionOpenFailure(requestGeneration)
    } else {
        showSessionOpenFailure(requestGeneration, sessionId, openedSessionToRestore)
    }
}

internal fun EntryStateHolder.applyOpenedSessionResult(
    current: SessionListUiState,
    openedSession: OpenedSession,
    sessionId: SessionId,
): SessionListUiState {
    val knownRuns = rememberSessionRuns(sessionId, openedSession)
    val latestObservation = latestObservationState(sessionId, knownRuns)
    val recoveryLoadBlocksSession =
        recoveryLoadPending ||
            recoveryLoadFailed ||
            sessionId in connectionRecoverySessionCounts ||
            sessionId in recoverySessionCounts
    return current.copy(
        sessions =
            current.sessions.mergeAuthoritative(openedSession.session),
        sessionMutations = retainSessionMutation(current.sessionMutations, sessionId),
        openingSessionId = null,
        isStale = inboundLinkListRequestInterrupted,
        isUnavailable = false,
        openedSession =
            openedSession.toOpenSessionUiState(
                OpenSessionOverlay(
                    messages = toMessageUiStates(openedSession.history),
                    composerText = sessionDraftForOpenedSession(sessionId),
                    sendErrorCategory = sendErrorCategoryFor(sessionId),
                    hasUnresolvedSubmission =
                        hasUnresolvedSubmission(sessionId) || recoveryLoadBlocksSession,
                    isSending = runJobs.containsKey(sessionId),
                ),
            ).withRunSnapshot(knownRuns, latestObservation).copy(
                isRefreshing = recoveryLoadBlocksSession,
                isReconciliationInProgress =
                    openedSession.history.latestRun() != null || recoveryLoadBlocksSession,
            ),
        errorCategory = null,
    )
}

/**
 * Applies the Session metadata as soon as `GET /api/sessions/{id}` returns, so the Session screen
 * can appear while the transcript is still loading. The transcript is filled by
 * [applyOpenedSessionResult] when the history request returns.
 */
internal fun EntryStateHolder.applyOpenedSessionMetadata(
    current: SessionListUiState,
    session: Session,
    sessionId: SessionId,
): SessionListUiState {
    val knownRuns = sessionRuns[sessionId].orEmpty()
    val latestObservation = latestObservationState(sessionId, knownRuns)
    val recoveryLoadBlocksSession =
        recoveryLoadPending ||
            recoveryLoadFailed ||
            sessionId in connectionRecoverySessionCounts ||
            sessionId in recoverySessionCounts
    val sessionWithoutHistory =
        OpenedSession(session = session, history = SessionHistory(sessionId, emptyList()))
    return current.copy(
        sessions =
            current.sessions.mergeAuthoritative(session),
        sessionMutations = retainSessionMutation(current.sessionMutations, sessionId),
        isStale = inboundLinkListRequestInterrupted,
        isUnavailable = false,
        openedSession =
            sessionWithoutHistory.toOpenSessionUiState(
                OpenSessionOverlay(
                    messages = emptyList(),
                    composerText = sessionDraftForOpenedSession(sessionId),
                    sendErrorCategory = sendErrorCategoryFor(sessionId),
                    hasUnresolvedSubmission = hasUnresolvedSubmission(sessionId) || recoveryLoadBlocksSession,
                    isSending = runJobs.containsKey(sessionId),
                    isRefreshing = true,
                ),
            ).withRunSnapshot(knownRuns, latestObservation),
        errorCategory = null,
    )
}

internal suspend fun EntryStateHolder.applyOpenedSessionOutcome(
    gateway: SessionGatewayPort,
    request: SessionRequestContext,
    requestConnectionGeneration: Long,
    openedSession: OpenedSession,
    replacedSessionId: SessionId?,
) {
    replacedSessionId?.let { releasedSessionId ->
        val released =
            synchronized(sessionRequestLock) { releaseRunObservation(releasedSessionId) }
        released.job?.cancel()
        released.observation?.close()
    }
    continueSessionRecovery(
        gateway = gateway,
        request = request,
        requestConnectionGeneration = requestConnectionGeneration,
        openedSession = openedSession,
    )
}

internal suspend fun EntryStateHolder.showSessionOpenFailure(
    requestGeneration: Long,
    sessionId: SessionId,
    openedSessionToRestore: OpenSessionUiState?,
) {
    updateCurrentSessionRequest(requestGeneration) { current ->
        current.copy(
            sessionMutations = retainRenameDraft(current.sessionMutations, sessionId),
            openingSessionId = null,
            openedSession = openedSessionToRestore ?: cachedOpenedSession(current, sessionId),
            isStale = true,
            isUnavailable = true,
            errorCategory = SessionListErrorCategory.SESSION_UNAVAILABLE,
        )
    }
}

internal fun EntryStateHolder.releaseRunObservation(sessionId: SessionId): RunObservationRelease {
    retainUnconfirmedTerminalRuns(sessionId)
    val job = runObservationJobs.remove(sessionId)
    val observation = runObservations.remove(sessionId)
    runObservationRunIds.remove(sessionId)
    return RunObservationRelease(job = job, observation = observation)
}

internal data class RunObservationRelease(
    val job: Job?,
    val observation: RunEventObservation?,
)

internal fun EntryStateHolder.returnToSessionList() {
    var observationToClose: RunEventObservation? = null
    var observationJobToCancel: Job? = null
    synchronized(sessionRequestLock) {
        val current = mutableUiState.value.sessionList ?: return
        if (current.createSession != null) {
            if (!current.createSession.isSubmitting) {
                mutableUiState.value =
                    mutableUiState.value.copy(
                        sessionList =
                            current.copy(
                                createSession = null,
                                errorCategory = null,
                                creationErrorCategory = null,
                            ),
                    )
            }
            return
        }
        current.openedSession?.session?.id?.let { sessionId ->
            val released = releaseRunObservation(sessionId)
            observationJobToCancel = released.job
            observationToClose = released.observation
        }
        beginSessionRequest()
        val retainedSession =
            current.openedSession?.session?.takeIf { session ->
                current.sessionMutations.containsKey(session.id) && current.sessions.none { it.id == session.id }
            }
        mutableUiState.value =
            mutableUiState.value.copy(
                inboundLinkNotice = null,
                sessionList =
                    current.copy(
                        sessions =
                            retainedSession?.let { mergeSessions(current.sessions, listOf(it)) }
                                ?: current.sessions,
                        openedSession = null,
                        openingSessionId = null,
                        errorCategory = null,
                        creationErrorCategory = null,
                    ),
            )
    }
    observationJobToCancel?.cancel()
    observationToClose?.close()
}
