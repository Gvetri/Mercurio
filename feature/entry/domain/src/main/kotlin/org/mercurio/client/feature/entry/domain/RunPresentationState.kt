package org.mercurio.client.feature.entry.domain

import java.util.Locale

/** Stable labels that the client may expose for a Gateway Run. */
enum class RunPresentationState(
    val label: String,
) {
    STARTING("Starting"),
    RUNNING("Running"),
    COMPLETING("Completing"),
    SUCCEEDED("Succeeded"),
    FAILED("Failed"),
    CANCELLED("Cancelled"),
    UNCERTAIN("Uncertain"),
}

fun String.toRunPresentationState(): RunPresentationState =
    when (trim().lowercase(Locale.ROOT)) {
        "queued", "starting", "started" -> RunPresentationState.STARTING
        "running", "in_progress", "in-progress", "waiting_for_approval" -> RunPresentationState.RUNNING
        "completing", "finalizing", "stopping" -> RunPresentationState.COMPLETING
        "completed", "complete", "succeeded", "success" -> RunPresentationState.SUCCEEDED
        "failed", "failure", "error" -> RunPresentationState.FAILED
        "cancelled", "canceled", "interrupted" -> RunPresentationState.CANCELLED
        else -> RunPresentationState.UNCERTAIN
    }

fun RunPresentationState.isTerminal(): Boolean =
    this == RunPresentationState.SUCCEEDED ||
        this == RunPresentationState.FAILED ||
        this == RunPresentationState.CANCELLED
