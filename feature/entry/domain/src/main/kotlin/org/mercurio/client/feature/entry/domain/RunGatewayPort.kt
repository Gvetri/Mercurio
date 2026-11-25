package org.mercurio.client.feature.entry.domain

interface RunGatewayPort {
    suspend fun createRun(
        sessionId: SessionId,
        input: String,
    ): Run

    /**
     * Run creation carrying the optional catalog selections of [RunCreateRequest].
     *
     * Ports that do not implement the additive model/effort contract fail closed
     * when a selection is present instead of silently dropping it.
     */
    suspend fun createRun(request: RunCreateRequest): Run {
        require(request.model == null && request.effort == null) {
            "This run gateway port does not support model or effort selection."
        }
        return createRun(request.sessionId, request.input)
    }

    suspend fun getRunStatus(runId: RunId): Run

    suspend fun observeRun(runId: RunId): RunEventObservation
}
