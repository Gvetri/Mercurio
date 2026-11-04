package org.mercurio.client.feature.entry.presentation

import kotlinx.coroutines.flow.update

internal fun EntryStateHolder.restoredAppLockState(): AppLockUiState {
    val enrolled = appLock?.enrolment?.isEnrolled() == true
    val storedOn = appLock?.settingsStore?.loadEnabled() == true
    return appLockState(enrolled = enrolled, storedOn = storedOn, locked = storedOn)
}

internal fun EntryStateHolder.dispatchAppLockEvent(event: EntryUiEvent.AppLock) {
    when (event) {
        is EntryUiEvent.AppLockToggleClicked -> toggleAppLock()
        is EntryUiEvent.AppUnlockRequested -> requestAppUnlock()
        is EntryUiEvent.AppLockAuthenticationResult -> applyAppLockAuthentication(event.authenticated)
    }
}

internal fun EntryStateHolder.toggleAppLock() {
    val lock = mutableUiState.value.appLock
    if (!lock.isAvailable) return
    val enabled = !lock.isEnabled
    appLock?.settingsStore?.saveEnabled(enabled)
    mutableUiState.update { state ->
        state.copy(appLock = state.appLock.copy(isEnabled = enabled, isLocked = false))
    }
}

internal fun EntryStateHolder.requestAppUnlock() {
    if (!mutableUiState.value.appLock.isUnlockRequired) return
    isUnlockPromptPending = true
    appLock?.requestUnlock?.invoke()
}

internal fun EntryStateHolder.applyAppLockAuthentication(authenticated: Boolean) {
    isUnlockPromptPending = false
    if (!authenticated) return
    mutableUiState.update { state ->
        state.copy(appLock = state.appLock.copy(isLocked = false))
    }
}

internal fun EntryStateHolder.lockAppOnBackground() {
    mutableUiState.update { state ->
        if (state.appLock.isEnabled) state.copy(appLock = state.appLock.copy(isLocked = true)) else state
    }
}

internal fun EntryStateHolder.refreshAppLockAvailability() {
    val enrolled = appLock?.enrolment?.isEnrolled() == true
    mutableUiState.update { state ->
        state.copy(
            appLock =
                appLockState(
                    enrolled = enrolled,
                    storedOn = state.appLock.isEnabled,
                    locked = state.appLock.isLocked,
                ),
        )
    }
}

private fun EntryStateHolder.appLockState(
    enrolled: Boolean,
    storedOn: Boolean,
    locked: Boolean,
): AppLockUiState {
    val enabled = storedOn && enrolled
    if (storedOn && !enabled) appLock?.settingsStore?.saveEnabled(false)
    return AppLockUiState(isEnabled = enabled, isAvailable = enrolled, isLocked = enabled && locked)
}
