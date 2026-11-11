package org.mercurio.client.feature.entry.presentation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mercurio.client.feature.entry.application.EntryState
import org.mercurio.client.feature.entry.domain.GatewayConnection
import org.mercurio.client.feature.entry.domain.GatewayErrorCategory
import org.mercurio.client.feature.entry.domain.GatewayException
import org.mercurio.client.feature.entry.domain.GatewayHistoryMessage
import org.mercurio.client.feature.entry.domain.SessionHistory
import org.mercurio.client.feature.entry.domain.SessionId
import org.mercurio.client.feature.entry.domain.SessionListRequest
import org.mercurio.client.feature.entry.domain.SessionPage
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class EntryStateHolderTest {
    @Test
    fun add_gateway_connection_event_requests_connection_setup() {
        val stateHolder =
            EntryStateHolder(
                EntryState(isGatewayConnectionConfigured = false),
            )

        stateHolder.onEvent(EntryUiEvent.AddGatewayConnectionClicked)
        runBlocking { stateHolder.awaitStateChangesApplied() }

        assertTrue(stateHolder.uiState.value.connectionSetupRequested)
    }

    @Test
    fun secure_default_persists_the_credential_without_any_opt_in() {
        val repository = FakeGatewayConnectionRepository()
        val holder =
            holder(
                repository = repository,
                verifier = { _, _ ->
                    clientManifestCapabilities()
                },
            )

        holder.onEvent(EntryUiEvent.AddGatewayConnectionClicked)
        holder.onEvent(EntryUiEvent.EndpointChanged("https://gateway.example/profile"))
        holder.onEvent(EntryUiEvent.BearerCredentialChanged("secure-token"))
        holder.onEvent(EntryUiEvent.VerifyGatewayConnectionClicked)

        assertTrue(holder.uiState.value.isConnected)
        assertEquals(GatewayConnection("https://gateway.example/profile", "secure-token"), repository.saved)
        holder.close()
    }

    @Test
    fun a_connected_user_can_rotate_the_credential_without_removing_the_gateway_connection() {
        val repository = FakeGatewayConnectionRepository()
        val holder =
            holder(
                repository = repository,
                verifier = { _, _ ->
                    clientManifestCapabilities()
                },
            )

        holder.onEvent(EntryUiEvent.AddGatewayConnectionClicked)
        holder.onEvent(EntryUiEvent.EndpointChanged("https://gateway.example/profile"))
        holder.onEvent(EntryUiEvent.BearerCredentialChanged("working-token"))
        holder.onEvent(EntryUiEvent.VerifyGatewayConnectionClicked)
        holder.onEvent(EntryUiEvent.ChangeGatewayCredentialClicked)
        holder.onEvent(EntryUiEvent.BearerCredentialChanged("replacement-token"))
        holder.onEvent(EntryUiEvent.VerifyGatewayConnectionClicked)

        assertEquals(GatewayConnection("https://gateway.example/profile", "replacement-token"), repository.saved)
        assertTrue(holder.uiState.value.isConnected)
        assertFalse(holder.uiState.value.isChangingCredential)
        holder.close()
    }

    @Test
    fun failed_credential_rotation_preserves_the_working_credential_and_keeps_rotation_retryable() {
        val repository = FakeGatewayConnectionRepository()
        var verificationCount = 0
        val holder =
            holder(
                repository = repository,
                verifier = { _, _ ->
                    verificationCount += 1
                    if (verificationCount == 2) throw GatewayException(GatewayErrorCategory.AUTHENTICATION_FAILED)
                    clientManifestCapabilities()
                },
            )

        holder.onEvent(EntryUiEvent.AddGatewayConnectionClicked)
        holder.onEvent(EntryUiEvent.EndpointChanged("https://gateway.example/profile"))
        holder.onEvent(EntryUiEvent.BearerCredentialChanged("working-token"))
        holder.onEvent(EntryUiEvent.VerifyGatewayConnectionClicked)
        holder.onEvent(EntryUiEvent.ChangeGatewayCredentialClicked)
        holder.onEvent(EntryUiEvent.BearerCredentialChanged("replacement-token"))
        holder.onEvent(EntryUiEvent.VerifyGatewayConnectionClicked)

        assertEquals(EntryErrorCategory.AUTHENTICATION_FAILED, holder.uiState.value.errorCategory)
        assertEquals(GatewayConnection("https://gateway.example/profile", "working-token"), repository.saved)
        assertTrue(holder.uiState.value.isConnected)
        assertTrue(holder.uiState.value.isChangingCredential)
        holder.close()
    }

    @Test
    fun failed_credential_persistence_during_rotation_preserves_the_working_credential() {
        val repository = FakeGatewayConnectionRepository()
        val holder =
            holder(
                repository = repository,
                verifier = { _, _ ->
                    clientManifestCapabilities()
                },
            )

        holder.onEvent(EntryUiEvent.AddGatewayConnectionClicked)
        holder.onEvent(EntryUiEvent.EndpointChanged("https://gateway.example/profile"))
        holder.onEvent(EntryUiEvent.BearerCredentialChanged("working-token"))
        holder.onEvent(EntryUiEvent.VerifyGatewayConnectionClicked)
        repository.throwOnSave = true
        holder.onEvent(EntryUiEvent.ChangeGatewayCredentialClicked)
        holder.onEvent(EntryUiEvent.BearerCredentialChanged("replacement-token"))
        holder.onEvent(EntryUiEvent.VerifyGatewayConnectionClicked)

        assertEquals(EntryErrorCategory.CREDENTIAL_STORAGE_FAILED, holder.uiState.value.errorCategory)
        assertEquals(GatewayConnection("https://gateway.example/profile", "working-token"), repository.saved)
        assertTrue(holder.uiState.value.isConnected)
        assertTrue(holder.uiState.value.isChangingCredential)
        holder.close()
    }

    @Test
    fun a_restarted_state_prefills_the_saved_endpoint_and_credential_for_explicit_verification() {
        val holder =
            EntryStateHolder(
                EntryState(
                    isGatewayConnectionConfigured = true,
                    configuredEndpoint = "https://gateway.example/profile",
                    configuredCredential = "secure-token",
                ),
            )

        assertEquals("https://gateway.example/profile", holder.uiState.value.endpoint)
        assertEquals("secure-token", holder.uiState.value.bearerCredential)
        assertTrue(holder.uiState.value.connectionSetupRequested)
        holder.close()
    }

    @Test
    fun secure_storage_failure_keeps_the_connection_disconnected_and_reports_a_safe_recovery_action() {
        val repository = FakeGatewayConnectionRepository().apply { throwOnSave = true }
        val holder =
            holder(
                repository = repository,
                verifier = { _, _ ->
                    clientManifestCapabilities()
                },
            )

        holder.onEvent(EntryUiEvent.AddGatewayConnectionClicked)
        holder.onEvent(EntryUiEvent.EndpointChanged("https://gateway.example/profile"))
        holder.onEvent(EntryUiEvent.BearerCredentialChanged("secure-token"))
        holder.onEvent(EntryUiEvent.VerifyGatewayConnectionClicked)

        assertEquals(EntryErrorCategory.CREDENTIAL_STORAGE_FAILED, holder.uiState.value.errorCategory)
        assertFalse(holder.uiState.value.isConnected)
        assertFalse(holder.uiState.value.isSaving)
        holder.close()
    }

    @Test
    fun verification_enters_loading_then_connected_and_persists_the_credential_securely() {
        val repository = FakeGatewayConnectionRepository()
        val holder =
            holder(
                repository = repository,
                verifier = { _, _ ->
                    clientManifestCapabilities()
                },
            )

        holder.onEvent(EntryUiEvent.AddGatewayConnectionClicked)
        holder.onEvent(EntryUiEvent.EndpointChanged("https://gateway.example/profile"))
        holder.onEvent(EntryUiEvent.BearerCredentialChanged("memory-only-token"))
        holder.onEvent(EntryUiEvent.VerifyGatewayConnectionClicked)

        assertTrue(holder.uiState.value.isConnected)
        assertFalse(holder.uiState.value.isVerifying)
        assertFalse(holder.uiState.value.isSaving)
        assertEquals(
            GatewayConnection("https://gateway.example/profile", "memory-only-token"),
            repository.saved,
        )
        holder.close()
    }

    @Test
    fun recoverable_failure_keeps_inputs_maps_invalid_response_to_a_safe_category_and_can_retry() {
        val repository = FakeGatewayConnectionRepository()
        var attempts = 0
        val holder =
            holder(
                repository = repository,
                verifier = { _, _ ->
                    attempts += 1
                    if (attempts == 1) {
                        throw GatewayException(GatewayErrorCategory.INVALID_RESPONSE)
                    }
                    clientManifestCapabilities()
                },
            )

        holder.onEvent(EntryUiEvent.AddGatewayConnectionClicked)
        holder.onEvent(EntryUiEvent.EndpointChanged("https://gateway.example/profile"))
        holder.onEvent(EntryUiEvent.BearerCredentialChanged("memory-only-token"))
        holder.onEvent(EntryUiEvent.VerifyGatewayConnectionClicked)

        assertEquals(EntryErrorCategory.GATEWAY_REQUEST_FAILED, holder.uiState.value.errorCategory)
        assertEquals("https://gateway.example/profile", holder.uiState.value.endpoint)
        assertEquals("memory-only-token", holder.uiState.value.bearerCredential)
        assertFalse(holder.uiState.value.isVerifying)

        holder.onEvent(EntryUiEvent.TryAgainClicked)

        assertTrue(holder.uiState.value.isConnected)
        assertEquals(2, attempts)
        holder.close()
    }

    @Test
    fun removing_connection_while_verification_is_in_flight_cannot_restore_the_connection() =
        runTest {
            val repository = FakeGatewayConnectionRepository()
            val holder =
                holder(
                    repository = repository,
                    verifier = { _, _ ->
                        clientManifestCapabilities()
                    },
                    gateway = FakeSessionGateway(),
                    dispatcher = StandardTestDispatcher(testScheduler),
                )

            try {
                verify(holder)
                holder.onEvent(EntryUiEvent.ConfirmRemoveGatewayConnectionClicked)
                advanceUntilIdle()

                assertFalse(holder.uiState.value.isConnected)
                assertNull(holder.uiState.value.sessionList)
            } finally {
                holder.close()
            }
        }

    @Test
    fun successful_connection_loads_the_first_page_with_pinned_sessions_first() {
        val repository = FakeGatewayConnectionRepository()
        val gateway = FakeSessionGateway()
        val unpinnedOlder = entrySession("unpinned-older", pinned = false)
        val pinnedOlder = entrySession("pinned-older", pinned = true)
        val pinnedNewer = entrySession("pinned-newer", pinned = true)
        val unpinnedNewer = entrySession("unpinned-newer", pinned = false)
        gateway.enqueueList(
            SessionPage(
                sessions = listOf(unpinnedOlder, pinnedOlder, pinnedNewer, unpinnedNewer),
                nextOffset = 1,
            ),
        )
        val holder =
            holder(
                repository = repository,
                verifier = { _, _ ->
                    clientManifestCapabilities()
                },
                gateway = gateway,
            )

        verify(holder)

        val sessionList = requireNotNull(holder.uiState.value.sessionList)
        assertEquals(
            listOf("pinned-older", "pinned-newer", "unpinned-older", "unpinned-newer"),
            sessionList.sessions.map { it.id.value },
        )
        assertEquals("", sessionList.sessions.first().title)
        assertTrue(sessionList.sessions.first().isPendingTitle)
        assertEquals(listOf(SessionListRequest()), gateway.listRequests)
        assertTrue(sessionList.showFirstUseGuidance)

        holder.close()
    }

    @Test
    fun refresh_preserves_visible_sessions_when_unavailable_and_reloads_the_first_page_on_retry() {
        val gateway = FakeSessionGateway()
        val firstSession = entrySession("first", title = "First")
        val refreshedSession = entrySession("second", title = "Second")
        gateway.enqueueList(SessionPage(listOf(firstSession), 1))
        gateway.enqueueFailure(GatewayException(GatewayErrorCategory.GATEWAY_REQUEST_FAILED))
        gateway.enqueueList(SessionPage(listOf(refreshedSession), null))
        val holder =
            holder(
                repository = FakeGatewayConnectionRepository(),
                verifier = { _, _ ->
                    clientManifestCapabilities()
                },
                gateway = gateway,
            )

        verify(holder)
        holder.onEvent(EntryUiEvent.RefreshSessionsClicked)

        val unavailable = requireNotNull(holder.uiState.value.sessionList)
        assertEquals(listOf("first"), unavailable.sessions.map { it.id.value })
        assertEquals(1, unavailable.nextOffset)
        assertTrue(unavailable.isStale)
        assertFalse(unavailable.isUnavailable)
        assertEquals(SessionListErrorCategory.GATEWAY_UNAVAILABLE, unavailable.errorCategory)

        holder.onEvent(EntryUiEvent.RefreshSessionsClicked)

        val recovered = requireNotNull(holder.uiState.value.sessionList)
        assertEquals(listOf("second"), recovered.sessions.map { it.id.value })
        assertFalse(recovered.isStale)
        assertFalse(recovered.isUnavailable)
        assertEquals(3, gateway.listRequests.size)
        assertTrue(gateway.listRequests.all { it == SessionListRequest() })
        holder.close()
    }

    @Test
    fun search_filters_server_provided_sessions_locally_without_issuing_a_request() {
        val gateway = FakeSessionGateway()
        val initial = entrySession("initial", title = "Initial")
        val matching = entrySession("matching", title = "Matching")
        val other = entrySession("other", title = "Other", pinned = false)
        gateway.enqueueList(SessionPage(listOf(initial, matching, other), nextOffset = 1))
        val holder =
            holder(
                repository = FakeGatewayConnectionRepository(),
                verifier = { _, _ ->
                    clientManifestCapabilities()
                },
                gateway = gateway,
            )

        verify(holder)
        holder.onEvent(EntryUiEvent.SessionSearchQueryChanged("matching"))

        val searched = requireNotNull(holder.uiState.value.sessionList)
        assertEquals("matching", searched.searchQuery)
        assertEquals(listOf("initial", "matching", "other"), searched.sessions.map { it.id.value })
        assertEquals(listOf("matching"), searched.visibleSessions.map { it.id.value })
        assertEquals(listOf(SessionListRequest()), gateway.listRequests)

        holder.onEvent(EntryUiEvent.ClearSessionSearchClicked)

        val cleared = requireNotNull(holder.uiState.value.sessionList)
        assertEquals("", cleared.searchQuery)
        assertEquals(listOf("initial", "matching", "other"), cleared.visibleSessions.map { it.id.value })
        assertEquals(1, gateway.listRequests.size)
        holder.close()
    }

    @Test
    fun rapid_search_query_changes_never_issue_a_gateway_request() {
        val gateway = FakeSessionGateway()
        gateway.enqueueList(SessionPage(listOf(entrySession("initial")), null))
        val holder =
            holder(
                repository = FakeGatewayConnectionRepository(),
                verifier = { _, _ ->
                    clientManifestCapabilities()
                },
                gateway = gateway,
            )

        verify(holder)
        holder.onEvent(EntryUiEvent.SessionSearchQueryChanged("n"))
        holder.onEvent(EntryUiEvent.SessionSearchQueryChanged("needle"))

        val state = requireNotNull(holder.uiState.value.sessionList)
        assertEquals("needle", state.searchQuery)
        assertEquals(listOf(SessionListRequest()), gateway.listRequests)
        holder.close()
    }

    @Test
    fun loading_more_uses_the_server_offset_merges_without_duplicates_and_refresh_resets_pagination() {
        val gateway = FakeSessionGateway()
        val pinned = entrySession("pinned", title = "Pinned", pinned = true)
        val first = entrySession("first", title = "First")
        val secondPinned = entrySession("second-pinned", title = "Second pinned", pinned = true)
        val refreshed = entrySession("refreshed", title = "Refreshed")
        gateway.enqueueList(SessionPage(listOf(first, pinned), nextOffset = 2))
        gateway.enqueueList(SessionPage(listOf(secondPinned, first), nextOffset = null))
        gateway.enqueueList(SessionPage(listOf(refreshed), nextOffset = 3))
        val holder =
            holder(
                repository = FakeGatewayConnectionRepository(),
                verifier = { _, _ ->
                    clientManifestCapabilities()
                },
                gateway = gateway,
            )

        verify(holder)
        holder.onEvent(EntryUiEvent.LoadMoreSessionsClicked)

        val paged = requireNotNull(holder.uiState.value.sessionList)
        assertEquals(
            listOf("pinned", "second-pinned", "first"),
            paged.sessions.map { it.id.value },
        )
        assertEquals(SessionListRequest(offset = 2), gateway.listRequests[1])
        assertEquals(3, paged.sessions.size)
        assertEquals(null, paged.nextOffset)
        assertFalse(paged.isLoadingMore)

        holder.onEvent(EntryUiEvent.RefreshSessionsClicked)

        val reset = requireNotNull(holder.uiState.value.sessionList)
        assertEquals(listOf("refreshed"), reset.sessions.map { it.id.value })
        assertEquals(3, reset.nextOffset)
        assertEquals(SessionListRequest(), gateway.listRequests.last())
        holder.close()
    }

    @Test
    fun duplicate_load_more_actions_are_ignored_while_the_page_is_in_flight() =
        runTest {
            val pageStarted = CountDownLatch(1)
            val pageRelease = CountDownLatch(1)
            val gateway = ScriptedSessionGateway(SessionPage(listOf(entrySession("first")), nextOffset = 1))
            gateway.onList = { request, _ ->
                if (request == SessionListRequest(offset = 1)) {
                    pageStarted.countDown()
                    assertTrue(pageRelease.await(ASYNC_TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))
                }
            }
            val holder = scriptedHolder(gateway, Dispatchers.Default)
            verifyScripted(holder)

            try {
                holder.onEvent(EntryUiEvent.LoadMoreSessionsClicked)
                assertTrue(pageStarted.await(ASYNC_TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))
                assertTrue(requireNotNull(holder.uiState.value.sessionList).isLoadingMore)

                holder.onEvent(EntryUiEvent.LoadMoreSessionsClicked)
                runBlocking { holder.awaitStateChangesApplied() }
                assertEquals(
                    listOf(SessionListRequest(), SessionListRequest(offset = 1)),
                    gateway.listRequests,
                )
                gateway.enqueuePage(SessionPage(listOf(entrySession("second")), null))
                gateway.onList = null
                pageRelease.countDown()

                awaitSessions(holder, "first", "second")
                assertFalse(requireNotNull(holder.uiState.value.sessionList).isLoadingMore)
            } finally {
                gateway.onList = null
                pageRelease.countDown()
                holder.close()
            }
        }

    @Test
    fun opening_during_a_refresh_opens_the_session_and_drops_the_refresh() =
        runTest {
            val target = SessionId("target")
            val gateway = FakeSessionGateway()
            gateway.enqueueList(SessionPage(listOf(entrySession(target.value, "Target")), null))
            gateway.openedSession = entrySession(target.value, "Authoritative title")
            gateway.openedHistory =
                SessionHistory(
                    target,
                    listOf(GatewayHistoryMessage("message-one", "user", "Opened during refresh")),
                )
            val refreshStarted = CountDownLatch(1)
            val refreshRelease = CountDownLatch(1)
            gateway.onList = { _ ->
                if (gateway.listRequests.size == 2) {
                    refreshStarted.countDown()
                    assertTrue(refreshRelease.await(ASYNC_TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))
                }
            }
            val holder = scriptedHolder(gateway, Dispatchers.Default)
            verifyScripted(holder)

            try {
                holder.onEvent(EntryUiEvent.RefreshSessionsClicked)
                assertTrue(refreshStarted.await(ASYNC_TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))
                assertTrue(requireNotNull(holder.uiState.value.sessionList).isRefreshing)

                holder.onEvent(EntryUiEvent.SessionClicked(target))
                runBlocking { holder.awaitStateChangesApplied() }
                gateway.enqueueList(SessionPage(listOf(entrySession(target.value, "Target")), null))
                refreshRelease.countDown()
                gateway.onList = null

                awaitState(holder, "the opened session after the dropped refresh") {
                    val opened = it.sessionList?.openedSession
                    opened?.session?.id == target &&
                        !it.sessionList.isRefreshing &&
                        opened.messages.map { message -> message.content } == listOf("Opened during refresh")
                }

                val state = requireNotNull(holder.uiState.value.sessionList)
                assertEquals(target, state.openedSession?.session?.id)
                assertEquals(
                    listOf("Opened during refresh"),
                    state.openedSession?.messages?.map { message -> message.content },
                )
                assertFalse(state.isRefreshing)
                assertFalse(state.isSearching)
                assertFalse(state.isLoading)
            } finally {
                gateway.onList = null
                refreshRelease.countDown()
                holder.close()
            }
        }

    @Test
    fun opening_a_session_during_local_search_opens_authoritative_history() =
        runTest {
            val sessionId = SessionId("target")
            val gateway = FakeSessionGateway()
            gateway.enqueueList(SessionPage(listOf(entrySession(sessionId.value, "Target")), null))
            gateway.openedSession = entrySession(sessionId.value, "Target")
            gateway.openedHistory =
                SessionHistory(
                    sessionId = sessionId,
                    messages = listOf(GatewayHistoryMessage("message-one", "user", "Found it")),
                )
            val holder =
                holder(
                    repository = FakeGatewayConnectionRepository(),
                    verifier = { _, _ ->
                        clientManifestCapabilities()
                    },
                    gateway = gateway,
                )

            verify(holder)
            holder.onEvent(EntryUiEvent.SessionSearchQueryChanged("targ"))
            holder.onEvent(EntryUiEvent.SessionClicked(sessionId))

            awaitState(holder, "opened search result") {
                it.sessionList?.openedSession?.session?.id == sessionId
            }
            val state = requireNotNull(holder.uiState.value.sessionList)
            assertEquals("targ", state.searchQuery)
            assertEquals(listOf("Found it"), state.openedSession?.messages?.map { it.content })
            holder.close()
        }

    @Test
    fun search_is_ignored_while_session_creation_is_submitting() =
        runTest {
            val created = entrySession("created", title = "Created")
            val createStarted = CountDownLatch(1)
            val createRelease = CountDownLatch(1)
            val gateway = ScriptedCreateGateway(entrySession("initial", title = "Initial"), created)
            gateway.onCreate = {
                createStarted.countDown()
                assertTrue(createRelease.await(ASYNC_TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))
            }
            val holder = scriptedHolder(gateway, Dispatchers.Default)
            verifyScripted(holder)

            try {
                holder.onEvent(EntryUiEvent.CreateSessionClicked)
                assertTrue(createStarted.await(ASYNC_TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))
                assertTrue(requireNotNull(holder.uiState.value.sessionList).createSession?.isSubmitting == true)

                holder.onEvent(EntryUiEvent.SessionSearchQueryChanged("needle"))
                runBlocking { holder.awaitStateChangesApplied() }

                val submitting = requireNotNull(holder.uiState.value.sessionList)
                assertEquals("", submitting.searchQuery)
                assertTrue(submitting.createSession?.isSubmitting == true)
                assertEquals(listOf(SessionListRequest()), gateway.listRequests)

                createRelease.countDown()
                gateway.onCreate = null
                awaitState(holder, "the created Session row") {
                    it.sessionList?.sessions?.any { session -> session.id == created.id } == true
                }
            } finally {
                gateway.onCreate = null
                createRelease.countDown()
                holder.close()
            }
        }

    @Test
    fun a_local_query_change_does_not_discard_an_in_flight_refresh_result() =
        runTest {
            val gateway = ScriptedSessionGateway(SessionPage(listOf(entrySession("initial")), null))
            val holder = scriptedHolder(gateway, StandardTestDispatcher(testScheduler))
            verifyScripted(holder)
            advanceUntilIdle()

            gateway.onList = { request, occurrence ->
                if (request == SessionListRequest() && occurrence == 1) {
                    holder.onEvent(EntryUiEvent.SessionSearchQueryChanged("final"))
                }
            }
            gateway.enqueuePage(SessionPage(listOf(entrySession("old-refresh")), null))

            holder.onEvent(EntryUiEvent.RefreshSessionsClicked)
            advanceUntilIdle()

            awaitState(holder, "the refresh result after the local query change") {
                val list = it.sessionList
                list?.searchQuery == "final" &&
                    list.sessions.map { session -> session.id.value } == listOf("old-refresh")
            }
            val state = requireNotNull(holder.uiState.value.sessionList)
            assertEquals("final", state.searchQuery)
            assertEquals(listOf("old-refresh"), state.sessions.map { it.id.value })
            assertTrue(state.visibleSessions.isEmpty())
            holder.close()
        }

    @Test
    fun stale_load_more_results_are_ignored_after_refresh_resets_the_first_page() =
        runTest {
            val oldRequest = SessionListRequest(offset = 1)
            val gateway = ScriptedSessionGateway(SessionPage(listOf(entrySession("initial")), nextOffset = 1))
            val holder = scriptedHolder(gateway, StandardTestDispatcher(testScheduler))
            verifyScripted(holder)
            advanceUntilIdle()

            gateway.onList = { request, occurrence ->
                if (request == oldRequest && occurrence == 0) {
                    holder.onEvent(EntryUiEvent.RefreshSessionsClicked)
                }
            }
            gateway.enqueuePage(SessionPage(listOf(entrySession("stale-more")), null))
            gateway.enqueuePage(SessionPage(listOf(entrySession("refreshed")), nextOffset = 2))

            holder.onEvent(EntryUiEvent.LoadMoreSessionsClicked)
            advanceUntilIdle()
            awaitSessions(holder, "refreshed")

            val state = requireNotNull(holder.uiState.value.sessionList)
            assertEquals(2, state.nextOffset)
            holder.close()
        }

    @Test
    fun selecting_a_session_opens_authoritative_history_and_a_deleted_session_is_recoverable() {
        val sessionId = SessionId("session-one")
        val gateway = FakeSessionGateway()
        gateway.enqueueList(SessionPage(listOf(entrySession(sessionId.value, "Listed title")), null))
        gateway.openedSession = entrySession(sessionId.value, "Authoritative title")
        gateway.openedHistory =
            SessionHistory(
                sessionId = sessionId,
                messages = listOf(GatewayHistoryMessage("message-one", "user", "Authoritative content")),
            )
        val holder =
            holder(
                repository = FakeGatewayConnectionRepository(),
                verifier = { _, _ ->
                    clientManifestCapabilities()
                },
                gateway = gateway,
            )

        verify(holder)
        holder.onEvent(EntryUiEvent.SessionClicked(sessionId))

        val opened = requireNotNull(requireNotNull(holder.uiState.value.sessionList).openedSession)
        assertEquals("Authoritative title", opened.session.title)
        assertEquals(listOf("Authoritative content"), opened.messages.map { it.content })
        assertEquals(listOf("open", "history"), gateway.operations)

        gateway.openFailure = GatewayException(GatewayErrorCategory.GATEWAY_REQUEST_FAILED)
        holder.onEvent(EntryUiEvent.ReturnToSessionListClicked)
        holder.onEvent(EntryUiEvent.SessionClicked(sessionId))

        val unavailable = requireNotNull(holder.uiState.value.sessionList)
        assertEquals(SessionListErrorCategory.SESSION_UNAVAILABLE, unavailable.errorCategory)
        assertTrue(unavailable.isUnavailable)
        assertEquals(listOf("session-one"), unavailable.sessions.map { it.id.value })
        holder.close()
    }
}
