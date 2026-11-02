package org.mercurio.client.feature.entry.presentation

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import org.mercurio.client.feature.entry.domain.RunId

@Composable
internal fun RunIdDisclosure(runId: RunId) {
    var showRunId by remember { mutableStateOf(false) }
    if (showRunId) {
        Text(
            text = stringResource(R.string.run_id_label, runId.value),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    TextButton(onClick = { showRunId = !showRunId }) {
        Text(text = stringResource(if (showRunId) R.string.run_id_hide else R.string.run_id_show))
    }
}
