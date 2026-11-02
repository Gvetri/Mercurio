package org.mercurio.client.fixture.journey

import kotlinx.serialization.json.JsonObject

/**
 * Explicit, repository-owned configuration for one deterministic emulator
 * journey. One scenario file backs exactly one Maestro flow.
 */
data class JourneyScenario(
    val name: String,
    val hermesRevision: String,
    val port: Int,
    val tls: Boolean,
    val capabilities: List<String>,
    val requireBearerCredential: String? = null,
    val sessionPageSize: Int? = null,
    val sessions: List<JourneySession> = emptyList(),
    val runs: List<JourneyRunScript> = emptyList(),
    val failNextSessionList: Boolean = false,
    val failAfterFirstSessionList: Boolean = false,
    val offlineRecoversAfterBlockedSessionLists: Int? = null,
    val failAfterFirstSessionOpen: Boolean = false,
    val offlineRecoversAfterBlockedSessionOpens: Int? = null,
    val holdCreateMs: Int? = null,
    val modelCatalog: JourneyModelCatalog? = null,
    val modelCatalogRow: JourneyModelCatalogRow? = null,
) {
    init {
        require(name.matches(Regex("[a-z][a-z0-9-]*"))) {
            "Journey scenario names must use lowercase kebab-case."
        }
        require(port in MIN_PORT..MAX_PORT) { "Journey scenario port must be valid." }
        require(capabilities.isNotEmpty()) { "Journey scenarios must declare capabilities." }
        require(holdCreateMs == null || holdCreateMs in 1..MAX_HOLD_CREATE_MILLIS) {
            "hold_create_ms must be between 1 and 9000 milliseconds."
        }
        require(modelCatalogRow == null || "run_models" in capabilities) {
            "A model catalog row override requires the run_models capability."
        }
        require(
            offlineRecoversAfterBlockedSessionLists == null ||
                (failAfterFirstSessionList && offlineRecoversAfterBlockedSessionLists >= 1),
        ) {
            "Offline recovery counts require the after-first-list offline gate and a positive bound."
        }
        require(
            offlineRecoversAfterBlockedSessionOpens == null ||
                (failAfterFirstSessionOpen && offlineRecoversAfterBlockedSessionOpens >= 1),
        ) {
            "Offline recovery counts for Session opens require the after-first-open offline gate and a positive bound."
        }
    }
}

private const val MIN_PORT = 1

private const val MAX_PORT = 65535

private const val MAX_HOLD_CREATE_MILLIS = 9000

internal fun rejectUnknownKeys(
    container: JsonObject,
    allowed: Set<String>,
    context: String,
) {
    val unknown = container.keys - allowed
    if (unknown.isNotEmpty()) {
        throw JourneyScenarioFormatException(
            "Journey scenario $context declares unsupported fields: ${unknown.sorted()}",
        )
    }
}
