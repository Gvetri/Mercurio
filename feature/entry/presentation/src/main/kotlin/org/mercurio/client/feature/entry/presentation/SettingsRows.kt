package org.mercurio.client.feature.entry.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun SettingsSectionHeader(label: String) {
    val tokens = LocalHermesDesignTokens.current
    SectionHeaderText(
        label = label,
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = SETTINGS_SECTION_HEADER_HEIGHT)
                .padding(top = tokens.spacing.s)
                .semantics { heading() },
    )
}

@Composable
internal fun SettingsValueRow(
    label: String,
    value: String,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = SETTINGS_ROW_HEIGHT)
                .padding(vertical = LocalHermesDesignTokens.current.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun SettingsActionRow(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val tokens = LocalHermesDesignTokens.current
    val contentColor =
        if (enabled) {
            tokens.colorScheme.primary
        } else {
            tokens.colorScheme.onSurface.copy(alpha = SETTINGS_DISABLED_CONTENT_ALPHA)
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = SETTINGS_ACTION_ROW_HEIGHT)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(horizontal = tokens.spacing.m, vertical = tokens.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = contentColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun SettingsSwitchRow(
    model: SettingsSwitchRowModel,
    onCheckedChange: (Boolean) -> Unit,
) {
    val tokens = LocalHermesDesignTokens.current
    val switchDescription = model.description
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = SETTINGS_ROW_HEIGHT)
                .padding(vertical = tokens.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = tokens.spacing.s)) {
            Text(text = model.label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = model.supporting,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            model.errorText?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        Switch(
            checked = model.checked,
            onCheckedChange = onCheckedChange,
            modifier =
                Modifier
                    .semantics { this.contentDescription = switchDescription },
        )
    }
}

internal data class SettingsSwitchRowModel(
    val label: String,
    val supporting: String,
    val checked: Boolean,
    val description: String,
    val errorText: String? = null,
)

private val SETTINGS_SECTION_HEADER_HEIGHT = 28.dp

private val SETTINGS_ROW_HEIGHT = 56.dp

private val SETTINGS_ACTION_ROW_HEIGHT = 48.dp

private const val SETTINGS_DISABLED_CONTENT_ALPHA = 0.38f
