package org.mercurio.client.feature.entry.presentation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mercurio.client.feature.entry.application.EntryState
import org.mercurio.client.feature.entry.domain.ImageLoadingSettingsStore
import org.mercurio.client.feature.entry.domain.InstallSource
import org.mercurio.client.feature.entry.domain.SessionPage
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class EntryStateHolderSettingsTest {
    @Test
    fun opening_settings_keeps_the_loaded_session_list_and_query() =
        runTest {
            val gateway = FakeSessionGateway()
            gateway.enqueueList(SessionPage(listOf(entrySession("session-one", "Listed title")), null))
            val holder =
                holder(
                    repository = FakeGatewayConnectionRepository(),
                    verifier = { _, _ ->
                        clientManifestCapabilities()
                    },
                    gateway = gateway,
                )

            try {
                verify(holder)
                awaitSessions(holder, "session-one")
                holder.onEvent(EntryUiEvent.SessionSearchQueryChanged("Listed"))

                holder.onEvent(EntryUiEvent.OpenSettingsClicked)

                val state = holder.uiState.value
                assertTrue(state.isSettingsOpen)
                val list = requireNotNull(state.sessionList)
                assertEquals("Listed", list.searchQuery)
                assertEquals("Listed title", list.visibleSessions.single().title)
            } finally {
                holder.close()
            }
        }

    @Test
    fun closing_settings_restores_the_session_list_without_a_new_request() =
        runTest {
            val gateway = FakeSessionGateway()
            gateway.enqueueList(SessionPage(listOf(entrySession("session-one", "Listed title")), null))
            val holder =
                holder(
                    repository = FakeGatewayConnectionRepository(),
                    verifier = { _, _ ->
                        clientManifestCapabilities()
                    },
                    gateway = gateway,
                )

            try {
                verify(holder)
                awaitSessions(holder, "session-one")
                holder.onEvent(EntryUiEvent.SessionSearchQueryChanged("Listed"))
                val requestsAfterLoad = gateway.listRequests.size

                holder.onEvent(EntryUiEvent.OpenSettingsClicked)
                holder.onEvent(EntryUiEvent.CloseSettingsClicked)

                val state = holder.uiState.value
                assertFalse(state.isSettingsOpen)
                val list = requireNotNull(state.sessionList)
                assertEquals("Listed", list.searchQuery)
                assertEquals("Listed title", list.visibleSessions.single().title)
                assertEquals(requestsAfterLoad, gateway.listRequests.size)
            } finally {
                holder.close()
            }
        }

    @Test
    fun opening_settings_requires_a_connected_session_surface() {
        val holder =
            EntryStateHolder(
                initialState = EntryState(isGatewayConnectionConfigured = false),
            )

        try {
            holder.onEvent(EntryUiEvent.OpenSettingsClicked)

            assertFalse(holder.uiState.value.isSettingsOpen)
        } finally {
            holder.close()
        }
    }

    @Test
    fun opening_settings_is_rejected_while_creation_is_submitting() =
        runTest {
            val created = entrySession("created", title = "Created")
            val createStarted = CountDownLatch(1)
            val createRelease = CountDownLatch(1)
            val gateway = ScriptedCreateGateway(entrySession("session-one", title = "Listed title"), created)
            gateway.onCreate = {
                createStarted.countDown()
                assertTrue(createRelease.await(ASYNC_TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))
            }
            val holder = scriptedHolder(gateway, Dispatchers.Default)

            try {
                verifyScripted(holder)
                holder.onEvent(EntryUiEvent.CreateSessionClicked)
                assertTrue(createStarted.await(ASYNC_TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))
                assertTrue(requireNotNull(holder.uiState.value.sessionList).createSession != null)

                holder.onEvent(EntryUiEvent.OpenSettingsClicked)
                runBlocking { holder.awaitStateChangesApplied() }

                assertFalse(holder.uiState.value.isSettingsOpen)
            } finally {
                gateway.onCreate = null
                createRelease.countDown()
                holder.close()
            }
        }

    @Test
    fun auto_load_defaults_on_when_no_decision_is_stored() {
        val holder =
            EntryStateHolder(
                initialState = EntryState(),
                dependencies =
                    EntryStateHolderDependencies(
                        imageLoadingSettingsStore = FakeImageLoadingSettingsStore(),
                    ),
            )

        try {
            assertTrue(holder.uiState.value.autoLoadImages)
        } finally {
            holder.close()
        }
    }

    @Test
    fun removing_the_connection_keeps_media_and_build_metadata() {
        val store = FakeImageLoadingSettingsStore(enabled = true)
        val holder =
            EntryStateHolder(
                initialState =
                    EntryState(
                        isGatewayConnectionConfigured = true,
                        buildNumber = 412L,
                        installSource = InstallSource.GOOGLE_PLAY,
                    ),
                dependencies = EntryStateHolderDependencies(imageLoadingSettingsStore = store),
            )

        try {
            holder.onEvent(EntryUiEvent.AutoLoadImagesToggleClicked)
            runBlocking { holder.awaitStateChangesApplied() }
            assertFalse(holder.uiState.value.autoLoadImages)

            holder.releaseGatewayConnection()

            val state = holder.uiState.value
            assertFalse(state.autoLoadImages)
            assertEquals(412L, state.buildNumber)
            assertEquals(InstallSource.GOOGLE_PLAY, state.installSource)
        } finally {
            holder.close()
        }
    }

    @Test
    fun the_auto_load_switch_persists_and_updates_the_surface() {
        val store = FakeImageLoadingSettingsStore(enabled = false)
        val holder =
            EntryStateHolder(
                initialState = EntryState(),
                dependencies = EntryStateHolderDependencies(imageLoadingSettingsStore = store),
            )

        try {
            assertFalse(holder.uiState.value.autoLoadImages)
            holder.onEvent(EntryUiEvent.AutoLoadImagesToggleClicked)
            runBlocking { holder.awaitStateChangesApplied() }
            assertTrue(holder.uiState.value.autoLoadImages)
            assertTrue(store.loadEnabled())
            holder.onEvent(EntryUiEvent.AutoLoadImagesToggleClicked)
            runBlocking { holder.awaitStateChangesApplied() }
            assertFalse(holder.uiState.value.autoLoadImages)
            assertFalse(store.loadEnabled())
        } finally {
            holder.close()
        }
    }
}

private class FakeImageLoadingSettingsStore(
    private var enabled: Boolean = true,
) : ImageLoadingSettingsStore {
    override fun loadEnabled(): Boolean = enabled

    override fun saveEnabled(enabled: Boolean) {
        this.enabled = enabled
    }
}
