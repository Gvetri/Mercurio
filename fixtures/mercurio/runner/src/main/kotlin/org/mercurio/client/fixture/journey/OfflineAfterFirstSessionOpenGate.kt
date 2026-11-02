package org.mercurio.client.fixture.journey

import java.util.concurrent.atomic.AtomicInteger

/**
 * Blocks every Session transcript read once one has succeeded, leaving the Session list and
 * the companion Session resource reads untouched. With a [recoversAfterBlockedSessionOpens]
 * bound, the gate stops blocking after that many blocked transcript reads, so a journey can
 * script a cached-transcript arm followed by a recovered authoritative re-open. The recovery
 * arm must stay reachable from the same route the gate blocks, which is why the companion
 * Session read is deliberately not blocked while this gate is armed.
 */
internal class OfflineAfterFirstSessionOpenGate(
    private val enabled: Boolean,
    private val recoversAfterBlockedSessionOpens: Int? = null,
) {
    private val sessionHistoryRequests = AtomicInteger(0)
    private val blockedSessionHistoryRequests = AtomicInteger(0)

    @Volatile
    private var recovered = false

    fun onSessionHistoryRequest(): Boolean {
        if (!enabled || sessionHistoryRequests.incrementAndGet() <= 1) {
            return false
        }
        val blocked = if (recovered) null else blockedSessionHistoryRequests.incrementAndGet()
        val shouldBlock =
            blocked != null &&
                (recoversAfterBlockedSessionOpens == null || blocked <= recoversAfterBlockedSessionOpens)
        if (!shouldBlock) {
            recovered = true
        }
        return shouldBlock
    }
}
