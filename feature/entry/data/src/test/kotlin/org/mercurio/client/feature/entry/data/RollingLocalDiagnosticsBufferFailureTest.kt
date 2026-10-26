package org.mercurio.client.feature.entry.data

import org.mercurio.client.feature.entry.domain.LocalDiagnosticEvent
import org.mercurio.client.feature.entry.domain.LocalDiagnosticEventType
import org.mercurio.client.feature.entry.domain.LocalDiagnosticStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

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
    fun a_recording_failure_is_surfaced_once_with_its_cause_and_a_later_attempt_still_records() {
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
    fun only_the_first_recording_failure_is_surfaced() {
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

    private companion object {
        val FIXED_INSTANT: Instant = Instant.parse("2026-09-28T10:15:30Z")
        val EXECUTED_EVENT =
            LocalDiagnosticEvent(LocalDiagnosticEventType.SESSION_LIST_LOAD, LocalDiagnosticStatus.SUCCEEDED)
    }
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
