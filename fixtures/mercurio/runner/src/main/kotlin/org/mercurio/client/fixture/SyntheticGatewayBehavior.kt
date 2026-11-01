package org.mercurio.client.fixture

import java.util.concurrent.CopyOnWriteArrayList

class SyntheticGatewayBehavior(
    val healthStatus: Int = 200,
    val capabilityStatus: Int = 200,
    val endpoints: Map<String, SyntheticEndpoint> = defaultCapabilityEndpoints(),
    initialSessions: List<SyntheticGatewaySession> = emptyList(),
) {
    init {
        require(healthStatus in MIN_HTTP_STATUS..MAX_HTTP_STATUS) { "healthStatus must be an HTTP status." }
        require(capabilityStatus in MIN_HTTP_STATUS..MAX_HTTP_STATUS) { "capabilityStatus must be an HTTP status." }
    }

    val sessions = CopyOnWriteArrayList(initialSessions)
    val requests = CopyOnWriteArrayList<SyntheticGatewayRequest>()

    @Volatile
    var failNextSessionList: Boolean = false

    /** Server-side page cap; the response echoes the effective limit like the pinned Gateway. */
    @Volatile
    var sessionPageSize: Int? = null

    @Volatile
    var failNextSessionRename: Boolean = false

    @Volatile
    var failNextSessionDelete: Boolean = false

    @Volatile
    var failNextSessionPin: Boolean = false

    @Volatile
    var failNextSessionUnpin: Boolean = false

    val createdSessionId: String = "55555555-5555-4555-8555-555555555555"

    companion object {
        fun defaultCapabilityEndpoints(): Map<String, SyntheticEndpoint> =
            linkedMapOf(
                "health" to SyntheticEndpoint("GET", "/health"),
                "sessions" to SyntheticEndpoint("GET", "/api/sessions"),
                "session_create" to SyntheticEndpoint("POST", "/api/sessions"),
                "session" to SyntheticEndpoint("GET", "/api/sessions/{session_id}"),
                "session_update" to SyntheticEndpoint("PATCH", "/api/sessions/{session_id}"),
                "session_delete" to SyntheticEndpoint("DELETE", "/api/sessions/{session_id}"),
                "session_messages" to SyntheticEndpoint("GET", "/api/sessions/{session_id}/messages"),
                "runs" to SyntheticEndpoint("POST", "/v1/runs"),
                "run_status" to SyntheticEndpoint("GET", "/v1/runs/{run_id}"),
                "run_events" to SyntheticEndpoint("GET", "/v1/runs/{run_id}/events"),
            )
    }
}

private const val MIN_HTTP_STATUS = 100

private const val MAX_HTTP_STATUS = 599
