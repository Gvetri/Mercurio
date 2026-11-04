package org.mercurio.client.feature.entry.wiring

import android.content.Context
import androidx.biometric.BiometricManager
import org.mercurio.client.feature.entry.presentation.AppLockPorts

/**
 * The authenticators the app lock allows: a biometric, with the device credential as the fallback.
 * The enrolment check and the prompt read this one value, so the two cannot disagree.
 */
val appLockAuthenticators: Int =
    BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL

internal fun appLockPorts(
    context: Context,
    overrides: EntryWiringOverrides,
    requestUnlock: (() -> Unit)?,
): AppLockPorts =
    AppLockPorts(
        settingsStore = overrides.appLockSettingsStore ?: SharedPreferencesAppLockSettingsStore(context),
        enrolment = overrides.appLockEnrolment ?: AndroidAppLockEnrolment(context),
        requestUnlock = { requestUnlock?.invoke() },
    )
