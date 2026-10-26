package org.mercurio.client.feature.entry.presentation

import org.mercurio.client.feature.entry.application.OpenedSession
import org.mercurio.client.feature.entry.domain.GatewayErrorCategory
import org.mercurio.client.feature.entry.domain.ModelSelection
import org.mercurio.client.feature.entry.domain.Run
import org.mercurio.client.feature.entry.domain.RunObservationState
import org.mercurio.client.feature.entry.domain.RunPresentationState
import org.mercurio.client.feature.entry.domain.SessionHistory
import org.mercurio.client.feature.entry.domain.isActive
import org.mercurio.client.feature.entry.domain.isRunRetryEligible
import org.mercurio.client.feature.entry.domain.runs

internal fun OpenedSession.toOpenSessionUiState(overlay: OpenSessionOverlay): OpenSessionUiState =
    OpenSessionUiState(
        session = session.toSessionItemUiState(),
        messages = overlay.messages ?: history.messages.map { it.toSessionMessageUiState() }.chronological(),
        composerText = overlay.composerText,
        sendErrorCategory = overlay.sendErrorCategory,
        hasUnresolvedSubmission = overlay.hasUnresolvedSubmission,
        latestRun = overlay.latestRun ?: history.latestRun(),
        activeRuns = overlay.activeRuns ?: history.runs().activeRuns(),
        isSending = overlay.isSending,
        latestRunState = overlay.latestRunState,
        latestRunRetryAvailable = overlay.latestRunRetryAvailable,
        activeResponse = overlay.activeResponse,
        isRefreshing = overlay.isRefreshing,
    )

internal fun RunObservationState.toSessionMessageUiState(
    retryInput: String? = null,
    modelSelection: ModelSelection? = null,
): SessionMessageUiState =
    SessionMessageUiState(
        id = "active-response:${run.id.value}",
        role = "assistant",
        content = responseText.takeIf(String::isNotEmpty),
        runId = run.id,
        runState = state,
        isStreaming = isStreaming,
        streamInterrupted = isStreamInterrupted,
        failureSafeMessage =
            if (state == RunPresentationState.FAILED) runFailureMessageFor(modelSelection) else null,
        retryAvailable = isRunRetryEligible(state, retryInput),
    )

internal fun SessionHistory.latestRun(): Run? = runs().latestRun()

internal fun List<Run>.latestRun(): Run? = lastOrNull()

internal fun List<Run>.latestActiveRun(): Run? = lastOrNull(Run::isActive)

internal fun List<Run>.activeRuns(): List<Run> = filter(Run::isActive)

internal fun mergeRuns(
    existing: List<Run>,
    incoming: List<Run>,
): List<Run> =
    (existing + incoming)
        .associateBy { it.id }
        .values
        .toList()

internal fun EntryUiState.connectionSetupState(): EntryUiState =
    copy(
        title = "Verify a Hermes Gateway",
        supportingText = "Enter the HTTPS endpoint and bearer credential for your Gateway.",
        actionLabel = "Verify Gateway Connection",
        connectionSetupRequested = true,
        errorCategory = null,
    )

internal fun GatewayErrorCategory.toUserFacingCategory(): EntryErrorCategory =
    when (this) {
        GatewayErrorCategory.INVALID_ADDRESS -> EntryErrorCategory.INVALID_ADDRESS
        GatewayErrorCategory.SECURE_CONNECTION_FAILED -> EntryErrorCategory.SECURE_CONNECTION_FAILED
        GatewayErrorCategory.AUTHENTICATION_FAILED -> EntryErrorCategory.AUTHENTICATION_FAILED
        GatewayErrorCategory.REQUIRED_FEATURE_UNAVAILABLE -> EntryErrorCategory.REQUIRED_FEATURE_UNAVAILABLE
        GatewayErrorCategory.MODEL_REFUSED,
        GatewayErrorCategory.EFFORT_REFUSED,
        GatewayErrorCategory.GATEWAY_REQUEST_FAILED,
        GatewayErrorCategory.INVALID_RESPONSE,
        -> EntryErrorCategory.GATEWAY_REQUEST_FAILED
    }

/**
 * The distinct send-surface copy for a refused model or effort; null for every other
 * category, whose submission failures keep the generic request-failed copy. Refusals
 * reached at admission (a 4xx on Run creation) are the only connection-adjacent
 * failures with their own message; the connection surface itself stays generic.
 */
internal fun GatewayErrorCategory.toSendFailureCategoryOrNull(): MessageSendErrorCategory? =
    when (this) {
        GatewayErrorCategory.MODEL_REFUSED -> MessageSendErrorCategory.MODEL_REFUSED
        GatewayErrorCategory.EFFORT_REFUSED -> MessageSendErrorCategory.EFFORT_REFUSED
        GatewayErrorCategory.INVALID_ADDRESS,
        GatewayErrorCategory.SECURE_CONNECTION_FAILED,
        GatewayErrorCategory.AUTHENTICATION_FAILED,
        GatewayErrorCategory.REQUIRED_FEATURE_UNAVAILABLE,
        GatewayErrorCategory.GATEWAY_REQUEST_FAILED,
        GatewayErrorCategory.INVALID_RESPONSE,
        -> null
    }
