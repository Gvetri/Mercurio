package org.mercurio.client.fixture.journey

internal data class RunSelectionTelemetry(
    val sessionId: String,
    val model: String?,
    val effort: String?,
    val accepted: Boolean,
)
