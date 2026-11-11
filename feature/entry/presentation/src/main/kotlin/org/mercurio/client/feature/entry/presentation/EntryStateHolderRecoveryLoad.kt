package org.mercurio.client.feature.entry.presentation

import org.mercurio.client.feature.entry.domain.RunRecoveryEntry
import org.mercurio.client.feature.entry.domain.SessionGatewayPort
import org.mercurio.client.feature.entry.domain.SessionId

/**
 * Begins the initial session-list load for the IO-reached recovery flow.
 *
 * `runVerification` calls this from the holder's IO scope, so the visible begin state routes
 * through the state-change consumer instead of writing on the producing coroutine; the load's
 * asynchronous result is applied on the consumer via `updateCurrentSessionRequest`.
 */
internal fun EntryStateHolder.loadInitialSessions(gateway: SessionGatewayPort) {
    run {
        val sessionList = mutableUiState.value.sessionList ?: return
        val requestGeneration = beginSessionRequest()
        val expectedConnectionGeneration = connectionGeneration
        val request = SessionRequestContext(requestGeneration, sessionList.searchQuery, offset = null)
        submitStateChange {
            run {
                if (
                    connectionGeneration != expectedConnectionGeneration ||
                    sessionRequestGeneration != requestGeneration
                ) {
                    return@submitStateChange
                }
                val current = mutableUiState.value.sessionList ?: return@submitStateChange
                mutableUiState.value =
                    mutableUiState.value.copy(
                        sessionList =
                            current.copy(
                                sessions = emptyList(),
                                isLoading = true,
                                isRefreshing = false,
                                isSearching = false,
                                isLoadingMore = false,
                                isStale = false,
                                isCached = false,
                                isUnavailable = false,
                                errorCategory = null,
                                nextOffset = null,
                                sessionMutations = restoreUnresolvedSessionMutations(emptyMap()),
                            ),
                    )
            }
        }
        createSessionJob {
            loadFirstPage(gateway, request, preserveSessions = false)
        }
    }.start()
}

internal fun EntryStateHolder.flushPendingRecoveryEntries(endpoint: String) {
    val entries =
        run {
            pendingDisconnectedRecoveryEntries.remove(endpoint).orEmpty().toList()
        }
    entries.forEach { entry ->
        if (persistOrQueueRecoveryEntry(endpoint, entry)) {
            reconcilePersistedRecoveryEntryIfConnected(endpoint, entry)
        }
    }
}

internal fun EntryStateHolder.persistOrQueueRecoveryEntry(
    endpoint: String,
    entry: RunRecoveryEntry,
): Boolean {
    val persistence =
        run {
            persistRunRecoveryEntry to runRecoveryRegistry
        }
    if (persistence.first == null && persistence.second == null) return true
    return try {
        run {
            persistence.first?.invoke(endpoint, entry)
                ?: persistence.second?.save(entry)
                ?: error("No endpoint-scoped recovery writer is configured.")
        }
        true
    } catch (_: Exception) {
        run {
            pendingDisconnectedRecoveryEntries
                .getOrPut(endpoint, ::mutableSetOf)
                .add(entry)
        }
        false
    }
}

internal fun EntryStateHolder.removeRecoveryEntry(
    endpoint: String?,
    entry: RunRecoveryEntry,
): Boolean =
    try {
        run {
            if (endpoint != null) {
                removeRunRecoveryEntry?.invoke(endpoint, entry) ?: runRecoveryRegistry?.remove(entry)
            } else {
                runRecoveryRegistry?.remove(entry)
            }
        }
        run {
            forgetPendingRecoveryEntry(endpoint, entry)
        }
        true
    } catch (_: Exception) {
        false
    }

internal fun EntryStateHolder.beginRecoveryLoad(expectedConnectionGeneration: Long): Boolean =
    run {
        if (connectionGeneration != expectedConnectionGeneration) {
            false
        } else {
            recoveryLoadPending = true
            recoveryLoadFailed = false
            submitStateChange {
                run {
                    if (connectionGeneration != expectedConnectionGeneration || !recoveryLoadPending) {
                        return@submitStateChange
                    }
                    val current = mutableUiState.value.sessionList
                    val opened = current?.openedSession
                    if (current != null && opened != null) {
                        mutableUiState.value =
                            mutableUiState.value.copy(
                                sessionList =
                                    current.copy(
                                        openedSession =
                                            opened.copy(
                                                isRefreshing = true,
                                                isReconciliationInProgress = true,
                                                hasUnresolvedSubmission = true,
                                            ),
                                    ),
                            )
                    }
                }
            }
            true
        }
    }

