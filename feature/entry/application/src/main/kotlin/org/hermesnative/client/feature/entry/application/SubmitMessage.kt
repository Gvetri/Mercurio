package org.hermesnative.client.feature.entry.application

import org.hermesnative.client.feature.entry.domain.GatewayErrorCategory
import org.hermesnative.client.feature.entry.domain.GatewayException
import org.hermesnative.client.feature.entry.domain.ModelSelection
import org.hermesnative.client.feature.entry.domain.Run
import org.hermesnative.client.feature.entry.domain.RunCreateRequest
import org.hermesnative.client.feature.entry.domain.RunGatewayPort
import org.hermesnative.client.feature.entry.domain.SessionId

class SubmitMessage(
    private val gateway: RunGatewayPort,
) {
    /**
     * Creates one Run for [input], carrying the endpoint's [selection] override when one
     * is set. A null selection keeps the pinned two-key request body: the optional
     * `model` and `effort` keys are omitted, never sent as null.
     */
    fun execute(
        sessionId: SessionId,
        input: String,
        selection: ModelSelection? = null,
    ): Run {
        val run =
            gateway.createRun(
                RunCreateRequest(
                    sessionId = sessionId,
                    input = input,
                    model = selection?.model,
                    effort = selection?.effort,
                ),
            )
        if (run.sessionId != sessionId) {
            throw GatewayException(
                GatewayErrorCategory.INVALID_RESPONSE,
                "Invalid Gateway response while creating a Run: session_id does not match the requested Session.",
            )
        }
        return run
    }
}
