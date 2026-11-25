package org.mercurio.client.feature.entry.data

import kotlinx.coroutines.runInterruptible
import org.mercurio.client.feature.entry.domain.GatewayException
import org.mercurio.client.feature.entry.domain.RunEvent
import org.mercurio.client.feature.entry.domain.RunEventObservation
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.cancellation.CancellationException

/**
 * One-shot observation of a Run event stream.
 *
 * The lifecycle state is atomic rather than monitor-guarded: [close] may run from another
 * coroutine while [next] waits on the stream, so the flags and the opened stream use
 * compare-and-set transitions. The stream stays single-consumer by contract: it opens on the
 * first [next], and a close closes the stream at most once.
 */
internal class GatewayRunEventObservation(
    private val operation: String,
    private val openStream: suspend () -> GatewayEventStream,
    private val parseFrame: (GatewaySseFrame) -> RunEvent?,
    private val mapTransportFailure: (Exception) -> GatewayException,
) : RunEventObservation {
    private val started = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    private val stream = AtomicReference<GatewayEventStream?>(null)
    private var events: Iterator<RunEvent>? = null

    override suspend fun next(): RunEvent? {
        val iterator = events()
        val event =
            try {
                read(iterator)
            } catch (error: CancellationException) {
                close()
                throw error
            } catch (expectedError: Exception) {
                close()
                throw expectedError.asFailure()
            }
        if (event == null) close()
        return event
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        stream.getAndSet(null)?.close()
    }

    private suspend fun events(): Iterator<RunEvent> {
        events?.let { return it }
        check(started.compareAndSet(false, true)) { "Gateway run observation can only be collected once." }
        check(!closed.get()) { "Gateway run observation is closed." }
        val openedStream = openStreamMapped()
        stream.set(openedStream)
        if (closed.get()) {
            stream.compareAndSet(openedStream, null)
            openedStream.close()
            error("Gateway run observation is closed.")
        }
        val seenFrames = mutableSetOf<String>()
        return GatewaySseParser.frames(openedStream.lines, operation)
            .filter { frame -> seenFrames.add(frame.data) }
            .mapNotNull { frame -> parseFrame(frame) }
            .iterator()
            .also { events = it }
    }

    private suspend fun openStreamMapped(): GatewayEventStream =
        try {
            openStream()
        } catch (error: CancellationException) {
            closed.set(true)
            throw error
        } catch (expectedError: Exception) {
            closed.set(true)
            throw expectedError.asFailure()
        }

    private fun Exception.asFailure(): Exception =
        when (this) {
            is GatewayException, is CancellationException -> this
            else -> mapTransportFailure(this)
        }

    private suspend fun read(iterator: Iterator<RunEvent>): RunEvent? {
        if (closed.get()) return null
        return runInterruptible {
            if (iterator.hasNext()) iterator.next() else null
        }
    }
}
