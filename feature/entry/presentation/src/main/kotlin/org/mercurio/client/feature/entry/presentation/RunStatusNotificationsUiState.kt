package org.mercurio.client.feature.entry.presentation

data class RunStatusNotificationsUiState(
    val enabled: Boolean = false,
    val explanation: RunStatusNotificationExplanation? = null,
)
