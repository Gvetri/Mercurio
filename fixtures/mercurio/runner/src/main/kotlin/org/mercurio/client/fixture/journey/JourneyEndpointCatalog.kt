package org.mercurio.client.fixture.journey

/** The pinned `/v1/capabilities` endpoint names a journey can advertise. */
object JourneyEndpointCatalog {
    data class Endpoint(
        val method: String,
        val path: String,
    )

    val endpoints: Map<String, Endpoint> =
        linkedMapOf(
            "health" to Endpoint("GET", "/health"),
            "sessions" to Endpoint("GET", "/api/sessions"),
            "session_create" to Endpoint("POST", "/api/sessions"),
            "session" to Endpoint("GET", "/api/sessions/{session_id}"),
            "session_update" to Endpoint("PATCH", "/api/sessions/{session_id}"),
            "session_delete" to Endpoint("DELETE", "/api/sessions/{session_id}"),
            "session_messages" to Endpoint("GET", "/api/sessions/{session_id}/messages"),
            "runs" to Endpoint("POST", "/v1/runs"),
            "run_status" to Endpoint("GET", "/v1/runs/{run_id}"),
            "run_events" to Endpoint("GET", "/v1/runs/{run_id}/events"),
            "run_models" to Endpoint("GET", "/api/models"),
            "gateway_future_endpoint" to Endpoint("GET", "/v1/future"),
        )
}
