package org.mercurio.client.feature.entry.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role

@Composable
internal fun SettingsUpdatePermissionBanner(
    message: String,
    onOpenSettings: () -> Unit,
) {
    val tokens = LocalHermesDesignTokens.current
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(tokens.shapes.small)
                .background(tokens.colorScheme.surfaceContainerHigh)
                .padding(horizontal = tokens.spacing.m, vertical = tokens.spacing.s),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = tokens.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.settings_app_updates_open_settings),
            style = MaterialTheme.typography.labelMedium,
            color = tokens.colorScheme.primary,
            modifier =
                Modifier
                    .align(Alignment.End)
                    .heightIn(min = tokens.spacing.xxxl)
                    .clickable(role = Role.Button, onClick = onOpenSettings)
                    .padding(horizontal = tokens.spacing.s, vertical = tokens.spacing.xs),
        )
    }
}
