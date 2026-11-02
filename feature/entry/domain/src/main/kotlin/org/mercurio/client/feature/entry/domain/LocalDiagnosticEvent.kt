package org.mercurio.client.feature.entry.domain

/** One approved operational event; it carries no identifier, endpoint, or content. */
data class LocalDiagnosticEvent(
    val eventType: LocalDiagnosticEventType,
    val status: LocalDiagnosticStatus? = null,
)
