package org.mercurio.client.feature.entry.data

import org.junit.Assert.assertEquals
import org.junit.Test
import org.mercurio.client.feature.entry.domain.GatewayErrorCategory
import org.mercurio.client.feature.entry.domain.GatewayException
import org.mercurio.client.feature.entry.domain.RunCreateRequest
import org.mercurio.client.feature.entry.domain.SessionId
import java.util.ArrayDeque

class RunCreateRefusalMappingTest {
    private val sessionId = SessionId("7c4d3b20-7c7a-4e2a-a593-3a11c2e93f70")

    private val modelNotFoundBody =
        "{\"error\":{\"message\":\"server prose\",\"type\":\"invalid_request_error\",\"code\":\"model_not_found\"}}"

    @Test
    fun a_4xx_admission_with_a_model_code_and_a_selection_present_is_a_distinct_model_refusal() {
        val refusal =
            submitWith(
                selection = true,
                status = 400,
                body = modelNotFoundBody,
            )

        assertEquals(GatewayErrorCategory.MODEL_REFUSED, refusal.category)
        assertEquals(false, refusal.message.orEmpty().contains("server prose"))
    }

    @Test
    fun a_4xx_admission_with_an_effort_code_and_a_selection_present_is_a_distinct_effort_refusal() {
        val refusal =
            submitWith(
                selection = true,
                status = 422,
                body = "{\"error\":{\"code\":\"unsupported_effort\"}}",
            )

        assertEquals(GatewayErrorCategory.EFFORT_REFUSED, refusal.category)
    }

    @Test
    fun a_4xx_admission_without_a_machine_readable_refusal_code_stays_a_generic_request_failure() {
        val refusal =
            submitWith(
                selection = true,
                status = 400,
                body = "{\"error\":{\"code\":\"rate_limited\"}}",
            )

        assertEquals(GatewayErrorCategory.GATEWAY_REQUEST_FAILED, refusal.category)
    }

    @Test
    fun a_4xx_admission_with_a_model_code_but_no_selection_stays_a_generic_request_failure() {
        val refusal =
            submitWith(
                selection = false,
                status = 400,
                body = "{\"error\":{\"code\":\"model_not_found\"}}",
            )

        assertEquals(GatewayErrorCategory.GATEWAY_REQUEST_FAILED, refusal.category)
    }

    @Test
    fun a_5xx_admission_never_produces_a_refusal_category_even_with_a_selection() {
        val refusal =
            submitWith(
                selection = true,
                status = 503,
                body = "{\"error\":{\"code\":\"model_not_found\"}}",
            )

        assertEquals(GatewayErrorCategory.GATEWAY_REQUEST_FAILED, refusal.category)
    }

    @Test
    fun authentication_failures_keep_their_category_even_with_a_selection() {
        val refusal =
            submitWith(
                selection = true,
                status = 401,
                body = "{\"error\":{\"code\":\"model_not_found\"}}",
            )

        assertEquals(GatewayErrorCategory.AUTHENTICATION_FAILED, refusal.category)
    }

    @Test
    fun a_malformed_error_envelope_falls_back_to_the_generic_request_failure() {
        val refusal =
            submitWith(
                selection = true,
                status = 400,
                body = "not json at all",
            )

        assertEquals(GatewayErrorCategory.GATEWAY_REQUEST_FAILED, refusal.category)
    }

    private fun submitWith(
        selection: Boolean,
        status: Int,
        body: String,
    ): GatewayException {
        val transport = RecordingTransport(responses = listOf(GatewayHttpResponse(status, body)))
        val client = DefaultGatewayClient("https://gateway.example", "test-token", transport)
        val request =
            RunCreateRequest(
                sessionId = sessionId,
                input = "Run this",
                model = if (selection) "deepseek-v4-flash" else null,
                effort = if (selection) "medium" else null,
            )
        try {
            client.createRun(request)
        } catch (error: GatewayException) {
            return error
        }
        throw AssertionError("Expected the refused Run creation to fail")
    }

    private class RecordingTransport(
        responses: List<GatewayHttpResponse> = emptyList(),
    ) : GatewayTransport {
        private val queuedResponses = ArrayDeque(responses)
        val requests = mutableListOf<GatewayHttpRequest>()

        override fun execute(request: GatewayHttpRequest): GatewayHttpResponse {
            requests += request
            return queuedResponses.removeFirst()
        }

        override fun openEventStream(request: GatewayHttpRequest): GatewayEventStream {
            requests += request
            error("Run admission never opens an event stream.")
        }
    }
}
