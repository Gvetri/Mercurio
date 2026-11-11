package org.mercurio.client.feature.entry.presentation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mercurio.client.feature.entry.domain.GatewayConnection
import java.util.concurrent.CountDownLatch
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
    fun repeated_verification_taps_launch_a_single_verification_while_one_is_in_flight() =
        runTest {
            val repository = FakeGatewayConnectionRepository()
            var verificationCalls = 0
            val verifyStarted = CountDownLatch(1)
            val verifyRelease = CountDownLatch(1)
            val holder =
                holder(
                    repository = repository,
                    verifier = { _, _ ->
                        verificationCalls += 1
                        verifyStarted.countDown()
                        assertTrue(verifyRelease.await(ASYNC_TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))
                        clientManifestCapabilities()
                    },
                    dispatcher = Dispatchers.Default,
                )

            holder.onEvent(EntryUiEvent.AddGatewayConnectionClicked)
            holder.onEvent(EntryUiEvent.EndpointChanged("https://gateway.example/profile"))
            holder.onEvent(EntryUiEvent.BearerCredentialChanged("secure-token"))
            holder.onEvent(EntryUiEvent.VerifyGatewayConnectionClicked)
            assertTrue(verifyStarted.await(ASYNC_TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))
            assertTrue(holder.uiState.value.isVerifying)

            holder.onEvent(EntryUiEvent.VerifyGatewayConnectionClicked)
            holder.onEvent(EntryUiEvent.VerifyGatewayConnectionClicked)
            runBlocking { holder.awaitStateChangesApplied() }

            verifyRelease.countDown()
            awaitState(holder, "connected") { it.isConnected }

            assertEquals(1, verificationCalls)
            assertEquals(
                GatewayConnection("https://gateway.example/profile", "secure-token"),
                repository.saved,
            )
            holder.close()
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
        assertEquals(UiText.Resource(R.string.entry_action_verify), holder.uiState.value.actionLabel)

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
    fun saving_phase_locks_inputs_until_secure_persist_finishes() =
        runTest {
            val repository = FakeGatewayConnectionRepository()
            lateinit var holder: EntryStateHolder
            var inputsWhileSaving: EntryUiState? = null
            repository.onSave = {
                holder.onEvent(EntryUiEvent.EndpointChanged("https://changed.example/profile"))
                holder.onEvent(EntryUiEvent.BearerCredentialChanged("changed-token"))
                inputsWhileSaving = holder.uiState.value
            }
            holder =
                holder(
                    repository = repository,
                    verifier = { _, _ ->
                        clientManifestCapabilities()
                    },
                    dispatcher = StandardTestDispatcher(testScheduler),
                )

            verify(holder)
            advanceUntilIdle()
            awaitState(holder, "connected") { it.isConnected && !it.isSaving }

            val locked = requireNotNull(inputsWhileSaving)
            assertTrue(locked.isSaving)
            assertFalse(locked.isVerifying)
            assertEquals("https://gateway.example/profile", locked.endpoint)
            assertEquals("memory-only-token", locked.bearerCredential)
            assertEquals(
                GatewayConnection("https://gateway.example/profile", "memory-only-token"),
                repository.saved,
            )
            holder.close()
        }
}
