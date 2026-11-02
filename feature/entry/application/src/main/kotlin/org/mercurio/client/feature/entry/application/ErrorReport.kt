package org.mercurio.client.feature.entry.application

/** One error event the application reports. */
data class ErrorReport(
    val release: String,
    val environment: String,
)
