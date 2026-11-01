package org.mercurio.client.fixture.journey

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal object JourneyRunInvariants {
    internal fun verifyActiveRunIsolation(telemetry: JsonObject) {
        val sse = sseFor(telemetry, JourneyInvariants.ACTIVE_RUN_ID)
        check(sse.opened == 2) {
            "Active Run was observed ${sse.opened} times; expected 2 (initial plus re-open, no refresh duplicate)."
        }
        check(sse.closed == 1) {
            "Active Run observation closed ${sse.closed} times; expected exactly 1 (Session switch)."
        }
        check(runStatusRequests(telemetry) >= 1) { "The client did not re-check the active Run status." }
        check(runCreatesFor(telemetry, JourneyInvariants.ACTIVE_SESSION_ID) == 1) {
            "The client submitted the active Run more than once."
        }
        check(recordedRequestPaths(telemetry).none { it.contains("cancel", ignoreCase = true) }) {
            "The client attempted to cancel the active Run while switching Sessions."
        }
        check(recordedRequestMethods(telemetry).none { it.equals("DELETE", ignoreCase = true) }) {
            "The client issued a DELETE request while switching Sessions."
        }
    }

    internal fun verifyStreaming(telemetry: JsonObject) {
        val sse = sseFor(telemetry, JourneyInvariants.STREAMING_RUN_ID)
        check(sse.opened == 1) { "Streaming Run was observed ${sse.opened} times; expected exactly 1." }
        check(runCreatesFor(telemetry, JourneyInvariants.ACTIVE_SESSION_ID) == 1) {
            "The client submitted the streaming Run more than once."
        }
    }

    internal fun verifyLiveStreaming(telemetry: JsonObject) {
        val sse = sseFor(telemetry, JourneyInvariants.LIVE_RUN_ID)
        check(sse.opened == 1) { "Live-framed Run was observed ${sse.opened} times; expected exactly 1." }
        check(runStatusRequests(telemetry) >= 1) { "The client did not reconcile the live-framed Run." }
        check(runCreatesFor(telemetry, JourneyInvariants.ACTIVE_SESSION_ID) == 1) {
            "The client submitted the live-framed Run more than once."
        }
    }

    internal fun verifyInterruptedSseRefetch(telemetry: JsonObject) {
        val sse = sseFor(telemetry, JourneyInvariants.INTERRUPTED_RUN_ID)
        check(sse.opened == 1) { "Interrupted Run was observed ${sse.opened} times; expected exactly 1." }
        check(JourneyInvariants.INTERRUPTED_RUN_ID in interruptedRunIds(telemetry)) {
            "The Gateway did not record the interrupted Run."
        }
        check(runStatusRequests(telemetry) >= 1) { "The client did not refetch authoritative Run status." }
        check(runCreatesFor(telemetry, JourneyInvariants.ACTIVE_SESSION_ID) == 1) {
            "The client re-submitted the interrupted Run."
        }
    }

    internal fun verifyComposerStop(telemetry: JsonObject) {
        val sse = sseFor(telemetry, JourneyInvariants.STOP_RUN_ID)
        check(sse.opened == 1) { "Stopped Run was observed ${sse.opened} times; expected exactly 1." }
        check(sse.closed == 1) {
            "Stopped Run observation closed ${sse.closed} times; expected exactly 1 (the composer stop)."
        }
        check(JourneyInvariants.STOP_RUN_ID !in interruptedRunIds(telemetry)) {
            "The Gateway interrupted the Run; the client did not stop it."
        }
        check(runStatusRequests(telemetry) >= 1) { "The client did not reconcile the Run after stopping." }
        check(runCreatesFor(telemetry, JourneyInvariants.ACTIVE_SESSION_ID) == 1) {
            "The client submitted the stopped Run more than once."
        }
    }

    internal fun verifyTerminal(telemetry: JsonObject) {
        requireConnectionAttempted(telemetry)
        check(runStatusRequests(telemetry) >= 1) { "The client did not reconcile the terminal Run." }
        val submissions = runCreatesFor(telemetry, JourneyInvariants.ACTIVE_SESSION_ID)
        check(submissions == 1) {
            "The client submitted the terminal Run $submissions times; " +
                "expected exactly 1 (no duplicate retry submissions)."
        }
    }

    internal fun verifyExplicitRetry(telemetry: JsonObject) {
        check(runCreatesFor(telemetry, JourneyInvariants.RETRY_SESSION_ID) == 2) {
            "The client did not submit exactly two Run creations (initial run plus one explicit retry)."
        }
        check(runStatusRequests(telemetry) >= 1) { "The client did not reconcile Run status." }
    }

    internal fun verifyFailedOfflineSend(telemetry: JsonObject) {
        requireConnectionAttempted(telemetry)
        check(runCreatesFor(telemetry, OFFLINE_SEND_SESSION_ID) == 2) {
            "The client did not submit exactly two Run creations (one refused while offline plus one recovery retry)."
        }
        check(runStatusRequests(telemetry) >= 1) { "The client did not reconcile the recovered Run." }
    }

    internal fun verifyConfirmedSend(telemetry: JsonObject) {
        requireConnectionAttempted(telemetry)
        check(runCreatesFor(telemetry, JourneyInvariants.CONFIRMED_SEND_SESSION_ID) == 1) {
            "The confirmed send did not submit exactly one Run creation."
        }
        check(runStatusRequests(telemetry) >= 1) { "The client did not reconcile the confirmed Run." }
        val sse = sseFor(telemetry, JourneyInvariants.CONFIRMED_SEND_RUN_ID)
        check(sse.opened >= 1) { "The client did not observe the confirmed Run." }
    }

    private const val OFFLINE_SEND_SESSION_ID = "session-offline"
}

internal fun recordedRequestMethods(telemetry: JsonObject): List<String> =
    telemetry["requests"]?.jsonArray.orEmpty().mapNotNull { request ->
        request.jsonObject["method"]?.jsonPrimitive?.content
    }
