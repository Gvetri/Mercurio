package org.mercurio.client.fixture.journey

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal object JourneyInvariants {
    private val scenarioVerifiers: Map<String, (JsonObject) -> Unit> =
        mapOf(
            "connection" to ::requireConnectionAttempted,
            "session-list-first" to ::requireConnectionAttempted,
            "empty-sessions" to JourneySessionCreationInvariants::verifyEmptySessions,
            "pending-creation" to JourneySessionCreationInvariants::verifyPendingCreation,
            "session-lifecycle" to JourneySessionLifecycleInvariants::verifySessionLifecycle,
            "pagination-search" to ::requireConnectionAttempted,
            "failed-offline-send" to JourneyRunInvariants::verifyFailedOfflineSend,
            "offline-session-list" to JourneyInvariants::verifyOfflineSessionList,
            "markdown-links" to ::requireConnectionAttempted,
            "accessibility-controls" to ::requireConnectionAttempted,
            "capabilities-additive" to JourneyInvariants::verifyCapabilitiesAdditive,
            "capabilities-missing-required" to ::requireConnectionAttempted,
            "active-run-isolation" to JourneyRunInvariants::verifyActiveRunIsolation,
            "streaming" to JourneyRunInvariants::verifyStreaming,
            "live-streaming" to JourneyRunInvariants::verifyLiveStreaming,
            "interrupted-sse-refetch" to JourneyRunInvariants::verifyInterruptedSseRefetch,
            "sse-idle-gap-streaming" to ::verifyIdleGapStreaming,
            "composer-stop" to JourneyRunInvariants::verifyComposerStop,
            "terminal-success" to JourneyRunInvariants::verifyTerminal,
            "terminal-failure" to JourneyRunInvariants::verifyTerminal,
            "explicit-retry" to JourneyRunInvariants::verifyExplicitRetry,
            "confirmed-send" to JourneyRunInvariants::verifyConfirmedSend,
            "markdown-blocks" to ::requireConnectionAttempted,
            "stale-recoverable" to ::requireConnectionAttempted,
            "diagnostics-recording" to JourneyDiagnosticsInvariants::verifyDiagnosticsRecording,
            "error-reporting" to JourneyErrorReportingInvariants::verifyErrorReporting,
            "settings" to JourneyInvariants::verifySettings,
            "settings-cache-clear" to JourneyInvariants::verifySettingsCacheClear,
            "connection-removal-purge" to JourneyInvariants::verifyConnectionRemovalPurge,
            "cache-clear-repopulates" to JourneyInvariants::verifyCacheClearRepopulates,
            "offline-cache-read" to JourneyInvariants::verifyOfflineCacheRead,
            "cache-run-isolation" to JourneyInvariants::verifyCacheRunIsolation,
            "model-picker-advertised" to JourneyModelPickerInvariants::verifyModelPickerAdvertised,
            "model-picker-absent" to JourneyModelPickerInvariants::verifyModelPickerAbsent,
            "model-picker-refusal" to JourneyModelPickerInvariants::verifyModelPickerRefusal,
            "model-catalog-row-mismatch" to JourneyModelPickerInvariants::verifyModelCatalogRowMismatch,
        )

    fun verify(
        scenarioName: String,
        telemetry: JsonObject,
    ) {
        scenarioVerifiers[scenarioName]?.invoke(telemetry)
            ?: error("No journey invariants declared for scenario '$scenarioName'.")
    }

    private fun verifySettings(telemetry: JsonObject) {
        requireConnectionAttempted(telemetry)
        val requests =
            telemetry.getValue("requests").jsonArray.map { request ->
                val fields = request.jsonObject
                fields.getValue("method").jsonPrimitive.content to fields.getValue("path").jsonPrimitive.content
            }
        val writes = requests.filter { (method, _) -> method != "GET" }
        check(writes.isEmpty()) {
            "The Settings journey mutated Gateway state: $writes"
        }
        check(requests.any { (method, path) -> method == "GET" && path == "/api/sessions" }) {
            "The Settings journey never loaded the Session list."
        }
        val runCreates = telemetry["run_creates"]?.jsonObject.orEmpty()
        check(runCreates.values.all { it.jsonPrimitive.int == 0 }) {
            "The Settings journey recorded a Run creation: $runCreates"
        }
    }

    private fun verifyOfflineSessionList(telemetry: JsonObject) {
        requireConnectionAttempted(telemetry)
        val paths = recordedRequestPaths(telemetry)
        check(paths.count { it == "/api/sessions" } >= 2) {
            "The client did not retry the Session list while the Gateway was offline."
        }
        check(paths.any { it.startsWith("/api/sessions/") }) {
            "The client did not attempt to open a Session while the Gateway was offline."
        }
    }

    private fun requireCacheLifecycleIsLocalOnly(
        telemetry: JsonObject,
        label: String,
    ) {
        requireConnectionAttempted(telemetry)
        val requests =
            telemetry.getValue("requests").jsonArray.map { request ->
                val fields = request.jsonObject
                fields.getValue("method").jsonPrimitive.content to fields.getValue("path").jsonPrimitive.content
            }
        val writes = requests.filter { (method, _) -> method != "GET" }
        check(writes.isEmpty()) {
            "The $label journey mutated Gateway state: $writes"
        }
        val runCreates = telemetry["run_creates"]?.jsonObject.orEmpty()
        check(runCreates.values.all { it.jsonPrimitive.int == 0 }) {
            "The $label journey recorded a Run creation: $runCreates"
        }
    }

    private fun verifySettingsCacheClear(telemetry: JsonObject) {
        requireCacheLifecycleIsLocalOnly(telemetry, "Settings cache clear")
        check(recordedRequestPaths(telemetry).any { it == "/api/sessions" }) {
            "The Settings cache clear journey never loaded the Session list."
        }
    }

    private fun verifyConnectionRemovalPurge(telemetry: JsonObject) {
        requireCacheLifecycleIsLocalOnly(telemetry, "Connection removal purge")
        check(recordedRequestPaths(telemetry).count { it == "/api/sessions" } >= 2) {
            "The Connection removal purge journey never reloaded the Session list after reconnecting."
        }
    }

    private fun verifyCacheClearRepopulates(telemetry: JsonObject) {
        requireCacheLifecycleIsLocalOnly(telemetry, "Cache clear repopulation")
        val paths = recordedRequestPaths(telemetry)
        check(paths.count { it == "/api/sessions" } >= MIN_CACHE_REPOPULATION_LIST_READS) {
            "The Cache clear repopulation journey did not retry the Session list enough times: $paths"
        }
        check(paths.count { it.startsWith("/api/sessions/") } >= MIN_CACHE_REPOPULATION_RESOURCE_READS) {
            "The Cache clear repopulation journey did not read Session content through the offline and recovered arms."
        }
    }

    private fun verifyOfflineCacheRead(telemetry: JsonObject) {
        requireCacheLifecycleIsLocalOnly(telemetry, "Offline cache read")
        val paths = recordedRequestPaths(telemetry)
        check(paths.count { it == "/api/sessions" } >= MIN_OFFLINE_CACHE_READ_LIST_READS) {
            "The Offline cache read journey did not exercise the Session list offline and recovered arms: $paths"
        }
        check(paths.count { it.endsWith("/messages") } >= MIN_OFFLINE_CACHE_READ_TRANSCRIPT_READS) {
            "The Offline cache read journey did not attempt the Session transcript offline: $paths"
        }
    }

    private fun verifyCacheRunIsolation(telemetry: JsonObject) {
        JourneyRunInvariants.verifyFailedOfflineSend(telemetry)
        val paths = recordedRequestPaths(telemetry)
        check(paths.count { it.endsWith("/messages") } >= MIN_RUN_ISOLATION_TRANSCRIPT_READS) {
            "The Run isolation journey did not read the Session transcript around the restart: $paths"
        }
        check(telemetry["session_mutations"]?.jsonArray.orEmpty().isEmpty()) {
            "The Run isolation journey mutated Session state beyond the scripted Runs."
        }
    }

    private fun verifyCapabilitiesAdditive(telemetry: JsonObject) {
        requireConnectionAttempted(telemetry)
        check(runCreatesFor(telemetry, ACTIVE_SESSION_ID) == 1) {
            "The client submitted the additive Run more than once."
        }
    }

    const val ACTIVE_RUN_ID = "active-run"
    const val ACTIVE_SESSION_ID = "session-alpha"
    const val STREAMING_RUN_ID = "streaming-run"
    const val MIN_CACHE_REPOPULATION_LIST_READS = 4
    const val MIN_CACHE_REPOPULATION_RESOURCE_READS = 4
    const val MIN_OFFLINE_CACHE_READ_LIST_READS = 3
    const val MIN_OFFLINE_CACHE_READ_TRANSCRIPT_READS = 2
    const val MIN_RUN_ISOLATION_TRANSCRIPT_READS = 2
    const val LIVE_RUN_ID = "live-run"
    const val STOP_RUN_ID = "stop-run"
    const val INTERRUPTED_RUN_ID = "interrupted-run"
    const val RETRY_SESSION_ID = "session-retry"
    const val CONFIRMED_SEND_SESSION_ID = "session-ux"
    const val CONFIRMED_SEND_RUN_ID = "run-ux-1"
}
