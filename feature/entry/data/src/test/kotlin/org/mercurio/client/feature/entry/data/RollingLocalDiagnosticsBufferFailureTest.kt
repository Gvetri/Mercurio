package org.mercurio.client.feature.entry.data

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mercurio.client.feature.entry.domain.LocalDiagnosticEvent
import org.mercurio.client.feature.entry.domain.LocalDiagnosticEventType
import org.mercurio.client.feature.entry.domain.LocalDiagnosticStatus
import java.io.FileNotFoundException
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Recording failures must surface once with their cause instead of silently
 * disabling the buffer, and a later recording attempt must still store its
 * event. This is the pipeline behavior whose loss made issue #173 a whole
 * session without local diagnostics: on API 24 the device-side class
 * initialization of `LocalDiagnosticsRecords` failed once, ART then rejected
 * every re-initialization, and nothing ever surfaced.
 */
class RollingLocalDiagnosticsBufferFailureTest {
    @Test
    fun a_recording_failure_is_surfaced_once_with_its_cause_and_a_later_attempt_still_records() =
        runBlocking<Unit> {
            val storage = FailingLocalDiagnosticsStorage(failuresBeforeSuccess = 1)
            val failures = mutableListOf<Throwable>()
            val buffer =
                RollingLocalDiagnosticsBuffer(
                    storage,
                    clock = { FIXED_INSTANT },
                    onRecordFailure = failures::add,
                )

            buffer.record(EXECUTED_EVENT)

            assertEquals(1, failures.size)
            assertTrue(failures.single() is IllegalStateException)

            buffer.record(EXECUTED_EVENT)

            assertEquals(1, buffer.recordCount())
            val expected = requireNotNull(LocalDiagnosticsRecords.encodeEventRecord(EXECUTED_EVENT, FIXED_INSTANT))
            assertEquals(listOf(expected), storage.read())
        }

    @Test
    fun only_the_first_recording_failure_is_surfaced() =
        runBlocking<Unit> {
            val storage = FailingLocalDiagnosticsStorage(failuresBeforeSuccess = 3)
            val failures = mutableListOf<Throwable>()
            val buffer =
                RollingLocalDiagnosticsBuffer(
                    storage,
                    clock = { FIXED_INSTANT },
                    onRecordFailure = failures::add,
                )

            buffer.record(EXECUTED_EVENT)
            buffer.record(EXECUTED_EVENT)
            buffer.record(EXECUTED_EVENT)
            buffer.record(EXECUTED_EVENT)

            assertEquals(1, failures.size)
            assertEquals(1, buffer.recordCount())
        }

    @Test
    fun cancellation_while_waiting_for_the_write_lock_is_not_reported_as_a_record_failure() =
        runBlocking<Unit> {
            val storage = BlockingWriteStorage()
            val failures = mutableListOf<Throwable>()
            val buffer =
                RollingLocalDiagnosticsBuffer(
                    storage,
                    clock = { FIXED_INSTANT },
                    onRecordFailure = failures::add,
                )

            val first = launch(Dispatchers.Default) { buffer.record(EXECUTED_EVENT) }
            assertTrue("The first record must reach the storage write", storage.awaitWriteStarted())
            val second =
                launch(start = CoroutineStart.UNDISPATCHED) {
                    buffer.record(EXECUTED_EVENT)
                }
            second.cancelAndJoin()
            storage.releaseWrite()
            first.join()

            assertTrue(failures.isEmpty())
            assertEquals(1, storage.writes)
        }

    @Test
    fun a_read_that_loses_the_file_reports_an_empty_buffer() {
        val buffer = RollingLocalDiagnosticsBuffer(MissingFileStorage())

        assertTrue(buffer.records().isEmpty())
        assertEquals(0, buffer.recordCount())
    }

    private companion object {
        val FIXED_INSTANT: Instant = Instant.parse("2026-09-28T10:15:30Z")
        val EXECUTED_EVENT =
            LocalDiagnosticEvent(LocalDiagnosticEventType.SESSION_LIST_LOAD, LocalDiagnosticStatus.SUCCEEDED)
    }
}

private class BlockingWriteStorage : LocalDiagnosticsStorage {
    private val writeStarted = CountDownLatch(1)
    private val writeAllowed = CountDownLatch(1)
    private val writeCount = java.util.concurrent.atomic.AtomicInteger(0)

    val writes: Int get() = writeCount.get()

    fun awaitWriteStarted(): Boolean = writeStarted.await(5, TimeUnit.SECONDS)

    fun releaseWrite() {
        writeAllowed.countDown()
    }

    override fun read(): List<String> = emptyList()

    override fun write(records: List<String>) {
        writeCount.incrementAndGet()
        writeStarted.countDown()
        check(writeAllowed.await(5, TimeUnit.SECONDS)) { "The test did not release the blocked write." }
    }
}

private class MissingFileStorage : LocalDiagnosticsStorage {
    override fun read(): List<String> = throw FileNotFoundException("The diagnostics file was cleared.")

    override fun write(records: List<String>) = Unit
}

private class FailingLocalDiagnosticsStorage(
    private var failuresBeforeSuccess: Int,
) : LocalDiagnosticsStorage {
    private var records: List<String> = emptyList()

    override fun read(): List<String> = records

    override fun write(records: List<String>) {
        if (failuresBeforeSuccess > 0) {
            failuresBeforeSuccess -= 1
            error("Simulated record-time initialization failure.")
        }
        this.records = records.toList()
    }
}
