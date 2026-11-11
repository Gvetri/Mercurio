package org.mercurio.client.feature.entry.presentation

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import org.mercurio.client.feature.entry.application.DeleteSession
import org.mercurio.client.feature.entry.domain.RunEventObservation
import org.mercurio.client.feature.entry.domain.SessionId

internal fun EntryStateHolder.showDeleteSession(sessionId: SessionId) {
    val current =
        mutableUiState.value.sessionList
            ?.takeIf { it.allowsSessionMutation() && it.sessionForMutation(sessionId) != null }
            ?: return
    val mutation = current.sessionMutations[sessionId] ?: SessionMutationUiState()
    if (mutation.pendingAction != null || mutation.rename != null) return
    mutableUiState.value =
        mutableUiState.value.copy(
            sessionList =
                current.copy(
                    sessionMutations =
                        current.sessionMutations +
                            (
                                sessionId to
                                    mutation.copy(
                                        delete = SessionDeleteUiState(),
                                        errorCategory = null,
                                        retryAction = null,
                                    )
                            ),
                ),
        )
}

internal fun EntryStateHolder.cancelDeleteSession(sessionId: SessionId) {
    val current = mutableUiState.value.sessionList ?: return
    val mutation = current.sessionMutations[sessionId]?.takeIf { it.pendingAction == null } ?: return
    val remaining = mutation.copy(delete = null, errorCategory = null, retryAction = null)
    unresolvedSessionMutations.remove(recoverySessionKey(sessionId))
    mutableUiState.value =
        mutableUiState.value.copy(
            sessionList =
                current.copy(
                    sessionMutations = mutationMapAfter(sessionId, remaining, current.sessionMutations),
                ),
        )
}

internal fun EntryStateHolder.confirmDeleteSession(sessionId: SessionId) {
    val gateway = sessionGateway ?: return
    val job =
        run {
            val current = mutationSessionFor(gateway) ?: return@run null
            val mutation = current.sessionMutations[sessionId]
            val delete = mutation?.delete
            if (mutation == null || delete == null) return@run null
            if (mutation.pendingAction != null || delete.isSubmitting) return@run null
            mutableUiState.value =
                mutableUiState.value.copy(
                    sessionList =
                        current.copy(
                            sessionMutations =
                                current.sessionMutations +
                                    (
                                        sessionId to
                                            mutation.copy(
                                                delete = delete.copy(isSubmitting = true),
                                                pendingAction = SessionMutationAction.DELETE,
                                                errorCategory = null,
                                                retryAction = null,
                                            )
                                    ),
                        ),
                )
            val request = beginSessionMutation(sessionId, gateway)
            unresolvedSessionMutations.remove(recoverySessionKey(sessionId))
            createMutationJob(request) {
                try {
                    DeleteSession(gateway).execute(sessionId)
                    removeConfirmedSession(sessionId, request)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    showDeleteFailure(request)
                }
            }
        } ?: return
    job.start()
}

internal suspend fun EntryStateHolder.showDeleteFailure(request: SessionMutationRequest) {
    applyStateChange {
        val (current, mutation) = mutationFailureTarget(request) ?: return@applyStateChange
        val sessionId = request.sessionId
        val delete = mutation.delete ?: return@applyStateChange
        mutableUiState.value =
            mutableUiState.value.copy(
                sessionList =
                    current.copy(
                        sessionMutations =
                            current.sessionMutations +
                                (
                                    sessionId to
                                        mutation.copy(
                                            delete = delete.copy(isSubmitting = false),
                                            pendingAction = null,
                                            errorCategory = SessionMutationErrorCategory.GATEWAY_REQUEST_FAILED,
                                            retryAction = SessionMutationAction.DELETE,
                                        )
                                ),
                    ),
            )
    }
}

internal suspend fun EntryStateHolder.removeConfirmedSession(
    sessionId: SessionId,
    request: SessionMutationRequest,
) {
    var observationJobToCancel: Job? = null
    var observationToClose: RunEventObservation? = null
    val refreshJob =
        applyStateChange {
            if (!isCurrentSessionMutation(request)) return@applyStateChange null
            val current = mutableUiState.value.sessionList ?: return@applyStateChange null
            val shouldRefresh = current.nextOffset != null
            if (current.openedSession?.session?.id == sessionId) {
                observationJobToCancel = runObservationJobs[sessionId]
                observationToClose = runObservations.remove(sessionId)
                runObservationRunIds.remove(sessionId)
                runObservationStates.remove(sessionId)
            }
            mutableUiState.value =
                mutableUiState.value.copy(
                    sessionList =
                        current.copy(
                            sessions = current.sessions.filterNot { it.id == sessionId },
                            openedSession = current.openedSession?.takeUnless { it.session.id == sessionId },
                            sessionMutations = current.sessionMutations - sessionId,
                        ),
                )
            unresolvedSessionMutations.remove(recoverySessionKey(sessionId))
            if (shouldRefresh) {
                sessionGateway?.let { gateway ->
                    createFirstPageLoadJob(
                        gateway = gateway,
                        query = current.searchQuery,
                        isRefreshing = true,
                        isSearching = false,
                        preserveSessions = true,
                    )
                }
            } else {
                null
            }
        }
    observationJobToCancel?.cancel()
    observationToClose?.close()
    refreshJob?.start()
}
