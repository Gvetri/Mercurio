package org.hermesnative.client.feature.entry.domain

internal fun detektFixtureGuardedTotal(input: Int, other: Int): Int {
    if (input < 0) return 0
    if (other < 0) return 1
    return input + other
}
