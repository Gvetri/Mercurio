package org.mercurio.client.feature.entry.presentation

enum class RunStatusNotificationExplanation(
    val messageRes: Int,
) {
    PERMISSION_DENIED(R.string.error_notifications_blocked),
}
