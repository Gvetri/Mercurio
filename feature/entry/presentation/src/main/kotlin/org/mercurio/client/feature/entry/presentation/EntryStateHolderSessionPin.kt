package org.mercurio.client.feature.entry.presentation

import kotlinx.coroutines.CancellationException
import org.mercurio.client.feature.entry.application.PinSession
import org.mercurio.client.feature.entry.application.UnpinSession
import org.mercurio.client.feature.entry.domain.SessionId

internal fun EntryStateHolder.pinSession(sessionId: SessionId) {
    changeSessionPin(sessionId, pinned = true)
}

internal fun EntryStateHolder.unpinSession(sessionId: SessionId) {
    changeSessionPin(sessionId, pinned = false)
}

internal fun EntryStateHolder.changeSessionPin(
    sessionId: SessionId,
    pinned: Boolean,
) {
    val gateway = sessionGateway ?: return
    val action = if (pinned) SessionMutationAction.PIN else SessionMutationAction.UNPIN
    val job =
        run {
            val current = mutationSessionFor(gateway) ?: return@run null
            val mutation = current.sessionMutations[sessionId] ?: SessionMutationUiState()
            val blocked =
                current.sessionForMutation(sessionId) == null ||
                    mutation.pendingAction != null ||
                    mutation.rename != null ||
                    mutation.delete != null
            if (blocked) return@run null
            mutableUiState.value =
                mutableUiState.value.copy(
                    sessionList =
                        current.copy(
                            sessionMutations =
                                current.sessionMutations +
                                    (
                                        sessionId to
                                            mutation.copy(
                                                pendingAction = action,
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
                    val result =
                        if (pinned) {
                            PinSession(gateway).execute(sessionId)
                        } else {
                            UnpinSession(gateway).execute(sessionId)
                        }
                    applyConfirmedPin(result.sessionId, result.pinned, request)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    showPinFailure(request, action)
                }
            }
        } ?: return
    job.start()
}

internal suspend fun EntryStateHolder.applyConfirmedPin(
    sessionId: SessionId,
    pinned: Boolean,
    request: SessionMutationRequest,
) {
    val refreshJob =
        applyStateChange {
            if (!isCurrentSessionMutation(request)) return@applyStateChange null
            val current = mutableUiState.value.sessionList ?: return@applyStateChange null
            if (current.sessionForMutation(sessionId) == null) return@applyStateChange null
            val mutation = current.sessionMutations[sessionId] ?: return@applyStateChange null
            val updatedSessions =
                current.sessions
                    .map { item -> if (item.id == sessionId) item.copy(pinned = pinned) else item }
                    .orderedSessions()
            val remainingMutation =
                mutation.copy(
                    pendingAction = null,
                    errorCategory = null,
                    retryAction = null,
                )
            mutableUiState.value =
                mutableUiState.value.copy(
                    sessionList =
                        current.copy(
                            sessions = updatedSessions,
                            openedSession =
                                current.openedSession?.let { opened ->
                                    if (opened.session.id == sessionId) {
                                        opened.copy(session = opened.session.copy(pinned = pinned))
                                    } else {
                                        opened
                                    }
                                },
                            sessionMutations =
                                mutationMapAfter(sessionId, remainingMutation, current.sessionMutations),
                        ),
                )
            unresolvedSessionMutations.remove(recoverySessionKey(sessionId))
            sessionGateway?.let { gateway ->
                createFirstPageLoadJob(
                    gateway = gateway,
                    query = current.searchQuery,
                    isRefreshing = true,
                    isSearching = false,
                    preserveSessions = true,
                )
            }
        }
    refreshJob?.start()
}

internal suspend fun EntryStateHolder.showPinFailure(
    request: SessionMutationRequest,
    action: SessionMutationAction,
) {
    applyStateChange {
        val (current, mutation) = mutationFailureTarget(request) ?: return@applyStateChange
        val sessionId = request.sessionId
        mutableUiState.value =
            mutableUiState.value.copy(
                sessionList =
                    current.copy(
                        sessionMutations =
                            current.sessionMutations +
                                (
                                    sessionId to
                                        mutation.copy(
                                            pendingAction = null,
                                            errorCategory = SessionMutationErrorCategory.GATEWAY_REQUEST_FAILED,
                                            retryAction = action,
                                        )
                                ),
                    ),
            )
    }
}
