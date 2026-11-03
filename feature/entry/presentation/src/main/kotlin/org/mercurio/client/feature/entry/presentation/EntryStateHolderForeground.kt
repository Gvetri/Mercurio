package org.mercurio.client.feature.entry.presentation

/**
 * Handles the first visible moment after the app was hidden: the Gateway is asked again for what
 * the user sees, so content that went stale while the app was stopped is replaced without a tap.
 * A foreground event that arrives while the holder already counts as foreground does nothing. The
 * first event on a fresh holder only marks it foreground, because no Gateway connection exists yet.
 */
internal fun EntryStateHolder.applyAppLifecycleEvent(event: EntryUiEvent.AppLifecycle) {
    when (event) {
        is EntryUiEvent.AppEnteredForeground -> enterForeground()
        is EntryUiEvent.AppEnteredBackground -> leaveForeground()
    }
}

internal fun EntryStateHolder.enterForeground() {
    if (isForeground) return
    isForeground = true
    refreshSessions()
}

internal fun EntryStateHolder.leaveForeground() {
    isForeground = false
}
