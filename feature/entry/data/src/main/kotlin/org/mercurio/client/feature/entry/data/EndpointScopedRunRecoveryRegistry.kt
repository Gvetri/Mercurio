package org.mercurio.client.feature.entry.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.mercurio.client.feature.entry.domain.RunRecoveryEntry
import org.mercurio.client.feature.entry.domain.RunRecoveryRegistry

/**
 * Shares endpoint-scoped registries so every holder uses the same storage transactions.
 *
 * The registry map and the fallback entries serialize through one [Mutex]; each registry's own
 * read-modify-write serializes through the shared storage-transaction [Mutex] that every registry
 * instance takes. Loads and removals of a registry delegate to those same methods, so no path
 * takes a transaction lock twice.
 */
class EndpointScopedRunRecoveryRegistry(
    private val endpointProvider: () -> String?,
    private val storageForEndpoint: (String?) -> RunRecoveryStorage,
) : RunRecoveryRegistry {
    private val registries = mutableMapOf<String?, DefaultRunRecoveryRegistry>()
    private val endpointViews = mutableMapOf<String, RunRecoveryRegistry>()
    private val registryMutex = Mutex()

    override suspend fun load(): List<RunRecoveryEntry> = loadForEndpoint(endpointProvider())

    override suspend fun save(entry: RunRecoveryEntry) {
        saveForEndpoint(endpointProvider(), entry)
    }

    suspend fun saveForEndpoint(
        endpoint: String?,
        entry: RunRecoveryEntry,
    ) {
        try {
            registry(endpoint).save(entry)
            registryMutex.withLock {
                FallbackRecoveryEntries.remove(endpoint, entry)
            }
        } catch (expectedError: Exception) {
            registryMutex.withLock {
                FallbackRecoveryEntries.forEndpoint(endpoint).add(entry)
            }
            throw expectedError
        }
    }

    override suspend fun remove(entry: RunRecoveryEntry) {
        removeForEndpoint(endpointProvider(), entry)
    }

    suspend fun removeForEndpoint(
        endpoint: String?,
        entry: RunRecoveryEntry,
    ) {
        registry(endpoint).remove(entry)
        registryMutex.withLock {
            FallbackRecoveryEntries.remove(endpoint, entry)
        }
    }

    suspend fun clearForEndpoint(endpoint: String?) {
        registry(endpoint).clear()
        registryMutex.withLock {
            FallbackRecoveryEntries.clear(endpoint)
        }
    }

    suspend fun registryForEndpoint(endpoint: String): RunRecoveryRegistry =
        registryMutex.withLock {
            endpointViews.getOrPut(endpoint) {
                object : RunRecoveryRegistry {
                    override suspend fun load(): List<RunRecoveryEntry> = loadForEndpoint(endpoint)

                    override suspend fun save(entry: RunRecoveryEntry) {
                        saveForEndpoint(endpoint, entry)
                    }

                    override suspend fun remove(entry: RunRecoveryEntry) {
                        removeForEndpoint(endpoint, entry)
                    }
                }
            }
        }

    private suspend fun loadForEndpoint(endpoint: String?): List<RunRecoveryEntry> =
        RunRecoveryStorageTransactions.mutex.withLock {
            val durable = registry(endpoint).load()
            val fallback =
                registryMutex.withLock {
                    FallbackRecoveryEntries.forEndpoint(endpoint).toList()
                }
            (durable + fallback)
                .distinct()
                .sortedWith(compareBy({ it.sessionId.value }, { it.runId.value }))
        }

    private suspend fun registry(endpoint: String?): DefaultRunRecoveryRegistry =
        registryMutex.withLock {
            registries.getOrPut(endpoint) {
                DefaultRunRecoveryRegistry(storageForEndpoint(endpoint))
            }
        }
}

private object FallbackRecoveryEntries {
    private val pending = mutableMapOf<String?, MutableSet<RunRecoveryEntry>>()

    fun forEndpoint(endpoint: String?): MutableSet<RunRecoveryEntry> = pending.getOrPut(endpoint) { mutableSetOf() }

    fun remove(
        endpoint: String?,
        entry: RunRecoveryEntry,
    ) {
        forEndpoint(endpoint).remove(entry)
        if (pending[endpoint].isNullOrEmpty()) pending.remove(endpoint)
    }

    fun clear(endpoint: String?) {
        pending.remove(endpoint)
    }
}
