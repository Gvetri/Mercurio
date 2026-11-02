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
            SettingsConnectionSection(state = state, onEvent = onEvent)
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
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = SETTINGS_TOP_BAR_HEIGHT)
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
                                .widthIn(min = SETTINGS_OVERFLOW_SIZE)
                                .heightIn(min = SETTINGS_OVERFLOW_SIZE)
                                .clip(tokens.shapes.small)
                                .clickable { menuOpen = true }
                                .semantics { contentDescription = actionsDescription },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = SETTINGS_OVERFLOW_MARK, style = MaterialTheme.typography.labelMedium)
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
}

@Composable
private fun SettingsConnectionSection(
    state: EntryUiState,
    onEvent: (EntryUiEvent) -> Unit,
) {
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
    if (!state.isGatewayConnectionConfigured && !state.isConnected) {
        HorizontalDivider()
        SettingsActionRow(
            label = stringResource(R.string.settings_action_add_connection),
            onClick = { onEvent(EntryUiEvent.AddGatewayConnectionClicked) },
        )
    }
}

@Composable
private fun SettingsNotificationSection(
    state: EntryUiState,
    onEvent: (EntryUiEvent) -> Unit,
) {
    val notifications = state.runStatusNotifications
    SettingsSectionHeader(stringResource(R.string.settings_section_notifications))
    SettingsSwitchRow(
        supporting =
            stringResource(
                if (notifications.enabled) {
                    R.string.settings_notifications_supporting_on
                } else {
                    R.string.settings_notifications_supporting_off
                },
            ),
        errorText = notifications.explanation?.let { stringResource(it.messageRes) },
        checked = notifications.enabled,
        onCheckedChange = { onEvent(EntryUiEvent.RunStatusNotificationsToggleClicked) },
        contentDescription = stringResource(R.string.settings_run_status_notifications),
    )
}

@Composable
private fun SettingsMediaSection(
    state: EntryUiState,
    onEvent: (EntryUiEvent) -> Unit,
) {
    SettingsSectionHeader(stringResource(R.string.settings_section_media))
    SettingsSwitchRow(
        supporting =
            stringResource(
                if (state.autoLoadImages) {
                    R.string.settings_media_auto_load_on
                } else {
                    R.string.settings_media_auto_load_off
                },
            ),
        checked = state.autoLoadImages,
        onCheckedChange = { onEvent(EntryUiEvent.AutoLoadImagesToggleClicked) },
        contentDescription = stringResource(R.string.settings_media_auto_load),
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
        testTag = SETTINGS_LOCAL_DIAGNOSTICS_TAG,
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
        testTag = SETTINGS_CLEAR_CACHE_TAG,
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
        value =
            if (state.buildNumber > 0L) {
                state.buildNumber.toString()
            } else {
                stringResource(R.string.settings_value_unknown)
            },
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

private val SETTINGS_TOP_BAR_HEIGHT = 48.dp

private val SETTINGS_OVERFLOW_SIZE = 48.dp

private const val SETTINGS_OVERFLOW_MARK = "::"

internal const val SETTINGS_LOCAL_DIAGNOSTICS_TAG = "settings-local-diagnostics"

internal const val SETTINGS_CLEAR_CACHE_TAG = "settings-clear-history-cache"
