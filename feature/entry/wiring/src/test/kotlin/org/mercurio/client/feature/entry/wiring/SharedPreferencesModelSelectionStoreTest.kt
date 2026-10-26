package org.mercurio.client.feature.entry.wiring

import org.mercurio.client.feature.entry.domain.ModelSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SharedPreferencesModelSelectionStoreTest {
    @Test
    fun a_written_override_round_trips_and_survives_a_restarted_store() {
        val context = RuntimeEnvironment.getApplication()
        val endpoint = "https://gateway.example/profile-a"

        SharedPreferencesModelSelectionStore(context).write(endpoint, ModelSelection("deepseek-v4-flash", "medium"))

        val restored = SharedPreferencesModelSelectionStore(context).read(endpoint)
        assertEquals(ModelSelection("deepseek-v4-flash", "medium"), restored)
    }

    @Test
    fun clearing_the_override_returns_reads_to_the_server_default_state() {
        val store = SharedPreferencesModelSelectionStore(RuntimeEnvironment.getApplication())
        val endpoint = "https://gateway.example/profile-a"
        store.write(endpoint, ModelSelection("deepseek-v4-flash", "medium"))

        store.clear(endpoint)

        assertNull(store.read(endpoint))
        store.write(endpoint, ModelSelection("hermes-agent"))
        assertEquals(ModelSelection("hermes-agent"), store.read(endpoint))
    }

    @Test
    fun selections_never_leak_across_endpoints() {
        val store = SharedPreferencesModelSelectionStore(RuntimeEnvironment.getApplication())
        val endpoint = "https://gateway.example/profile-a"
        val otherEndpoint = "https://gateway.example/profile-b"

        store.write(endpoint, ModelSelection("deepseek-v4-flash", "medium"))

        assertNull(store.read(otherEndpoint))
        store.clear(endpoint)
        assertNull(store.read(endpoint))
    }

    @Test
    fun favorites_toggle_per_endpoint_and_persist_across_restarts() {
        val context = RuntimeEnvironment.getApplication()
        val endpoint = "https://gateway.example/profile-a"
        val otherEndpoint = "https://gateway.example/profile-b"
        SharedPreferencesModelSelectionStore(context).setFavorite(endpoint, "deepseek-v4-flash", favorite = true)
        SharedPreferencesModelSelectionStore(context).setFavorite(otherEndpoint, "hermes-agent", favorite = true)

        val restarted = SharedPreferencesModelSelectionStore(context)
        assertEquals(setOf("deepseek-v4-flash"), restarted.favorites(endpoint))
        assertEquals(setOf("hermes-agent"), restarted.favorites(otherEndpoint))

        restarted.setFavorite(endpoint, "deepseek-v4-flash", favorite = false)
        assertTrue(restarted.favorites(endpoint).isEmpty())
        assertEquals(setOf("hermes-agent"), restarted.favorites(otherEndpoint))
    }

    @Test
    fun recents_are_most_recent_first_deduplicated_and_bounded() {
        val store = SharedPreferencesModelSelectionStore(RuntimeEnvironment.getApplication())
        val endpoint = "https://gateway.example/profile-a"

        (1..12).forEach { index -> store.recordUse(endpoint, "model-$index") }
        store.recordUse(endpoint, "model-12")

        val recents = store.recents(endpoint)
        assertEquals(10, recents.size)
        assertEquals("model-12", recents.first())
        assertEquals("model-11", recents[1])
        assertEquals("model-3", recents.last())
    }

    @Test
    fun recents_are_isolated_per_endpoint() {
        val store = SharedPreferencesModelSelectionStore(RuntimeEnvironment.getApplication())
        store.recordUse("https://gateway.example/profile-a", "model-a")
        store.recordUse("https://gateway.example/profile-b", "model-b")

        assertEquals(listOf("model-a"), store.recents("https://gateway.example/profile-a"))
        assertEquals(listOf("model-b"), store.recents("https://gateway.example/profile-b"))
    }

    @Test
    fun clearing_one_endpoint_purges_only_that_endpoints_records() {
        val store = SharedPreferencesModelSelectionStore(RuntimeEnvironment.getApplication())
        val endpoint = "https://gateway.example/profile-a"
        val otherEndpoint = "https://gateway.example/profile-b"
        store.write(endpoint, ModelSelection("deepseek-v4-flash", "medium"))
        store.setFavorite(endpoint, "deepseek-v4-flash", favorite = true)
        store.recordUse(endpoint, "deepseek-v4-flash")
        store.write(otherEndpoint, ModelSelection("hermes-agent"))
        store.setFavorite(otherEndpoint, "hermes-agent", favorite = true)
        store.recordUse(otherEndpoint, "hermes-agent")

        store.clearEndpoint(endpoint)

        assertNull(store.read(endpoint))
        assertTrue(store.favorites(endpoint).isEmpty())
        assertTrue(store.recents(endpoint).isEmpty())
        assertEquals(ModelSelection("hermes-agent"), store.read(otherEndpoint))
        assertEquals(setOf("hermes-agent"), store.favorites(otherEndpoint))
        assertEquals(listOf("hermes-agent"), store.recents(otherEndpoint))
    }
}
