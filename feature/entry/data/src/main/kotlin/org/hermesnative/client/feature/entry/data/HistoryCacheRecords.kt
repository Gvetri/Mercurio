package org.hermesnative.client.feature.entry.data

import org.hermesnative.client.feature.entry.domain.GatewayHistoryMessage
import org.hermesnative.client.feature.entry.domain.RunId
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.nio.charset.StandardCharsets
import java.time.Instant

/** One cached message with its storage key and the instant it entered the cache. */
data class CachedHistoryRecord(
    val key: String,
    val message: GatewayHistoryMessage,
    val cachedAt: Instant,
)

/**
 * The cache record codec: a versioned, length-prefixed binary form of the retained
 * records, independent of the encryption envelope that carries it.
 */
object HistoryCacheRecords {
    const val FORMAT_VERSION = 1
    private const val NULL_FIELD = -1
    private const val EOF_MARKER = -1
    private const val MAX_FIELD_BYTES = 32 * 1024 * 1024
    private const val MAX_RECORDS = 1_000_000

    fun encode(records: List<CachedHistoryRecord>): ByteArray {
        val out = ByteArrayOutputStream()
        DataOutputStream(out).use { stream ->
            stream.writeByte(FORMAT_VERSION)
            stream.writeInt(records.size)
            records.forEach { record -> writeRecord(stream, record) }
        }
        return out.toByteArray()
    }

    fun decode(bytes: ByteArray): List<CachedHistoryRecord> {
        DataInputStream(bytes.inputStream()).use { stream ->
            val version = stream.readUnsignedByte()
            require(version == FORMAT_VERSION) { "Unsupported history cache records." }
            val count = stream.readInt()
            require(count in 0..MAX_RECORDS) { "Invalid history cache records." }
            val records = List(count) { readRecord(stream) }
            require(stream.read() == EOF_MARKER) { "Invalid history cache records." }
            return records
        }
    }

    private fun writeRecord(
        stream: DataOutputStream,
        record: CachedHistoryRecord,
    ) {
        writeField(stream, record.key)
        stream.writeLong(record.cachedAt.epochSecond)
        val message = record.message
        writeField(stream, message.id)
        writeField(stream, message.role)
        writeField(stream, message.content)
        writeField(stream, message.runId?.value)
        writeField(stream, message.runStatus)
        writeField(stream, message.runResult)
        writeField(stream, message.timestamp)
    }

    private fun readRecord(stream: DataInputStream): CachedHistoryRecord {
        val key = readField(stream) ?: throw EOFException("Invalid history cache record.")
        val cachedAt = Instant.ofEpochSecond(stream.readLong())
        val id = readField(stream) ?: throw EOFException("Invalid history cache record.")
        val role = readField(stream)
        val content = readField(stream)
        val runId = readField(stream)
        val runStatus = readField(stream)
        val runResult = readField(stream)
        val timestamp = readField(stream)
        return CachedHistoryRecord(
            key = key,
            message =
                GatewayHistoryMessage(
                    id = id,
                    role = role,
                    content = content,
                    runId = runId?.let(::RunId),
                    runStatus = runStatus,
                    runResult = runResult,
                    timestamp = timestamp,
                ),
            cachedAt = cachedAt,
        )
    }

    private fun writeField(
        stream: DataOutputStream,
        value: String?,
    ) {
        if (value == null) {
            stream.writeInt(NULL_FIELD)
            return
        }
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        stream.writeInt(bytes.size)
        stream.write(bytes)
    }

    private fun readField(stream: DataInputStream): String? {
        val size = stream.readInt()
        if (size == NULL_FIELD) return null
        require(size in 0..MAX_FIELD_BYTES) { "Invalid history cache record." }
        val bytes = ByteArray(size)
        stream.readFully(bytes)
        return String(bytes, StandardCharsets.UTF_8)
    }
}
