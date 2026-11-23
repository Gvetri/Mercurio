package org.mercurio.client.feature.entry.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.mercurio.client.feature.entry.domain.RunRecoveryEntry
import org.mercurio.client.feature.entry.domain.RunRecoveryRegistry
import org.mercurio.client.feature.entry.domain.isValid

/**
 * The Run recovery registry over one storage record set.
 *
 * Mutations serialize through one [Mutex] across a suspend boundary, because each one rewrites the
 * whole entry set. [load] takes no lock: the storage commits one immutable set atomically, so a
 * reader observes either the previous set or the next complete one.
 */
class DefaultRunRecoveryRegistry(
    private val storage: RunRecoveryStorage,
) : RunRecoveryRegistry {
    private val writeMutex = RunRecoveryStorageTransactions.mutex

    override suspend fun load(): List<RunRecoveryEntry> =
        storage
            .load()
            .filter(RunRecoveryEntry::isValid)
            .distinct()
            .sortedWith(compareBy({ it.sessionId.value }, { it.runId.value }))

    override suspend fun save(entry: RunRecoveryEntry) {
        if (!entry.isValid()) return
        updateEntries { it + entry }
    }

    override suspend fun remove(entry: RunRecoveryEntry) {
        updateEntries { it - entry }
    }

    suspend fun clear() {
        writeMutex.withLock {
            storage.clear()
        }
    }

    private suspend fun updateEntries(transform: (Set<RunRecoveryEntry>) -> Set<RunRecoveryEntry>) {
        writeMutex.withLock {
            storage.save(transform(storage.load().filter(RunRecoveryEntry::isValid).toSet()))
        }
    }
}
