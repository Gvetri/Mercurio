package org.mercurio.client.feature.entry.application

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mercurio.client.feature.entry.domain.GatewayConnection
import org.mercurio.client.feature.entry.domain.GatewayConnectionRepository

class RemoveGatewayConnectionTest {
    @Test
    fun removes_the_connection_and_all_connection_specific_local_data() {
        val repository = FakeGatewayConnectionRepository(GatewayConnection("https://gateway.example", "token"))
        var clearedEndpoint: String? = null

        runBlocking { RemoveGatewayConnection(repository) { endpoint -> clearedEndpoint = endpoint }.execute() }

        assertEquals("https://gateway.example", clearedEndpoint)
        assertNull(repository.load())
    }

    @Test
    fun cleanup_still_runs_when_the_saved_endpoint_cannot_be_read() {
        val repository = FakeGatewayConnectionRepository(GatewayConnection("https://gateway.example", "token"))
        repository.failOnLoad = true
        var clearedEndpoint: String? = "unset"
        var clearedConnectionSpecificData = false

        val failure =
            runCatching {
                runBlocking {
                    RemoveGatewayConnection(repository) { endpoint ->
                        clearedEndpoint = endpoint
                        clearedConnectionSpecificData = true
                    }.execute()
                }
            }.exceptionOrNull()

        assertTrue(failure != null)
        assertTrue(repository.cleared)
        assertTrue(clearedConnectionSpecificData)
        assertNull(clearedEndpoint)
    }

    @Test
    fun a_suspending_cleanup_hook_runs_to_completion_across_the_coroutine_boundary() {
        val repository = FakeGatewayConnectionRepository(GatewayConnection("https://gateway.example", "token"))
        var clearedEndpoint: String? = null

        runBlocking {
            RemoveGatewayConnection(repository) { endpoint ->
                delay(1)
                clearedEndpoint = endpoint
            }.execute()
        }

        assertEquals("https://gateway.example", clearedEndpoint)
        assertNull(repository.load())
    }

    @Test
    fun the_default_clear_hook_still_removes_the_saved_connection() {
        val repository = FakeGatewayConnectionRepository(GatewayConnection("https://gateway.example", "token"))

        runBlocking { RemoveGatewayConnection(repository).execute() }

        assertNull(repository.load())
    }

    @Test
    fun a_failing_cleanup_hook_propagates_its_own_failure() {
        val repository = FakeGatewayConnectionRepository(GatewayConnection("https://gateway.example", "token"))

        val failure =
            runCatching {
                runBlocking {
                    RemoveGatewayConnection(repository) { error("cleanup failed") }.execute()
                }
            }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertTrue(repository.cleared)
    }

    private class FakeGatewayConnectionRepository(
        private var connection: GatewayConnection?,
    ) : GatewayConnectionRepository {
        var failOnLoad = false
        var cleared = false

        override fun load(): GatewayConnection? {
            if (failOnLoad) throw ClassCastException("The stored Gateway endpoint is not a string.")
            return connection
        }

        override fun save(connection: GatewayConnection) {
            this.connection = connection
        }

        override fun clear() {
            connection = null
            cleared = true
        }
    }
}
