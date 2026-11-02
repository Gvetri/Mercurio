package org.mercurio.client.feature.entry.domain

interface RunRecoveryRegistry {
    fun load(): List<RunRecoveryEntry>

    fun save(entry: RunRecoveryEntry)

    fun remove(entry: RunRecoveryEntry)
}
