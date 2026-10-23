package org.hermesnative.client.feature.entry.domain

enum class GatewayContractOperation {
    SESSION_LIST,
    SESSION_CREATE,
    SESSION_OPEN,
    SESSION_HISTORY,
    SESSION_RENAME,
    SESSION_DELETE,
    SESSION_PIN,
    SESSION_UNPIN,
    RUN_CREATE,
    RUN_STATUS,
    RUN_SSE,
    MODEL_CATALOG,
}

/**
 * One binding from the pinned `GET /v1/capabilities` `endpoints` table.
 *
 * The pinned Gateway advertises each client route as `{"method": ..., "path": ...}`
 * under a stable endpoint name; capability detection verifies the names, methods,
 * and paths the client actually calls instead of a synthetic capability identifier
 * list.
 */
data class GatewayEndpoint(
    val method: String,
    val path: String,
)

data class GatewayCapabilityRequirement(
    val endpoint: String,
    val method: String,
    val path: String,
    val operation: GatewayContractOperation,
)

data class GatewayCapabilityManifest(
    val version: Int,
    val discoveryPath: String,
    val requirements: List<GatewayCapabilityRequirement>,
) {
    /** Required endpoint name to the pinned method and path the client calls. */
    val requiredEndpoints: Map<String, GatewayEndpoint>
        get() = requirements.associate { it.endpoint to GatewayEndpoint(it.method, it.path) }
}

/**
 * Fail-closed check of a discovered `endpoints` table against the pinned manifest.
 *
 * A required endpoint must be present with the exact pinned method and path: the
 * client's requests are hardcoded to those paths, so a name-only or method-only
 * match could accept a Gateway whose routes the client cannot call.
 */
fun GatewayCapabilities.satisfies(manifest: GatewayCapabilityManifest): Boolean =
    manifest.requiredEndpoints.all { (name, required) ->
        endpoints[name].matches(required)
    }

private fun GatewayEndpoint?.matches(required: GatewayEndpoint): Boolean =
    this != null &&
        method.equals(required.method, ignoreCase = true) &&
        path == required.path

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

data class GatewayCapabilities(
    val endpoints: Map<String, GatewayEndpoint>,
) {
    fun supports(endpoint: String): Boolean = endpoint in endpoints
}

/**
 * Additive-optional detection of the Run model catalog.
 *
 * The catalog is available only when the discovered table advertises the pinned
 * `run_models` row with the exact method and path; absence or mismatch means the
 * catalog is unavailable, never that the connection fails.
 */
fun GatewayCapabilities.supportsModelCatalog(): Boolean {
    val required = PublicBetaGatewayCapabilityManifest.modelCatalogEndpoint
    return supports(required.endpoint) &&
        endpoints[required.endpoint].matches(GatewayEndpoint(required.method, required.path))
}
