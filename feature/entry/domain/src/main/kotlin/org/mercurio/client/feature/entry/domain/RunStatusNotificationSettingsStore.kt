package org.mercurio.client.feature.entry.domain

/** Persisted user preference for Run status notifications. */
interface RunStatusNotificationSettingsStore {
    fun loadEnabled(): Boolean

    fun saveEnabled(enabled: Boolean)
}
