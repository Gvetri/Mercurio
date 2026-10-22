package org.hermesnative.client.feature.entry.domain

interface GatewayCapabilityPort {
    fun discoverCapabilities(): GatewayCapabilities
}

/**
 * Discovers and fetches the additive-optional Run model catalog for the current endpoint.
 *
 * The catalog follows the documented fail-closed ladder: callers fetch only when the
 * discovered capabilities advertise the `run_models` row ([supportsModelCatalog]); a
 * transport or non-200 failure surfaces as `GATEWAY_REQUEST_FAILED` and a malformed
 * response as `INVALID_RESPONSE`. Both mean "catalog unavailable, selection hidden" and
 * never block connection verification. Catalog availability is exposed by
 * [GatewayCapabilities.supportsModelCatalog] on the discovered endpoints table; callers consult it and
 * never fetch when it is false. Ports that do not implement the catalog fail
 * closed instead of returning an empty catalog.
 */
interface ModelGatewayPort {
    fun fetchModelCatalog(): ModelCatalog =
        throw GatewayException(
            GatewayErrorCategory.REQUIRED_FEATURE_UNAVAILABLE,
            "Model catalog unavailable on this Gateway port.",
        )
}

interface SessionGatewayPort {
    fun listSessions(request: SessionListRequest): SessionPage

    fun listSessions(): SessionPage = listSessions(SessionListRequest())

    fun createSession(title: String?): Session

    fun openSession(sessionId: SessionId): Session

    fun loadSessionHistory(sessionId: SessionId): SessionHistory

    fun renameSession(
        sessionId: SessionId,
        title: String,
    ): Session

    fun deleteSession(sessionId: SessionId)

    fun pinSession(sessionId: SessionId): SessionPinResult

    fun unpinSession(sessionId: SessionId): SessionPinResult
}

interface RunGatewayPort {
    fun createRun(
        sessionId: SessionId,
        input: String,
    ): Run

    /**
     * Run creation carrying the optional catalog selections of [RunCreateRequest].
     *
     * Ports that do not implement the additive model/effort contract fail closed
     * when a selection is present instead of silently dropping it.
     */
    fun createRun(request: RunCreateRequest): Run {
        require(request.model == null && request.effort == null) {
            "This run gateway port does not support model or effort selection."
        }
        return createRun(request.sessionId, request.input)
    }

    fun getRunStatus(runId: RunId): Run

    fun observeRun(runId: RunId): RunEventObservation
}

interface RunEventObservation : Sequence<RunEvent>, AutoCloseable

interface GatewayContractPort :
    GatewayCapabilityPort,
    SessionGatewayPort,
    RunGatewayPort,
    ModelGatewayPort
