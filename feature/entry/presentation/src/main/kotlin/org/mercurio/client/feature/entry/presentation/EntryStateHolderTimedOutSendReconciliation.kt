package org.mercurio.client.feature.entry.presentation

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.mercurio.client.feature.entry.application.ReconcileRun
import org.mercurio.client.feature.entry.application.ReconcileSession
import org.mercurio.client.feature.entry.domain.GatewayException
import org.mercurio.client.feature.entry.domain.PendingRunSubmissionKey
import org.mercurio.client.feature.entry.domain.Run
import org.mercurio.client.feature.entry.domain.RunGatewayPort
import org.mercurio.client.feature.entry.domain.RunId
import org.mercurio.client.feature.entry.domain.RunRecoveryEntry
import org.mercurio.client.feature.entry.domain.SessionGatewayPort
import org.mercurio.client.feature.entry.domain.SessionId
import org.mercurio.client.feature.entry.domain.SessionReconciliation
import org.mercurio.client.feature.entry.domain.isActive

internal suspend fun EntryStateHolder.reconcileTimedOutSend(request: TimedOutSendReconciliationRequest): Boolean {
    var requestEndpoint = ""
    val connectionIsCurrent =
        synchronized(sessionRequestLock) {
            if (connectionGeneration != request.connectionGeneration) {
                false
            } else {
                requestEndpoint = mutableUiState.value.endpoint
                pendingTimedOutSends[request.sessionId] =
                    TimedOutSendRecovery(
                        knownRunIds = request.knownRunIds,
                        attemptId = request.attemptId,
                        draft =
                            pendingRunDrafts[request.sessionId]
                                ?: PendingDraft(
                                    text = sessionDrafts[request.sessionId].orEmpty(),
                                    revision = sessionDraftRevisions[request.sessionId] ?: 0L,
                                ),
                        errorCategory = request.uncertaintyErrorCategory,
                    )
                true
            }
        }
    if (!connectionIsCurrent) return false
    val currentSessionGeneration =
        synchronized(sessionRequestLock) {
            mutableUiState.value.sessionList?.openedSession
                ?.takeIf { it.session.id == request.sessionId }
                ?.let { sessionRequestGeneration }
        }
    return currentSessionGeneration?.let { generation ->
        reconcileTimedOutSendNow(
            sessionId = request.sessionId,
            request = ReconciliationRequest(request.connectionGeneration, generation),
            knownRunIds = request.knownRunIds,
            resolveWhenNoNewRun = request.resolveWhenNoNewRun,
            requestEndpoint = requestEndpoint,
        )
    } ?: true
}

internal suspend fun EntryStateHolder.reconcileTimedOutSendNow(
    sessionId: SessionId,
    request: ReconciliationRequest,
    knownRunIds: Set<RunId>,
    resolveWhenNoNewRun: Boolean = false,
    requestEndpoint: String = mutableUiState.value.endpoint,
): Boolean {
    if (!awaitReconciliationClaim(sessionId, request.connectionGeneration, request.sessionGeneration)) return false
    return try {
        val gateways = timedOutSendGateways(request.connectionGeneration, request.sessionGeneration)
        if (gateways == null) {
            showTimedOutSendFailure(sessionId, request.connectionGeneration, request.sessionGeneration)
            true
        } else {
            runTimedOutSendReconciliation(
                TimedOutSendAttempt(
                    sessionId = sessionId,
                    gateways = gateways,
                    knownRunIds = knownRunIds,
                    requestEndpoint = requestEndpoint,
                    request = request,
                    resolveWhenNoNewRun = resolveWhenNoNewRun,
                ),
            )
        }
    } catch (error: CancellationException) {
        throw error
    } catch (error: GatewayException) {
        showTimedOutSendFailure(
            sessionId,
            request.connectionGeneration,
            request.sessionGeneration,
            refusalCategory = error.category.toSendFailureCategoryOrNull(),
        )
        true
    } catch (_: Exception) {
        showTimedOutSendFailure(sessionId, request.connectionGeneration, request.sessionGeneration)
        true
    } finally {
        finishReconciliation(sessionId, request.connectionGeneration, request.sessionGeneration)
    }
}

internal suspend fun EntryStateHolder.awaitReconciliationClaim(
    sessionId: SessionId,
    requestConnectionGeneration: Long,
    requestSessionGeneration: Long,
): Boolean {
    var began = false
    var attempt = 0
    while (!began && attempt < RECOVERY_CLAIM_ATTEMPTS) {
        val requestIsCurrent =
            synchronized(sessionRequestLock) {
                connectionGeneration == requestConnectionGeneration &&
                    sessionRequestGeneration == requestSessionGeneration
            }
        if (!requestIsCurrent) return false
        if (beginReconciliation(sessionId, requestConnectionGeneration, requestSessionGeneration)) {
            began = true
        } else {
            delay(RECOVERY_CLAIM_RETRY_MILLIS)
            attempt += 1
        }
    }
    return began
}

