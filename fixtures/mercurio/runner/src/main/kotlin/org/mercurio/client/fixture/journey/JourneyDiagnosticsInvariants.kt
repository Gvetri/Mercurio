package org.mercurio.client.fixture.journey

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal object JourneyDiagnosticsInvariants {
    internal fun verifyDiagnosticsRecording(telemetry: JsonObject) {
        requireConnectionAttempted(telemetry)
        val loadedSessionList =
            telemetry["requests"]?.jsonArray.orEmpty().any { request ->
                val fields = request.jsonObject
                fields["method"]?.jsonPrimitive?.content == "GET" &&
                    fields["path"]?.jsonPrimitive?.content == "/api/sessions"
            }
        check(loadedSessionList) { "The diagnostics journey never loaded the Session list." }
    }
}
