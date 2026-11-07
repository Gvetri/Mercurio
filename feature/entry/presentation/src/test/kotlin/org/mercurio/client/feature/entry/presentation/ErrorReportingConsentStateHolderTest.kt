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

    @Test
    fun an_unbound_store_never_asks_the_question() {
        val holder =
            EntryStateHolder(
                initialState = EntryState(isGatewayConnectionConfigured = false),
                dependencies = EntryStateHolderDependencies(),
            )

        try {
            assertFalse(holder.uiState.value.isErrorReportingConsentRequired)
            assertEquals(ErrorReportingConsent.Undecided, holder.uiState.value.errorReportingConsent)
        } finally {
            holder.close()
        }
    }

    @Test
    fun the_settings_row_reads_the_stored_answer() {
        val holder =
            consentHolder(
                InMemoryConsentStore(ErrorReportingConsent.Granted),
                isErrorReportingConfigured = true,
            )

        try {
            assertEquals(ErrorReportingConsent.Granted, holder.uiState.value.errorReportingConsent)
            assertTrue(holder.uiState.value.isErrorReportingConfigured)
        } finally {
            holder.close()
        }
    }

    @Test
    fun the_settings_toggle_writes_the_same_stored_decision() {
        val store = InMemoryConsentStore(ErrorReportingConsent.Granted)
        val holder = consentHolder(store, isErrorReportingConfigured = true)

        try {
            holder.onEvent(EntryUiEvent.ErrorReportingConsentToggleClicked(granted = false))

            assertEquals(ErrorReportingConsent.Declined, store.read())
            assertEquals(ErrorReportingConsent.Declined, holder.uiState.value.errorReportingConsent)
            assertFalse(holder.uiState.value.isErrorReportingConsentRequired)

            holder.onEvent(EntryUiEvent.ErrorReportingConsentToggleClicked(granted = true))

            assertEquals(ErrorReportingConsent.Granted, store.read())
            assertEquals(ErrorReportingConsent.Granted, holder.uiState.value.errorReportingConsent)
        } finally {
            holder.close()
        }
    }

    private fun consentHolder(
        store: ErrorReportingConsentStore,
        isErrorReportingConfigured: Boolean = false,
    ): EntryStateHolder =
        EntryStateHolder(
            initialState = EntryState(isGatewayConnectionConfigured = false),
            dependencies =
                EntryStateHolderDependencies(
                    errorReportingConsentStore = store,
                    isErrorReportingConfigured = isErrorReportingConfigured,
                ),
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
