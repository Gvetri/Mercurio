package org.mercurio.client.fixture

data class SyntheticGatewayRequest(
    val method: String,
    val path: String,
    val query: String?,
    val hasAuthorizationHeader: Boolean,
    val body: String? = null,
)
