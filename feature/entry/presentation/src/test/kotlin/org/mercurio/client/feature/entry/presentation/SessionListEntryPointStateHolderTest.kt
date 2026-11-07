package org.mercurio.client.feature.entry.presentation

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mercurio.client.feature.entry.domain.GatewayErrorCategory
import org.mercurio.client.feature.entry.domain.GatewayException
import org.mercurio.client.feature.entry.domain.GatewayHistoryMessage
import org.mercurio.client.feature.entry.domain.SessionHistory
import org.mercurio.client.feature.entry.domain.SessionId
import org.mercurio.client.feature.entry.domain.SessionPage

class SessionListEntryPointStateHolderTest {
    @Test
    fun a_session_row_opens_while_the_next_page_loads() =
        runTest {
            val target = SessionId("target")
            val gateway = FakeSessionGateway()
            gateway.enqueueList(SessionPage(listOf(entrySession(target.value, "Target")), nextOffset = 1))
            gateway.enqueueList(SessionPage(listOf(entrySession("next", "Next")), null))
            gateway.openedSession = entrySession(target.value, "Target")
            gateway.openedHistory =
                SessionHistory(target, listOf(GatewayHistoryMessage("message-one", "user", "Hello")))
            val holder = scriptedHolder(gateway, StandardTestDispatcher(testScheduler))

            verifyScripted(holder)
            advanceUntilIdle()
            holder.onEvent(EntryUiEvent.LoadMoreSessionsClicked)
            assertTrue(requireNotNull(holder.uiState.value.sessionList).isLoadingMore)

            holder.onEvent(EntryUiEvent.SessionClicked(target))
            advanceUntilIdle()

            val state = requireNotNull(holder.uiState.value.sessionList)
            assertEquals(target, state.openedSession?.session?.id)
            assertEquals(listOf("Hello"), state.openedSession?.messages?.map { message -> message.content })
            assertFalse(state.isLoadingMore)
            holder.close()
        }

    @Test
    fun creation_is_accepted_while_a_refresh_is_in_flight() =
        runTest {
            val created = entrySession("created", title = "Created")
            val target = entrySession("target", title = "Target")
            val gateway = FakeSessionGateway()
            gateway.enqueueList(SessionPage(listOf(target), null))
            gateway.enqueueCreate(created)
            gateway.enqueueList(SessionPage(listOf(created, target), null))
            val holder = scriptedHolder(gateway, StandardTestDispatcher(testScheduler))

            verifyScripted(holder)
            advanceUntilIdle()
            holder.onEvent(EntryUiEvent.RefreshSessionsClicked)
            assertTrue(requireNotNull(holder.uiState.value.sessionList).isRefreshing)

            holder.onEvent(EntryUiEvent.CreateSessionClicked)
            advanceUntilIdle()

            awaitState(holder, "the created Session row") { state ->
                state.sessionList?.sessions?.any { session -> session.id == created.id } == true
            }
            assertEquals(1, gateway.createCalls)
            holder.close()
        }

    @Test
    fun creation_is_accepted_while_the_next_page_loads() =
        runTest {
            val created = entrySession("created", title = "Created")
            val target = entrySession("target", title = "Target")
            val next = entrySession("next", title = "Next")
            val gateway = FakeSessionGateway()
            gateway.enqueueList(SessionPage(listOf(target), nextOffset = 1))
            gateway.enqueueCreate(created)
            gateway.enqueueList(SessionPage(listOf(created, target, next), null))
            val holder = scriptedHolder(gateway, StandardTestDispatcher(testScheduler))

            verifyScripted(holder)
            advanceUntilIdle()
            holder.onEvent(EntryUiEvent.LoadMoreSessionsClicked)
            assertTrue(requireNotNull(holder.uiState.value.sessionList).isLoadingMore)

            holder.onEvent(EntryUiEvent.CreateSessionClicked)
            advanceUntilIdle()

            awaitState(holder, "the created Session row") { state ->
                state.sessionList?.sessions?.any { session -> session.id == created.id } == true
            }
            assertEquals(1, gateway.createCalls)
            holder.close()
        }

