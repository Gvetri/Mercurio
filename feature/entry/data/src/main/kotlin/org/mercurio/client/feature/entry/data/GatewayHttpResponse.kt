package org.mercurio.client.feature.entry.data

class GatewayHttpResponse(
    val statusCode: Int,
    val body: String,
) {
    override fun toString(): String = "GatewayHttpResponse(statusCode=$statusCode)"
}
