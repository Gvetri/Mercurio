package org.mercurio.client.feature.entry.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import org.mercurio.client.feature.entry.domain.InstallSource

@Composable
internal fun SettingsContent(
    state: EntryUiState,
    onEvent: (EntryUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalHermesDesignTokens.current
    Column(
        modifier = modifier.fillMaxSize().padding(tokens.spacing.s),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.s),
    ) {
        SettingsTopBar(state = state, onEvent = onEvent)
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(tokens.shapes.large)
                    .background(tokens.colorScheme.surface)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = tokens.spacing.m, vertical = tokens.spacing.s),
        ) {
            SettingsConnectionSection(state = state)
            SettingsSecuritySection(state = state, onEvent = onEvent)
            SettingsNotificationSection(state = state, onEvent = onEvent)
            SettingsMediaSection(state = state, onEvent = onEvent)
            SettingsDiagnosticsSection(state = state, onEvent = onEvent)
            SettingsAboutSection(state = state)
        }
    }
}

@Composable
private fun SettingsTopBar(
    state: EntryUiState,
    onEvent: (EntryUiEvent) -> Unit,
) {
    val tokens = LocalHermesDesignTokens.current
    val actionsDescription = stringResource(R.string.settings_connection_actions)
    val overflowMark = stringResource(R.string.shell_connection_actions_mark)
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = tokens.spacing.xxxl)
                .clip(tokens.shapes.large)
                .background(tokens.colorScheme.surface)
                .border(1.dp, tokens.colorScheme.outlineVariant, tokens.shapes.large)
                .padding(start = tokens.spacing.m, end = tokens.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.titleSmall.copy(letterSpacing = 0.02.em),
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        if (state.isGatewayConnectionConfigured || state.isConnected) {
            Box {
                Box(
                    modifier =
                        Modifier
                            .widthIn(min = tokens.spacing.xxxl)
                            .heightIn(min = tokens.spacing.xxxl)
                            .clip(tokens.shapes.small)
                            .clickable(role = Role.Button) { menuOpen = true }
                            .semantics { contentDescription = actionsDescription },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = overflowMark, style = MaterialTheme.typography.labelMedium)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(text = stringResource(R.string.settings_action_change_credential)) },
                        onClick = {
                            menuOpen = false
                            onEvent(EntryUiEvent.ChangeGatewayCredentialClicked)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(text = stringResource(R.string.settings_action_remove_connection)) },
                        onClick = {
                            menuOpen = false
                            onEvent(EntryUiEvent.RemoveGatewayConnectionClicked)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsConnectionSection(state: EntryUiState) {
    SettingsSectionHeader(stringResource(R.string.settings_section_connection))
    SettingsValueRow(
        label = stringResource(R.string.settings_connection_row),
        value =
            when {
                state.isConnected -> stringResource(R.string.settings_connection_connected)
                state.isGatewayConnectionConfigured -> stringResource(R.string.settings_connection_saved)
                else -> stringResource(R.string.settings_connection_none)
            },
    )
}

@Composable
private fun SettingsSecuritySection(
    state: EntryUiState,
    onEvent: (EntryUiEvent) -> Unit,
) {
    val lock = state.appLock
    SettingsSectionHeader(stringResource(R.string.settings_section_security))
    SettingsSwitchRow(
        model =
            SettingsSwitchRowModel(
                label = stringResource(R.string.settings_app_lock_row),
                supporting =
                    stringResource(
                        if (lock.isAvailable) {
                            R.string.settings_app_lock_supporting
                        } else {
                            R.string.settings_app_lock_unavailable
                        },
                    ),
                checked = lock.isEnabled,
                description = stringResource(R.string.settings_app_lock_row),
                enabled = lock.isAvailable,
            ),
        onCheckedChange = { onEvent(EntryUiEvent.AppLockToggleClicked) },
    )
}

@Composable
private fun SettingsNotificationSection(
    state: EntryUiState,
    onEvent: (EntryUiEvent) -> Unit,
) {
    val notifications = state.runStatusNotifications
    SettingsSectionHeader(stringResource(R.string.settings_section_notifications))
    SettingsSwitchRow(
        model =
            SettingsSwitchRowModel(
                label = stringResource(R.string.settings_notifications_row),
                supporting =
                    stringResource(
                        if (notifications.enabled) {
                            R.string.settings_notifications_supporting_on
                        } else {
                            R.string.settings_notifications_supporting_off
                        },
                    ),
                checked = notifications.enabled,
                description = stringResource(R.string.settings_run_status_notifications_description),
                errorText = notifications.explanation?.let { stringResource(it.messageRes) },
            ),
        onCheckedChange = { onEvent(EntryUiEvent.RunStatusNotificationsToggleClicked) },
    )
}

@Composable
private fun SettingsMediaSection(
    state: EntryUiState,
    onEvent: (EntryUiEvent) -> Unit,
) {
    SettingsSectionHeader(stringResource(R.string.settings_section_media))
    SettingsSwitchRow(
        model =
            SettingsSwitchRowModel(
                label = stringResource(R.string.settings_media_auto_load),
                supporting =
                    stringResource(
                        if (state.autoLoadImages) {
                            R.string.settings_media_auto_load_on
                        } else {
                            R.string.settings_media_auto_load_off
                        },
                    ),
                checked = state.autoLoadImages,
                description = stringResource(R.string.settings_media_auto_load_description),
            ),
        onCheckedChange = { onEvent(EntryUiEvent.AutoLoadImagesToggleClicked) },
    )
}

@Composable
private fun SettingsDiagnosticsSection(
    state: EntryUiState,
    onEvent: (EntryUiEvent) -> Unit,
) {
    SettingsSectionHeader(stringResource(R.string.settings_section_diagnostics))
    HorizontalDivider()
    SettingsActionRow(
        label = stringResource(R.string.settings_local_diagnostics),
        enabled = state.sessionList?.hasPendingMutation != true,
        onClick = { onEvent(EntryUiEvent.OpenLocalDiagnosticsClicked) },
    )
    Text(
        text = stringResource(R.string.settings_clear_history_cache_summary),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(vertical = LocalHermesDesignTokens.current.spacing.xs),
    )
    HorizontalDivider()
    SettingsActionRow(
        label = stringResource(R.string.settings_clear_history_cache),
        onClick = { onEvent(EntryUiEvent.ClearHistoryCacheClicked) },
    )
}

@Composable
private fun SettingsAboutSection(state: EntryUiState) {
    SettingsSectionHeader(stringResource(R.string.settings_section_about))
    SettingsValueRow(
        label = stringResource(R.string.settings_about_version),
        value = state.clientVersion,
    )
    SettingsValueRow(
        label = stringResource(R.string.settings_about_build),
        value = state.buildNumber?.takeIf { it > 0L }?.toString() ?: stringResource(R.string.settings_value_unknown),
    )
    SettingsValueRow(
        label = stringResource(R.string.settings_about_install_source),
        value =
            stringResource(
                when (state.installSource) {
                    InstallSource.GOOGLE_PLAY -> R.string.settings_install_source_google_play
                    InstallSource.OTHER_SOURCE -> R.string.settings_install_source_other
                },
            ),
    )
}

@Composable
internal fun SettingsBackHandling(
    state: EntryUiState,
    onEvent: (EntryUiEvent) -> Unit,
) {
    when {
        state.isRemoveConfirmationOpen ->
            ShellBackHandler { onEvent(EntryUiEvent.CancelRemoveGatewayConnectionClicked) }
        state.isClearHistoryCacheConfirmationOpen ->
            ShellBackHandler { onEvent(EntryUiEvent.CancelClearHistoryCacheClicked) }
        state.isChangingCredential ->
            ShellBackHandler { onEvent(EntryUiEvent.CancelGatewayCredentialChangeClicked) }
        else -> ShellBackHandler { onEvent(EntryUiEvent.CloseSettingsClicked) }
    }
}
