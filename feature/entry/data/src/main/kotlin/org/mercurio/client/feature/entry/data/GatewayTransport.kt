package org.mercurio.client.feature.entry.data

interface GatewayTransport {
    fun execute(request: GatewayHttpRequest): GatewayHttpResponse

    fun openEventStream(request: GatewayHttpRequest): GatewayEventStream
}
