package org.hermesnative.client.feature.entry.presentation

import org.hermesnative.client.feature.entry.domain.GatewayErrorCategory
import org.hermesnative.client.feature.entry.domain.GatewayException
import org.hermesnative.client.feature.entry.domain.ModelSelection
import org.hermesnative.client.feature.entry.domain.ModelSelectionStore
import org.hermesnative.client.feature.entry.domain.Run
import org.hermesnative.client.feature.entry.domain.RunCreateRequest
import org.hermesnative.client.feature.entry.domain.RunId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelSelectionSubmissionTest {
    @Test
    fun a_selected_override_flows_into_the_run_create_request_and_records_the_model_use() {
        val session = retrySession("session-1")
        val gateway = RetryFakeGateway(sessions = listOf(session))
        gateway.enqueueRun(Run(RunId("run-selected"), session.id, "running"))
        val store = FakeModelSelectionStore(selection = ModelSelection("deepseek-v4-flash", "medium"))
        val holder = retryHolder(gateway, modelSelectionStore = store)

        try {
            openRetrySession(holder, gateway, session.id)
            holder.onEvent(EntryUiEvent.ComposerTextChanged("Selected request"))
            holder.onEvent(EntryUiEvent.SendMessageClicked)
            awaitRetryState(holder) { it.sessionList?.openedSession?.latestRun?.id?.value == "run-selected" }

            val request = gateway.runCreateRequests.single()
            assertEquals("deepseek-v4-flash", request.model)
            assertEquals("medium", request.effort)
            assertEquals(listOf("deepseek-v4-flash"), store.recents(ENDPOINT))
        } finally {
            holder.close()
        }
    }

    @Test
    fun a_cleared_override_submits_a_request_equal_to_the_pinned_two_key_body() {
        val session = retrySession("session-1")
        val gateway = RetryFakeGateway(sessions = listOf(session))
        gateway.enqueueRun(Run(RunId("run-cleared"), session.id, "running"))
        val store = FakeModelSelectionStore(selection = ModelSelection("deepseek-v4-flash", "medium"))
        store.clear(ENDPOINT)
        val holder = retryHolder(gateway, modelSelectionStore = store)

        try {
            openRetrySession(holder, gateway, session.id)
            holder.onEvent(EntryUiEvent.ComposerTextChanged("Default request"))
            holder.onEvent(EntryUiEvent.SendMessageClicked)
            awaitRetryState(holder) { it.sessionList?.openedSession?.latestRun?.id?.value == "run-cleared" }

            val request = gateway.runCreateRequests.single()
            assertEquals(RunCreateRequest(sessionId = session.id, input = "Default request"), request)
            assertTrue(gateway.runCreateRequests.single().effort == null)
        } finally {
            holder.close()
        }
    }

    @Test
    fun a_model_refusal_at_admission_shows_the_distinct_model_send_copy() {
        assertSendCategoryAfterRefusal(GatewayErrorCategory.MODEL_REFUSED, MessageSendErrorCategory.MODEL_REFUSED)
    }

    @Test
    fun an_effort_refusal_at_admission_shows_the_distinct_effort_send_copy() {
        assertSendCategoryAfterRefusal(GatewayErrorCategory.EFFORT_REFUSED, MessageSendErrorCategory.EFFORT_REFUSED)
    }

    @Test
    fun a_non_refusal_admission_failure_keeps_the_generic_send_copy() {
        assertSendCategoryAfterRefusal(
            GatewayErrorCategory.GATEWAY_REQUEST_FAILED,
            MessageSendErrorCategory.GATEWAY_REQUEST_FAILED,
        )
    }

    private fun assertSendCategoryAfterRefusal(
        gatewayCategory: GatewayErrorCategory,
        expectedSendCategory: MessageSendErrorCategory,
    ) {
        val session = retrySession("session-1")
        val gateway = RetryFakeGateway(sessions = listOf(session))
        gateway.enqueueRunFailure(GatewayException(gatewayCategory))
        val holder = retryHolder(gateway)

        try {
            openRetrySession(holder, gateway, session.id)
            holder.onEvent(EntryUiEvent.ComposerTextChanged("Refused request"))
            holder.onEvent(EntryUiEvent.SendMessageClicked)
            awaitRetryState(holder) { it.sessionList?.openedSession?.sendErrorCategory == expectedSendCategory }

            val opened = requireNotNull(requireNotNull(holder.uiState.value.sessionList).openedSession)
            assertEquals(expectedSendCategory, opened.sendErrorCategory)
        } finally {
            holder.close()
        }
    }

    @Test
    fun an_async_failure_of_a_selected_run_uses_the_refused_model_run_copy() {
        val session = retrySession("session-1")
        val failedRunId = RunId("run-async-failed")
        val gateway =
            RetryFakeGateway(
                sessions = listOf(session),
                runStatuses = mapOf(failedRunId to Run(failedRunId, session.id, "failed")),
            )
        gateway.setHistory(
            retryHistory(
                session.id,
                retryUserMessage("Async request", failedRunId),
                retryFailedMessage(failedRunId, "Provider refused"),
            ),
        )
        gateway.enqueueRun(Run(failedRunId, session.id, "failed"))
        val store = FakeModelSelectionStore(selection = ModelSelection("deepseek-v4-flash", "medium"))
        val holder = retryHolder(gateway, modelSelectionStore = store)

        try {
            openRetrySession(holder, gateway, session.id)
            holder.onEvent(EntryUiEvent.ComposerTextChanged("Async request"))
            holder.onEvent(EntryUiEvent.SendMessageClicked)
            awaitRetryState(holder) {
                it.sessionList?.openedSession?.messages
                    ?.any { message -> message.runId == failedRunId && message.isFailedRun } == true
            }

            val opened = requireNotNull(requireNotNull(holder.uiState.value.sessionList).openedSession)
            val failedMessage = opened.messages.single { it.runId == failedRunId && it.isFailedRun }
            assertEquals(RunFailureCategory.MODEL_REFUSED.safeMessage, failedMessage.failureSafeMessage)
            assertTrue(failedMessage.retryAvailable)
        } finally {
            holder.close()
        }
    }

    @Test
    fun an_async_failure_of_an_unselected_run_keeps_the_generic_run_copy() {
        val session = retrySession("session-1")
        val failedRunId = RunId("run-async-failed")
        val gateway =
            RetryFakeGateway(
                sessions = listOf(session),
                runStatuses = mapOf(failedRunId to Run(failedRunId, session.id, "failed")),
            )
        gateway.setHistory(
            retryHistory(
                session.id,
                retryUserMessage("Default request", failedRunId),
                retryFailedMessage(failedRunId, "Provider refused"),
            ),
        )
        gateway.enqueueRun(Run(failedRunId, session.id, "failed"))
        val holder = retryHolder(gateway)

        try {
            openRetrySession(holder, gateway, session.id)
            holder.onEvent(EntryUiEvent.ComposerTextChanged("Default request"))
            holder.onEvent(EntryUiEvent.SendMessageClicked)
            awaitRetryState(holder) {
                it.sessionList?.openedSession?.messages
                    ?.any { message -> message.runId == failedRunId && message.isFailedRun } == true
            }

            val opened = requireNotNull(requireNotNull(holder.uiState.value.sessionList).openedSession)
            val failedMessage = opened.messages.single { it.runId == failedRunId && it.isFailedRun }
            assertEquals(RunFailureCategory.GATEWAY_REPORTED.safeMessage, failedMessage.failureSafeMessage)
        } finally {
            holder.close()
        }
    }

    @Test
    fun the_single_mapping_layer_gives_refusals_their_own_send_categories_and_generic_connection_copy() {
        assertEquals(
            MessageSendErrorCategory.MODEL_REFUSED,
            GatewayErrorCategory.MODEL_REFUSED.toSendFailureCategoryOrNull(),
        )
        assertEquals(
            MessageSendErrorCategory.EFFORT_REFUSED,
            GatewayErrorCategory.EFFORT_REFUSED.toSendFailureCategoryOrNull(),
        )
        assertNull(GatewayErrorCategory.INVALID_RESPONSE.toSendFailureCategoryOrNull())
        assertNull(GatewayErrorCategory.AUTHENTICATION_FAILED.toSendFailureCategoryOrNull())
        val connectionFallback = EntryErrorCategory.GATEWAY_REQUEST_FAILED
        assertEquals(connectionFallback, GatewayErrorCategory.MODEL_REFUSED.toUserFacingCategory())
        assertEquals(connectionFallback, GatewayErrorCategory.EFFORT_REFUSED.toUserFacingCategory())
    }

    private class FakeModelSelectionStore(
        var selection: ModelSelection? = null,
    ) : ModelSelectionStore {
        private val recentIds = mutableListOf<String>()
        private val favoriteIds = mutableSetOf<String>()

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

        override fun favorites(endpoint: String): Set<String> = favoriteIds.toSet()

        override fun setFavorite(
            endpoint: String,
            modelId: String,
            favorite: Boolean,
        ) {
            if (favorite) {
                favoriteIds += modelId
            } else {
                favoriteIds -= modelId
            }
        }

        override fun recents(endpoint: String): List<String> = recentIds.toList()

        override fun recordUse(
            endpoint: String,
            modelId: String,
        ) {
            recentIds.remove(modelId)
            recentIds.add(0, modelId)
        }
    }

    private companion object {
        const val ENDPOINT = "https://gateway.example/profile"
    }
}
