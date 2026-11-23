package org.mercurio.client.feature.entry.domain

/**
 * Identifier-only recovery metadata for Runs that were started locally.
 *
 * Calls suspend. Mutations serialize through one mutex, because each one is a read-modify-write of
 * the whole entry set; the load reads one atomically committed record set but shares the store's
 * registry views, so it suspends on the same boundary.
 */
interface RunRecoveryRegistry {
    suspend fun load(): List<RunRecoveryEntry>

    suspend fun save(entry: RunRecoveryEntry)

    suspend fun remove(entry: RunRecoveryEntry)
}
