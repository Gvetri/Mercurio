package org.mercurio.client.feature.entry.presentation

enum class SessionCreationErrorCategory(
    val messageRes: Int,
) {
    GATEWAY_REQUEST_FAILED(R.string.error_session_not_created),
}
