package org.mercurio.client.feature.entry.presentation

import org.mercurio.client.feature.entry.application.NightlyUpdateFailure
import org.mercurio.client.feature.entry.application.NightlyUpdateRefusal
import java.time.Instant

/** The App updates section: one manual check and its last result. */
data class AppUpdatesUiState(
    val status: AppUpdateStatus = AppUpdateStatus.Idle,
)

/** The state of the one manual update check. */
sealed interface AppUpdateStatus {
    data object Idle : AppUpdateStatus

    data object Checking : AppUpdateStatus

    data class UpToDate(
        val checkedAt: Instant,
    ) : AppUpdateStatus

    data class Offered(
        val versionName: String,
        val sourceCommit: String,
        val publishedAt: Instant,
        val releaseTag: String,
    ) : AppUpdateStatus

    data class Refused(
        val releaseTag: String?,
        val reason: NightlyUpdateRefusal,
    ) : AppUpdateStatus

    data class Failed(
        val reason: NightlyUpdateFailure,
    ) : AppUpdateStatus
}
