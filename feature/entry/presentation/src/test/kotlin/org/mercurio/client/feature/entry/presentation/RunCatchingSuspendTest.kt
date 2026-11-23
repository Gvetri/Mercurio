package org.mercurio.client.feature.entry.presentation

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class RunCatchingSuspendTest {
    @Test
    fun an_error_becomes_a_failure_result_like_run_catching() =
        runBlocking<Unit> {
            val result = runCatchingSuspend<Unit> { throw ExceptionInInitializerError("initialization failed") }

            assertTrue(result.exceptionOrNull() is ExceptionInInitializerError)
        }

    @Test
    fun cancellation_is_rethrown_instead_of_becoming_a_failure_result() =
        runBlocking<Unit> {
            val thrown =
                try {
                    runCatchingSuspend<Unit> { throw CancellationException("stopped") }
                    null
                } catch (error: CancellationException) {
                    error
                }

            assertTrue(thrown is CancellationException)
        }
}
