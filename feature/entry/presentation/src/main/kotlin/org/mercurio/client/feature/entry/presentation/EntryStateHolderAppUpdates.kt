package org.mercurio.client.feature.entry.presentation

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.mercurio.client.feature.entry.application.NightlyDownloadOutcome
import org.mercurio.client.feature.entry.application.NightlyUpdateCheck
import org.mercurio.client.feature.entry.application.NightlyUpdateFailure
import org.mercurio.client.feature.entry.domain.InstallSource
import org.mercurio.client.feature.entry.domain.NightlyDownloadProgress

@Suppress("SwallowedException", "TooGenericExceptionCaught")
internal fun EntryStateHolder.checkForUpdates() {
    if (mutableUiState.value.installSource == InstallSource.GOOGLE_PLAY) return
    val check = checkForNightlyUpdate ?: return
    stopDownload()
    val generation = updateCheckGeneration + 1
    updateCheckGeneration = generation
    updateCheckJob?.cancel()
    setUpdateStatus(AppUpdateStatus.Checking)
    updateCheckJob =
        scope.launch {
            val status =
                try {
                    check.execute().toAppUpdateStatus()
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    AppUpdateStatus.Failed(NightlyUpdateFailure.SERVICE_UNAVAILABLE)
                }
            ensureActive()
            if (updateCheckGeneration != generation) return@launch
            setUpdateStatus(status)
        }
}

@Suppress("SwallowedException", "TooGenericExceptionCaught")
internal fun EntryStateHolder.downloadUpdate() {
    val current = mutableUiState.value
    val offer = (current.appUpdates.status as? AppUpdateStatus.Offered)?.offer
    val download = downloadNightlyUpdate
    if (current.installSource == InstallSource.GOOGLE_PLAY || offer == null || download == null) return
    updateCheckGeneration += 1
    val generation = updateDownloadGeneration + 1
    updateDownloadGeneration = generation
    updateDownloadJob?.cancel()
    updateDownloadOffer = offer
    setUpdateStatus(AppUpdateStatus.Downloading(NightlyDownloadProgress(0L, offer.apk.sizeBytes)))
    updateDownloadJob =
        scope.launch {
            val status =
                try {
                    download.execute(offer) { progress -> reportDownloadProgress(generation, progress) }
                        .toAppUpdateStatus()
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    AppUpdateStatus.Failed(NightlyUpdateFailure.SERVICE_UNAVAILABLE)
                }
            ensureActive()
            if (updateDownloadGeneration != generation) return@launch
            setUpdateStatus(status)
        }
}

internal fun EntryStateHolder.reportDownloadProgress(
    generation: Long,
    progress: NightlyDownloadProgress,
) {
    if (updateDownloadGeneration != generation) return
    setUpdateStatus(AppUpdateStatus.Downloading(progress))
}

internal fun EntryStateHolder.cancelUpdateDownload() {
    val offer = updateDownloadOffer ?: return
    stopDownload()
    setUpdateStatus(AppUpdateStatus.Offered(offer))
}

internal fun EntryStateHolder.setUpdateStatus(status: AppUpdateStatus) {
    mutableUiState.update { current ->
        current.copy(appUpdates = current.appUpdates.copy(status = status))
    }
}

private fun EntryStateHolder.stopDownload() {
    updateDownloadGeneration += 1
    updateDownloadJob?.cancel()
    updateDownloadJob = null
    updateDownloadOffer = null
}

private fun NightlyUpdateCheck.toAppUpdateStatus(): AppUpdateStatus =
    when (this) {
        is NightlyUpdateCheck.UpToDate -> AppUpdateStatus.UpToDate(checkedAt)
        is NightlyUpdateCheck.Offered -> AppUpdateStatus.Offered(offer)
        is NightlyUpdateCheck.Refused -> AppUpdateStatus.Refused(releaseTag, reason)
        is NightlyUpdateCheck.Failed -> AppUpdateStatus.Failed(reason)
    }

private fun NightlyDownloadOutcome.toAppUpdateStatus(): AppUpdateStatus =
    when (this) {
        is NightlyDownloadOutcome.Verified -> AppUpdateStatus.Verified(verifiedAt)
        is NightlyDownloadOutcome.Refused -> AppUpdateStatus.DownloadRefused(reason)
        is NightlyDownloadOutcome.Failed -> AppUpdateStatus.Failed(reason)
    }
