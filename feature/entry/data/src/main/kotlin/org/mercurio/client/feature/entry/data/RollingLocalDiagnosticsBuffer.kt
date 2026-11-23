package org.mercurio.client.feature.entry.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.mercurio.client.feature.entry.domain.LocalDiagnosticEvent
import org.mercurio.client.feature.entry.domain.LocalDiagnosticsRecorder
import org.mercurio.client.feature.entry.domain.LocalDiagnosticsStore
import java.io.FileNotFoundException
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

/** Bound of the rolling local diagnostics buffer, measured from encoded records. */
const val LOCAL_DIAGNOSTICS_LIMIT_BYTES: Int = 1_048_576

/**
 * The rolling local diagnostics buffer: a private, redacted set of approved
 * operational records bounded by its byte limit and measured from encoded records.
 *
 * The buffer evicts the oldest complete records to make room for a new one and
 * discards a record that cannot fit as a whole, so it never retains or emits a
 * partial record. A line that is already stored but is not a complete event
 * record, for example a partial line left by an interrupted write or a metadata
 * record, is dropped on the next read and on the next append instead of being
 * repaired.
 *
 * Recording stays best-effort: a record-time failure, for example a class or
 * format initialization that fails on the device, is reported once through
 * [onRecordFailure] and never surfaces to the caller. The catch is deliberately
 * `Throwable`: the initialization failure on API 24 is an `Error`
 * (`ExceptionInInitializerError`), not an `Exception`, and the recorder contract
 * forbids surfacing any record-time failure to the caller. The next [record]
 * attempt retries the failed initialization, so a transient failure disables
 * recording for one event instead of for the whole session.
 *
 * Mutations serialize through one [Mutex] across a suspend boundary because each one replaces the
 * whole record file. Reads take no lock: the storage writes a complete file atomically, so a reader
 * observes either the previous file or the next complete one. A concurrent [clear] deletes the file
 * instead of replacing it; a read that loses that race observes a missing file and reports an empty
 * buffer instead of failing.
 */
class RollingLocalDiagnosticsBuffer(
    private val storage: LocalDiagnosticsStorage,
    private val limitBytes: Int = LOCAL_DIAGNOSTICS_LIMIT_BYTES,
    private val clock: () -> Instant = Instant::now,
    private val onRecordFailure: (Throwable) -> Unit = {},
) : LocalDiagnosticsRecorder,
    LocalDiagnosticsStore {
    private val writeMutex = Mutex()
    private val failureReported = AtomicBoolean(false)

    init {
        require(limitBytes > 0) { "limitBytes must be positive." }
    }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun record(event: LocalDiagnosticEvent) {
        try {
            recordEvent(event)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            if (failureReported.compareAndSet(false, true)) onRecordFailure(error)
        }
    }

    private suspend fun recordEvent(event: LocalDiagnosticEvent) {
        val encoded = LocalDiagnosticsRecords.encodeEventRecord(event, clock()) ?: return
        val encodedBytes = LocalDiagnosticsRecords.retainedBytes(encoded)
        if (encodedBytes > limitBytes) return
        writeMutex.withLock {
            val retained = readRetainedLocked().toMutableList()
            var retainedBytes = retained.sumOf(LocalDiagnosticsRecords::retainedBytes)
            while (retained.isNotEmpty() && retainedBytes + encodedBytes > limitBytes) {
                retainedBytes -= LocalDiagnosticsRecords.retainedBytes(retained.removeAt(0))
            }
            retained += encoded
            storage.write(retained)
        }
    }

    override fun recordCount(): Int = records().size

    override suspend fun clear() {
        writeMutex.withLock {
            storage.write(emptyList())
        }
    }

    /** Complete retained records, oldest first; the source of an explicit export. */
    fun records(): List<String> = readRetainedLocked()

    private fun readRetainedLocked(): List<String> =
        try {
            storage.read().filter(LocalDiagnosticsRecords::isValidEventRecord)
        } catch (_: FileNotFoundException) {
            emptyList()
        }
}
