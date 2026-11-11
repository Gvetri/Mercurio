package org.mercurio.client.feature.entry.presentation

enum class SessionRenameErrorCategory(
    val messageRes: Int,
) {
    EMPTY_TITLE(R.string.error_session_title_empty),
    CONTROL_CHARACTER(R.string.error_session_title_control_character),
    GATEWAY_REQUEST_FAILED(R.string.error_session_not_renamed),
}
