package org.mercurio.client.feature.entry.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

@Composable
internal fun AppLockContent(
    onEvent: (EntryUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    ChatEmptyState(
        title = stringResource(R.string.app_lock_title),
        body = stringResource(R.string.app_lock_body),
        modifier = modifier,
        actionLabel = stringResource(R.string.app_lock_action),
        onAction = { onEvent(EntryUiEvent.AppUnlockRequested) },
    )
}
