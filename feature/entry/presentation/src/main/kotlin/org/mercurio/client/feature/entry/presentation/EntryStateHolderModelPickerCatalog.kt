package org.mercurio.client.feature.entry.presentation

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.mercurio.client.feature.entry.domain.GatewayException
import org.mercurio.client.feature.entry.domain.ModelCatalog

/**
 * Fetches the model catalog once the picker becomes relevant on an advertised row:
 * the fetch runs at most once per armed picker state — each new connection and each
 * re-verified connection re-arms it — and any failure lands as
 * [ModelPickerUiState.catalogFetchFailed] — control hidden, connection untouched.
 */
internal fun EntryStateHolder.maybeFetchModelCatalog() {
    val request =
        synchronized(sessionRequestLock) {
            val state = mutableUiState.value
            val picker = state.sessionList?.modelPicker
            if (picker?.catalogFetchNeeded != true) {
                null
            } else {
                claimModelCatalogFetch(state.endpoint)
            }
        }
    if (request != null) {
        scope.launch { runModelCatalogFetch(request) }
    }
}

private fun EntryStateHolder.claimModelCatalogFetch(endpoint: String): ModelCatalogFetchRequest? {
    val port = catalogGateway
    if (port == null) {
        updateModelPickerLocked { it.copy(catalogFetchFailed = true) }
        return null
    }
    updateModelPickerLocked { it.copy(isFetchingCatalog = true) }
    return ModelCatalogFetchRequest(port, endpoint, connectionGeneration)
}

internal fun EntryStateHolder.runModelCatalogFetch(request: ModelCatalogFetchRequest) {
    val catalog =
        try {
            request.port.fetchModelCatalog()
        } catch (error: CancellationException) {
            throw error
        } catch (_: GatewayException) {
            null
        } catch (_: Exception) {
            null
        }
    applyModelCatalogResult(request, catalog)
}

internal fun EntryStateHolder.applyModelCatalogResult(
    request: ModelCatalogFetchRequest,
    catalog: ModelCatalog?,
) {
    synchronized(sessionRequestLock) {
        val state = mutableUiState.value
        if (connectionGeneration == request.connectionGeneration && state.endpoint == request.endpoint) {
            updateModelPickerLocked {
                ModelPickerUiState(
                    capabilityKnown = true,
                    capabilityAdvertised = true,
                    catalog = catalog,
                    catalogFetchFailed = catalog == null,
                    selection = modelSelectionStore.read(request.endpoint),
                    favorites = modelSelectionStore.favorites(request.endpoint),
                    recents = modelSelectionStore.recents(request.endpoint),
                )
            }
        }
    }
}

/**
 * Applies a picker-state transform under the session lock. Callers either hold the lock
 * themselves or arrive through the fetch path, which re-checks the generation.
 */
internal fun EntryStateHolder.updateModelPickerLocked(transform: (ModelPickerUiState) -> ModelPickerUiState) {
    val state = mutableUiState.value
    val sessionList = state.sessionList ?: return
    mutableUiState.value = state.copy(sessionList = sessionList.copy(modelPicker = transform(sessionList.modelPicker)))
}
