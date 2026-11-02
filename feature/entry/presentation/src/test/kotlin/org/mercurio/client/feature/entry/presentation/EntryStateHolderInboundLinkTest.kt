package org.mercurio.client.feature.entry.presentation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mercurio.client.feature.entry.domain.GatewayErrorCategory
import org.mercurio.client.feature.entry.domain.GatewayException
import org.mercurio.client.feature.entry.domain.Session
import org.mercurio.client.feature.entry.domain.SessionGatewayPort
import org.mercurio.client.feature.entry.domain.SessionHistory
import org.mercurio.client.feature.entry.domain.SessionId
import org.mercurio.client.feature.entry.domain.SessionListRequest
import org.mercurio.client.feature.entry.domain.SessionPage
import org.mercurio.client.feature.entry.domain.SessionPinResult
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EntryStateHolderInboundLinkTest {
    @Test
    fun a_session_link_before_the_connection_verifies_is_discarded_with_a_truthful_message() {
        val gateway = FakeSessionGateway()
        val holder =
            holder(
                repository = FakeGatewayConnectionRepository(),
                verifier = { _, _ -> clientManifestCapabilities() },
                gateway = gateway,
            )

        holder.applyInboundLink(EntryLink.Session("session-one"))

        val state = holder.uiState.value
        assertEquals(InboundLinkNoticeCategory.SESSION_LINK_NEEDS_CONNECTION, state.inboundLinkNotice)
        assertTrue(!state.isConnected)
        assertNull(state.sessionList)
        assertTrue(gateway.operations.isEmpty())
        holder.close()
    }

    @Test
    fun a_compose_link_before_the_connection_verifies_is_discarded_with_a_truthful_message() {
        val gateway = FakeSessionGateway()
        val holder =
            holder(
                repository = FakeGatewayConnectionRepository(),
                verifier = { _, _ -> clientManifestCapabilities() },
                gateway = gateway,
            )

        holder.applyInboundLink(EntryLink.Compose)

        assertEquals(
            InboundLinkNoticeCategory.COMPOSE_LINK_NEEDS_CONNECTION,
            holder.uiState.value.inboundLinkNotice,
        )
        assertTrue(gateway.operations.isEmpty())
        holder.close()
    }

    @Test
    fun a_session_link_opens_an_id_that_is_not_in_the_loaded_page() =
        runTest {
            val gateway = FakeSessionGateway()
            gateway.enqueueList(SessionPage(emptyList(), null))
            gateway.openedSession = entrySession("linked-session")
            gateway.openedHistory = SessionHistory(SessionId("linked-session"), emptyList())
            val holder =
                holder(
                    repository = FakeGatewayConnectionRepository(),
                    verifier = { _, _ -> clientManifestCapabilities() },
                    gateway = gateway,
                )
            verify(holder)
            awaitState(holder, "the loaded Session list") { it.sessionList?.isLoading == false }

            holder.applyInboundLink(EntryLink.Session("linked-session"))

            awaitState(holder, "the linked Session") {
                it.sessionList?.openedSession?.session?.id == SessionId("linked-session")
            }
            val list = requireNotNull(holder.uiState.value.sessionList)
            assertEquals(listOf("open", "history"), gateway.operations)
            assertTrue(list.sessions.any { it.id == SessionId("linked-session") })
            assertNull(holder.uiState.value.inboundLinkNotice)
            holder.close()
        }

    @Test
    fun an_unknown_session_id_lands_on_the_list_with_the_link_message() =
        runTest {
            val gateway = FakeSessionGateway()
            gateway.enqueueList(SessionPage(listOf(entrySession("listed-session")), null))
            gateway.openFailure = GatewayException(GatewayErrorCategory.GATEWAY_REQUEST_FAILED)
            val holder =
                holder(
                    repository = FakeGatewayConnectionRepository(),
                    verifier = { _, _ -> clientManifestCapabilities() },
                    gateway = gateway,
                )
            verify(holder)
            awaitSessions(holder, "listed-session")

            holder.applyInboundLink(EntryLink.Session("foreign-session"))

            awaitState(holder, "the unknown link message") {
                it.inboundLinkNotice == InboundLinkNoticeCategory.SESSION_LINK_UNKNOWN
            }
            val list = requireNotNull(holder.uiState.value.sessionList)
            assertNull(list.openedSession)
            assertNull(list.openingSessionId)
            assertEquals(listOf("open"), gateway.operations)
            holder.close()
        }

    @Test
    fun a_session_link_without_a_usable_id_reports_the_unknown_message_without_a_request() =
        runTest {
            val gateway = FakeSessionGateway()
            gateway.enqueueList(SessionPage(listOf(entrySession("listed-session")), null))
            val holder =
                holder(
                    repository = FakeGatewayConnectionRepository(),
                    verifier = { _, _ -> clientManifestCapabilities() },
                    gateway = gateway,
                )
            verify(holder)
            awaitSessions(holder, "listed-session")

            holder.applyInboundLink(EntryLink.Session(null))

            assertEquals(InboundLinkNoticeCategory.SESSION_LINK_UNKNOWN, holder.uiState.value.inboundLinkNotice)
            assertTrue(gateway.operations.isEmpty())
            holder.close()
        }

    @Test
    fun a_compose_link_focuses_the_open_composer_without_sending() =
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
            val before = requireNotNull(holder.uiState.value.sessionList?.openedSession).composerFocusRequestId
            gateway.operations.clear()

            holder.applyInboundLink(EntryLink.Compose)
            val firstRequest = requireNotNull(holder.uiState.value.sessionList?.openedSession).composerFocusRequestId
            holder.applyInboundLink(EntryLink.Compose)

            val opened = requireNotNull(holder.uiState.value.sessionList?.openedSession)
            assertTrue(firstRequest > before)
            assertTrue(opened.composerFocusRequestId > firstRequest)
            assertEquals("", opened.composerText)
            assertTrue(gateway.operations.isEmpty())
            holder.close()
        }

    @Test
    fun a_compose_link_with_no_open_session_asks_for_a_session() =
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

            holder.applyInboundLink(EntryLink.Compose)

            assertEquals(
                InboundLinkNoticeCategory.COMPOSE_LINK_NEEDS_SESSION,
                holder.uiState.value.inboundLinkNotice,
            )
            assertTrue(gateway.operations.isEmpty())
            holder.close()
        }

    @Test
    fun an_inbound_link_closes_an_open_settings_surface() =
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
            holder.onEvent(EntryUiEvent.OpenSettingsClicked)
            assertTrue(holder.uiState.value.isSettingsOpen)

            holder.applyInboundLink(EntryLink.Compose)

            assertTrue(!holder.uiState.value.isSettingsOpen)
            holder.close()
        }

    @Test
    fun an_inbound_link_closes_an_open_credential_change_form() =
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
            holder.onEvent(EntryUiEvent.ChangeGatewayCredentialClicked)
            assertTrue(holder.uiState.value.isChangingCredential)

            holder.applyInboundLink(EntryLink.Compose)

            assertTrue(!holder.uiState.value.isChangingCredential)
            holder.close()
        }

    @Test
    fun a_session_link_without_a_usable_id_lands_on_the_list_when_a_session_is_open() =
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

            holder.applyInboundLink(EntryLink.Session(null))

            val list = requireNotNull(holder.uiState.value.sessionList)
            assertNull(list.openedSession)
            assertEquals(InboundLinkNoticeCategory.SESSION_LINK_UNKNOWN, holder.uiState.value.inboundLinkNotice)
            holder.close()
        }

    @Test
    fun a_session_link_over_a_stale_list_keeps_the_list_stale() =
        runTest {
            val gateway = FakeSessionGateway()
            gateway.enqueueFailure(GatewayException(GatewayErrorCategory.GATEWAY_REQUEST_FAILED))
            gateway.openedSession = entrySession("linked-session")
            gateway.openedHistory = SessionHistory(SessionId("linked-session"), emptyList())
            val holder =
                holder(
                    repository = FakeGatewayConnectionRepository(),
                    verifier = { _, _ -> clientManifestCapabilities() },
                    gateway = gateway,
                )
            verify(holder)
            awaitState(holder, "the stale Session list") { it.sessionList?.isUnavailable == true }

            holder.applyInboundLink(EntryLink.Session("linked-session"))

            awaitState(holder, "the linked Session over the stale list") {
                it.sessionList?.openedSession?.session?.id == SessionId("linked-session")
            }
            val list = requireNotNull(holder.uiState.value.sessionList)
            assertTrue(list.isStale)
            assertFalse(list.isUnavailable)
            holder.close()
        }

    @Test
    fun a_blank_id_link_during_the_first_page_load_restarts_the_cancelled_request() =
        runTest {
            val gateway = BlockingListGateway(entrySession("listed-session"))
            val holder = scriptedHolder(gateway, Dispatchers.Default)
            verify(holder)
            assertTrue(gateway.listStarted.await(5, TimeUnit.SECONDS))

            holder.applyInboundLink(EntryLink.Session(null))

            awaitStateRealTime(holder, "the restarted first page") {
                it.sessionList?.sessions?.isNotEmpty() == true
            }
            assertTrue(gateway.listCalls >= 2)
            assertFalse(requireNotNull(holder.uiState.value.sessionList).isStale)
            gateway.releaseList.countDown()
            holder.close()
        }

    @Test
    fun an_unknown_session_link_during_the_first_page_load_restarts_the_cancelled_request() =
        runTest {
            val gateway = BlockingListGateway(entrySession("listed-session"))
            gateway.openFailure = GatewayException(GatewayErrorCategory.GATEWAY_REQUEST_FAILED)
            val holder = scriptedHolder(gateway, Dispatchers.Default)
            verify(holder)
            assertTrue(gateway.listStarted.await(5, TimeUnit.SECONDS))

            holder.applyInboundLink(EntryLink.Session("foreign-session"))

            awaitStateRealTime(holder, "the unknown link notice") {
                it.inboundLinkNotice == InboundLinkNoticeCategory.SESSION_LINK_UNKNOWN
            }
            awaitStateRealTime(holder, "the restarted first page") {
                it.sessionList?.sessions?.isNotEmpty() == true
            }
            assertTrue(gateway.listCalls >= 2)
            assertFalse(requireNotNull(holder.uiState.value.sessionList).isStale)
            gateway.releaseList.countDown()
            holder.close()
        }

    @Test
    fun the_intent_event_entry_routes_a_link_through_the_holder() =
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

            holder.onEvent(EntryUiEvent.InboundUri("mercurio://compose"))

            assertEquals(
                InboundLinkNoticeCategory.COMPOSE_LINK_NEEDS_SESSION,
                holder.uiState.value.inboundLinkNotice,
            )
            holder.close()
        }

    @Test
    fun a_session_link_fired_while_the_first_page_loads_still_opens_and_clears_the_request() =
        runTest {
            val session = entrySession("linked-session")
            val gateway = BlockingListGateway(session)
            val holder = scriptedHolder(gateway, Dispatchers.Default)
            verify(holder)
            assertTrue(gateway.listStarted.await(5, TimeUnit.SECONDS))

            holder.applyInboundLink(EntryLink.Session("linked-session"))
            gateway.releaseList.countDown()

            awaitStateRealTime(holder, "the linked Session during the list load") {
                it.sessionList?.openedSession?.session?.id == SessionId("linked-session")
            }
            val list = requireNotNull(holder.uiState.value.sessionList)
            assertFalse(list.isLoading)
            assertFalse(list.isRefreshing)
            assertTrue(list.isStale)
            holder.close()
        }
}

