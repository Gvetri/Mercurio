package org.hermesnative.client.feature.entry.data

import org.hermesnative.client.feature.entry.domain.GatewayHistoryMessage
import org.hermesnative.client.feature.entry.domain.HISTORY_CACHE_MAX_AGE_DAYS
import org.hermesnative.client.feature.entry.domain.HISTORY_CACHE_MAX_MESSAGES
import org.hermesnative.client.feature.entry.domain.HistoryCacheKey
import org.hermesnative.client.feature.entry.domain.HistoryCacheStore
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant

/**
 * The bounded, encrypted device-local history cache.
 *
 * Records are kept under a per-endpoint namespace so one endpoint's content is never
 * readable as another's. The store holds at most [HISTORY_CACHE_MAX_MESSAGES] messages
 * and never returns content cached more than [HISTORY_CACHE_MAX_AGE_DAYS] days ago:
 * expired records drop on every read and leave the persisted blob on the next write,
 * and count overflow evicts the oldest cached records first, deterministically.
 * Corrupted or foreign content reads as empty; a later write replaces it. The bounds
 * are fixed by the domain port and are not configurable here.
 */
class BoundedHistoryCacheStore(
    private val storage: HistoryCacheStorage,
    private val cipher: AesGcmHistoryCacheCipher,
    private val endpointNamespace: (String) -> String,
    private val clock: () -> Instant = Instant::now,
) : HistoryCacheStore {
    private val lock = Any()

    override fun read(key: HistoryCacheKey): List<GatewayHistoryMessage> =
        synchronized(lock) {
            val sessionKey = recordKey(key)
            load().filter { it.key == sessionKey }.map(CachedHistoryRecord::message)
        }

    override fun write(
        key: HistoryCacheKey,
        messages: List<GatewayHistoryMessage>,
    ) {
        synchronized(lock) {
            val sessionKey = recordKey(key)
            val cachedAt = clock()
            val retained =
                load().filterNot { it.key == sessionKey } +
                    messages.map { message -> CachedHistoryRecord(sessionKey, message, cachedAt) }
            persist(trimToMessageBound(retained))
        }
    }

    override fun clearEndpoint(endpoint: String) {
        synchronized(lock) {
            val prefix = "${endpointNamespace(endpoint)}."
            persist(load().filterNot { it.key.startsWith(prefix) })
        }
    }

    override fun clear() {
        synchronized(lock) {
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

    private fun trimToMessageBound(records: List<CachedHistoryRecord>): List<CachedHistoryRecord> =
        if (records.size <= HISTORY_CACHE_MAX_MESSAGES) {
            records
        } else {
            records.drop(records.size - HISTORY_CACHE_MAX_MESSAGES)
        }

    private fun recordKey(key: HistoryCacheKey): String {
        val namespace = endpointNamespace(key.endpoint)
        return "$namespace.${encodeSessionId(key.sessionId.value)}"
    }

    private fun encodeSessionId(value: String): String =
        value.toByteArray(StandardCharsets.UTF_8)
            .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and BYTE_MASK) }

    private companion object {
        const val BYTE_MASK = 0xFF
        val MAX_CACHED_AGE: Duration = Duration.ofDays(HISTORY_CACHE_MAX_AGE_DAYS.toLong())
    }
}
