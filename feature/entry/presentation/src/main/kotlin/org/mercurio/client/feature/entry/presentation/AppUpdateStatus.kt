package org.mercurio.client.feature.entry.presentation

import org.mercurio.client.feature.entry.application.NightlyDownloadRefusal
import org.mercurio.client.feature.entry.application.NightlyUpdateFailure
import org.mercurio.client.feature.entry.application.NightlyUpdateRefusal
import org.mercurio.client.feature.entry.domain.NightlyDownloadProgress
import org.mercurio.client.feature.entry.domain.NightlyInstallRefusal
import org.mercurio.client.feature.entry.domain.NightlyUpdateOffer
import java.time.Instant

/** The state of the one manual update action. */
sealed interface AppUpdateStatus {
    data object Idle : AppUpdateStatus

    data object Checking : AppUpdateStatus

    data class UpToDate(
        val checkedAt: Instant,
    ) : AppUpdateStatus

    data class Offered(
        val offer: NightlyUpdateOffer,
    ) : AppUpdateStatus

    data class Downloading(
        val progress: NightlyDownloadProgress,
    ) : AppUpdateStatus

    data class Verified(
        val verifiedAt: Instant,
    ) : AppUpdateStatus

    data class PermissionRequired(
        val versionName: String,
        val versionCode: Long,
    ) : AppUpdateStatus

    data class Installed(
        val versionName: String,
        val versionCode: Long,
    ) : AppUpdateStatus

    data class Refused(
        val releaseTag: String?,
        val reason: NightlyUpdateRefusal,
    ) : AppUpdateStatus

    data class DownloadRefused(
        val reason: NightlyDownloadRefusal,
    ) : AppUpdateStatus

    data class InstallRefused(
        val reason: NightlyInstallRefusal,
    ) : AppUpdateStatus

    data class Failed(
        val reason: NightlyUpdateFailure,
    ) : AppUpdateStatus
}
