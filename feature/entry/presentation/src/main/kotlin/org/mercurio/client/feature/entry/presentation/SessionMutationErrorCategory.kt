package org.mercurio.client.feature.entry.presentation

enum class SessionMutationErrorCategory(
    val messageRes: Int,
) {
    GATEWAY_REQUEST_FAILED(R.string.error_session_action_failed),
}
