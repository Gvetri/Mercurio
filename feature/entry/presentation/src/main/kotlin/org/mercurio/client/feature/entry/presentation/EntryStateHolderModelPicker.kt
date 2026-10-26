package org.mercurio.client.feature.entry.presentation

import org.mercurio.client.feature.entry.domain.ModelSelection

/**
 * Composer model-picker event handlers. Selection, effort, and favorites persist through
 * the per-endpoint [ModelSelectionStore]; recents are only recorded when a submission is
 * admitted (the #277 wiring), so a selection re-reads the store instead of writing it.
 * An effort without a selected model is unrepresentable: [ModelSelection] only carries
 * effort with its model, and the handler drops the event when no model is selected.
 */
internal fun EntryStateHolder.dispatchModelPickerEvent(event: EntryUiEvent.ModelPicker) {
    when (event) {
        is EntryUiEvent.ModelPickerClicked -> openModelPicker()
        is EntryUiEvent.ModelPickerDismissed -> closeModelPicker()
        is EntryUiEvent.ServerDefaultModelClicked -> clearModelSelection()
        is EntryUiEvent.ModelRowClicked -> selectModel(event.modelId)
        is EntryUiEvent.ModelEffortClicked -> selectModelEffort(event.effort)
        is EntryUiEvent.ModelFavoriteClicked -> setModelFavorite(event.modelId, event.favorite)
    }
}

private fun EntryStateHolder.openModelPicker() =
    synchronized(sessionRequestLock) {
        refreshModelPickerFromStoreLocked()
        updateModelPickerLocked { it.copy(isPickerOpen = true) }
    }

private fun EntryStateHolder.closeModelPicker() =
    synchronized(sessionRequestLock) {
        updateModelPickerLocked { it.copy(isPickerOpen = false) }
    }

private fun EntryStateHolder.selectModel(modelId: String) {
    synchronized(sessionRequestLock) {
        val endpoint = mutableUiState.value.endpoint
        val current = modelSelectionStore.read(endpoint)
        val next = current?.takeIf { it.model == modelId } ?: ModelSelection(modelId)
        modelSelectionStore.write(endpoint, next)
        refreshModelPickerFromStoreLocked()
    }
}

private fun EntryStateHolder.selectModelEffort(effort: String) {
    synchronized(sessionRequestLock) {
        val endpoint = mutableUiState.value.endpoint
        val current = modelSelectionStore.read(endpoint)
        if (current != null) {
            modelSelectionStore.write(endpoint, current.copy(effort = effort))
            refreshModelPickerFromStoreLocked()
        }
    }
}

private fun EntryStateHolder.clearModelSelection() {
    synchronized(sessionRequestLock) {
        val endpoint = mutableUiState.value.endpoint
        modelSelectionStore.clear(endpoint)
        refreshModelPickerFromStoreLocked()
    }
}

private fun EntryStateHolder.setModelFavorite(
    modelId: String,
    favorite: Boolean,
) {
    synchronized(sessionRequestLock) {
        val endpoint = mutableUiState.value.endpoint
        modelSelectionStore.setFavorite(endpoint, modelId, favorite)
        refreshModelPickerFromStoreLocked()
    }
}

private fun EntryStateHolder.refreshModelPickerFromStoreLocked() {
    val endpoint = mutableUiState.value.endpoint
    updateModelPickerLocked {
        it.copy(
            selection = modelSelectionStore.read(endpoint),
            favorites = modelSelectionStore.favorites(endpoint),
            recents = modelSelectionStore.recents(endpoint),
        )
    }
}
