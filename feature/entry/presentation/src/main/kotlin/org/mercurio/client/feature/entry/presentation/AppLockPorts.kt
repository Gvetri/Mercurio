package org.mercurio.client.feature.entry.presentation

import org.mercurio.client.feature.entry.domain.AppLockEnrolment
import org.mercurio.client.feature.entry.domain.AppLockSettingsStore

/**
 * The device-bound parts of the app-lock gate: its persisted switch, the device's enrolment, and
 * the request that shows the system prompt.
 */
data class AppLockPorts(
    val settingsStore: AppLockSettingsStore,
    val enrolment: AppLockEnrolment,
    val requestUnlock: () -> Unit,
)
