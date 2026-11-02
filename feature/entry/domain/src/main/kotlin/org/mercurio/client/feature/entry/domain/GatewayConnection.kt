package org.mercurio.client.feature.entry.domain

data class GatewayConnection(
    val endpoint: String,
    val bearerCredential: String? = null,
)
