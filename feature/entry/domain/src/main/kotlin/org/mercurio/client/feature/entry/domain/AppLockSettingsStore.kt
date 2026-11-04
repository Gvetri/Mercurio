package org.mercurio.client.feature.entry.domain

/**
 * Persisted preference that gates the app behind the device's own screen lock.
 *
 * A fresh install leaves the gate off, so a store that holds no saved decision reports `false`.
 */
interface AppLockSettingsStore {
    fun loadEnabled(): Boolean

    fun saveEnabled(enabled: Boolean)
}
