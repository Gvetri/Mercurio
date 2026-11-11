package org.mercurio.client.feature.entry.presentation

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import org.mercurio.client.feature.entry.application.ReconcileRun
import org.mercurio.client.feature.entry.domain.AuthoritativeRunReconciliation
import org.mercurio.client.feature.entry.domain.GatewayException
import org.mercurio.client.feature.entry.domain.Run
import org.mercurio.client.feature.entry.domain.RunId
import org.mercurio.client.feature.entry.domain.SessionGatewayPort
import org.mercurio.client.feature.entry.domain.SessionId
import org.mercurio.client.feature.entry.domain.isActive

internal suspend fun EntryStateHolder.reconcileRunObservationCandidate(
    context: OpenedRunReconcileContext,
    runId: RunId,
    hasCandidate: Boolean,
): Run? {
    val run =
        run {
            visibleSessionRuns(context.sessionId).lastOrNull { it.id == runId }
                ?: observationStateFor(context.sessionId, runId)?.run
                ?: unresolvedLocalRunIds[recoverySessionKey(context.sessionId)]
                    ?.takeIf { runId in it }
                    ?.let { Run(runId, context.sessionId, UNCERTAIN_RUN_STATUS) }
                ?: uncertainSubmissionRunIds[context.sessionId]
                    ?.takeIf { runId == it }
                    ?.let { Run(runId, context.sessionId, UNCERTAIN_RUN_STATUS) }
        } ?: return null
    val reconciliation =
        reconcileRun(
            sessionId = context.sessionId,
            runId = run.id,
            requestConnectionGeneration = context.requestConnectionGeneration,
            requestSessionGeneration = context.requestSessionGeneration,
            sessionGateway = context.sessionGateway,
        )
    val reconciledRun = reconciliation?.run
    return continuedObservationRun(reconciledRun, run, hasCandidate)
}

internal fun EntryStateHolder.continuedObservationRun(
    reconciledRun: Run?,
    fallback: Run,
    hasCandidate: Boolean,
): Run? {
    val reconciledIsActive = reconciledRun?.isActive() == true
    val fallbackIsActive = reconciledRun == null && !hasCandidate && fallback.isActive()
    return if (reconciledIsActive || fallbackIsActive) reconciledRun ?: fallback else null
}

internal suspend fun EntryStateHolder.reconcileRun(
    sessionId: SessionId,
    runId: RunId,
    requestConnectionGeneration: Long,
    requestSessionGeneration: Long,
    sessionGateway: SessionGatewayPort,
): AuthoritativeRunReconciliation? {
    val runGateway =
        if (beginReconciliation(sessionId, requestConnectionGeneration, requestSessionGeneration)) {
            runGateway?.takeIf {
                connectionGeneration == requestConnectionGeneration &&
                    sessionRequestGeneration == requestSessionGeneration &&
                    mutableUiState.value.sessionList?.openedSession?.session?.id == sessionId
            }
        } else {
            null
        } ?: return null
    return try {
        val result = ReconcileRun(runGateway, sessionGateway).execute(runId, sessionId)
        applyAuthoritativeRunReconciliation(
            request =
                AuthoritativeReconciliationRequest(
                    sessionId = sessionId,
                    connectionGeneration = requestConnectionGeneration,
                    sessionGeneration = requestSessionGeneration,
                ),
            reconciliation = result,
        )
        result
    } catch (error: CancellationException) {
        throw error
    } catch (_: GatewayException) {
        showRunReconciliationFailure(sessionId, runId, requestConnectionGeneration, requestSessionGeneration)
        null
    } catch (_: Exception) {
        showRunReconciliationFailure(sessionId, runId, requestConnectionGeneration, requestSessionGeneration)
        null
    } finally {
        finishReconciliation(sessionId, requestConnectionGeneration, requestSessionGeneration)
    }
}

