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
import org.mercurio.client.feature.entry.application.NightlyUpdateFailure
import org.mercurio.client.feature.entry.application.NightlyUpdateRefusal
import org.mercurio.client.feature.entry.domain.InstallSource
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
        label = stringResource(R.string.settings_app_updates_row),
        value = settingsUpdateSummary(status = status, playManaged = playManaged),
        detail =
            (status as? AppUpdateStatus.Offered)?.let { offered ->
                stringResource(
                    R.string.settings_app_updates_offered_detail,
                    offered.publishedAt.settingsPublishedTime(),
                    offered.releaseTag,
                )
            },
    )
    when (status) {
        AppUpdateStatus.Checking -> SettingsUpdateProgress()
        is AppUpdateStatus.Refused ->
            SettingsUpdateFailureBanner(
                message = settingsUpdateRefusal(status),
                onRetry = { onEvent(EntryUiEvent.CheckForUpdatesClicked) },
            )
        is AppUpdateStatus.Failed ->
            SettingsUpdateFailureBanner(
                message = settingsUpdateFailure(status.reason),
                onRetry = { onEvent(EntryUiEvent.CheckForUpdatesClicked) },
            )
        AppUpdateStatus.Idle, is AppUpdateStatus.UpToDate, is AppUpdateStatus.Offered -> Unit
    }
    SettingsActionRow(
        label = stringResource(R.string.settings_app_updates_check),
        enabled = !playManaged,
        onClick = { onEvent(EntryUiEvent.CheckForUpdatesClicked) },
    )
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
                stringResource(R.string.settings_app_updates_offered, status.versionName, status.sourceCommit)
            is AppUpdateStatus.Refused -> stringResource(R.string.settings_app_updates_refused_row)
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

@Composable
private fun settingsUpdateRefusal(status: AppUpdateStatus.Refused): String =
    when (status.reason) {
        NightlyUpdateRefusal.CONTRADICTORY_RELEASE ->
            status.releaseTag?.let { tag ->
                stringResource(R.string.settings_app_updates_refused_contradiction, tag)
            } ?: stringResource(R.string.settings_app_updates_refused_generic)
        NightlyUpdateRefusal.UNREADABLE_INSTALLED_BUILD ->
            stringResource(R.string.settings_app_updates_refused_unreadable_build)
    }

@Composable
private fun settingsUpdateFailure(reason: NightlyUpdateFailure): String =
    stringResource(
        when (reason) {
            NightlyUpdateFailure.OFFLINE -> R.string.settings_app_updates_offline
            NightlyUpdateFailure.RATE_LIMITED -> R.string.settings_app_updates_rate_limited
            NightlyUpdateFailure.SERVICE_UNAVAILABLE -> R.string.settings_app_updates_unavailable
        },
    )

private fun Instant.settingsCheckedTime(): String = SETTINGS_CHECKED_TIME.format(atZone(ZoneId.systemDefault()))

private fun Instant.settingsPublishedTime(): String = SETTINGS_PUBLISHED_TIME.format(atZone(ZoneOffset.UTC))

private val SETTINGS_CHECKED_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())

private val SETTINGS_PUBLISHED_TIME: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'", Locale.ROOT)
