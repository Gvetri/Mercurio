package org.mercurio.client.feature.entry.domain

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