private suspend fun awaitStateRealTime(
    holder: EntryStateHolder,
    description: String,
    predicate: (EntryUiState) -> Boolean,
) {
    withContext(Dispatchers.Default) {
        awaitState(holder, description, predicate)
    }
}

private class BlockingListGateway(
    private val session: Session,
) : SessionGatewayPort {
    val listStarted = CountDownLatch(1)
    val releaseList = CountDownLatch(1)

    @Volatile
    var listCalls = 0

    @Volatile
    var openFailure: GatewayException? = null

    override fun listSessions(request: SessionListRequest): SessionPage {
        val call =
            synchronized(this) {
                listCalls += 1
                listCalls
            }
        if (call == 1) {
            listStarted.countDown()
            check(releaseList.await(ASYNC_TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                "The first list request was not released."
            }
        }
        return SessionPage(listOf(session), null)
    }

    override fun createSession(title: String?): Session = error("not used")

    override fun openSession(sessionId: SessionId): Session {
        openFailure?.let { throw it }
        return session
    }

    override fun loadSessionHistory(sessionId: SessionId): SessionHistory = SessionHistory(sessionId, emptyList())

    override fun renameSession(
        sessionId: SessionId,
        title: String,
    ): Session = error("not used")

    override fun deleteSession(sessionId: SessionId): Unit = error("not used")

    override fun pinSession(sessionId: SessionId): SessionPinResult = error("not used")

    override fun unpinSession(sessionId: SessionId): SessionPinResult = error("not used")
}
