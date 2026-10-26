package org.mercurio.client.feature.entry.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelSelectionStoreTest {
    @Test
    fun the_in_memory_default_holds_no_selection_no_favorites_and_no_recents() {
        val endpoint = "https://gateway.example"

        assertTrue(NoOpModelSelectionStore is ModelSelectionStore)
        assertNull(NoOpModelSelectionStore.read(endpoint))
        assertTrue(NoOpModelSelectionStore.favorites(endpoint).isEmpty())
        assertTrue(NoOpModelSelectionStore.recents(endpoint).isEmpty())

        NoOpModelSelectionStore.write(endpoint, ModelSelection(model = "any-model", effort = "high"))
        NoOpModelSelectionStore.setFavorite(endpoint, "any-model", favorite = true)
        NoOpModelSelectionStore.recordUse(endpoint, "any-model")
        NoOpModelSelectionStore.clear(endpoint)
        NoOpModelSelectionStore.clearEndpoint(endpoint)

        assertNull(NoOpModelSelectionStore.read(endpoint))
        assertTrue(NoOpModelSelectionStore.favorites(endpoint).isEmpty())
        assertTrue(NoOpModelSelectionStore.recents(endpoint).isEmpty())
    }

    @Test
    fun a_selection_carries_the_model_and_keeps_the_effort_optional() {
        val bare = ModelSelection(model = "deepseek-v4-flash")
        val combined = ModelSelection(model = "deepseek-v4-flash", effort = "medium")

        assertEquals("deepseek-v4-flash", bare.model)
        assertNull(bare.effort)
        assertEquals("medium", combined.effort)
    }

    @Test
    fun a_written_selection_round_trips_through_a_fake_store() {
        val store = RecordingSelectionStore()

        store.write("https://gateway.example", ModelSelection("deepseek-v4-flash", "medium"))

        assertEquals(ModelSelection("deepseek-v4-flash", "medium"), store.read("https://gateway.example"))
        store.clear("https://gateway.example")
        assertNull(store.read("https://gateway.example"))
    }

    private class RecordingSelectionStore : ModelSelectionStore {
        private var selection: ModelSelection? = null

        override fun read(endpoint: String): ModelSelection? = selection

        override fun write(
            endpoint: String,
            selection: ModelSelection,
        ) {
            this.selection = selection
        }

        override fun clear(endpoint: String) {
            selection = null
        }

        override fun favorites(endpoint: String): Set<String> = emptySet()

        override fun setFavorite(
            endpoint: String,
            modelId: String,
            favorite: Boolean,
        ) = Unit

        override fun recents(endpoint: String): List<String> = emptyList()

        override fun recordUse(
            endpoint: String,
            modelId: String,
        ) = Unit
    }
}
