package org.mercurio.client.feature.entry.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mercurio.client.feature.entry.domain.GatewayErrorCategory
import org.mercurio.client.feature.entry.domain.GatewayException
import org.mercurio.client.feature.entry.domain.RunEventType
import org.mercurio.client.feature.entry.domain.RunId
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class GatewaySseParserTest {
    private val runId = RunId("run-1")

    private val capturedLiveGatewayStream =
        """
        : open

        id: 0
        data: {"event":"message.delta","run_id":"run-1","delta":"Receipt confirmed","seq":0}

        id: 1
        data: {"event":"message.delta","run_id":"run-1","delta":" \u2014","seq":1}

        id: 7
        data: {"event":"reasoning.available","run_id":"run-1","text":"Receipt confirmed \u2014","seq":7}

        id: 8
        data: {"event":"run.completed","run_id":"run-1","completed":true,"partial":false,"interrupted":false,"seq":8}

        : stream closed
        """.trimIndent().lines()

    @Test
    fun parses_data_only_events_with_the_event_type_inside_the_json_payload() =
        runBlocking {
            val frames =
                GatewaySseParser.frames(
                    sequenceOf(
                        "data: {\"event\":\"tool.started\",\"run_id\":\"run-1" +
                            "\",\"tool\":\"terminal\",\"preview\":\"ls\"}",
                        "",
                        "data: {\"event\":\"message.delta\",\"run_id\":\"run-1\",\"delta\":\"Hello\"}",
                        "",
                        "data: {\"event\":\"run.completed\",\"run_id\":\"run-1\"}",
                    ),
                    "run observation",
                ).toList()

            val events = frames.mapNotNull { GatewayJsonParser.parseRunEvent("run observation", it) }

            assertEquals(
                listOf(RunEventType.RUNNING, RunEventType.MESSAGE_DELTA, RunEventType.COMPLETED),
                events.map { it.type },
            )
            assertEquals("Hello", events[1].text)
            assertEquals(runId, events[1].runId)
            assertEquals("succeeded", events[2].status)
        }

    @Test
    fun ignores_comments_and_unknown_event_payloads_without_rendering_or_failing() =
        runBlocking {
            val frames =
                GatewaySseParser.frames(
                    sequenceOf(
                        ": keepalive",
                        "",
                        "data: {\"event\":\"fixture.metadata\",\"hermes_revision\":\"pinned\"}",
                        "",
                        "data: {\"event\":\"run.stopping\",\"run_id\":\"run-1\"}",
                    ),
                    "run observation",
                ).toList()

            assertEquals(2, frames.size)
            assertNull(GatewayJsonParser.parseRunEvent("run observation", frames[0]))
            val stopping = requireNotNull(GatewayJsonParser.parseRunEvent("run observation", frames[1]))
            assertEquals(RunEventType.COMPLETING, stopping.type)
        }

    @Test
    fun exact_duplicate_payloads_are_emitted_once_but_distinct_payloads_are_preserved() =
        runBlocking {
            val observation =
                GatewayRunEventObservation(
                    operation = "run observation",
                    openStream = {
                        GatewayEventStream(
                            statusCode = 200,
                            lines =
                                sequenceOf(
                                    "data: {\"event\":\"message.delta\",\"run_id\":\"run-1\",\"delta\":\"a\"}",
                                    "",
                                    "data: {\"event\":\"message.delta\",\"run_id\":\"run-1\",\"delta\":\"a\"}",
                                    "",
                                    "data: {\"event\":\"message.delta\",\"run_id\":\"run-1" +
                                        "\",\"delta\":\"a\",\"seq\":2}",
                                ),
                        )
                    },
                    parseFrame = { frame -> GatewayJsonParser.parseRunEvent("run observation", frame) },
                    mapTransportFailure = { GatewayException(GatewayErrorCategory.GATEWAY_REQUEST_FAILED) },
                )

            val events = observation.collectEvents()

            assertEquals(2, events.size)
            assertEquals(listOf("a", "a"), events.map { it.text })
            assertNull(events[0].eventId)
            assertNull(events[1].eventId)
        }

    @Test
    fun a_stream_opened_after_the_open_call_suspended_yields_its_events() =
        runBlocking {
            val openGate = CompletableDeferred<Unit>()
            val observation =
                GatewayRunEventObservation(
                    operation = "run observation",
                    openStream = {
                        openGate.await()
                        GatewayEventStream(
                            statusCode = 200,
                            lines =
                                sequenceOf(
                                    "data: {\"event\":\"message.delta\",\"run_id\":\"run-1\",\"delta\":\"Late\"}",
                                    "",
                                ),
                        )
                    },
                    parseFrame = { frame -> GatewayJsonParser.parseRunEvent("run observation", frame) },
                    mapTransportFailure = { GatewayException(GatewayErrorCategory.GATEWAY_REQUEST_FAILED) },
                )

            val events = async { observation.collectEvents() }
            openGate.complete(Unit)

            assertEquals(listOf(RunEventType.MESSAGE_DELTA), events.await().map { it.type })
        }

    @Test
    fun an_open_failure_after_the_open_call_suspended_keeps_its_gateway_category() =
        runBlocking {
            val openGate = CompletableDeferred<Unit>()
            val observation =
                GatewayRunEventObservation(
                    operation = "run observation",
                    openStream = {
                        openGate.await()
                        throw GatewayException(GatewayErrorCategory.GATEWAY_REQUEST_FAILED)
                    },
                    parseFrame = { frame -> GatewayJsonParser.parseRunEvent("run observation", frame) },
                    mapTransportFailure = { GatewayException(GatewayErrorCategory.INVALID_RESPONSE) },
                )

            val events = async { runCatching { observation.collectEvents() } }
            openGate.complete(Unit)

            val failure = events.await().exceptionOrNull() as GatewayException
            assertEquals(GatewayErrorCategory.GATEWAY_REQUEST_FAILED, failure.category)
        }

    @Test
    fun a_transport_failure_after_the_open_call_suspended_is_mapped() =
        runBlocking {
            val openGate = CompletableDeferred<Unit>()
            val observation =
                GatewayRunEventObservation(
                    operation = "run observation",
                    openStream = {
                        openGate.await()
                        error("stream unavailable")
                    },
                    parseFrame = { frame -> GatewayJsonParser.parseRunEvent("run observation", frame) },
                    mapTransportFailure = { GatewayException(GatewayErrorCategory.INVALID_RESPONSE) },
                )

            val events = async { runCatching { observation.collectEvents() } }
            openGate.complete(Unit)

            val failure = events.await().exceptionOrNull() as GatewayException
            assertEquals(GatewayErrorCategory.INVALID_RESPONSE, failure.category)
        }

    @Test
    fun every_mapped_gateway_event_name_parses_to_its_run_event_type() {
        val expected =
            mapOf(
                "message.delta" to RunEventType.MESSAGE_DELTA,
                "run.completed" to RunEventType.COMPLETED,
                "run.failed" to RunEventType.FAILED,
                "run.cancelled" to RunEventType.CANCELLED,
                "run.stopping" to RunEventType.COMPLETING,
                "tool.started" to RunEventType.RUNNING,
                "tool.completed" to RunEventType.RUNNING,
                "reasoning.available" to RunEventType.RUNNING,
                "subagent.start" to RunEventType.RUNNING,
                "subagent.complete" to RunEventType.RUNNING,
                "approval.request" to RunEventType.RUNNING,
                "approval.responded" to RunEventType.RUNNING,
                "run.steered" to RunEventType.RUNNING,
            )
        expected.forEach { (wireName, type) ->
            val delta = ",\"delta\":\"x\"".takeIf { type == RunEventType.MESSAGE_DELTA }.orEmpty()
            val frame = GatewaySseFrame("{\"event\":\"$wireName\",\"run_id\":\"run-1\"$delta}")
            assertEquals(wireName, type, GatewayJsonParser.parseRunEvent("run observation", frame)?.type)
        }
        assertNull(
            GatewayJsonParser.parseRunEvent(
                "run observation",
                GatewaySseFrame("{\"event\":\"unknown.event\",\"run_id\":\"run-1\"}"),
            ),
        )
    }

    @Test
    fun parses_the_captured_live_gateway_framing_with_comment_and_id_lines() =
        runBlocking {
            val frames =
                GatewaySseParser.frames(
                    capturedLiveGatewayStream.asSequence(),
                    "run observation",
                ).toList()

            val events = frames.mapNotNull { GatewayJsonParser.parseRunEvent("run observation", it) }

            assertEquals(
                listOf(
                    RunEventType.MESSAGE_DELTA,
                    RunEventType.MESSAGE_DELTA,
                    RunEventType.RUNNING,
                    RunEventType.COMPLETED,
                ),
                events.map { it.type },
            )
            assertEquals("Receipt confirmed", events[0].text)
            assertEquals(" \u2014", events[1].text)
            assertEquals("succeeded", events[3].status)
        }

    @Test
    fun the_captured_live_gateway_stream_yields_deltas_and_a_terminal_completed_event() =
        runBlocking {
            val observation =
                GatewayRunEventObservation(
                    operation = "run observation",
                    openStream = {
                        GatewayEventStream(
                            statusCode = 200,
                            lines = capturedLiveGatewayStream.asSequence(),
                        )
                    },
                    parseFrame = { frame -> GatewayJsonParser.parseRunEvent("run observation", frame) },
                    mapTransportFailure = { GatewayException(GatewayErrorCategory.GATEWAY_REQUEST_FAILED) },
                )

            val events = observation.collectEvents()

            assertEquals(
                listOf(
                    RunEventType.MESSAGE_DELTA,
                    RunEventType.MESSAGE_DELTA,
                    RunEventType.RUNNING,
                    RunEventType.COMPLETED,
                ),
                events.map { it.type },
            )
            assertEquals("Receipt confirmed", events[0].text)
            assertEquals(" \u2014", events[1].text)
            assertEquals("succeeded", events.last().status)
        }

    @Test
    fun malformed_framing_is_rejected_as_a_safe_invalid_response() =
        runBlocking {
            val error =
                runCatching {
                    GatewaySseParser.frames(
                        sequenceOf(
                            "data: {\"event\":\"message.delta\",\"run_id\":\"run-1\",\"delta\":\"Hello\"}",
                            "",
                            "event: run.stopped",
                            "data: {\"event\":\"run.stopped\",\"run_id\":\"run-1\"}",
                        ),
                        "run observation",
                    ).toList()
                }.exceptionOrNull()

            assertEquals(GatewayErrorCategory.INVALID_RESPONSE, (error as? GatewayException)?.category)
        }

    @Test
    fun an_interrupted_line_sequence_is_mapped_to_a_safe_gateway_failure_and_closes_the_stream() =
        runBlocking {
            var closed = false
            val observation =
                GatewayRunEventObservation(
                    operation = "run observation",
                    openStream = {
                        GatewayEventStream(
                            statusCode = 200,
                            lines =
                                sequence {
                                    yield(
                                        "data: {\"event\":\"message.delta\",\"run_id\":\"run-1" +
                                            "\",\"delta\":\"Partial\"}",
                                    )
                                    yield("")
                                    error("connection dropped")
                                },
                            closeAction = { closed = true },
                        )
                    },
                    parseFrame = { frame -> GatewayJsonParser.parseRunEvent("run observation", frame) },
                    mapTransportFailure = { GatewayException(GatewayErrorCategory.GATEWAY_REQUEST_FAILED) },
                )

            val error = runCatching { observation.collectEvents() }.exceptionOrNull()

            assertEquals(GatewayErrorCategory.GATEWAY_REQUEST_FAILED, (error as? GatewayException)?.category)
            assertTrue(closed)
        }

    @Test
    fun closing_during_stream_open_does_not_block_and_closes_the_late_stream() =
        runBlocking {
            val timeoutMillis = 5_000L
            val openStarted = CountDownLatch(1)
            val releaseOpen = CountDownLatch(1)
            val closeReturned = CountDownLatch(1)
            val streamClosed = CountDownLatch(1)
            val iteratorFailure = AtomicReference<Throwable?>()
            val observation =
                GatewayRunEventObservation(
                    operation = "run observation",
                    openStream = {
                        openStarted.countDown()
                        check(releaseOpen.await(timeoutMillis, TimeUnit.MILLISECONDS)) {
                            "stream opening was not released"
                        }
                        GatewayEventStream(
                            statusCode = 200,
                            lines = emptySequence(),
                            closeAction = { streamClosed.countDown() },
                        )
                    },
                    parseFrame = { frame -> GatewayJsonParser.parseRunEvent("run observation", frame) },
                    mapTransportFailure = { GatewayException(GatewayErrorCategory.GATEWAY_REQUEST_FAILED) },
                )
            val iteratorThread =
                Thread {
                    try {
                        runBlocking { observation.next() }
                    } catch (error: Throwable) {
                        iteratorFailure.set(error)
                    }
                }
            val closeThread =
                Thread {
                    try {
                        observation.close()
                    } finally {
                        closeReturned.countDown()
                    }
                }

            iteratorThread.start()
            val closeCompletedBeforeOpenReleased =
                try {
                    assertTrue(openStarted.await(timeoutMillis, TimeUnit.MILLISECONDS))
                    closeThread.start()
                    closeReturned.await(timeoutMillis, TimeUnit.MILLISECONDS)
                } finally {
                    releaseOpen.countDown()
                }
            iteratorThread.join(timeoutMillis)
            closeThread.join(timeoutMillis)

            assertTrue(closeCompletedBeforeOpenReleased)
            assertFalse(iteratorThread.isAlive)
            assertFalse(closeThread.isAlive)
            assertTrue(streamClosed.await(timeoutMillis, TimeUnit.MILLISECONDS))
            assertTrue(iteratorFailure.get() is IllegalStateException)
        }
}
