package org.mercurio.client.feature.entry.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.mercurio.client.feature.entry.domain.RunRecoveryEntry
import org.mercurio.client.feature.entry.domain.RunRecoveryRegistry

/**
 * Shares endpoint-scoped registries so every holder uses the same storage transactions.
 *
 * The registry map serializes through one per-instance [Mutex]. The fallback entries are shared by
 * every registry, so they serialize through their own process-wide [Mutex]; two registries cannot
 * mutate the same fallback set concurrently. Each registry's own read-modify-write serializes
 * through the shared storage-transaction [Mutex] that every registry instance takes. Loads and
 * removals of a registry delegate to those same methods, so no path takes a transaction lock
 * twice.
 *
 * A load reads the fallback entries before the durable ones. A successful save writes durable
 * first and then drops the fallback copy; with that order a load that races a successful save
 * reports the entry from at least one side, so it is never observed as missing.
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
            FallbackRecoveryEntries.remove(endpoint, entry)
        } catch (error: CancellationException) {
            throw error
        } catch (expectedError: Exception) {
            FallbackRecoveryEntries.add(endpoint, entry)
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
        FallbackRecoveryEntries.remove(endpoint, entry)
    }

    suspend fun clearForEndpoint(endpoint: String?) {
        registry(endpoint).clear()
        FallbackRecoveryEntries.clear(endpoint)
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

    private suspend fun loadForEndpoint(endpoint: String?): List<RunRecoveryEntry> {
        val fallback = FallbackRecoveryEntries.snapshot(endpoint)
        val durable = RunRecoveryStorageTransactions.mutex.withLock { registry(endpoint).load() }
        return (durable + fallback)
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
    private val mutex = Mutex()
    private val pending = mutableMapOf<String?, MutableSet<RunRecoveryEntry>>()

    suspend fun snapshot(endpoint: String?): List<RunRecoveryEntry> = mutex.withLock { forEndpoint(endpoint).toList() }

    suspend fun add(
        endpoint: String?,
        entry: RunRecoveryEntry,
    ) {
        mutex.withLock {
            forEndpoint(endpoint).add(entry)
        }
    }

    suspend fun remove(
        endpoint: String?,
        entry: RunRecoveryEntry,
    ) {
        mutex.withLock {
            forEndpoint(endpoint).remove(entry)
            if (pending[endpoint].isNullOrEmpty()) pending.remove(endpoint)
        }
    }

    suspend fun clear(endpoint: String?) {
        mutex.withLock {
            pending.remove(endpoint)
        }
    }

    private fun forEndpoint(endpoint: String?): MutableSet<RunRecoveryEntry> =
        pending.getOrPut(endpoint) {
            mutableSetOf()
        }
}
