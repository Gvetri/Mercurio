package org.mercurio.client.feature.entry.presentation

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mercurio.client.feature.entry.application.EntryState

class GatewayScreenStateHolderTest {
    @Test
    fun closing_the_add_gateway_screen_returns_to_the_landing_with_cleared_inputs() {
        val holder = EntryStateHolder(EntryState(isGatewayConnectionConfigured = false))
        holder.onEvent(EntryUiEvent.AddGatewayConnectionClicked)
        holder.onEvent(EntryUiEvent.EndpointChanged("https://gateway.example"))
        holder.onEvent(EntryUiEvent.BearerCredentialChanged("synthetic-token"))

        holder.onEvent(EntryUiEvent.CloseGatewayConnectionSetupClicked)
        runBlocking { holder.awaitStateChangesApplied() }

        assertFalse(holder.uiState.value.connectionSetupRequested)
        assertEquals("", holder.uiState.value.endpoint)
        assertEquals("", holder.uiState.value.bearerCredential)
        holder.close()
    }

    @Test
    fun closing_the_add_gateway_screen_keeps_a_saved_connection() {
        val holder =
            EntryStateHolder(
                EntryState(
                    isGatewayConnectionConfigured = true,
                    configuredEndpoint = "https://gateway.example",
                    configuredCredential = "saved-token",
                ),
            )

        holder.onEvent(EntryUiEvent.CloseGatewayConnectionSetupClicked)
        runBlocking { holder.awaitStateChangesApplied() }

        assertFalse(holder.uiState.value.connectionSetupRequested)
        assertEquals("https://gateway.example", holder.uiState.value.endpoint)
        assertEquals("saved-token", holder.uiState.value.bearerCredential)
        holder.close()
    }

    @Test
    fun cancelling_a_repeated_credential_change_restores_the_saved_credential() {
        val holder =
            EntryStateHolder(
                EntryState(
                    isGatewayConnectionConfigured = true,
                    configuredEndpoint = "https://gateway.example",
                    configuredCredential = "saved-token",
                ),
            )

        holder.onEvent(EntryUiEvent.ChangeGatewayCredentialClicked)
        holder.onEvent(EntryUiEvent.ChangeGatewayCredentialClicked)
        holder.onEvent(EntryUiEvent.CancelGatewayCredentialChangeClicked)
        runBlocking { holder.awaitStateChangesApplied() }

        assertFalse(holder.uiState.value.isChangingCredential)
        assertEquals("saved-token", holder.uiState.value.bearerCredential)
        holder.close()
    }

    @Test
    fun a_saved_connection_can_change_its_credential_after_a_failed_reconnect() {
        val holder =
            EntryStateHolder(
                EntryState(
                    isGatewayConnectionConfigured = true,
                    configuredEndpoint = "https://gateway.example",
                    configuredCredential = "saved-token",
                ),
            )

        holder.onEvent(EntryUiEvent.ChangeGatewayCredentialClicked)
        runBlocking { holder.awaitStateChangesApplied() }

        assertTrue(holder.uiState.value.isChangingCredential)
        assertEquals("https://gateway.example", holder.uiState.value.endpoint)
        holder.close()
    }

    @Test
    fun a_successful_verification_records_the_verified_facts() {
        val holder =
            holder(
                repository = FakeGatewayConnectionRepository(),
                verifier = { _, _ -> clientManifestCapabilities() },
            )
        verify(holder)

        assertTrue(holder.uiState.value.isConnected)
        val facts = holder.uiState.value.gatewayFacts
        assertNotNull(facts)
        assertEquals(R.string.gateway_facts_verified, (facts as UiText.Resource).id)
        holder.close()
    }
}
