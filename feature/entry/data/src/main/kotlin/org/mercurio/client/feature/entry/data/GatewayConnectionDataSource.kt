package org.mercurio.client.feature.entry.data

interface GatewayConnectionDataSource {
    fun loadEndpoint(): String?

    fun saveEndpoint(endpoint: String)

    fun clearEndpoint() = Unit
}
