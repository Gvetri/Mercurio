package org.mercurio.client.feature.entry.domain

data class RunObservationState(
    val run: Run,
    val state: RunPresentationState = run.toRunPresentationState(),
    val responseText: String = "",
    val isStreaming: Boolean = false,
    val isStreamInterrupted: Boolean = false,
    val processedEventIds: Set<String> = emptySet(),
) {
    fun transition(event: RunEvent): RunObservationState = RunEventStateTransition.apply(this, event)

    fun interrupted(): RunObservationState = RunEventStateTransition.interrupted(this)

    val userFacingState: RunPresentationState
        get() = state
}
