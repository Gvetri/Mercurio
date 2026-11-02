package org.mercurio.client.feature.entry.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mercurio.client.feature.entry.application.EntryState
import org.mercurio.client.feature.entry.domain.ErrorReportingConsent
import org.mercurio.client.feature.entry.domain.ErrorReportingConsentStore

class ErrorReportingConsentStateHolderTest {
    @Test
    fun a_fresh_install_requires_the_answer_before_the_connection_surface() {
        val store = InMemoryConsentStore()
        val holder = consentHolder(store)

        try {
            assertTrue(holder.uiState.value.isErrorReportingConsentRequired)
        } finally {
            holder.close()
        }
    }

    @Test
    fun accepting_stores_granted_and_shows_the_next_screen() {
        val store = InMemoryConsentStore()
        val holder = consentHolder(store)

        try {
            holder.onEvent(EntryUiEvent.ErrorReportingConsentGranted)

            assertEquals(ErrorReportingConsent.Granted, store.read())
            assertFalse(holder.uiState.value.isErrorReportingConsentRequired)
        } finally {
            holder.close()
        }
    }

    @Test
    fun declining_stores_declined_and_shows_the_next_screen() {
        val store = InMemoryConsentStore()
        val holder = consentHolder(store)

        try {
            holder.onEvent(EntryUiEvent.ErrorReportingConsentDeclined)

            assertEquals(ErrorReportingConsent.Declined, store.read())
            assertFalse(holder.uiState.value.isErrorReportingConsentRequired)
        } finally {
            holder.close()
        }
    }

    @Test
    fun a_stored_answer_never_asks_again() {
        listOf(ErrorReportingConsent.Granted, ErrorReportingConsent.Declined).forEach { stored ->
            val holder = consentHolder(InMemoryConsentStore(stored))
            try {
                assertFalse(
                    "a stored $stored answer must not ask again",
                    holder.uiState.value.isErrorReportingConsentRequired,
                )
            } finally {
                holder.close()
            }
        }
    }

    private fun consentHolder(store: ErrorReportingConsentStore): EntryStateHolder =
        EntryStateHolder(
            initialState = EntryState(isGatewayConnectionConfigured = false),
            dependencies = EntryStateHolderDependencies(errorReportingConsentStore = store),
        )
}

private class InMemoryConsentStore(
    private var stored: ErrorReportingConsent = ErrorReportingConsent.Undecided,
) : ErrorReportingConsentStore {
    override fun read(): ErrorReportingConsent = stored

    override fun write(consent: ErrorReportingConsent) {
        stored = consent
    }
}
