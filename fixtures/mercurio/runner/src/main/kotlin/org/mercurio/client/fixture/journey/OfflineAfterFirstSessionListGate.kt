package org.mercurio.client.fixture.journey

import java.util.concurrent.atomic.AtomicInteger

/**
 * Blocks every Session list and Session resource request once one list has succeeded.
 * With a [recoversAfterBlockedSessionLists] bound, the gate stops blocking after that
 * many blocked list requests, so a journey can script offline content followed by a
 * recovered authoritative read.
 */
internal class OfflineAfterFirstSessionListGate(
    private val enabled: Boolean,
    private val recoversAfterBlockedSessionLists: Int? = null,
) {
    private val sessionListRequests = AtomicInteger(0)
    private val blockedSessionListRequests = AtomicInteger(0)

    @Volatile
    private var armed = false

    @Volatile
    private var recovered = false

    val blocking: Boolean
        get() = enabled && armed && !recovered

    fun onSessionListRequest(): Boolean {
        if (!enabled || sessionListRequests.incrementAndGet() <= 1) {
            return false
        }
        armed = true
        val blocked = if (recovered) null else blockedSessionListRequests.incrementAndGet()
        val shouldBlock =
            blocked != null &&
                (recoversAfterBlockedSessionLists == null || blocked <= recoversAfterBlockedSessionLists)
        if (!shouldBlock) {
            recovered = true
        }
        return shouldBlock
    }
}
