package org.mercurio.client.feature.entry.domain

data class RunEvent(
    val type: RunEventType,
    val runId: RunId,
    val status: String = "",
    val text: String? = null,
    val eventId: String? = null,
) {
    val delta: String?
        get() = text

    val content: String?
        get() = text
}
