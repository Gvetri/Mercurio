package org.mercurio.client.feature.entry.presentation

import java.time.Duration
import java.time.Instant

sealed interface RelativeLastActive {
    data object Unknown : RelativeLastActive

    data object Now : RelativeLastActive

    data class Ago(
        val amount: Long,
        val unit: RelativeLastActiveUnit,
    ) : RelativeLastActive
}

/**
 * Names the Gateway's last-active instant as the compact relative value the
 * Session list time column renders. A missing value keeps the unknown value.
 */
internal fun relativeLastActive(
    lastActive: Instant?,
    now: Instant,
): RelativeLastActive {
    if (lastActive == null) return RelativeLastActive.Unknown
    val seconds = Duration.between(lastActive, now).seconds
    return when {
        seconds < MINUTE_SECONDS -> RelativeLastActive.Now
        seconds < HOUR_SECONDS -> RelativeLastActive.Ago(seconds / MINUTE_SECONDS, RelativeLastActiveUnit.MINUTES)
        seconds < DAY_SECONDS -> RelativeLastActive.Ago(seconds / HOUR_SECONDS, RelativeLastActiveUnit.HOURS)
        seconds < WEEK_SECONDS -> RelativeLastActive.Ago(seconds / DAY_SECONDS, RelativeLastActiveUnit.DAYS)
        seconds < MONTH_SECONDS -> RelativeLastActive.Ago(seconds / WEEK_SECONDS, RelativeLastActiveUnit.WEEKS)
        seconds < YEAR_SECONDS ->
            RelativeLastActive.Ago(seconds / MONTH_SECONDS, RelativeLastActiveUnit.MONTHS)
        else -> RelativeLastActive.Ago(seconds / YEAR_SECONDS, RelativeLastActiveUnit.YEARS)
    }
}

private const val MINUTE_SECONDS = 60L
private const val HOUR_SECONDS = 3_600L
private const val DAY_SECONDS = 86_400L
private const val WEEK_SECONDS = 604_800L
private const val MONTH_SECONDS = 2_592_000L
private const val YEAR_SECONDS = 31_536_000L
