package org.mercurio.client.feature.entry.domain

/** The single approved result or status value an event record may carry. */
enum class LocalDiagnosticStatus(
    val value: String,
) {
    SUCCEEDED("succeeded"),
    FAILED("failed"),
    UNCERTAIN("uncertain"),
}
