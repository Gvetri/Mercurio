package org.hermesnative.client.feature.entry.presentation

import kotlinx.coroutines.asCoroutineDispatcher
import org.hermesnative.client.feature.entry.domain.GatewayConnection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class EntryStateHolderSecureSaveTest {
    @Test
    fun storage_failure_offers_try_again_with_retained_inputs_and_full_reverification() {
        val repository = FakeGatewayConnectionRepository()
        repository.throwOnSave = true
        var verificationCount = 0
        val holder =
            holder(
                repository = repository,
                verifier = { _, _ ->
                    verificationCount += 1
                    clientManifestCapabilities()
                },
            )

        holder.onEvent(EntryUiEvent.AddGatewayConnectionClicked)
        holder.onEvent(EntryUiEvent.EndpointChanged("https://gateway.example/profile"))
        holder.onEvent(EntryUiEvent.BearerCredentialChanged("secure-token"))
        holder.onEvent(EntryUiEvent.VerifyGatewayConnectionClicked)

        assertEquals(EntryErrorCategory.CREDENTIAL_STORAGE_FAILED, holder.uiState.value.errorCategory)
        assertFalse(holder.uiState.value.isConnected)
        assertEquals("https://gateway.example/profile", holder.uiState.value.endpoint)
        assertEquals("secure-token", holder.uiState.value.bearerCredential)

        repository.throwOnSave = false
        holder.onEvent(EntryUiEvent.TryAgainClicked)

        assertEquals(2, verificationCount)
        assertTrue(holder.uiState.value.isConnected)
        assertEquals(
            GatewayConnection("https://gateway.example/profile", "secure-token"),
            repository.saved,
        )
        holder.close()
    }

    @Test
    fun saving_phase_locks_inputs_until_secure_persist_finishes() {
        val repository = FakeGatewayConnectionRepository()
        val saveStarted = CountDownLatch(1)
        val releaseSave = CountDownLatch(1)
        repository.onSave = {
            saveStarted.countDown()
            assertTrue(releaseSave.await(ASYNC_TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))
        }
        val executor = Executors.newSingleThreadExecutor()
        val dispatcher = executor.asCoroutineDispatcher()
        val holder =
            holder(
                repository = repository,
                verifier = { _, _ ->
                    clientManifestCapabilities()
                },
                dispatcher = dispatcher,
            )

        try {
            verify(holder)
            assertTrue(saveStarted.await(ASYNC_TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))
            awaitState(holder, "saving phase") { it.isSaving && !it.isVerifying }

            holder.onEvent(EntryUiEvent.EndpointChanged("https://changed.example/profile"))
            holder.onEvent(EntryUiEvent.BearerCredentialChanged("changed-token"))

            assertEquals("https://gateway.example/profile", holder.uiState.value.endpoint)
            assertEquals("memory-only-token", holder.uiState.value.bearerCredential)

            releaseSave.countDown()
            executor.submit {}.get(ASYNC_TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
            awaitState(holder, "connected") { it.isConnected && !it.isSaving }
            assertEquals(
                GatewayConnection("https://gateway.example/profile", "memory-only-token"),
                repository.saved,
            )
        } finally {
            releaseSave.countDown()
            holder.close()
            dispatcher.close()
        }
    }
}
