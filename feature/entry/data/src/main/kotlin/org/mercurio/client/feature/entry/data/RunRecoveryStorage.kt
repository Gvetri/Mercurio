package org.mercurio.client.feature.entry.data

import org.mercurio.client.feature.entry.domain.RunRecoveryEntry

interface RunRecoveryStorage {
    fun load(): Set<RunRecoveryEntry>

    fun save(entries: Set<RunRecoveryEntry>)

    fun clear() {
        save(emptySet())
    }
}

internal object RunRecoveryStorageTransactions {
    val lock = Any()
}
