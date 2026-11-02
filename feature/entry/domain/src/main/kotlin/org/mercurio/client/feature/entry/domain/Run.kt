package org.mercurio.client.feature.entry.domain

import java.util.Locale

data class Run(
    val id: RunId,
    val sessionId: SessionId,
    val status: String,
)

const val UNKNOWN_RUN_STATUS = "unknown"

private val terminalRunStatuses =
    setOf(
        "completed",
        "complete",
        "succeeded",
        "success",
        "failed",
        "failure",
        "error",
        "cancelled",
        "canceled",
        "interrupted",
    )

fun Run.isActive(): Boolean = status.trim().lowercase(Locale.ROOT) !in terminalRunStatuses

fun Run.toRunPresentationState(): RunPresentationState = status.toRunPresentationState()
