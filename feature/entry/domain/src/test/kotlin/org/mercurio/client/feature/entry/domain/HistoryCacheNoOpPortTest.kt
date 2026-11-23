package org.mercurio.client.feature.entry.domain

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

class HistoryCacheNoOpPortTest {
    @Test
    fun the_in_memory_default_holds_no_session_lists_and_stays_inert() {
        val endpoint = "https://gateway.example"

        assertTrue(NoOpHistoryCacheStore.readSessionList(endpoint).isEmpty())

        runSuspend {
            NoOpHistoryCacheStore.writeSessionList(
                endpoint,
                listOf(Session(SessionId("session-1"), "Title", null, false)),
            )
        }

        assertTrue(NoOpHistoryCacheStore.readSessionList(endpoint).isEmpty())
    }
}

private fun runSuspend(block: suspend () -> Unit) {
    block.startCoroutine(
        object : Continuation<Unit> {
            override val context = EmptyCoroutineContext

            override fun resumeWith(result: Result<Unit>) {
                result.getOrThrow()
            }
        },
    )
}
