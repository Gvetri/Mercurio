package org.hermesnative.client.feature.entry.application

import org.hermesnative.client.feature.entry.domain.GatewayErrorCategory
import org.hermesnative.client.feature.entry.domain.GatewayException
import org.hermesnative.client.feature.entry.domain.ModelSelection
import org.hermesnative.client.feature.entry.domain.Run
import org.hermesnative.client.feature.entry.domain.RunCreateRequest
import org.hermesnative.client.feature.entry.domain.RunGatewayPort
import org.hermesnative.client.feature.entry.domain.RunId
import org.hermesnative.client.feature.entry.domain.SessionId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SubmitMessageTest {
    @Test
    fun forwards_the_session_and_input_to_the_gateway_and_returns_its_run() {
        val gateway = RecordingRunGateway()
        val expected = Run(RunId("run-1"), SessionId("session-1"), "starting")
        gateway.result = expected

        val actual = SubmitMessage(gateway).execute(SessionId("session-1"), "Run this")

        assertSame(expected, actual)
        assertEquals(listOf(SessionId("session-1") to "Run this"), gateway.requests)
    }

    @Test
    fun sends_both_catalog_selection_keys_when_an_override_is_set() {
        val gateway = RecordingRequestGateway()

        SubmitMessage(gateway).execute(
            sessionId = SessionId("session-1"),
            input = "Run this",
            selection = ModelSelection(model = "deepseek-v4-flash", effort = "medium"),
        )

        val request = gateway.requests.single()
        assertEquals("deepseek-v4-flash", request.model)
        assertEquals("medium", request.effort)
    }

    @Test
    fun sends_the_model_selection_key_alone_when_the_override_has_no_effort() {
        val gateway = RecordingRequestGateway()

        SubmitMessage(gateway).execute(
            sessionId = SessionId("session-1"),
            input = "Run this",
            selection = ModelSelection(model = "hermes-agent"),
        )

        val request = gateway.requests.single()
        assertEquals("hermes-agent", request.model)
        assertNull(request.effort)
    }

    @Test
    fun leaves_the_request_at_the_two_key_body_when_the_override_is_unset_or_cleared() {
        val gateway = RecordingRequestGateway()

        SubmitMessage(gateway).execute(SessionId("session-1"), "Run this")

        val request = gateway.requests.single()
        assertEquals(SessionId("session-1"), request.sessionId)
        assertEquals("Run this", request.input)
        assertNull(request.model)
        assertNull(request.effort)
    }

    @Test
    fun a_gateway_port_without_the_optional_selection_contract_fails_closed() {
        val gateway = RecordingRunGateway()

        try {
            SubmitMessage(gateway).execute(
                sessionId = SessionId("session-1"),
                input = "Run this",
                selection = ModelSelection("deepseek-v4-flash", "high"),
            )
            fail("Expected a selection-carrying create to fail closed on an unselectable port.")
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message!!.contains("model or effort"))
        }
        assertTrue(gateway.requests.isEmpty())
    }

    @Test
    fun rejects_a_selected_run_returned_for_a_different_session() {
        val gateway = RecordingRequestGateway()
        gateway.result = Run(RunId("run-1"), SessionId("other-session"), "starting")

        try {
            SubmitMessage(gateway).execute(
                sessionId = SessionId("session-1"),
                input = "Run this",
                selection = ModelSelection("deepseek-v4-flash", "medium"),
            )
            fail("Expected a mismatched Run response to be rejected.")
        } catch (error: GatewayException) {
            assertEquals(GatewayErrorCategory.INVALID_RESPONSE, error.category)
        }
    }

    @Test
    fun rejects_a_run_returned_for_a_different_session() {
        val gateway = RecordingRunGateway()
        gateway.result = Run(RunId("run-1"), SessionId("other-session"), "starting")

        try {
            SubmitMessage(gateway).execute(SessionId("session-1"), "Run this")
            fail("Expected a mismatched Run response to be rejected.")
        } catch (error: GatewayException) {
            assertEquals(GatewayErrorCategory.INVALID_RESPONSE, error.category)
        }
    }

    private class RecordingRunGateway : RunGatewayPort {
        var result = Run(RunId("run-default"), SessionId("session-default"), "starting")
        val requests = mutableListOf<Pair<SessionId, String>>()

        override fun createRun(
            sessionId: SessionId,
            input: String,
        ): Run {
            requests += sessionId to input
            return result
        }

        override fun getRunStatus(runId: RunId): Run = error("not used")

        override fun observeRun(runId: RunId) = error("not used")
    }

    private class RecordingRequestGateway : RunGatewayPort {
        var result = Run(RunId("run-default"), SessionId("session-1"), "starting")
        val requests = mutableListOf<RunCreateRequest>()

        override fun createRun(
            sessionId: SessionId,
            input: String,
        ): Run = createRun(RunCreateRequest(sessionId = sessionId, input = input))

        override fun createRun(request: RunCreateRequest): Run {
            requests += request
            return result
        }

        override fun getRunStatus(runId: RunId): Run = error("not used")

        override fun observeRun(runId: RunId) = error("not used")
    }
}
