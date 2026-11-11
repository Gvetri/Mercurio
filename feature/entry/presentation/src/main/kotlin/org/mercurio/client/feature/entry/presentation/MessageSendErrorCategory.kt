package org.mercurio.client.feature.entry.presentation

enum class MessageSendErrorCategory(
    val messageRes: Int,
) {
    GATEWAY_REQUEST_FAILED(R.string.error_message_not_sent),
    UNCERTAIN(R.string.error_message_uncertain),
    MODEL_REFUSED(R.string.error_message_model_refused),
    EFFORT_REFUSED(R.string.error_message_effort_refused),
}
