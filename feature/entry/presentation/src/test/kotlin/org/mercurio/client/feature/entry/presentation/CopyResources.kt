package org.mercurio.client.feature.entry.presentation

import org.robolectric.RuntimeEnvironment

internal fun copy(resId: Int): String = RuntimeEnvironment.getApplication().getString(resId)

internal fun pluralCopy(
    resId: Int,
    count: Int,
): String = RuntimeEnvironment.getApplication().resources.getQuantityString(resId, count, count)

internal fun relativeLastActive(value: String): RelativeLastActive =
    when {
        value == "—" -> RelativeLastActive.Unknown
        value == "now" -> RelativeLastActive.Now
        value.endsWith("mo") -> RelativeLastActive.Ago(value.removeSuffix("mo").toLong(), RelativeLastActiveUnit.MONTHS)
        value.endsWith("m") -> RelativeLastActive.Ago(value.removeSuffix("m").toLong(), RelativeLastActiveUnit.MINUTES)
        value.endsWith("h") -> RelativeLastActive.Ago(value.removeSuffix("h").toLong(), RelativeLastActiveUnit.HOURS)
        value.endsWith("d") -> RelativeLastActive.Ago(value.removeSuffix("d").toLong(), RelativeLastActiveUnit.DAYS)
        value.endsWith("w") -> RelativeLastActive.Ago(value.removeSuffix("w").toLong(), RelativeLastActiveUnit.WEEKS)
        else -> RelativeLastActive.Ago(value.removeSuffix("y").toLong(), RelativeLastActiveUnit.YEARS)
    }
