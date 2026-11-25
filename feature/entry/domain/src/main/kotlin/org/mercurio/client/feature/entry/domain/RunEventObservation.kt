package org.mercurio.client.feature.entry.domain

/**
 * One-shot pull stream of the events of one Run.
 *
 * The consumer pulls one event at a time, so it stops at the terminal event and another
 * coroutine may close the stream. [next] suspends until an event arrives, so a consumer can be
 * stopped inside the call by a test dispatcher and a double can gate it with a channel or a
 * deferred instead of blocking a thread.
 */
interface RunEventObservation : AutoCloseable {
    /** Returns the next event, or `null` once the stream ended. */
    suspend fun next(): RunEvent?
}
