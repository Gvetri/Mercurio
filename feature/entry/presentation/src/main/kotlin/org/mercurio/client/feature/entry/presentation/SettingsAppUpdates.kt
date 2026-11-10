package org.mercurio.client.feature.entry.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import org.mercurio.client.feature.entry.domain.InstallSource
import org.mercurio.client.feature.entry.domain.NightlyDownloadProgress
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun SettingsAppUpdatesSection(
    state: EntryUiState,
    onEvent: (EntryUiEvent) -> Unit,
) {
    val playManaged = state.installSource == InstallSource.GOOGLE_PLAY
    val status = state.appUpdates.status
    SettingsSectionHeader(stringResource(R.string.settings_section_app_updates))
    SettingsValueRow(
        label = stringResource(R.string.settings_app_updates_installed_version),
        value = state.clientVersion,
    )
    SettingsValueRow(
        label = stringResource(R.string.settings_app_updates_row),
        value = settingsUpdateSummary(status = status, playManaged = playManaged),
        detail =
            (status as? AppUpdateStatus.Offered)?.let { offered ->
                stringResource(
                    R.string.settings_app_updates_offered_detail,
                    offered.offer.publishedAt.settingsPublishedTime(),
                    offered.offer.releaseTag,
                )
            },
    )
    SettingsUpdateStatusBody(status = status, onEvent = onEvent)
    if (status is AppUpdateStatus.Offered) {
        SettingsActionRow(
            label = stringResource(R.string.settings_app_updates_download),
            enabled = !playManaged,
            onClick = { onEvent(EntryUiEvent.DownloadUpdateClicked) },
        )
    }
    SettingsActionRow(
        label = stringResource(R.string.settings_app_updates_check),
        enabled = !playManaged,
        onClick = { onEvent(EntryUiEvent.CheckForUpdatesClicked) },
    )
}

@Composable
private fun SettingsUpdateStatusBody(
    status: AppUpdateStatus,
    onEvent: (EntryUiEvent) -> Unit,
) {
    when (status) {
        AppUpdateStatus.Checking -> SettingsUpdateProgress()
        is AppUpdateStatus.Downloading ->
            SettingsDownloadProgress(
                progress = status.progress,
                onCancel = { onEvent(EntryUiEvent.CancelUpdateDownloadClicked) },
            )
        is AppUpdateStatus.Refused ->
            SettingsUpdateFailureBanner(
                message = settingsUpdateRefusal(status),
                onRetry = { onEvent(EntryUiEvent.CheckForUpdatesClicked) },
            )
        is AppUpdateStatus.DownloadRefused ->
            SettingsUpdateFailureBanner(
                message = settingsDownloadRefusal(status.reason),
                onRetry = { onEvent(EntryUiEvent.CheckForUpdatesClicked) },
            )
        is AppUpdateStatus.Failed ->
            SettingsUpdateFailureBanner(
                message = settingsUpdateFailure(status.reason),
                onRetry = { onEvent(EntryUiEvent.CheckForUpdatesClicked) },
            )
        AppUpdateStatus.Idle,
        is AppUpdateStatus.UpToDate,
        is AppUpdateStatus.Offered,
        is AppUpdateStatus.Verified,
        -> Unit
    }
}

@Composable
private fun settingsUpdateSummary(
    status: AppUpdateStatus,
    playManaged: Boolean,
): String =
    if (playManaged) {
        stringResource(R.string.settings_app_updates_play_managed)
    } else {
        when (status) {
            AppUpdateStatus.Idle -> stringResource(R.string.settings_app_updates_idle)
            AppUpdateStatus.Checking -> stringResource(R.string.settings_app_updates_checking)
            is AppUpdateStatus.UpToDate ->
                stringResource(R.string.settings_app_updates_up_to_date, status.checkedAt.settingsCheckedTime())
            is AppUpdateStatus.Offered ->
                stringResource(
                    R.string.settings_app_updates_offered,
                    status.offer.versionName,
                    status.offer.sourceCommit,
                )
            is AppUpdateStatus.Downloading -> stringResource(R.string.settings_app_updates_downloading_phase)
            is AppUpdateStatus.Verified ->
                stringResource(R.string.settings_app_updates_verified, status.verifiedAt.settingsCheckedTime())
            is AppUpdateStatus.Refused -> stringResource(R.string.settings_app_updates_refused_row)
            is AppUpdateStatus.DownloadRefused -> stringResource(R.string.settings_app_updates_download_refused_row)
            is AppUpdateStatus.Failed -> stringResource(R.string.settings_app_updates_failed_row)
        }
    }

@Composable
private fun SettingsUpdateProgress() {
    val tokens = LocalHermesDesignTokens.current
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = tokens.spacing.xs),
    ) {
        Text(
            text = stringResource(R.string.settings_app_updates_waiting),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LinearProgressIndicator(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = tokens.spacing.xs)
                    .height(tokens.spacing.s),
        )
    }
}

@Composable
private fun SettingsDownloadProgress(
    progress: NightlyDownloadProgress,
    onCancel: () -> Unit,
) {
    val tokens = LocalHermesDesignTokens.current
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = tokens.spacing.xs),
    ) {
        Text(
            text = stringResource(R.string.settings_app_updates_downloading, progress.percent()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LinearProgressIndicator(
            progress = { progress.fraction() },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = tokens.spacing.xs)
                    .height(tokens.spacing.s),
        )
        Text(
            text = stringResource(R.string.settings_app_updates_cancel),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier =
                Modifier
                    .align(Alignment.End)
                    .heightIn(min = tokens.spacing.xxxl)
                    .clickable(role = Role.Button, onClick = onCancel)
                    .padding(horizontal = tokens.spacing.s, vertical = tokens.spacing.xs),
        )
    }
}

@Composable
private fun SettingsUpdateFailureBanner(
    message: String,
    onRetry: () -> Unit,
) {
    val tokens = LocalHermesDesignTokens.current
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(tokens.shapes.small)
                .background(tokens.colorScheme.errorContainer)
                .padding(horizontal = tokens.spacing.m, vertical = tokens.spacing.s),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = tokens.colorScheme.onErrorContainer,
        )
        Text(
            text = stringResource(R.string.settings_app_updates_retry),
            style = MaterialTheme.typography.labelMedium,
            color = tokens.colorScheme.onErrorContainer,
            modifier =
                Modifier
                    .align(Alignment.End)
                    .heightIn(min = tokens.spacing.xxxl)
                    .clickable(role = Role.Button, onClick = onRetry)
                    .padding(horizontal = tokens.spacing.s, vertical = tokens.spacing.xs),
        )
    }
}

private fun NightlyDownloadProgress.fraction(): Float =
    if (totalBytes <= 0L) {
        0f
    } else {
        (bytesDownloaded.toFloat() / totalBytes).coerceIn(0f, 1f)
    }

private fun NightlyDownloadProgress.percent(): Int = (fraction() * PERCENT_FULL).toInt()

private fun Instant.settingsCheckedTime(): String = SETTINGS_CHECKED_TIME.format(atZone(ZoneId.systemDefault()))

private fun Instant.settingsPublishedTime(): String = SETTINGS_PUBLISHED_TIME.format(atZone(ZoneOffset.UTC))

private const val PERCENT_FULL = 100f

private val SETTINGS_CHECKED_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())

private val SETTINGS_PUBLISHED_TIME: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'", Locale.ROOT)
