package org.mercurio.client.fixture.journey

data class JourneySession(
    val id: String,
    val title: String?,
    val preview: String?,
    val pinned: Boolean,
    val history: List<JourneyMessage> = emptyList(),
)
