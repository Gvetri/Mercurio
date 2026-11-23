package org.mercurio.client.feature.entry.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.mercurio.client.feature.entry.domain.GatewayHistoryMessage
import org.mercurio.client.feature.entry.domain.HISTORY_CACHE_MAX_AGE_DAYS
import org.mercurio.client.feature.entry.domain.HISTORY_CACHE_MAX_MESSAGES
import org.mercurio.client.feature.entry.domain.HistoryCacheKey
import org.mercurio.client.feature.entry.domain.HistoryCacheStore
import org.mercurio.client.feature.entry.domain.Session
import java.time.Duration
import java.time.Instant

/**
 * The bounded, encrypted device-local history cache.
 *
 * Records are kept under a per-endpoint namespace so one endpoint's content is never
 * readable as another's. The store holds at most [HISTORY_CACHE_MAX_MESSAGES] cached
 * messages and never returns content cached more than [HISTORY_CACHE_MAX_AGE_DAYS]
 * days ago: expired records drop on every read and leave the persisted blob on the
 * next write, and count overflow evicts the oldest cached messages first,
 * deterministically. Cached Session lists are replaced wholesale per endpoint and
 * outside the message count. Corrupted or foreign content reads as empty; a later
 * write replaces it. The bounds are fixed by the domain port and are not configurable
 * here. The supplied [endpointNamespace] must be fixed-length and injective: the
 * prefix-based [clearEndpoint] purge is collision-safe only under that contract, and
 * the wiring SHA-256 hex namespace satisfies it.
 *
 * Mutations serialize through one [Mutex] across a suspend boundary because each one rewrites the
 * whole record set. Reads take no lock: the storage replaces one immutable blob atomically, so a
 * concurrent read observes either the previous record or the next complete one.
 */
class BoundedHistoryCacheStore(
    private val storage: HistoryCacheStorage,
    private val cipher: AesGcmHistoryCacheCipher,
    private val endpointNamespace: (String) -> String,
    private val clock: () -> Instant = Instant::now,
) : HistoryCacheStore {
    private val writeMutex = Mutex()
    private val recordKeys = HistoryCacheRecordKeys(endpointNamespace)

    override fun read(key: HistoryCacheKey): List<GatewayHistoryMessage> {
        val sessionKey = recordKeys.transcript(key)
        return load().filter { it.key == sessionKey }.mapNotNull(CachedHistoryRecord::message)
    }

    override suspend fun write(
        key: HistoryCacheKey,
        messages: List<GatewayHistoryMessage>,
    ) {
        writeMutex.withLock {
            val sessionKey = recordKeys.transcript(key)
            val cachedAt = clock()
            val retained =
                load().filterNot { it.key == sessionKey } +
                    messages.map { message -> CachedHistoryRecord(sessionKey, message, cachedAt) }
            persist(trimToMessageBound(retained))
        }
    }

    override fun readSessionList(endpoint: String): List<Session> =
        load().firstOrNull { it.key == recordKeys.sessionList(endpoint) }?.sessionList.orEmpty()

    override suspend fun writeSessionList(
        endpoint: String,
        sessions: List<Session>,
    ) {
        writeMutex.withLock {
            val listKey = recordKeys.sessionList(endpoint)
            val retained = load().filterNot { it.key == listKey }
            val withList =
                if (sessions.isEmpty()) {
                    retained
                } else {
                    retained + CachedHistoryRecord(listKey, cachedAt = clock(), sessionList = sessions)
                }
            persist(trimToMessageBound(withList))
        }
    }

    override suspend fun clearEndpoint(endpoint: String) {
        writeMutex.withLock {
            val prefix = "${endpointNamespace(endpoint)}."
            persist(load().filterNot { it.key.startsWith(prefix) })
        }
    }

    override suspend fun clear() {
        writeMutex.withLock {
            storage.clear()
        }
    }

    private fun load(): List<CachedHistoryRecord> {
        val bytes = storage.read() ?: return emptyList()
        val records =
            runCatching { HistoryCacheRecords.decode(cipher.decrypt(EncryptedHistoryCacheRecord.decode(bytes))) }
                .getOrDefault(emptyList())
        val expiresBefore = clock().minus(MAX_CACHED_AGE)
        return records.filter { it.cachedAt.isAfter(expiresBefore) || it.cachedAt == expiresBefore }
    }

    private fun persist(records: List<CachedHistoryRecord>) {
        val plaintext = HistoryCacheRecords.encode(records)
        try {
            storage.write(cipher.encrypt(plaintext).encode())
        } finally {
            plaintext.fill(0)
        }
    }

    private fun trimToMessageBound(records: List<CachedHistoryRecord>): List<CachedHistoryRecord> {
        var evictions = records.count { it.message != null } - HISTORY_CACHE_MAX_MESSAGES
        if (evictions <= 0) return records
        return records.filter { record ->
            if (record.message != null && evictions > 0) {
                evictions -= 1
                false
            } else {
                true
            }
        }
    }

    private companion object {
        val MAX_CACHED_AGE: Duration = Duration.ofDays(HISTORY_CACHE_MAX_AGE_DAYS.toLong())
    }
}
