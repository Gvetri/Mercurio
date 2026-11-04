package org.mercurio.client.feature.entry.wiring

import android.content.Context
import org.mercurio.client.feature.entry.presentation.AppLockPorts

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
