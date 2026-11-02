package org.mercurio.client.feature.entry.presentation

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.em

@Composable
internal fun SectionHeaderText(
    label: String,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalHermesDesignTokens.current
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.16.em),
        color = tokens.colorScheme.onSurfaceVariant,
        maxLines = 1,
        modifier = modifier,
    )
}
