package org.mercurio.client.feature.entry.domain

/**
 * Persisted preference that allows automatic picture loading in Messages.
 *
 * A fresh install loads pictures automatically, so a store that holds no saved
 * decision reports `true`.
 */
interface ImageLoadingSettingsStore {
    fun loadEnabled(): Boolean

    fun saveEnabled(enabled: Boolean)
}
