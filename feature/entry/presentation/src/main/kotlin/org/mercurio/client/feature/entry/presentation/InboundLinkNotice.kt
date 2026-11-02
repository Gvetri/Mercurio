package org.mercurio.client.feature.entry.presentation

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics

@Composable
internal fun InboundLinkNoticeText(
    notice: InboundLinkNoticeCategory,
    modifier: Modifier = Modifier,
) {
    Text(
        text = stringResource(notice.messageRes),
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium,
        modifier =
            modifier.fillMaxWidth().semantics {
                liveRegion = LiveRegionMode.Assertive
            },
    )
}
