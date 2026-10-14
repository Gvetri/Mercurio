package org.hermesnative.client.feature.entry.presentation

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.hermesnative.client.feature.entry.domain.RunId

@Composable
internal fun RunIdDisclosure(runId: RunId) {
    var showRunId by remember { mutableStateOf(false) }
    if (showRunId) {
        Text(
            text = "Run ID: ${runId.value}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    TextButton(onClick = { showRunId = !showRunId }) {
        Text(text = if (showRunId) "Hide run ID" else "Show run ID")
    }
}
