package org.hermesnative.client.feature.entry.presentation

import org.hermesnative.client.feature.entry.domain.ModelCatalog
import org.hermesnative.client.feature.entry.domain.ModelCatalogEntry
import org.hermesnative.client.feature.entry.domain.ModelEffortOptions
import org.hermesnative.client.feature.entry.domain.ModelSelection
import org.junit.Assert.assertEquals
import org.junit.Test

class ModelPickerOrderingTest {
    @Test
    fun favorites_come_first_then_recents_then_the_rest_in_catalog_order() {
        val catalog = pickerCatalog()
        val ordered =
            orderedModelEntries(
                catalog = catalog,
                favorites = setOf("gamma"),
                recents = listOf("beta", "delta", "alpha"),
            )
        assertEquals(listOf("gamma", "beta", "delta", "alpha", "epsilon"), ordered.map { it.id })
    }

    @Test
    fun a_favorited_model_is_not_repeated_in_the_recents_group() {
        val catalog = pickerCatalog()
        val ordered =
            orderedModelEntries(
                catalog = catalog,
                favorites = setOf("beta"),
                recents = listOf("beta", "alpha"),
            )
        assertEquals(listOf("beta", "alpha", "gamma", "delta", "epsilon"), ordered.map { it.id })
    }

    @Test
    fun favorites_or_recents_that_name_an_unknown_model_are_dropped() {
        val catalog = pickerCatalog()
        val ordered =
            orderedModelEntries(
                catalog = catalog,
                favorites = setOf("retired-model"),
                recents = listOf("retired-model", "epsilon"),
            )
        assertEquals(listOf("epsilon", "alpha", "beta", "gamma", "delta"), ordered.map { it.id })
    }

    @Test
    fun an_empty_picker_falls_back_to_pure_catalog_order() {
        val catalog = pickerCatalog()
        val ordered = orderedModelEntries(catalog = catalog, favorites = emptySet(), recents = emptyList())
        assertEquals(catalog.entries.map { it.id }, ordered.map { it.id })
    }

    @Test
    fun the_control_label_names_the_selected_entry_display_name_and_the_cleared_state_names_the_default() {
        val catalog = pickerCatalog()
        val overridden = ModelPickerUiState(catalog = catalog, selection = ModelSelection("beta"))
        assertEquals("Second Model", overridden.controlLabel)

        val cleared = ModelPickerUiState(catalog = catalog, selection = null)
        assertEquals("Server default", cleared.controlLabel)

        val staleOverride = ModelPickerUiState(catalog = catalog, selection = ModelSelection("retired-model"))
        assertEquals("retired-model", staleOverride.controlLabel)
    }

    @Test
    fun the_effort_control_appears_only_for_the_advertising_selected_entry() {
        val catalog = pickerCatalog()
        val selectedAdvertising =
            ModelPickerUiState(
                catalog = catalog,
                selection = ModelSelection("alpha"),
            )
        assertEquals(listOf("low", "medium", "high"), selectedAdvertising.selectedEfforts)
        assertEquals("medium", selectedAdvertising.effectiveEffort)

        val selectedWithOverride =
            ModelPickerUiState(
                catalog = catalog,
                selection = ModelSelection("alpha", "high"),
            )
        assertEquals("high", selectedWithOverride.effectiveEffort)

        val selectedSilent = ModelPickerUiState(catalog = catalog, selection = ModelSelection("beta"))
        assertEquals(emptyList<String>(), selectedSilent.selectedEfforts)
        assertEquals(null, selectedSilent.effectiveEffort)

        val cleared = ModelPickerUiState(catalog = catalog, selection = null)
        assertEquals(emptyList<String>(), cleared.selectedEfforts)
    }

    @Test
    fun the_control_renders_only_with_an_advertised_loaded_catalog_and_the_copy_only_without_the_row() {
        val catalog = pickerCatalog()
        assertEquals(
            true,
            ModelPickerUiState(capabilityAdvertised = true, catalog = catalog).showsSelectionControl,
        )
        assertEquals(
            false,
            ModelPickerUiState(capabilityAdvertised = true, catalog = null, isFetchingCatalog = true)
                .showsSelectionControl,
        )
        assertEquals(
            false,
            ModelPickerUiState(capabilityAdvertised = true, catalog = null, catalogFetchFailed = true)
                .showsSelectionControl,
        )
        assertEquals(false, ModelPickerUiState().showsSelectionControl)

        assertEquals(
            true,
            ModelPickerUiState(capabilityKnown = true, capabilityAdvertised = false).showsUnavailableCopy,
        )
        assertEquals(
            false,
            ModelPickerUiState(capabilityKnown = false, capabilityAdvertised = false).showsUnavailableCopy,
        )
    }

    private fun pickerCatalog(): ModelCatalog =
        ModelCatalog(
            discriminator = "hermes.api_server.run_models",
            defaultModel = "alpha",
            entries =
                listOf(
                    ModelCatalogEntry(
                        "alpha",
                        "First Model",
                        "opencode-go",
                        ModelEffortOptions(listOf("low", "medium", "high"), "medium"),
                    ),
                    ModelCatalogEntry("beta", "Second Model", "opencode-go", null),
                    ModelCatalogEntry("gamma", "Third Model", null, null),
                    ModelCatalogEntry("delta", "Fourth Model", null, ModelEffortOptions(listOf("low"), "low")),
                    ModelCatalogEntry("epsilon", "Fifth Model", null, null),
                ),
        )
}
