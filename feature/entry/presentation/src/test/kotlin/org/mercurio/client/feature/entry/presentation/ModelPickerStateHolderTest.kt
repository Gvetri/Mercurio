package org.mercurio.client.feature.entry.presentation

import org.junit.Assert.assertEquals
import org.junit.Test
import org.mercurio.client.feature.entry.domain.GatewayErrorCategory
import org.mercurio.client.feature.entry.domain.GatewayException
import org.mercurio.client.feature.entry.domain.ModelSelection
import org.mercurio.client.feature.entry.domain.SessionId

class ModelPickerStateHolderTest {
    @Test
    fun a_gateway_without_the_run_models_row_never_fetches_the_catalog() {
        val session = retrySession(PICKER_SESSION_ID.value)
        val delegate = RetryFakeGateway(sessions = listOf(session))
        val catalog = PickerFakeGateway(delegate)
        val holder = pickerHolder(catalog, capabilities = pickerCapabilities(withRunModelsRow = false))

        try {
            holder.connectAndOpenPickerSession(delegate)

            val picker = requireNotNull(holder.uiState.value.sessionList).modelPicker
            assertEquals(true, picker.capabilityKnown)
            assertEquals(false, picker.capabilityAdvertised)
            assertEquals(null, picker.catalog)
            assertEquals(false, picker.showsSelectionControl)
            assertEquals(true, picker.showsUnavailableCopy)
            assertEquals(0, catalog.fetchCount)
        } finally {
            holder.close()
        }
    }

    @Test
    fun the_catalog_is_fetched_once_the_picker_becomes_relevant_and_lands_with_store_state() {
        val session = retrySession(PICKER_SESSION_ID.value)
        val delegate = RetryFakeGateway(sessions = listOf(session))
        val catalog = PickerFakeGateway(delegate, catalogResult = Result.success(pickerCatalog()))
        val store =
            FakePickerSelectionStore(
                selection = ModelSelection("beta", "high"),
                favorites = setOf("beta"),
                recents = listOf("alpha"),
            )
        val holder = pickerHolder(catalog, store = store)

        try {
            holder.connectAndOpenPickerSession(delegate)
            awaitPickerState(holder) { it.catalog != null }

            val picker = requireNotNull(holder.uiState.value.sessionList).modelPicker
            assertEquals(true, picker.capabilityAdvertised)
            assertEquals(pickerCatalog(), picker.catalog)
            assertEquals(ModelSelection("beta", "high"), picker.selection)
            assertEquals(setOf("beta"), picker.favorites)
            assertEquals(listOf("alpha"), picker.recents)
            assertEquals(true, picker.showsSelectionControl)
            assertEquals("Second Model", picker.controlLabel)
            assertEquals(1, catalog.fetchCount)
        } finally {
            holder.close()
        }
    }

    @Test
    fun a_fetched_catalog_is_not_refetched_for_later_sessions_of_the_same_connection() {
        val first = retrySession("session-1")
        val second = retrySession("session-2")
        val delegate = RetryFakeGateway(sessions = listOf(first, second))
        val catalog = PickerFakeGateway(delegate, catalogResult = Result.success(pickerCatalog()))
        val holder = pickerHolder(catalog)

        try {
            holder.connectAndOpenPickerSession(delegate)
            awaitPickerState(holder) { it.catalog != null }
            holder.onEvent(EntryUiEvent.SessionClicked(SessionId("session-2")))
            awaitRetryState(holder) { it.sessionList?.openedSession?.session?.id == SessionId("session-2") }

            assertEquals(1, catalog.fetchCount)
        } finally {
            holder.close()
        }
    }

    @Test
    fun an_unreachable_catalog_hides_the_control_and_never_disturbs_the_connection() {
        val session = retrySession(PICKER_SESSION_ID.value)
        val delegate = RetryFakeGateway(sessions = listOf(session))
        val catalog =
            PickerFakeGateway(
                delegate,
                catalogResult = Result.failure(GatewayException(GatewayErrorCategory.GATEWAY_REQUEST_FAILED)),
            )
        val holder = pickerHolder(catalog)

        try {
            holder.connectAndOpenPickerSession(delegate)
            awaitPickerState(holder) { it.catalogFetchFailed }

            val state = requireNotNull(holder.uiState.value.sessionList)
            assertEquals(true, state.modelPicker.catalogFetchFailed)
            assertEquals(false, state.modelPicker.showsSelectionControl)
            assertEquals(null, state.modelPicker.catalog)
            assertEquals(false, state.modelPicker.showsUnavailableCopy)
            assertEquals(true, holder.uiState.value.isConnected)
            assertEquals(null, holder.uiState.value.errorCategory)
            assertEquals(null, state.errorCategory)

            holder.onEvent(EntryUiEvent.SessionClicked(PICKER_SESSION_ID))
            assertEquals(1, catalog.fetchCount)
        } finally {
            holder.close()
        }
    }

