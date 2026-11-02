package org.mercurio.client.fixture

data class SyntheticGatewayMessage(
    val id: String,
    val role: String?,
    val content: String?,
    val timestamp: String? = null,
)
