package org.hermesnative.client.feature.entry.presentation

import org.hermesnative.client.feature.entry.domain.GatewayConnection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EntryStateHolderRemovalConfirmationTest {
    @Test
    fun removal_request_opens_the_confirmation_without_mutating_the_connection() {
        val repository = FakeGatewayConnectionRepository()
        val holder =
            holder(
                repository = repository,
                verifier = { _, _ ->
                    clientManifestCapabilities()
                },
            )
        verify(holder)

        holder.onEvent(EntryUiEvent.RemoveGatewayConnectionClicked)

        assertTrue(holder.uiState.value.isRemoveConfirmationOpen)
        assertTrue(holder.uiState.value.isConnected)
        assertEquals(
            GatewayConnection("https://gateway.example/profile", "memory-only-token"),
            repository.saved,
        )

        holder.onEvent(EntryUiEvent.CancelRemoveGatewayConnectionClicked)

        assertFalse(holder.uiState.value.isRemoveConfirmationOpen)
        assertTrue(holder.uiState.value.isConnected)
        assertEquals(
            GatewayConnection("https://gateway.example/profile", "memory-only-token"),
            repository.saved,
        )
        holder.close()
    }

    @Test
    fun confirming_removal_clears_the_local_connection_once() {
        val holder =
            holder(
                repository = FakeGatewayConnectionRepository(),
                verifier = { _, _ ->
                    clientManifestCapabilities()
                },
            )
        verify(holder)

        holder.onEvent(EntryUiEvent.RemoveGatewayConnectionClicked)
        holder.onEvent(EntryUiEvent.ConfirmRemoveGatewayConnectionClicked)

        assertFalse(holder.uiState.value.isConnected)
        assertFalse(holder.uiState.value.isRemoveConfirmationOpen)
        assertFalse(holder.uiState.value.isGatewayConnectionConfigured)
        assertNull(holder.uiState.value.sessionList)
        holder.close()
    }
}
