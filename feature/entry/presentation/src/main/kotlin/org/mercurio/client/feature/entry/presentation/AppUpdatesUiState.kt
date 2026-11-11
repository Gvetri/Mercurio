package org.mercurio.client.feature.entry.presentation

/** The App updates section: one manual check, one download, one install, and their last result. */
data class AppUpdatesUiState(
    val status: AppUpdateStatus = AppUpdateStatus.Idle,
)
