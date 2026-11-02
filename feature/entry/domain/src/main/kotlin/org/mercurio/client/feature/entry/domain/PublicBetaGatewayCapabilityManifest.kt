package org.mercurio.client.feature.entry.domain

object PublicBetaGatewayCapabilityManifest {
    const val VERSION = 1

    /**
     * The additive-optional `run_models` endpoint row a Gateway may advertise for the
     * Run model catalog. It is deliberately not a requirement row: a missing or
     * mismatched row makes the catalog unavailable and never fails connection
     * verification.
     */
    val modelCatalogEndpoint =
        GatewayCapabilityRequirement(
            endpoint = "run_models",
            method = "GET",
            path = "/api/models",
            operation = GatewayContractOperation.MODEL_CATALOG,
        )

    val current =
        GatewayCapabilityManifest(
            version = VERSION,
            discoveryPath = "/v1/capabilities",
            requirements =
                listOf(
                    GatewayCapabilityRequirement(
                        "sessions",
                        "GET",
                        "/api/sessions",
                        GatewayContractOperation.SESSION_LIST,
                    ),
                    GatewayCapabilityRequirement(
                        "session_create",
                        "POST",
                        "/api/sessions",
                        GatewayContractOperation.SESSION_CREATE,
                    ),
                    GatewayCapabilityRequirement(
                        "session",
                        "GET",
                        "/api/sessions/{session_id}",
                        GatewayContractOperation.SESSION_OPEN,
                    ),
                    GatewayCapabilityRequirement(
                        "session_messages",
                        "GET",
                        "/api/sessions/{session_id}/messages",
                        GatewayContractOperation.SESSION_HISTORY,
                    ),
                    GatewayCapabilityRequirement(
                        "session_update",
                        "PATCH",
                        "/api/sessions/{session_id}",
                        GatewayContractOperation.SESSION_RENAME,
                    ),
                    GatewayCapabilityRequirement(
                        "session_delete",
                        "DELETE",
                        "/api/sessions/{session_id}",
                        GatewayContractOperation.SESSION_DELETE,
                    ),
                    GatewayCapabilityRequirement(
                        "session_update",
                        "PATCH",
                        "/api/sessions/{session_id}",
                        GatewayContractOperation.SESSION_PIN,
                    ),
                    GatewayCapabilityRequirement(
                        "session_update",
                        "PATCH",
                        "/api/sessions/{session_id}",
                        GatewayContractOperation.SESSION_UNPIN,
                    ),
                    GatewayCapabilityRequirement(
                        "runs",
                        "POST",
                        "/v1/runs",
                        GatewayContractOperation.RUN_CREATE,
                    ),
                    GatewayCapabilityRequirement(
                        "run_status",
                        "GET",
                        "/v1/runs/{run_id}",
                        GatewayContractOperation.RUN_STATUS,
                    ),
                    GatewayCapabilityRequirement(
                        "run_events",
                        "GET",
                        "/v1/runs/{run_id}/events",
                        GatewayContractOperation.RUN_SSE,
                    ),
                ),
        )
}
