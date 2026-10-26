package org.mercurio.client.feature.entry.presentation

import kotlinx.coroutines.asCoroutineDispatcher
import org.mercurio.client.feature.entry.domain.GatewayConnection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    fun repeated_verification_taps_launch_a_single_verification_while_one_is_in_flight() {
        val repository = FakeGatewayConnectionRepository()
        val verificationStarted = CountDownLatch(1)
        val releaseVerification = CountDownLatch(1)
        var verificationCalls = 0
        val executor = Executors.newSingleThreadExecutor()
        val dispatcher = executor.asCoroutineDispatcher()
        val holder =
            holder(
                repository = repository,
                verifier = { _, _ ->
                    verificationCalls += 1
                    verificationStarted.countDown()
                    assertTrue(releaseVerification.await(ASYNC_TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))
                    clientManifestCapabilities()
                },
                dispatcher = dispatcher,
            )

        try {
            holder.onEvent(EntryUiEvent.AddGatewayConnectionClicked)
            holder.onEvent(EntryUiEvent.EndpointChanged("https://gateway.example/profile"))
            holder.onEvent(EntryUiEvent.BearerCredentialChanged("secure-token"))
            holder.onEvent(EntryUiEvent.VerifyGatewayConnectionClicked)
            assertTrue(verificationStarted.await(ASYNC_TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))

            holder.onEvent(EntryUiEvent.VerifyGatewayConnectionClicked)
            holder.onEvent(EntryUiEvent.VerifyGatewayConnectionClicked)

            releaseVerification.countDown()
            executor.submit {}.get(ASYNC_TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
            awaitState(holder, "connected") { it.isConnected }

            assertEquals(1, verificationCalls)
            assertEquals(
                GatewayConnection("https://gateway.example/profile", "secure-token"),
                repository.saved,
            )
        } finally {
            releaseVerification.countDown()
            holder.close()
            dispatcher.close()
        }
    }

    @Test
    fun storage_failure_retry_after_a_credential_edit_verifies_and_persists_the_edited_value() {
        val repository = FakeGatewayConnectionRepository()
        repository.throwOnSave = true
        val credentials = mutableListOf<String>()
        val holder =
            holder(
                repository = repository,
                verifier = { _, credential ->
                    credentials += credential
                    clientManifestCapabilities()
                },
            )

        holder.onEvent(EntryUiEvent.AddGatewayConnectionClicked)
        holder.onEvent(EntryUiEvent.EndpointChanged("https://gateway.example/profile"))
        holder.onEvent(EntryUiEvent.BearerCredentialChanged("secure-token"))
        holder.onEvent(EntryUiEvent.VerifyGatewayConnectionClicked)

        assertEquals(EntryErrorCategory.CREDENTIAL_STORAGE_FAILED, holder.uiState.value.errorCategory)

        holder.onEvent(EntryUiEvent.BearerCredentialChanged("secure-token-edited"))

        assertNull(holder.uiState.value.errorCategory)
        assertEquals("Verify Gateway Connection", holder.uiState.value.actionLabel)

        repository.throwOnSave = false
        holder.onEvent(EntryUiEvent.VerifyGatewayConnectionClicked)

        assertEquals(listOf("secure-token", "secure-token-edited"), credentials)
        assertTrue(holder.uiState.value.isConnected)
        assertEquals(
            GatewayConnection("https://gateway.example/profile", "secure-token-edited"),
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
