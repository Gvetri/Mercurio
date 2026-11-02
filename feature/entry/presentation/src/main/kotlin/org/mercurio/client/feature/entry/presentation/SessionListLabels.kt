package org.mercurio.client.feature.entry.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/** The Session title the list renders: Gateway data, or the pending-title placeholder. */
@Composable
internal fun sessionTitle(item: SessionItemUiState): String =
    if (item.isPendingTitle) stringResource(R.string.sessions_pending_title) else item.title

/** The compact last-active value the time column renders. */
@Composable
internal fun relativeLastActiveLabel(value: RelativeLastActive): String =
    when (value) {
        RelativeLastActive.Unknown -> stringResource(R.string.sessions_unknown_last_active)
        RelativeLastActive.Now -> stringResource(R.string.sessions_last_active_now)
        is RelativeLastActive.Ago -> stringResource(value.unit.labelRes(), value.amount)
    }

private fun RelativeLastActiveUnit.labelRes(): Int =
    when (this) {
        RelativeLastActiveUnit.MINUTES -> R.string.sessions_last_active_minutes
        RelativeLastActiveUnit.HOURS -> R.string.sessions_last_active_hours
        RelativeLastActiveUnit.DAYS -> R.string.sessions_last_active_days
        RelativeLastActiveUnit.WEEKS -> R.string.sessions_last_active_weeks
        RelativeLastActiveUnit.MONTHS -> R.string.sessions_last_active_months
        RelativeLastActiveUnit.YEARS -> R.string.sessions_last_active_years
    }
