package org.mercurio.client.feature.entry.presentation

import kotlinx.coroutines.launch
import org.mercurio.client.feature.entry.domain.InstallSource

/**
 * Continues a flow that stopped at the permission gate once the app is visible again: the probe runs
 * again, and a granted permission proceeds to staging. Nothing is staged while the permission is
 * still missing, because [continueInstall] re-runs the same gate.
 */
internal fun EntryStateHolder.resumeInstallAfterPermission() {
    val current = mutableUiState.value
    val offer = updateDownloadOffer ?: return
    if (current.appUpdates.status !is AppUpdateStatus.PermissionRequired) return
    val canResume =
        current.installSource != InstallSource.GOOGLE_PLAY &&
            updateInstallArtifact != null &&
            installNightlyUpdate != null
    if (canResume) {
        val generation = updateDownloadGeneration + 1
        updateDownloadGeneration = generation
        updateDownloadJob?.cancel()
        updateDownloadJob = scope.launch { continueInstall(offer, generation) }
    }
}

internal fun EntryStateHolder.openInstallPermissionSettings() {
    if (mutableUiState.value.appUpdates.status !is AppUpdateStatus.PermissionRequired) return
    requestInstallPermissionSettings?.invoke()
}

internal fun EntryStateHolder.restartToFinish() {
    if (mutableUiState.value.appUpdates.status !is AppUpdateStatus.Installed) return
    requestAppRestart?.invoke()
}
