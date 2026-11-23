package org.mercurio.client.feature.entry.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.mercurio.client.feature.entry.domain.RunRecoveryEntry
import org.mercurio.client.feature.entry.domain.RunRecoveryRegistry
import org.mercurio.client.feature.entry.domain.isValid

class InMemoryRunRecoveryRegistry(
    initialEntries: Iterable<RunRecoveryEntry> = emptyList(),
) : RunRecoveryRegistry {
    private val entries =
        linkedSetOf<RunRecoveryEntry>().apply {
            addAll(initialEntries.filter(RunRecoveryEntry::isValid))
        }
    private val mutex = Mutex()

    override suspend fun load(): List<RunRecoveryEntry> = mutex.withLock { entries.toList() }

    override suspend fun save(entry: RunRecoveryEntry) {
        mutex.withLock {
            if (entry.isValid()) entries += entry
        }
    }

    override suspend fun remove(entry: RunRecoveryEntry) {
        mutex.withLock {
            entries -= entry
        }
    }
}
