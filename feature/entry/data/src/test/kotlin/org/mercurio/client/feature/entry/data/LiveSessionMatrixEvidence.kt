package org.mercurio.client.feature.entry.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File

internal const val EVIDENCE_OUTCOME_VERIFIED = "verified"
internal const val EVIDENCE_OUTCOME_FAILED = "failed"

internal data class RunStreamSummary(
    val eventsByType: Map<String, Int>,
    val deltaCount: Int,
    val terminalEvent: String?,
    val unmappedFramesSkipped: Int,
    val idLines: Int,
    val boundaryComments: Int,
    val keepAliveComments: Boolean,
)

internal data class LiveSessionMatrixEvidence(
    val operation: String,
    val method: String,
    val pathShape: String,
    val queryShape: List<String>,
    val expectedStatus: Int,
    val actualStatus: Int?,
    val outcome: String,
    val envelope: Map<String, String>,
    val errorCategory: String?,
    val streamSummary: RunStreamSummary? = null,
)

internal data class OperationSpec(
    val operation: String,
    val method: String,
    val pathShape: String,
    val expectedStatus: Int,
    val envelope: Map<String, String>,
    val queryShape: List<String> = emptyList(),
)

internal fun verifiedEvidence(
    spec: OperationSpec,
    actualStatus: Int,
): LiveSessionMatrixEvidence =
    LiveSessionMatrixEvidence(
        operation = spec.operation,
        method = spec.method,
        pathShape = spec.pathShape,
        queryShape = spec.queryShape,
        expectedStatus = spec.expectedStatus,
        actualStatus = actualStatus,
        outcome = EVIDENCE_OUTCOME_VERIFIED,
        envelope = spec.envelope,
        errorCategory = null,
    )

internal fun failedEvidence(
    spec: OperationSpec,
    errorCategory: String,
): LiveSessionMatrixEvidence =
    LiveSessionMatrixEvidence(
        operation = spec.operation,
        method = spec.method,
        pathShape = spec.pathShape,
        queryShape = spec.queryShape,
        expectedStatus = spec.expectedStatus,
        actualStatus = null,
        outcome = EVIDENCE_OUTCOME_FAILED,
        envelope = spec.envelope,
        errorCategory = errorCategory,
    )

internal fun verifiedRunObservationEvidence(
    spec: OperationSpec,
    actualStatus: Int,
    streamSummary: RunStreamSummary,
): LiveSessionMatrixEvidence = verifiedEvidence(spec, actualStatus).copy(streamSummary = streamSummary)

internal object EvidenceRedaction {
    fun redact(
        line: String,
        workspaceRoot: File,
    ): String {
        val withoutBearerValues =
            BEARER_TOKEN_PATTERN.replace(line) { match -> "${match.groupValues[1]}<redacted>" }
        val withoutSensitiveValues =
            SENSITIVE_KEY_VALUE_PATTERN.replace(withoutBearerValues) { match ->
                "${match.groupValues[1]}\"<redacted>\""
            }
        val withoutUrls = URL_PATTERN.replace(withoutSensitiveValues, "<redacted-url>")
        return withoutUrls.replace(workspaceRoot.absolutePath, "<workspace>")
    }

    private val BEARER_TOKEN_PATTERN = Regex("(?i)\\b((?:bearer|basic)\\s+)[^\\s,;\"']+")

    private val SENSITIVE_KEY_VALUE_PATTERN =
        Regex(
            "(?i)([\"']?(?:authorization|token|password|secret|api[_-]?key|access[_-]?token|" +
                "client[_-]?secret|private[_-]?key|input[_-]?text)[\"']?\\s*[:=]\\s*)" +
                "\"(?:\\\\.|[^\"\\\\\\r\\n])*\"",
        )

    private val URL_PATTERN = Regex("(?i)https?://[^\\s<>\"']+")
}

internal class LiveSessionMatrixRecorder(
    sink: File,
    private val workspaceRoot: File,
) {
    private val sinkFile: File

    init {
        sink.parentFile.mkdirs()
        sink.delete()
        sinkFile = sink
    }

    val file: File
        get() = sinkFile

    fun record(evidence: LiveSessionMatrixEvidence) {
        sinkFile.appendText(redact(jsonLine(evidence)) + "\n")
    }

    private fun jsonLine(evidence: LiveSessionMatrixEvidence): String =
        JsonObject(
            mapOf(
                "operation" to JsonPrimitive(evidence.operation),
                "method" to JsonPrimitive(evidence.method),
                "pathShape" to JsonPrimitive(evidence.pathShape),
                "queryShape" to JsonArray(evidence.queryShape.map(::JsonPrimitive)),
                "expectedStatus" to JsonPrimitive(evidence.expectedStatus),
                "actualStatus" to (evidence.actualStatus?.let(::JsonPrimitive) ?: JsonNull),
                "outcome" to JsonPrimitive(evidence.outcome),
                "envelope" to JsonObject(evidence.envelope.mapValues { JsonPrimitive(it.value) }),
                "errorCategory" to (evidence.errorCategory?.let(::JsonPrimitive) ?: JsonNull),
                "streamSummary" to (evidence.streamSummary?.let(::summaryObject) ?: JsonNull),
            ),
        ).toString()

    private fun summaryObject(summary: RunStreamSummary): JsonObject =
        JsonObject(
            mapOf(
                "eventsByType" to JsonObject(summary.eventsByType.mapValues { JsonPrimitive(it.value) }),
                "deltaCount" to JsonPrimitive(summary.deltaCount),
                "terminalEvent" to (summary.terminalEvent?.let(::JsonPrimitive) ?: JsonNull),
                "unmappedFramesSkipped" to JsonPrimitive(summary.unmappedFramesSkipped),
                "idLines" to JsonPrimitive(summary.idLines),
                "boundaryComments" to JsonPrimitive(summary.boundaryComments),
                "keepAliveComments" to JsonPrimitive(summary.keepAliveComments),
            ),
        )

    private fun redact(line: String): String = EvidenceRedaction.redact(line, workspaceRoot)
}
