package org.mercurio.client.feature.entry.data

import org.mercurio.client.feature.entry.domain.GatewayException
import org.mercurio.client.feature.entry.domain.RunEvent
import org.mercurio.client.feature.entry.domain.RunEventObservation
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * One-shot observation of a Run event stream.
 *
 * The lifecycle state is atomic rather than monitor-guarded: an observation is a Sequence, so its
 * consumer cannot suspend, and [close] may run from another thread, so the flags and the opened
 * stream use compare-and-set transitions. The stream itself stays single-consumer by contract:
 * [iterator] can be collected once, and a close closes the stream at most once.
 */
internal class GatewayRunEventObservation(
    private val operation: String,
    private val openStream: () -> GatewayEventStream,
    private val parseFrame: (GatewaySseFrame) -> RunEvent?,
    private val mapTransportFailure: (Exception) -> GatewayException,
) : RunEventObservation {
    private val started = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    private val stream = AtomicReference<GatewayEventStream?>(null)

    override fun iterator(): Iterator<RunEvent> {
        check(started.compareAndSet(false, true)) { "Gateway run observation can only be collected once." }
        check(!closed.get()) { "Gateway run observation is closed." }
        val openedStream =
            try {
                openStream()
            } catch (error: GatewayException) {
                closed.set(true)
                throw error
            } catch (expectedError: Exception) {
                closed.set(true)
                throw mapTransportFailure(expectedError)
            }
        stream.set(openedStream)
        if (closed.get()) {
            stream.compareAndSet(openedStream, null)
            openedStream.close()
            error("Gateway run observation is closed.")
        }
        val seenFrames = mutableSetOf<String>()
        val events =
            GatewaySseParser.frames(openedStream.lines, operation)
                .filter { frame -> seenFrames.add(frame.data) }
                .mapNotNull { frame -> parseFrame(frame) }
                .iterator()
        return object : Iterator<RunEvent> {
            override fun hasNext(): Boolean {
                if (closed.get()) return false
                val available = guarded { events.hasNext() }
                if (!available) close()
                return available
            }

            override fun next(): RunEvent {
                if (!hasNext()) throw NoSuchElementException()
                return guarded { events.next() }
            }

            private fun <T> guarded(block: () -> T): T =
                try {
                    block()
                } catch (error: GatewayException) {
                    close()
                    throw error
                } catch (expectedError: Exception) {
                    close()
                    throw mapTransportFailure(expectedError)
                }
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        stream.getAndSet(null)?.close()
    }
}