internal fun EntryStateHolder.beginReconciliation(
    sessionId: SessionId,
    requestConnectionGeneration: Long,
    requestSessionGeneration: Long,
): Boolean =
    run {
        val opened = mutableUiState.value.sessionList?.openedSession
        val reconciliationIsSuperseded =
            connectionGeneration != requestConnectionGeneration ||
                sessionRequestGeneration != requestSessionGeneration ||
                opened?.session?.id != sessionId ||
                reconcilingSessions[sessionId] == requestSessionGeneration
        if (reconciliationIsSuperseded) {
            false
        } else {
            reconcilingSessions[sessionId] = requestSessionGeneration
            submitStateChange {
                if (
                    connectionGeneration != requestConnectionGeneration ||
                    sessionRequestGeneration != requestSessionGeneration ||
                    reconcilingSessions[sessionId] != requestSessionGeneration
                ) {
                    return@submitStateChange
                }
                val current = mutableUiState.value.sessionList ?: return@submitStateChange
                val openedNow =
                    current.openedSession?.takeIf { it.session.id == sessionId }
                        ?: return@submitStateChange
                mutableUiState.value =
                    mutableUiState.value.copy(
                        sessionList =
                            current.copy(
                                openedSession =
                                    openedNow.copy(
                                        isRefreshing = true,
                                        isReconciliationInProgress = true,
                                        errorCategory = null,
                                    ),
                            ),
                    )
            }
            true
        }
    }

/**
 * Releases the reconciliation claim and clears its visible in-progress state on the
 * state-change consumer.
 *
 * The claim bookkeeping is removed synchronously before the visible release is enqueued, so
 * it cannot overtake a begin state that was queued before it: with both sides on the
 * consumer, a reconciliation attempt's set and release keep their submission order and a
 * later attempt's begin re-arms the flags after the earlier release applies. The release
 * re-derives the connection and request generations and the opened-session identity at
 * application time; it does not re-check the claim, which was already removed as
 * bookkeeping. `blocksRunSubmission` and the composer send state read the flags through the
 * UI state flow, so a retry click observes the release once the consumer has drained the
 * commands queued before it.
 */
internal fun EntryStateHolder.finishReconciliation(
    sessionId: SessionId,
    requestConnectionGeneration: Long,
    requestSessionGeneration: Long,
) {
    if (reconcilingSessions[sessionId] == requestSessionGeneration) {
        reconcilingSessions.remove(sessionId)
    }

    submitStateChange {
        if (
            connectionGeneration != requestConnectionGeneration ||
            sessionRequestGeneration != requestSessionGeneration
        ) {
            return@submitStateChange
        }
        val (current, opened) = openedSessionFor(sessionId) ?: return@submitStateChange
        mutableUiState.value =
            mutableUiState.value.copy(
                sessionList =
                    current.copy(
                        openedSession =
                            opened.copy(
                                isRefreshing = false,
                                isReconciliationInProgress = false,
                            ),
                    ),
            )
    }
}

internal suspend fun EntryStateHolder.applyAuthoritativeRunReconciliation(
    request: AuthoritativeReconciliationRequest,
    reconciliation: AuthoritativeRunReconciliation,
) {
    val application =
        applyStateChange {
            applyReconciliation(request, reconciliation)
        }
    val reconciledRun = reconciliation.run
    val runIsClaimed =
        recoveryClaims.keys.any { key ->
            key.sessionId == request.sessionId && key.runId == reconciledRun.id
        }
    if (runIsClaimed) {
        recoveryReconciledRunIds += reconciledRun.id
    }

    application.observationToClose?.close()
    application.observationJobToCancel?.cancel()
    removeReconciledRecoveryEntry(
        recoveryEntryToRemove = application.recoveryEntryToRemove,
        recoveryEndpointToRemove = application.recoveryEndpointToRemove,
        sessionId = request.sessionId,
        requestConnectionGeneration = request.connectionGeneration,
        requestHistoryGeneration = request.historyGeneration,
    )
}
