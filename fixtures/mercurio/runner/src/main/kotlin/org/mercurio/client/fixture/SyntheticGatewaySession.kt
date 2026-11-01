package org.mercurio.client.fixture

data class SyntheticGatewaySession(
    val id: String,
    val title: String?,
    val preview: String?,
    val pinned: Boolean,
    val history: List<SyntheticGatewayMessage> = emptyList(),
)
