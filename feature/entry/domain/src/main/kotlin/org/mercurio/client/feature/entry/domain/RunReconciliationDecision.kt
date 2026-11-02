package org.mercurio.client.feature.entry.domain

enum class RunReconciliationDecision {
    CONFIRMED,
    UNCERTAIN,
}

/**
 * Decides whether an authoritative Run observation settles a client-tracked Run.
 *
 * The pinned Gateway's Run resource (`GET /v1/runs/{run_id}`) is the only
 * authoritative source of Run state: its Session message payloads never link
 * messages to Runs (`api_server._message_response` whitelists no run fields),
 * so a terminal Run status is itself the confirmation. Active Runs remain
 * uncertain until a terminal status is observed; the Session history fetched
 * alongside the status is applied for display but cannot corroborate Run state
 * on this contract.
 */
fun decideRunReconciliation(run: Run): RunReconciliationDecision =
    if (run.isActive()) {
        RunReconciliationDecision.UNCERTAIN
    } else {
        RunReconciliationDecision.CONFIRMED
    }
