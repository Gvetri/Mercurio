package org.mercurio.client.feature.entry.data

class InMemoryGatewayConnectionDataSource(
    private var endpoint: String? = null,
) : GatewayConnectionDataSource {
    override fun loadEndpoint(): String? = endpoint

    override fun saveEndpoint(endpoint: String) {
        this.endpoint = endpoint
    }

    override fun clearEndpoint() {
        endpoint = null
    }
}
