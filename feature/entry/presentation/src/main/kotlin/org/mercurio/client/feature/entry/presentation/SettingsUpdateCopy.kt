package org.mercurio.client.feature.entry.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.mercurio.client.feature.entry.application.NightlyDownloadRefusal
import org.mercurio.client.feature.entry.application.NightlyUpdateFailure
import org.mercurio.client.feature.entry.application.NightlyUpdateRefusal

@Composable
internal fun settingsUpdateRefusal(status: AppUpdateStatus.Refused): String =
    when (status.reason) {
        NightlyUpdateRefusal.CONTRADICTORY_RELEASE ->
            status.releaseTag?.let { tag ->
                stringResource(R.string.settings_app_updates_refused_contradiction, tag)
            } ?: stringResource(R.string.settings_app_updates_refused_generic)
        NightlyUpdateRefusal.UNREADABLE_INSTALLED_BUILD ->
            stringResource(R.string.settings_app_updates_refused_unreadable_build)
    }

@Composable
internal fun settingsDownloadRefusal(reason: NightlyDownloadRefusal): String =
    stringResource(
        when (reason) {
            NightlyDownloadRefusal.SIZE_MISMATCH -> R.string.settings_app_updates_refused_size
            NightlyDownloadRefusal.HASH_MISMATCH -> R.string.settings_app_updates_refused_hash
            NightlyDownloadRefusal.UNREADABLE_APK -> R.string.settings_app_updates_refused_unreadable_apk
            NightlyDownloadRefusal.VERSION_MISMATCH -> R.string.settings_app_updates_refused_version
            NightlyDownloadRefusal.UNREADABLE_INSTALLED_SIGNER ->
                R.string.settings_app_updates_refused_unknown_signer
            NightlyDownloadRefusal.SIGNER_MISMATCH -> R.string.settings_app_updates_refused_signer
            NightlyDownloadRefusal.ABI_MISMATCH -> R.string.settings_app_updates_refused_abi
        },
    )

@Composable
internal fun settingsUpdateFailure(reason: NightlyUpdateFailure): String =
    stringResource(
        when (reason) {
            NightlyUpdateFailure.OFFLINE -> R.string.settings_app_updates_offline
            NightlyUpdateFailure.RATE_LIMITED -> R.string.settings_app_updates_rate_limited
            NightlyUpdateFailure.SERVICE_UNAVAILABLE -> R.string.settings_app_updates_unavailable
            NightlyUpdateFailure.STORAGE_UNAVAILABLE -> R.string.settings_app_updates_storage
        },
    )
