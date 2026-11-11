package org.mercurio.client.feature.entry.presentation

enum class SessionListErrorCategory(
    val messageRes: Int,
) {
    GATEWAY_UNAVAILABLE(R.string.error_session_list_unavailable),
    SESSION_UNAVAILABLE(R.string.error_session_unavailable),
}