internal fun EntryStateHolder.timedOutSendGateways(
    requestConnectionGeneration: Long,
    requestSessionGeneration: Long,
): Pair<SessionGatewayPort, RunGatewayPort>? =
    synchronized(sessionRequestLock) {
        val sessionGateway = sessionGateway
        val runGateway = runGateway
        if (
            connectionGeneration != requestConnectionGeneration ||
            sessionRequestGeneration != requestSessionGeneration
        ) {
            null
        } else if (sessionGateway == null || runGateway == null) {
            null
        } else {
            sessionGateway to runGateway
        }
    }

internal suspend fun EntryStateHolder.runTimedOutSendReconciliation(attempt: TimedOutSendAttempt): Boolean {
    val result =
        ReconcileSession(attempt.gateways.first, attempt.gateways.second)
            .execute(attempt.sessionId, attempt.knownRunIds)
    val boundRunId =
        runSubmissionUncertaintyStore.boundRunId(
            PendingRunSubmissionKey(attempt.requestEndpoint, attempt.sessionId),
        )
    val boundRunReconciliation =
        boundRunId
            ?.takeUnless { runId -> result.discoveredRuns.any { it.id == runId } }
            ?.let { runId ->
                runCatching {
                    ReconcileRun(attempt.gateways.second, attempt.gateways.first)
                        .execute(runId, attempt.sessionId)
                }.getOrNull()
            }
    val reconciledResult =
        boundRunReconciliation?.let { bound ->
            result.copy(
                history = bound.history,
                discoveredRuns = mergeRuns(result.discoveredRuns, listOf(bound.run)),
            )
        } ?: result
    val outcome =
        applyTimedOutSendReconciliation(
            attempt.sessionId,
            attempt.request.connectionGeneration,
            attempt.request.sessionGeneration,
            reconciledResult,
            attempt.resolveWhenNoNewRun,
        )
    if (outcome.applied) {
        outcome.runToPersist?.let { entry ->
            persistOrQueueRecoveryEntry(attempt.requestEndpoint, entry)
        }
        outcome.runToRemove?.let { entry ->
            removeRecoveryEntry(attempt.requestEndpoint, entry)
        }
        outcome.runToObserve?.let { run ->
            startRunObservation(
                sessionId = attempt.sessionId,
                run = run,
                expectedConnectionGeneration = attempt.request.connectionGeneration,
                expectedSessionGeneration = attempt.request.sessionGeneration,
            )
        }
    }
    return outcome.applied
}

internal suspend fun EntryStateHolder.applyTimedOutSendReconciliation(
    sessionId: SessionId,
    requestConnectionGeneration: Long,
    requestSessionGeneration: Long,
    reconciliation: SessionReconciliation,
    resolveWhenNoNewRun: Boolean,
): TimedOutSendReconciliationOutcome =
    applyStateChange {
        synchronized(sessionRequestLock) {
            if (
                connectionGeneration != requestConnectionGeneration ||
                sessionRequestGeneration != requestSessionGeneration
            ) {
                return@synchronized TimedOutSendReconciliationOutcome(applied = false)
            }
            val current =
                mutableUiState.value.sessionList
                    ?: return@synchronized TimedOutSendReconciliationOutcome(applied = false)
            val opened =
                current.openedSession?.takeIf { it.session.id == sessionId }
                    ?: return@synchronized TimedOutSendReconciliationOutcome(applied = false)
            val evaluation =
                evaluateTimedOutSendUncertainty(sessionId, reconciliation, resolveWhenNoNewRun)
                    ?: return@synchronized TimedOutSendReconciliationOutcome(applied = false)
            authoritativeSessionRuns[sessionId] =
                mergeRuns(evaluation.retainedAuthoritativeRuns, evaluation.authoritativeRuns)
            evaluation.durableBoundRunId?.let { boundRunId -> uncertainSubmissionRunIds[sessionId] = boundRunId }
            if (evaluation.discoveredUnattributedRun) {
                ambiguousSubmissionSessions += sessionId
                runCatching {
                    runSubmissionUncertaintyStore.markAmbiguous(evaluation.pendingKey, evaluation.attemptId)
                }
            }
            evaluation.terminalRunIds.forEach { runId -> forgetUnresolvedLocalRun(sessionId, runId) }
            if (evaluation.canClearUncertainty) {
                clearTimedOutSendUncertainty(sessionId, current, opened, evaluation, resolveWhenNoNewRun)
            } else {
                markTimedOutSendUncertain(
                    sessionId = sessionId,
                    ui =
                        TimedOutSendUiContext(
                            current = current,
                            opened = opened,
                            recoveryDraft = evaluation.recoveryDraft,
                            authoritativeMessages = evaluation.authoritativeMessages,
                        ),
                    errorCategory = evaluation.pendingRecovery?.errorCategory ?: MessageSendErrorCategory.UNCERTAIN,
                )
            }
            pendingTimedOutSends.remove(sessionId)
            TimedOutSendReconciliationOutcome(
                applied = true,
                runToObserve = evaluation.boundSubmissionRun?.takeIf(Run::isActive),
                runToPersist =
                    evaluation.discoveredLocalRun
                        ?.takeIf(Run::isActive)
                        ?.let { RunRecoveryEntry(sessionId, it.id) },
                runToRemove =
                    evaluation.submissionRun
                        ?.takeIf { evaluation.canClearUncertainty && !it.isActive() }
                        ?.let { RunRecoveryEntry(sessionId, it.id) },
            )
        }
    }
