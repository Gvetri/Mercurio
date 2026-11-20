package org.mercurio.client.feature.entry.presentation

/**
 * The app-lock gate: the persisted switch, whether this device can prove the user's identity at
 * all, and whether the current foreground visit still waits for that proof.
 */
data class AppLockUiState(
    val isEnabled: Boolean = false,
    val isAvailable: Boolean = false,
    val isLocked: Boolean = false,
) {
    val isUnlockRequired: Boolean
        get() = isEnabled && isLocked
}
