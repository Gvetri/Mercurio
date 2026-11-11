package org.mercurio.client.feature.entry.presentation

import org.mercurio.client.feature.entry.domain.GatewayHistoryMessage
import org.mercurio.client.feature.entry.domain.ModelSelection
import org.mercurio.client.feature.entry.domain.RunId
import org.mercurio.client.feature.entry.domain.RunPresentationState
import org.mercurio.client.feature.entry.domain.isRunRetryEligible
import org.mercurio.client.feature.entry.domain.toRunPresentationState
import java.math.BigDecimal
import java.time.Instant

data class SessionMessageUiState(
    val id: String,
    val role: String?,
    val content: String?,
    val runId: RunId? = null,
    val runStatus: String? = null,
    val runResult: String? = null,
    val timestamp: Instant? = null,
    val isStreaming: Boolean = false,
    val runState: RunPresentationState? = null,
    val streamInterrupted: Boolean = false,
    val failureMessage: UiText? = null,
    val failureTechnicalDetail: String? = null,
    val retryAvailable: Boolean = false,
) {
    val isFailedRun: Boolean
        get() =
            runState == RunPresentationState.FAILED ||
                runStatus?.toRunPresentationState() == RunPresentationState.FAILED
}

internal fun GatewayHistoryMessage.toSessionMessageUiState(
    retryInput: String? = null,
    modelSelection: ModelSelection? = null,
): SessionMessageUiState {
    val failed = runStatus?.toRunPresentationState() == RunPresentationState.FAILED
    return SessionMessageUiState(
        id = id,
        role = role,
        content = content,
        runId = runId,
        runStatus = runStatus,
        runResult = runResult,
        timestamp = timestamp?.let(::parseGatewayTimestamp),
        failureMessage = if (failed) runFailureMessageFor(modelSelection) else null,
        retryAvailable = isRunRetryEligible(if (failed) RunPresentationState.FAILED else null, retryInput),
    )
}

private fun parseGatewayTimestamp(value: String): Instant? =
    runCatching { Instant.parse(value) }.getOrNull() ?: epochSecondsToInstant(value)

private const val NANOSECOND_SCALE = 9

private fun epochSecondsToInstant(value: String): Instant? {
    val seconds = value.toBigDecimalOrNull() ?: return null
    return runCatching {
        val wholeSeconds = seconds.toBigInteger()
        val fractionNanos = seconds.remainder(BigDecimal.ONE).movePointRight(NANOSECOND_SCALE).toLong()
        Instant.ofEpochSecond(wholeSeconds.longValueExact(), fractionNanos)
    }.getOrNull()
}

internal fun runFailureMessageFor(modelSelection: ModelSelection?): UiText {
    val category =
        when {
            modelSelection == null -> RunFailureCategory.GATEWAY_REPORTED
            modelSelection.effort != null -> RunFailureCategory.EFFORT_REFUSED
            else -> RunFailureCategory.MODEL_REFUSED
        }
    return UiText.Resource(category.messageRes)
}

internal fun List<SessionMessageUiState>.chronological(): List<SessionMessageUiState> {
    val timestamped =
        filter { it.timestamp != null }
            .sortedBy { requireNotNull(it.timestamp) }
    return when {
        timestamped.size == size -> timestamped
        timestamped.isEmpty() -> this
        else -> {
            var timestampedIndex = 0
            map { message ->
                if (message.timestamp == null) {
                    message
                } else {
                    timestamped[timestampedIndex++]
                }
            }
        }
    }
}
