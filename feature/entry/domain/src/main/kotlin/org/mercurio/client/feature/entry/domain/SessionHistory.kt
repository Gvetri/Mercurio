package org.mercurio.client.feature.entry.domain

data class SessionHistory(
    val sessionId: SessionId,
    val messages: List<GatewayHistoryMessage>,
)

fun SessionHistory.runs(): List<Run> {
    val runsById = linkedMapOf<RunId, Run>()
    messages.forEach { message ->
        val runId = message.runId
        val explicitStatus = message.runStatus?.takeIf(String::isNotBlank)
        if (runId != null) {
            val knownStatus = explicitStatus ?: runsById[runId]?.status ?: UNKNOWN_RUN_STATUS
            runsById.remove(runId)
            runsById[runId] = Run(runId, sessionId, knownStatus)
        }
    }
    return runsById.values.toList()
}
