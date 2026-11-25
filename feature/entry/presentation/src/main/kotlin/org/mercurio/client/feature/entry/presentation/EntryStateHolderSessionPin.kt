package org.mercurio.client.feature.entry.presentation

import kotlinx.coroutines.CancellationException
import org.mercurio.client.feature.entry.application.PinSession
import org.mercurio.client.feature.entry.application.UnpinSession
import org.mercurio.client.feature.entry.domain.SessionGatewayPort
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
            if (current.sessionMutationBlocked(sessionId, mutation)) return@run null
            mutableUiState.value =
                mutableUiState.value.copy(
                    sessionList = current.withPendingPin(sessionId, mutation, action),
                )
            val request = beginSessionMutation(sessionId, gateway)
            unresolvedSessionMutations.remove(recoverySessionKey(sessionId))
            createMutationJob(request) {
                runPinAction(gateway, sessionId, pinned, request, action)
            }
        } ?: return
    job.start()
}

private fun SessionListUiState.sessionMutationBlocked(
    sessionId: SessionId,
    mutation: SessionMutationUiState,
): Boolean =
    sessionForMutation(sessionId) == null ||
        mutation.pendingAction != null ||
        mutation.rename != null ||
        mutation.delete != null

private fun SessionListUiState.withPendingPin(
    sessionId: SessionId,
    mutation: SessionMutationUiState,
    action: SessionMutationAction,
): SessionListUiState =
    copy(
        sessionMutations =
            sessionMutations +
                (
                    sessionId to
                        mutation.copy(
                            pendingAction = action,
                            errorCategory = null,
                            retryAction = null,
                        )
                ),
    )

private suspend fun EntryStateHolder.runPinAction(
    gateway: SessionGatewayPort,
    sessionId: SessionId,
    pinned: Boolean,
    request: SessionMutationRequest,
    action: SessionMutationAction,
) {
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
            val remainingMutation =
                mutation.copy(
                    pendingAction = null,
                    errorCategory = null,
                    retryAction = null,
                )
            mutableUiState.value =
                mutableUiState.value.copy(
                    sessionList =
                        current.withConfirmedPin(
                            sessionId = sessionId,
                            pinned = pinned,
                            mutations = mutationMapAfter(sessionId, remainingMutation, current.sessionMutations),
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

private fun SessionListUiState.withConfirmedPin(
    sessionId: SessionId,
    pinned: Boolean,
    mutations: Map<SessionId, SessionMutationUiState>,
): SessionListUiState =
    copy(
        sessions =
            sessions
                .map { item -> if (item.id == sessionId) item.copy(pinned = pinned) else item }
                .orderedSessions(),
        openedSession =
            openedSession?.let { opened ->
                if (opened.session.id == sessionId) {
                    opened.copy(session = opened.session.copy(pinned = pinned))
                } else {
                    opened
                }
            },
        sessionMutations = mutations,
    )