    @Test
    fun a_connected_gateway_port_without_the_catalog_contract_fails_closed() {
        val session = retrySession(PICKER_SESSION_ID.value)
        val delegate = RetryFakeGateway(sessions = listOf(session))
        val holder = pickerHolder(delegate)

        try {
            holder.connectAndOpenPickerSession(delegate)

            val picker = requireNotNull(holder.uiState.value.sessionList).modelPicker
            assertEquals(true, picker.capabilityAdvertised)
            assertEquals(null, picker.catalog)
            assertEquals(false, picker.showsSelectionControl)
            assertEquals(false, picker.isFetchingCatalog)
        } finally {
            holder.close()
        }
    }

    @Test
    fun reconnecting_without_the_row_drops_the_previously_loaded_catalog() {
        val session = retrySession(PICKER_SESSION_ID.value)
        val delegate = RetryFakeGateway(sessions = listOf(session))
        val catalog = PickerFakeGateway(delegate, catalogResult = Result.success(pickerCatalog()))
        val capabilities = java.util.concurrent.atomic.AtomicReference(pickerCapabilities(withRunModelsRow = true))
        val holder = pickerHolder(catalog) { capabilities.get() }

        try {
            holder.connectAndOpenPickerSession(delegate)
            awaitPickerState(holder) { it.catalog != null }

            holder.onEvent(EntryUiEvent.RemoveGatewayConnectionClicked)
            holder.onEvent(EntryUiEvent.ConfirmRemoveGatewayConnectionClicked)
            awaitRetryState(holder) { !it.isConnected }

            capabilities.set(pickerCapabilities(withRunModelsRow = false))
            holder.connectAndOpenPickerSession(delegate)

            val picker = requireNotNull(holder.uiState.value.sessionList).modelPicker
            assertEquals(true, picker.capabilityKnown)
            assertEquals(false, picker.capabilityAdvertised)
            assertEquals(null, picker.catalog)
            assertEquals(true, picker.showsUnavailableCopy)
            assertEquals(1, catalog.fetchCount)
        } finally {
            holder.close()
        }
    }

    @Test
    fun selecting_a_model_writes_the_override_and_never_double_records_a_use() {
        val session = retrySession(PICKER_SESSION_ID.value)
        val delegate = RetryFakeGateway(sessions = listOf(session))
        val catalog = PickerFakeGateway(delegate, catalogResult = Result.success(pickerCatalog()))
        val store = FakePickerSelectionStore()
        val holder = pickerHolder(catalog, store = store)

        try {
            holder.connectAndOpenPickerSession(delegate)
            awaitPickerState(holder) { it.catalog != null }

            store.recordUse(PICKER_ENDPOINT, "gamma")
            val seededUses = store.useLog.size
            holder.onEvent(EntryUiEvent.ModelRowClicked("beta"))
            val picker = pickerState(holder)
            assertEquals(ModelSelection("beta"), picker.selection)
            assertEquals(listOf(PICKER_ENDPOINT to ModelSelection("beta")), store.writes)
            assertEquals(listOf("gamma"), picker.recents)
            assertEquals(seededUses, store.useLog.size)
        } finally {
            holder.close()
        }
    }

    @Test
    fun selecting_a_different_model_drops_the_previous_effort_but_reselecting_keeps_it() {
        val session = retrySession(PICKER_SESSION_ID.value)
        val delegate = RetryFakeGateway(sessions = listOf(session))
        val catalog = PickerFakeGateway(delegate, catalogResult = Result.success(pickerCatalog()))
        val store = FakePickerSelectionStore(selection = ModelSelection("beta", "high"))
        val holder = pickerHolder(catalog, store = store)

        try {
            holder.connectAndOpenPickerSession(delegate)
            awaitPickerState(holder) { it.catalog != null }

            holder.onEvent(EntryUiEvent.ModelRowClicked("beta"))
            assertEquals(ModelSelection("beta", "high"), pickerState(holder).selection)

            holder.onEvent(EntryUiEvent.ModelRowClicked("alpha"))
            assertEquals(ModelSelection("alpha"), pickerState(holder).selection)

            holder.onEvent(EntryUiEvent.ModelRowClicked("alpha"))
            assertEquals(ModelSelection("alpha", null), pickerState(holder).selection)
        } finally {
            holder.close()
        }
    }

    @Test
    fun server_default_clears_the_override_and_sends_no_selection_fields() {
        val session = retrySession(PICKER_SESSION_ID.value)
        val delegate = RetryFakeGateway(sessions = listOf(session))
        val catalog = PickerFakeGateway(delegate, catalogResult = Result.success(pickerCatalog()))
        val store = FakePickerSelectionStore(selection = ModelSelection("beta"))
        val holder = pickerHolder(catalog, store = store)

        try {
            holder.connectAndOpenPickerSession(delegate)
            awaitPickerState(holder) { it.catalog != null }

            holder.onEvent(EntryUiEvent.ServerDefaultModelClicked)
            assertEquals(null, pickerState(holder).selection)
            assertEquals(listOf(PICKER_ENDPOINT), store.clears)
            assertEquals("Server default", pickerState(holder).controlLabel)
        } finally {
            holder.close()
        }
    }