    @Test
    fun a_failed_create_during_a_refresh_keeps_the_list_refreshable() =
        runTest {
            val target = entrySession("target", title = "Target")
            val gateway = FakeSessionGateway()
            gateway.enqueueList(SessionPage(listOf(target), null))
            gateway.enqueueCreateFailure(GatewayException(GatewayErrorCategory.GATEWAY_REQUEST_FAILED))
            gateway.enqueueList(SessionPage(listOf(target), null))
            val holder = scriptedHolder(gateway, StandardTestDispatcher(testScheduler))

            verifyScripted(holder)
            advanceUntilIdle()
            holder.onEvent(EntryUiEvent.RefreshSessionsClicked)
            assertTrue(requireNotNull(holder.uiState.value.sessionList).isRefreshing)

            holder.onEvent(EntryUiEvent.CreateSessionClicked)
            advanceUntilIdle()

            val failed = requireNotNull(holder.uiState.value.sessionList)
            assertEquals(SessionCreationErrorCategory.GATEWAY_REQUEST_FAILED, failed.creationErrorCategory)
            assertFalse(failed.isRefreshing)
            assertFalse(failed.isLoadingMore)

            holder.onEvent(EntryUiEvent.RefreshSessionsClicked)
            advanceUntilIdle()
            awaitState(holder, "the reloaded first page") { state ->
                state.sessionList?.sessions?.map { session -> session.id.value } == listOf("target")
            }
            assertEquals(2, gateway.listRequests.size)
            holder.close()
        }

    @Test
    fun a_failed_create_during_a_next_page_load_keeps_the_list_refreshable() =
        runTest {
            val target = entrySession("target", title = "Target")
            val gateway = FakeSessionGateway()
            gateway.enqueueList(SessionPage(listOf(target), nextOffset = 1))
            gateway.enqueueCreateFailure(GatewayException(GatewayErrorCategory.GATEWAY_REQUEST_FAILED))
            gateway.enqueueList(SessionPage(listOf(target), null))
            val holder = scriptedHolder(gateway, StandardTestDispatcher(testScheduler))

            verifyScripted(holder)
            advanceUntilIdle()
            holder.onEvent(EntryUiEvent.LoadMoreSessionsClicked)
            assertTrue(requireNotNull(holder.uiState.value.sessionList).isLoadingMore)

            holder.onEvent(EntryUiEvent.CreateSessionClicked)
            advanceUntilIdle()

            val failed = requireNotNull(holder.uiState.value.sessionList)
            assertEquals(SessionCreationErrorCategory.GATEWAY_REQUEST_FAILED, failed.creationErrorCategory)
            assertFalse(failed.isLoadingMore)
            assertFalse(failed.isRefreshing)

            holder.onEvent(EntryUiEvent.LoadMoreSessionsClicked)
            advanceUntilIdle()
            assertEquals(2, gateway.listRequests.size)
            holder.close()
        }

    @Test
    fun an_unavailable_gateway_refuses_both_the_row_and_the_create_action() =
        runTest {
            val first = entrySession("first", "First")
            val second = entrySession("second", "Second")
            val gateway = FakeSessionGateway()
            gateway.enqueueList(SessionPage(listOf(first, second), null))
            gateway.openFailure = GatewayException(GatewayErrorCategory.GATEWAY_REQUEST_FAILED)
            val holder = scriptedHolder(gateway, StandardTestDispatcher(testScheduler))

            verifyScripted(holder)
            advanceUntilIdle()
            holder.onEvent(EntryUiEvent.SessionClicked(first.id))
            advanceUntilIdle()
            assertTrue(requireNotNull(holder.uiState.value.sessionList).isUnavailable)

            holder.onEvent(EntryUiEvent.SessionClicked(second.id))
            holder.onEvent(EntryUiEvent.CreateSessionClicked)
            advanceUntilIdle()

            val state = requireNotNull(holder.uiState.value.sessionList)
            assertNull(state.openedSession)
            assertNull(state.openingSessionId)
            assertEquals(0, gateway.createCalls)
            assertEquals(1, gateway.operations.count { operation -> operation == "open" })
            holder.close()
        }
}
