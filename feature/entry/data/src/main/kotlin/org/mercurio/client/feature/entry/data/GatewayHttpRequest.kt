package org.mercurio.client.feature.entry.data

class GatewayHttpRequest(
    val method: String,
    val url: String,
    headers: Map<String, String>,
    val body: String? = null,
) {
    val headers: Map<String, String> = headers.toMap()

    override fun toString(): String = "GatewayHttpRequest(method=$method, url=$url)"
}