    @Test
    fun an_effort_choice_updates_the_selection_and_effort_without_a_model_is_unrepresentable() {
        val session = retrySession(PICKER_SESSION_ID.value)
        val delegate = RetryFakeGateway(sessions = listOf(session))
        val catalog = PickerFakeGateway(delegate, catalogResult = Result.success(pickerCatalog()))
        val store = FakePickerSelectionStore(selection = ModelSelection("beta"))
        val holder = pickerHolder(catalog, store = store)

        try {
            holder.connectAndOpenPickerSession(delegate)
            awaitPickerState(holder) { it.catalog != null }

            holder.onEvent(EntryUiEvent.ModelEffortClicked("high"))
            assertEquals(ModelSelection("beta", "high"), store.selection)
            assertEquals("high", pickerState(holder).effectiveEffort)

            holder.onEvent(EntryUiEvent.ServerDefaultModelClicked)
            holder.onEvent(EntryUiEvent.ModelEffortClicked("low"))
            assertEquals(null, store.selection)
            assertEquals(null, pickerState(holder).selection?.effort)
        } finally {
            holder.close()
        }
    }

    @Test
    fun opening_the_picker_refreshes_recents_from_the_store_without_recording_a_use() {
        val session = retrySession(PICKER_SESSION_ID.value)
        val delegate = RetryFakeGateway(sessions = listOf(session))
        val catalog = PickerFakeGateway(delegate, catalogResult = Result.success(pickerCatalog()))
        val store = FakePickerSelectionStore()
        val holder = pickerHolder(catalog, store = store)

        try {
            holder.connectAndOpenPickerSession(delegate)
            awaitPickerState(holder) { it.catalog != null }

            store.recordUse(PICKER_ENDPOINT, "beta")
            val seededUses = store.useLog.size
            holder.onEvent(EntryUiEvent.ModelPickerClicked)

            val picker = pickerState(holder)
            assertEquals(true, picker.isPickerOpen)
            assertEquals(listOf("beta"), picker.recents)

            holder.onEvent(EntryUiEvent.ModelRowClicked("beta"))
            holder.onEvent(EntryUiEvent.ModelFavoriteClicked("alpha", true))
            assertEquals(seededUses, store.useLog.size)

            holder.onEvent(EntryUiEvent.ModelPickerDismissed)
            assertEquals(false, pickerState(holder).isPickerOpen)
        } finally {
            holder.close()
        }
    }

    @Test
    fun a_favorite_toggle_updates_the_store_and_the_picker_favorites() {
        val session = retrySession(PICKER_SESSION_ID.value)
        val delegate = RetryFakeGateway(sessions = listOf(session))
        val catalog = PickerFakeGateway(delegate, catalogResult = Result.success(pickerCatalog()))
        val store = FakePickerSelectionStore()
        val holder = pickerHolder(catalog, store = store)

        try {
            holder.connectAndOpenPickerSession(delegate)
            awaitPickerState(holder) { it.catalog != null }

            holder.onEvent(EntryUiEvent.ModelFavoriteClicked("beta", true))
            assertEquals(setOf("beta"), pickerState(holder).favorites)
            assertEquals(listOf(Triple(PICKER_ENDPOINT, "beta", true)), store.favoriteCalls)
        } finally {
            holder.close()
        }
    }

    @Test
    fun the_create_session_path_arms_the_catalog_fetch_when_the_composer_lands() {
        val gateway = PickerCreateFakeGateway()
        val holder = pickerHolder(gateway, store = FakePickerSelectionStore())

        try {
            holder.onEvent(EntryUiEvent.AddGatewayConnectionClicked)
            holder.onEvent(EntryUiEvent.EndpointChanged(PICKER_ENDPOINT))
            holder.onEvent(EntryUiEvent.BearerCredentialChanged("memory-only-token"))
            holder.onEvent(EntryUiEvent.VerifyGatewayConnectionClicked)
            awaitRetryState(holder) { it.isConnected }
            assertEquals(0, gateway.fetchCount)

            holder.onEvent(EntryUiEvent.CreateSessionClicked)
            holder.onEvent(EntryUiEvent.ConfirmCreateSessionClicked)
            awaitRetryState(holder) { it.sessionList?.openedSession?.session?.id == PICKER_CREATED_SESSION_ID }
            awaitPickerState(holder) { it.catalog != null }

            assertEquals(1, gateway.fetchCount)
            val picker = pickerState(holder)
            assertEquals(pickerCatalog(), picker.catalog)
            assertEquals(true, picker.showsSelectionControl)
            assertEquals("Server default", picker.controlLabel)
        } finally {
            holder.close()
        }
    }

    private fun pickerState(holder: EntryStateHolder): ModelPickerUiState =
        requireNotNull(requireNotNull(holder.uiState.value.sessionList).modelPicker)
}
