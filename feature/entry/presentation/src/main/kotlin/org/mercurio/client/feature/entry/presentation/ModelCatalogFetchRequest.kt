package org.mercurio.client.feature.entry.presentation

import org.mercurio.client.feature.entry.domain.ModelCatalogSurface
import org.mercurio.client.feature.entry.domain.ModelGatewayPort

/**
 * A launched model-catalog fetch bound to the connection it was started for; a result is
 * applied only while that connection is still current.
 */
internal data class ModelCatalogFetchRequest(
    val port: ModelGatewayPort,
    val endpoint: String,
    val surface: ModelCatalogSurface,
    val connectionGeneration: Long,
)