internal suspend fun EntryStateHolder.finishRecoveryLoad(
    expectedConnectionGeneration: Long,
    entries: List<RunRecoveryEntry>,
    failed: Boolean,
) {
    applyStateChange {
        run {
            if (connectionGeneration != expectedConnectionGeneration) return@run
            recoveryLoadPending = false
            recoveryLoadFailed = failed
            val recoveredSessionIds = entries.mapTo(mutableSetOf()) { it.sessionId }
            val current = mutableUiState.value.sessionList
            if (failed) {
                current?.sessions?.forEach { item -> recoveryUnavailableSessions += item.id }
                current?.openedSession?.session?.id?.let(recoveryUnavailableSessions::add)
            }
            val opened = current?.openedSession
            if (current != null && opened != null) {
                val sessionId = opened.session.id
                val recoveryInProgress =
                    sessionId in recoveredSessionIds ||
                        sessionId in connectionRecoverySessionCounts ||
                        sessionId in recoverySessionCounts ||
                        sessionId in reconcilingSessions
                mutableUiState.value =
                    mutableUiState.value.copy(
                        sessionList =
                            current.copy(
                                openedSession =
                                    opened.copy(
                                        isRefreshing = if (failed) false else recoveryInProgress,
                                        isReconciliationInProgress = if (failed) false else recoveryInProgress,
                                        hasUnresolvedSubmission =
                                            failed || hasUnresolvedSubmission(sessionId),
                                        sendErrorCategory =
                                            if (failed) {
                                                MessageSendErrorCategory.UNCERTAIN
                                            } else {
                                                sendErrorCategoryFor(sessionId)
                                            },
                                    ),
                            ),
                    )
            }
        }
    }
}

internal fun EntryStateHolder.markRecoveryFailure(
    sessionId: SessionId,
    expectedConnectionGeneration: Long,
) {
    run {
        recoveryUnavailableSessions += sessionId
    }
    submitStateChange {
        run {
            if (connectionGeneration != expectedConnectionGeneration) return@run
            val current = mutableUiState.value.sessionList
            val opened = current?.openedSession?.takeIf { it.session.id == sessionId }
            if (current != null && opened != null) {
                mutableUiState.value =
                    mutableUiState.value.copy(
                        sessionList =
                            current.copy(
                                openedSession =
                                    opened.copy(
                                        isRefreshing = false,
                                        isReconciliationInProgress = false,
                                        hasUnresolvedSubmission = true,
                                        sendErrorCategory = MessageSendErrorCategory.UNCERTAIN,
                                    ),
                            ),
                    )
            }
        }
    }
}

internal fun EntryStateHolder.hasPendingRecoveryWork(sessionId: SessionId): Boolean =
    sessionId in connectionRecoverySessionCounts ||
        sessionId in recoverySessionCounts ||
        sessionId in reconcilingSessions ||
        recoveryLoadPending

internal fun EntryStateHolder.openedSessionFor(sessionId: SessionId): Pair<SessionListUiState, OpenSessionUiState>? =
    mutableUiState.value.sessionList?.let { current ->
        current.openedSession?.takeIf { it.session.id == sessionId }?.let { opened -> current to opened }
    }

internal fun EntryStateHolder.clearRecoveryUiIfIdle(sessionId: SessionId) {
    if (hasPendingRecoveryWork(sessionId)) return
    submitStateChange {
        run {
            if (hasPendingRecoveryWork(sessionId)) return@run
            val (current, opened) = openedSessionFor(sessionId) ?: return@run
            mutableUiState.value =
                mutableUiState.value.copy(
                    sessionList =
                        current.copy(
                            openedSession =
                                opened.copy(
                                    isRefreshing = false,
                                    isReconciliationInProgress = false,
                                    hasUnresolvedSubmission = hasUnresolvedSubmission(sessionId),
                                    sendErrorCategory = sendErrorCategoryFor(sessionId),
                                ),
                        ),
                )
        }
    }
}
