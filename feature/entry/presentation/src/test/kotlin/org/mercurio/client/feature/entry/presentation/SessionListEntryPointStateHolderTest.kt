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
import org.mercurio.client.feature.entry.domain.GatewayErrorCategory
import org.mercurio.client.feature.entry.domain.GatewayException
import org.mercurio.client.feature.entry.domain.GatewayHistoryMessage
import org.mercurio.client.feature.entry.domain.SessionHistory
import org.mercurio.client.feature.entry.domain.SessionId
import org.mercurio.client.feature.entry.domain.SessionPage
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

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
            val pageStarted = CountDownLatch(1)
            val pageRelease = CountDownLatch(1)
            gateway.onList = { request ->
                if (request.offset == 1) {
                    pageStarted.countDown()
                    assertTrue(pageRelease.await(ASYNC_TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))
                }
            }
            val holder = scriptedHolder(gateway, Dispatchers.Default)

            try {
                verifyScripted(holder)
                holder.onEvent(EntryUiEvent.LoadMoreSessionsClicked)
                assertTrue(pageStarted.await(ASYNC_TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))
                assertTrue(requireNotNull(holder.uiState.value.sessionList).isLoadingMore)

                holder.onEvent(EntryUiEvent.SessionClicked(target))
                runBlocking { holder.awaitStateChangesApplied() }
                gateway.onList = null
                pageRelease.countDown()

                awaitState(holder, "the opened session with the page load settled") {
                    val list = it.sessionList ?: return@awaitState false
                    val opened = list.openedSession ?: return@awaitState false
                    opened.session.id == target &&
                        opened.messages.map { message -> message.content } == listOf("Hello") &&
                        !list.isLoadingMore
                }

                val state = requireNotNull(holder.uiState.value.sessionList)
                assertEquals(target, state.openedSession?.session?.id)
                assertEquals(listOf("Hello"), state.openedSession?.messages?.map { message -> message.content })
                assertFalse(state.isLoadingMore)
            } finally {
                gateway.onList = null
                pageRelease.countDown()
                holder.close()
            }
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
            val refreshStarted = CountDownLatch(1)
            val refreshRelease = CountDownLatch(1)
            gateway.onList = { _ ->
                if (gateway.listRequests.size == 2) {
                    refreshStarted.countDown()
                    assertTrue(refreshRelease.await(ASYNC_TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))
                }
            }
            val holder = scriptedHolder(gateway, Dispatchers.Default)

            try {
                verifyScripted(holder)
                holder.onEvent(EntryUiEvent.RefreshSessionsClicked)
                assertTrue(refreshStarted.await(ASYNC_TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))
                assertTrue(requireNotNull(holder.uiState.value.sessionList).isRefreshing)

                holder.onEvent(EntryUiEvent.CreateSessionClicked)
                runBlocking { holder.awaitStateChangesApplied() }
                gateway.onList = null
                refreshRelease.countDown()

                awaitState(holder, "the created Session row") { state ->
                    state.sessionList?.sessions?.any { session -> session.id == created.id } == true
                }
                assertEquals(1, gateway.createCalls)
            } finally {
                gateway.onList = null
                refreshRelease.countDown()
                holder.close()
            }
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
            val pageStarted = CountDownLatch(1)
            val pageRelease = CountDownLatch(1)
            gateway.onList = { request ->
                if (request.offset == 1) {
                    pageStarted.countDown()
                    assertTrue(pageRelease.await(ASYNC_TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))
                }
            }
            val holder = scriptedHolder(gateway, Dispatchers.Default)

            try {
                verifyScripted(holder)
                holder.onEvent(EntryUiEvent.LoadMoreSessionsClicked)
                assertTrue(pageStarted.await(ASYNC_TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))
                assertTrue(requireNotNull(holder.uiState.value.sessionList).isLoadingMore)

                holder.onEvent(EntryUiEvent.CreateSessionClicked)
                runBlocking { holder.awaitStateChangesApplied() }
                gateway.onList = null
                pageRelease.countDown()

                awaitState(holder, "the created Session row") { state ->
                    state.sessionList?.sessions?.any { session -> session.id == created.id } == true
                }
                assertEquals(1, gateway.createCalls)
            } finally {
                gateway.onList = null
                pageRelease.countDown()
                holder.close()
            }
        }

    @Test
    fun a_failed_create_during_a_refresh_keeps_the_list_refreshable() =
        runTest {
            val target = entrySession("target", title = "Target")
            val gateway = FakeSessionGateway()
            gateway.enqueueList(SessionPage(listOf(target), null))
            gateway.enqueueCreateFailure(GatewayException(GatewayErrorCategory.GATEWAY_REQUEST_FAILED))
            gateway.enqueueList(SessionPage(listOf(target), null))
            val refreshStarted = CountDownLatch(1)
            val refreshRelease = CountDownLatch(1)
            gateway.onList = { _ ->
                if (gateway.listRequests.size == 2) {
                    refreshStarted.countDown()
                    assertTrue(refreshRelease.await(ASYNC_TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))
                }
            }
            val holder = scriptedHolder(gateway, Dispatchers.Default)

            try {
                verifyScripted(holder)
                holder.onEvent(EntryUiEvent.RefreshSessionsClicked)
                assertTrue(refreshStarted.await(ASYNC_TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))
                assertTrue(requireNotNull(holder.uiState.value.sessionList).isRefreshing)

                holder.onEvent(EntryUiEvent.CreateSessionClicked)
                runBlocking { holder.awaitStateChangesApplied() }
                awaitState(holder, "the failed create") {
                    it.sessionList?.creationErrorCategory != null
                }

                val failed = requireNotNull(holder.uiState.value.sessionList)
                assertEquals(SessionCreationErrorCategory.GATEWAY_REQUEST_FAILED, failed.creationErrorCategory)
                assertFalse(failed.isRefreshing)
                assertFalse(failed.isLoadingMore)

                holder.onEvent(EntryUiEvent.RefreshSessionsClicked)
                awaitState(holder, "the tail refresh reloading the list") {
                    val list = it.sessionList
                    list != null &&
                        list.sessions.map { session -> session.id.value } == listOf("target") &&
                        list.nextOffset == null &&
                        !list.isRefreshing &&
                        !list.isLoadingMore
                }
                assertEquals(3, gateway.listRequests.size)
            } finally {
                gateway.onList = null
                refreshRelease.countDown()
                holder.close()
            }
        }

    @Test
    fun a_failed_create_during_a_next_page_load_keeps_the_list_refreshable() =
        runTest {
            val target = entrySession("target", title = "Target")
            val gateway = FakeSessionGateway()
            gateway.enqueueList(SessionPage(listOf(target), nextOffset = 1))
            gateway.enqueueCreateFailure(GatewayException(GatewayErrorCategory.GATEWAY_REQUEST_FAILED))
            gateway.enqueueList(SessionPage(listOf(target), null))
            val pageStarted = CountDownLatch(1)
            val pageRelease = CountDownLatch(1)
            gateway.onList = { request ->
                if (request.offset == 1) {
                    pageStarted.countDown()
                    assertTrue(pageRelease.await(ASYNC_TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))
                }
            }
            val holder = scriptedHolder(gateway, Dispatchers.Default)

            try {
                verifyScripted(holder)
                holder.onEvent(EntryUiEvent.LoadMoreSessionsClicked)
                assertTrue(pageStarted.await(ASYNC_TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))
                assertTrue(requireNotNull(holder.uiState.value.sessionList).isLoadingMore)

                holder.onEvent(EntryUiEvent.CreateSessionClicked)
                runBlocking { holder.awaitStateChangesApplied() }
                awaitState(holder, "the failed create") {
                    it.sessionList?.creationErrorCategory != null
                }

                val failed = requireNotNull(holder.uiState.value.sessionList)
                assertEquals(SessionCreationErrorCategory.GATEWAY_REQUEST_FAILED, failed.creationErrorCategory)
                assertFalse(failed.isLoadingMore)
                assertFalse(failed.isRefreshing)

                // The parked page call resumes and its stale-generation result is dropped;
                // the identical queued page serves the tail load below.
                gateway.enqueueList(SessionPage(listOf(target), null))
                gateway.onList = null
                pageRelease.countDown()

                holder.onEvent(EntryUiEvent.LoadMoreSessionsClicked)
                awaitState(holder, "the tail page load finishing") {
                    val list = it.sessionList
                    list != null &&
                        list.sessions.map { session -> session.id.value } == listOf("target") &&
                        list.nextOffset == null &&
                        !list.isLoadingMore &&
                        !list.isRefreshing
                }
                assertEquals(3, gateway.listRequests.size)
            } finally {
                gateway.onList = null
                pageRelease.countDown()
                holder.close()
            }
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
