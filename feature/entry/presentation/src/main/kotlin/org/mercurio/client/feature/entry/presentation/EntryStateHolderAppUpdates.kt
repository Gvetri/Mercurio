package org.mercurio.client.feature.entry.presentation

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.mercurio.client.feature.entry.application.NightlyUpdateCheck
import org.mercurio.client.feature.entry.application.NightlyUpdateFailure
import org.mercurio.client.feature.entry.domain.InstallSource

@Suppress("SwallowedException", "TooGenericExceptionCaught")
internal fun EntryStateHolder.checkForUpdates() {
    if (mutableUiState.value.installSource == InstallSource.GOOGLE_PLAY) return
    val check = checkForNightlyUpdate ?: return
    val generation = updateCheckGeneration + 1
    updateCheckGeneration = generation
    updateCheckJob?.cancel()
    mutableUiState.update { current ->
        current.copy(appUpdates = current.appUpdates.copy(status = AppUpdateStatus.Checking))
    }
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
            mutableUiState.update { current ->
                current.copy(appUpdates = current.appUpdates.copy(status = status))
            }
        }
}

private fun NightlyUpdateCheck.toAppUpdateStatus(): AppUpdateStatus =
    when (this) {
        is NightlyUpdateCheck.UpToDate -> AppUpdateStatus.UpToDate(checkedAt)
        is NightlyUpdateCheck.Offered ->
            AppUpdateStatus.Offered(
                versionName = versionName,
                sourceCommit = sourceCommit,
                publishedAt = publishedAt,
                releaseTag = releaseTag,
            )
        is NightlyUpdateCheck.Refused -> AppUpdateStatus.Refused(releaseTag, reason)
        is NightlyUpdateCheck.Failed -> AppUpdateStatus.Failed(reason)
    }
