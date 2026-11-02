package org.mercurio.client.feature.entry.domain

/**
 * Approved operational metadata the local diagnostics buffer may record.
 *
 * The set is closed and deliberately small: a category names an operation the
 * client performed. It never names a Gateway payload, an endpoint, a Session, a
 * Run, a credential, or any other identifier.
 */
enum class LocalDiagnosticEventType(
    val value: String,
) {
    GATEWAY_CONNECTION_VERIFICATION("gateway_connection_verification"),
    SESSION_LIST_LOAD("session_list_load"),
    RUN_SUBMISSION("run_submission"),
}
