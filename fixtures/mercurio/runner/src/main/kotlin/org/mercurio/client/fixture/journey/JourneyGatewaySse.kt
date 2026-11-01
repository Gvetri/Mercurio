package org.mercurio.client.fixture.journey

import com.sun.net.httpserver.HttpExchange
import java.util.concurrent.atomic.AtomicInteger

internal object JourneyGatewaySse {
    internal fun streamRunEvents(
        exchange: HttpExchange,
        behavior: JourneyGatewayBehavior,
        runId: String,
    ) {
        val script =
            behavior.scenario.runs.firstOrNull { it.runId == runId } ?: run {
                respond(exchange, HTTP_NOT_FOUND, """{"error":"run-not-found"}""")
                return
            }
        behavior.sseOpened.computeIfAbsent(runId) { AtomicInteger() }.incrementAndGet()
        exchange.responseHeaders.add("Content-Type", "text/event-stream")
        exchange.responseHeaders.add("Cache-Control", "no-cache")
        exchange.sendResponseHeaders(HTTP_OK, 0)
        try {
            writeObservationEvents(exchange, behavior, script, runId)
        } catch (_: Exception) {
            if (script.finalizeOnDisconnect) {
                behavior.observationDelivered[runId] = true
            }
        } finally {
            behavior.sseClosed.computeIfAbsent(runId) { AtomicInteger() }.incrementAndGet()
            exchange.close()
        }
    }

    private fun writeObservationEvents(
        exchange: HttpExchange,
        behavior: JourneyGatewayBehavior,
        script: JourneyRunScript,
        runId: String,
    ) {
        val interruptAfter = script.interruptAfterEvents
        val events = script.observation
        if (script.liveFraming) {
            exchange.responseBody.write(streamOpenComment)
            exchange.responseBody.flush()
        }
        events.forEachIndexed { index, event ->
            if (!script.holdOpen && script.finalStatus != null && index == events.lastIndex) {
                behavior.observationDelivered[runId] = true
            }
            script.idleGapMillis?.let { gapMillis ->
                Thread.sleep(gapMillis.toLong())
                exchange.responseBody.write(keepAliveComment)
                exchange.responseBody.flush()
            }
            exchange.responseBody.write(JourneyGatewayJson.renderEvent(script, event, index))
            exchange.responseBody.flush()
            if (interruptAfter != null && index + 1 >= interruptAfter) {
                behavior.interruptedRunIds += runId
                exchange.close()
                return
            }
        }
        if (script.holdOpen) {
            writeKeepAliveEvents(exchange)
            if (script.finalizeOnDisconnect) {
                behavior.observationDelivered[runId] = true
            }
        } else if (script.finalStatus != null && events.isEmpty()) {
            behavior.observationDelivered[runId] = true
        }
        if (script.liveFraming) {
            exchange.responseBody.write(streamClosedComment)
            exchange.responseBody.flush()
        }
    }

    private fun writeKeepAliveEvents(exchange: HttpExchange) {
        try {
            while (true) {
                exchange.responseBody.write(keepAliveComment)
                exchange.responseBody.flush()
                Thread.sleep(HOLD_OPEN_KEEPALIVE_MILLIS)
            }
        } catch (_: Exception) {
        }
    }
}

private val keepAliveComment: ByteArray = ": keep-alive\n\n".toByteArray(Charsets.UTF_8)
private val streamOpenComment: ByteArray = ": open\n\n".toByteArray(Charsets.UTF_8)
private val streamClosedComment: ByteArray = ": stream closed\n\n".toByteArray(Charsets.UTF_8)
