package org.mercurio.client.feature.entry.presentation

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mercurio.client.feature.entry.domain.SessionDraftKey
import org.mercurio.client.feature.entry.domain.SessionHistory
import org.mercurio.client.feature.entry.domain.SessionId
import org.mercurio.client.feature.entry.domain.SessionPage
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EntryStateHolderSharedTextTest {
    @Test
    fun shared_text_before_the_connection_verifies_is_discarded_with_a_truthful_message() {
        val gateway = FakeSessionGateway()
        val holder =
            holder(
                repository = FakeGatewayConnectionRepository(),
                verifier = { _, _ -> clientManifestCapabilities() },
                gateway = gateway,
            )

        holder.onEvent(EntryUiEvent.SharedText("text/plain", "Shared words", false, false))

        val state = holder.uiState.value
        assertEquals(InboundLinkNoticeCategory.SHARE_NEEDS_CONNECTION, state.inboundLinkNotice)
        assertNull(state.sessionList)
        assertTrue(gateway.operations.isEmpty())
        holder.close()
    }

    @Test
    fun a_non_text_share_is_rejected_with_a_clear_message_and_keeps_no_text() =
        runTest {
            val gateway = FakeSessionGateway()
            gateway.enqueueList(SessionPage(listOf(entrySession("session-one")), null))
            val holder =
                holder(
                    repository = FakeGatewayConnectionRepository(),
                    verifier = { _, _ -> clientManifestCapabilities() },
                    gateway = gateway,
                )
            verify(holder)
            awaitSessions(holder, "session-one")
            gateway.operations.clear()

            holder.onEvent(EntryUiEvent.SharedText("image/png", null, true, false))

            val state = holder.uiState.value
            assertEquals(InboundLinkNoticeCategory.SHARE_UNSUPPORTED, state.inboundLinkNotice)
            assertTrue(gateway.operations.isEmpty())
            holder.close()
        }

    @Test
    fun a_multiple_share_is_rejected_even_when_it_carries_text() =
        runTest {
            val gateway = FakeSessionGateway()
            gateway.enqueueList(SessionPage(listOf(entrySession("session-one")), null))
            val holder =
                holder(
                    repository = FakeGatewayConnectionRepository(),
                    verifier = { _, _ -> clientManifestCapabilities() },
                    gateway = gateway,
                )
            verify(holder)
            awaitSessions(holder, "session-one")

            holder.onEvent(EntryUiEvent.SharedText("text/plain", "Shared words", false, true))

            assertEquals(
                InboundLinkNoticeCategory.SHARE_UNSUPPORTED,
                holder.uiState.value.inboundLinkNotice,
            )
            holder.close()
        }

    @Test
    fun a_blank_text_share_is_rejected_with_a_clear_message() =
        runTest {
            val gateway = FakeSessionGateway()
            gateway.enqueueList(SessionPage(listOf(entrySession("session-one")), null))
            val holder =
                holder(
                    repository = FakeGatewayConnectionRepository(),
                    verifier = { _, _ -> clientManifestCapabilities() },
                    gateway = gateway,
                )
            verify(holder)
            awaitSessions(holder, "session-one")

            holder.onEvent(EntryUiEvent.SharedText("text/plain", "   ", false, false))

            assertEquals(
                InboundLinkNoticeCategory.SHARE_UNSUPPORTED,
                holder.uiState.value.inboundLinkNotice,
            )
            holder.close()
        }

    @Test
    fun shared_text_waits_for_the_session_pick_and_prefills_the_composer_without_sending() =
        runTest {
            val sessionId = SessionId("session-one")
            val gateway = FakeSessionGateway()
            gateway.enqueueList(SessionPage(listOf(entrySession(sessionId.value)), null))
            gateway.openedSession = entrySession(sessionId.value)
            gateway.openedHistory = SessionHistory(sessionId, emptyList())
            val draftStore = InMemorySessionDraftStore()
            val holder =
                holder(
                    repository = FakeGatewayConnectionRepository(),
                    verifier = { _, _ -> clientManifestCapabilities() },
                    gateway = gateway,
                    dependencies =
                        EntryStateHolderDependencies(
                            sessionDraftStore = draftStore,
                        ),
                )
            verify(holder)
            awaitSessions(holder, sessionId.value)
            gateway.operations.clear()

            holder.onEvent(EntryUiEvent.SharedText("text/plain", "Shared words", false, false))

            val pickerState = holder.uiState.value
            assertEquals(InboundLinkNoticeCategory.SHARE_PICK_SESSION, pickerState.inboundLinkNotice)
            assertNull(pickerState.sessionList?.openedSession)
            assertTrue(gateway.operations.isEmpty())
            assertNull(
                draftStore.read(SessionDraftKey(holder.uiState.value.endpoint, sessionId)),
            )

            holder.onEvent(EntryUiEvent.SessionClicked(sessionId))

            awaitState(holder, "the opened Session") { it.sessionList?.openedSession != null }
            val opened = requireNotNull(holder.uiState.value.sessionList?.openedSession)
            assertEquals("Shared words", opened.composerText)
            assertFalse(opened.isSending)
            assertEquals(
                "Shared words",
                draftStore.read(SessionDraftKey(holder.uiState.value.endpoint, sessionId)),
            )
            holder.close()
        }

    @Test
    fun a_shared_text_arriving_while_a_session_is_open_returns_to_the_picker() =
        runTest {
            val sessionId = SessionId("session-one")
            val gateway = FakeSessionGateway()
            gateway.enqueueList(SessionPage(listOf(entrySession(sessionId.value)), null))
            gateway.openedSession = entrySession(sessionId.value)
            gateway.openedHistory = SessionHistory(sessionId, emptyList())
            val holder =
                holder(
                    repository = FakeGatewayConnectionRepository(),
                    verifier = { _, _ -> clientManifestCapabilities() },
                    gateway = gateway,
                )
            verify(holder)
            awaitSessions(holder, sessionId.value)
            holder.onEvent(EntryUiEvent.SessionClicked(sessionId))
            awaitState(holder, "the opened Session") { it.sessionList?.openedSession != null }

            holder.onEvent(EntryUiEvent.SharedText("text/plain", "Shared words", false, false))

            val state = holder.uiState.value
            assertNull(state.sessionList?.openedSession)
            assertEquals(InboundLinkNoticeCategory.SHARE_PICK_SESSION, state.inboundLinkNotice)
            holder.close()
        }

    @Test
    fun shared_text_appends_below_an_existing_unsent_draft() =
        runTest {
            val sessionId = SessionId("session-one")
            val gateway = FakeSessionGateway()
            gateway.enqueueList(SessionPage(listOf(entrySession(sessionId.value)), null))
            gateway.openedSession = entrySession(sessionId.value)
            gateway.openedHistory = SessionHistory(sessionId, emptyList())
            val draftStore = InMemorySessionDraftStore()
            val holder =
                holder(
                    repository = FakeGatewayConnectionRepository(),
                    verifier = { _, _ -> clientManifestCapabilities() },
                    gateway = gateway,
                    dependencies =
                        EntryStateHolderDependencies(
                            sessionDraftStore = draftStore,
                        ),
                )
            verify(holder)
            awaitSessions(holder, sessionId.value)
            holder.onEvent(EntryUiEvent.SessionClicked(sessionId))
            awaitState(holder, "the opened Session") { it.sessionList?.openedSession != null }
            holder.onEvent(EntryUiEvent.ComposerTextChanged("Typed but unsent"))
            holder.onEvent(EntryUiEvent.SharedText("text/plain", "Shared words", false, false))

            holder.onEvent(EntryUiEvent.SessionClicked(sessionId))

            awaitState(holder, "the reopened Session") { it.sessionList?.openedSession != null }
            val opened = requireNotNull(holder.uiState.value.sessionList?.openedSession)
            assertEquals("Typed but unsent\n\nShared words", opened.composerText)
            assertEquals(
                "Typed but unsent\n\nShared words",
                draftStore.read(SessionDraftKey(holder.uiState.value.endpoint, sessionId)),
            )
            holder.close()
        }

    @Test
    fun a_pending_share_expires_when_the_app_goes_to_the_background() =
        runTest {
            val sessionId = SessionId("session-one")
            val gateway = FakeSessionGateway()
            gateway.enqueueList(SessionPage(listOf(entrySession(sessionId.value)), null))
            gateway.openedSession = entrySession(sessionId.value)
            gateway.openedHistory = SessionHistory(sessionId, emptyList())
            val holder =
                holder(
                    repository = FakeGatewayConnectionRepository(),
                    verifier = { _, _ -> clientManifestCapabilities() },
                    gateway = gateway,
                )
            verify(holder)
            awaitSessions(holder, sessionId.value)

            holder.onEvent(EntryUiEvent.SharedText("text/plain", "Shared words", false, false))
            holder.onEvent(EntryUiEvent.AppEnteredBackground)
            holder.onEvent(EntryUiEvent.SessionClicked(sessionId))

            awaitState(holder, "the opened Session") { it.sessionList?.openedSession != null }
            val opened = requireNotNull(holder.uiState.value.sessionList?.openedSession)
            assertEquals("", opened.composerText)
            holder.close()
        }

    @Test
    fun dismissing_the_picker_discards_the_shared_text() =
        runTest {
            val sessionId = SessionId("session-one")
            val gateway = FakeSessionGateway()
            gateway.enqueueList(SessionPage(listOf(entrySession(sessionId.value)), null))
            gateway.openedSession = entrySession(sessionId.value)
            gateway.openedHistory = SessionHistory(sessionId, emptyList())
            val holder =
                holder(
                    repository = FakeGatewayConnectionRepository(),
                    verifier = { _, _ -> clientManifestCapabilities() },
                    gateway = gateway,
                )
            verify(holder)
            awaitSessions(holder, sessionId.value)

            holder.onEvent(EntryUiEvent.SharedText("text/plain", "Shared words", false, false))
            holder.onEvent(EntryUiEvent.SharedTextDismissed)
            holder.onEvent(EntryUiEvent.SessionClicked(sessionId))

            awaitState(holder, "the opened Session") { it.sessionList?.openedSession != null }
            val opened = requireNotNull(holder.uiState.value.sessionList?.openedSession)
            assertEquals("", opened.composerText)
            holder.close()
        }
}
