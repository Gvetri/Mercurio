package org.mercurio.client.feature.entry.presentation

enum class EntryErrorCategory(
    val messageRes: Int,
) {
    INVALID_ADDRESS(R.string.error_invalid_address),
    SECURE_CONNECTION_FAILED(R.string.error_secure_connection_failed),
    AUTHENTICATION_FAILED(R.string.error_authentication_failed),
    REQUIRED_FEATURE_UNAVAILABLE(R.string.error_required_feature_unavailable),
    GATEWAY_REQUEST_FAILED(R.string.error_gateway_request_failed),
    CREDENTIAL_STORAGE_FAILED(R.string.error_credential_storage_failed),
}
