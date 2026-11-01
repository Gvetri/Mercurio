package org.mercurio.client.fixture.journey

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.io.File

object JourneyScenarioParser {
    fun parse(
        scenarioFile: File,
        pinnedHermesRevision: String,
    ): JourneyScenario {
        val text = readScenarioText(scenarioFile)
        val normalizedText = normalizeUnicodeEscapes(text)
        requireSingleProvenanceField(normalizedText)
        val root = requireScenarioRoot(text)
        rejectUnknownKeys(root, allowedTopLevelKeys, "top-level")
        val scenario = requireScenarioFields(root)
        requireKnownEndpoints(scenario)
        requireProvenance(scenario, pinnedHermesRevision)
        return scenario
    }

    private fun readScenarioText(scenarioFile: File): String =
        runCatching { scenarioFile.readText() }.getOrElse { error ->
            throw JourneyScenarioFormatException("Journey scenario could not be read: ${error.message}")
        }

    private fun normalizeUnicodeEscapes(text: String): String =
        UNICODE_ESCAPE.replace(text) { match ->
            val codePoint = match.groupValues[1].toInt(HEX_RADIX)
            if (codePoint in 0..SURROGATE_RANGE_END || codePoint in SURROGATE_RANGE_START..CODE_POINT_MAX) {
                codePoint.toChar().toString()
            } else {
                match.value
            }
        }

    private fun requireSingleProvenanceField(text: String) {
        val provenanceFieldCount = Regex("\"hermes_revision\"\\s*:").findAll(text).count()
        if (provenanceFieldCount != 1) {
            throw JourneyScenarioFormatException(
                "Journey scenario must declare exactly one hermes_revision provenance field.",
            )
        }
    }

    private fun requireScenarioRoot(text: String): JsonObject =
        runCatching { Json.parseToJsonElement(text) as? JsonObject }.getOrNull()
            ?: throw JourneyScenarioFormatException("Journey scenario is not a JSON object.")

    private fun requireScenarioFields(root: JsonObject): JourneyScenario =
        try {
            JourneyScenario(
                name = JourneyScenarioJson.requiredString(root, "name"),
                hermesRevision = JourneyScenarioJson.requiredString(root, "hermes_revision"),
                port = JourneyScenarioJson.requiredInt(root, "port"),
                tls = JourneyScenarioJson.requiredBoolean(root, "tls"),
                capabilities = JourneyScenarioJson.requiredStringList(root, "capabilities"),
                requireBearerCredential = JourneyScenarioJson.optionalString(root, "require_bearer_credential"),
                sessionPageSize = JourneyScenarioJson.optionalInt(root, "session_page_size"),
                sessions = parseSessions(root),
                runs = JourneyScenarioJson.parseRuns(root),
                failNextSessionList =
                    JourneyScenarioJson.optionalBoolean(root, "fail_next_session_list", default = false),
                failAfterFirstSessionList =
                    JourneyScenarioJson.optionalBoolean(root, "fail_after_first_session_list", default = false),
                offlineRecoversAfterBlockedSessionLists =
                    JourneyScenarioJson.optionalInt(root, "offline_recovers_after_blocked_session_lists"),
                failAfterFirstSessionOpen =
                    JourneyScenarioJson.optionalBoolean(root, "fail_after_first_session_open", default = false),
                offlineRecoversAfterBlockedSessionOpens =
                    JourneyScenarioJson.optionalInt(root, "offline_recovers_after_blocked_session_opens"),
                holdCreateMs = JourneyScenarioJson.optionalInt(root, "hold_create_ms"),
                modelCatalog = JourneyScenarioCatalog.parseModelCatalog(root),
                modelCatalogRow = JourneyScenarioCatalog.parseModelCatalogRow(root),
            )
        } catch (error: IllegalArgumentException) {
            throw JourneyScenarioFormatException(error.message ?: "Journey scenario is invalid.", error)
        }

    private fun requireKnownEndpoints(scenario: JourneyScenario) {
        val unknown = scenario.capabilities.filterNot(JourneyEndpointCatalog.endpoints::containsKey)
        if (unknown.isNotEmpty()) {
            throw JourneyScenarioFormatException(
                "Journey scenario declares unsupported capability endpoints: ${unknown.sorted()}",
            )
        }
    }

    private fun requireProvenance(
        scenario: JourneyScenario,
        pinnedHermesRevision: String,
    ) {
        if (scenario.hermesRevision != pinnedHermesRevision) {
            throw JourneyScenarioFormatException(
                "Journey scenario provenance does not match the pinned fixture descriptor.",
            )
        }
    }

    private fun parseSessions(root: JsonObject): List<JourneySession> =
        root["sessions"]?.jsonArray.orEmpty().mapIndexed { index, element ->
            val session = element.jsonObject
            rejectUnknownKeys(session, allowedSessionKeys, "session[$index]")
            JourneySession(
                id = JourneyScenarioJson.requiredString(session, "id"),
                title = JourneyScenarioJson.optionalString(session, "title"),
                preview = JourneyScenarioJson.optionalString(session, "preview"),
                pinned = JourneyScenarioJson.optionalBoolean(session, "pinned", default = false),
                history = JourneyScenarioJson.parseMessages(session, "history", "session[$index].history"),
            )
        }
}

private const val HEX_RADIX = 16

private const val SURROGATE_RANGE_END = 0xD7FF

private const val SURROGATE_RANGE_START = 0xE000

private const val CODE_POINT_MAX = 0xFFFF

private val allowedTopLevelKeys =
    setOf(
        "name",
        "hermes_revision",
        "port",
        "tls",
        "capabilities",
        "require_bearer_credential",
        "session_page_size",
        "sessions",
        "runs",
        "fail_next_session_list",
        "fail_after_first_session_list",
        "offline_recovers_after_blocked_session_lists",
        "fail_after_first_session_open",
        "offline_recovers_after_blocked_session_opens",
        "hold_create_ms",
        "model_catalog",
        "model_catalog_row",
    )
private val allowedSessionKeys = setOf("id", "title", "preview", "pinned", "history")
internal val allowedMessageKeys = setOf("id", "role", "content", "timestamp")
internal val allowedRunKeys =
    setOf(
        "run_id",
        "session_id",
        "create_status",
        "observation",
        "interrupt_after_events",
        "hold_open",
        "finalize_on_disconnect",
        "refuse_create",
        "admission_refusal_code",
        "live_framing",
        "final_status",
        "terminal_history",
        "idle_gap_ms",
    )
internal val allowedEventKeys = setOf("type", "delta")
internal val allowedCatalogKeys = setOf("default_model", "models")
internal val allowedCatalogEntryKeys = setOf("id", "display_name", "provider", "effort")
internal val allowedEffortKeys = setOf("supported", "default")
internal val allowedCatalogRowKeys = setOf("method", "path")
private val UNICODE_ESCAPE = Regex("\\\\u([0-9a-fA-F]{4})")
