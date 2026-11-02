package org.mercurio.client.feature.entry.domain

class GatewayException(
    val category: GatewayErrorCategory,
    message: String = category.safeMessage,
) : RuntimeException(message)
