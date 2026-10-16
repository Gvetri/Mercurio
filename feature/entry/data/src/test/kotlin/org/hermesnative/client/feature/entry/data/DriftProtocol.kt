package org.hermesnative.client.feature.entry.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

internal const val DRIFT_LEDGER_FILE_NAME = "contract-drift-issues.jsonl"
internal const val DRIFT_ISSUE_LABEL = "ready-for-agent"
internal const val DRIFT_STATUS_OPEN = "open"
internal const val DRIFT_STATUS_RESOLVED = "resolved"
internal const val DRIFT_MANIFEST_VERSION_GUIDANCE =
    "Manifest version advances only when a required endpoint's name, method or path changes."

internal data class DriftIssueRecord(
    val id: String,
    val label: String,
    val status: String,
    val title: String,
    val divergentOperations: List<String>,
    val evidenceReference: String,
    val evidence: String,
    val occurrences: Int,
    val proposedRebaseline: String,
    val manifestVersionGuidance: String,
    val filedIssueUrl: String? = null,
)

internal interface DriftIssueSink {
    fun openIssues(): List<DriftIssueRecord>

    fun upsert(record: DriftIssueRecord)
}

internal object DriftProtocol {
    fun reconcile(
        entries: List<FixtureParityEntry>,
        sink: DriftIssueSink,
    ) {
        val openIssues = sink.openIssues()
        entries
            .filter { it.verdict == FixtureParity.VERDICT_MISMATCH }
            .forEach { entry ->
                val existing = openIssues.firstOrNull { issue -> issue.id == divergenceId(entry) }
                sink.upsert(
                    when (existing) {
                        null -> filedRecord(entry)
                        else -> existing.copy(occurrences = existing.occurrences + 1, evidence = evidenceSummary(entry))
                    },
                )
            }
        val matchedIds =
            entries
                .filter { it.verdict == FixtureParity.VERDICT_MATCH }
                .mapTo(mutableSetOf(), ::divergenceId)
        openIssues
            .filter { issue -> issue.id in matchedIds }
            .forEach { issue -> sink.upsert(issue.copy(status = DRIFT_STATUS_RESOLVED)) }
    }

    private fun filedRecord(entry: FixtureParityEntry): DriftIssueRecord =
        DriftIssueRecord(
            id = divergenceId(entry),
            label = DRIFT_ISSUE_LABEL,
            status = DRIFT_STATUS_OPEN,
            title = "Contract alignment: ${entry.operation} diverges from ${entry.fixture}",
            divergentOperations = listOf(entry.operation),
            evidenceReference = LIVE_PARITY_ARTIFACT_FILE_NAME,
            evidence = evidenceSummary(entry),
            occurrences = 1,
            proposedRebaseline = proposedRebaseline(entry.fixture),
            manifestVersionGuidance = DRIFT_MANIFEST_VERSION_GUIDANCE,
        )

    private fun divergenceId(entry: FixtureParityEntry): String = "${entry.operation} :: ${entry.fixture}"

    private fun evidenceSummary(entry: FixtureParityEntry): String {
        val capture =
            if (entry.observedStatus == null) {
                "live capture missing"
            } else {
                "live capture status ${entry.observedStatus}"
            }
        val difference = entry.observedDifference ?: "observed difference not recorded"
        return "$capture; $difference"
    }

    private fun proposedRebaseline(fixture: String): String =
        "Re-baseline $fixture to the observed live shape through a reviewed change that cites this issue; " +
            "the drift protocol changes no fixture and no parser."
}

internal class DriftIssueFiler(
    private val repository: String,
    private val sink: DriftIssueSink,
) {
    fun fileOpenIssues(runner: (List<String>) -> String): List<String> =
        sink
            .openIssues()
            .filter { it.filedIssueUrl == null }
            .map { issue ->
                val url = runner(ghCreateCommand(issue)).trim()
                sink.upsert(issue.copy(filedIssueUrl = url))
                url
            }

    internal fun ghCreateCommand(issue: DriftIssueRecord): List<String> =
        listOf(
            "gh",
            "issue",
            "create",
            "--repo",
            repository,
            "--label",
            issue.label,
            "--title",
            issue.title,
            "--body",
            issueBody(issue),
        )

    private fun issueBody(issue: DriftIssueRecord): String =
        listOf(
            issue.evidence,
            "Divergent operations: ${issue.divergentOperations.joinToString(separator = ", ")}",
            "Occurrences: ${issue.occurrences}",
            "Evidence reference: ${issue.evidenceReference}",
            issue.proposedRebaseline,
            issue.manifestVersionGuidance,
        ).joinToString(separator = "\n\n")
}

internal fun ghIssueRunner(command: List<String>): String {
    val process =
        ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()
    val output = process.inputStream.bufferedReader().readText()
    val exited = process.waitFor()
    require(exited == 0) { "The tracker issue filing command failed with exit code $exited: $output" }
    return output
}

internal class LocalIssueLedger(
    private val ledgerFile: File,
    private val workspaceRoot: File,
) : DriftIssueSink {
    override fun openIssues(): List<DriftIssueRecord> = readRecords().filter { it.status == DRIFT_STATUS_OPEN }

    override fun upsert(record: DriftIssueRecord) {
        val records = readRecords()
        val existingIndex = records.indexOfFirst { it.id == record.id }
        val nextRecords =
            if (existingIndex >= 0) {
                records.toMutableList().apply { this[existingIndex] = record }
            } else {
                records + record
            }
        writeRecords(nextRecords)
    }

    private fun readRecords(): List<DriftIssueRecord> =
        if (ledgerFile.isFile) {
            ledgerFile.readLines().filter(String::isNotBlank).map(::parseRecord)
        } else {
            emptyList()
        }

    private fun writeRecords(records: List<DriftIssueRecord>) {
        ledgerFile.parentFile.mkdirs()
        ledgerFile.writeText(records.joinToString(separator = "") { record -> recordLine(record) })
    }

    private fun recordLine(record: DriftIssueRecord): String =
        EvidenceRedaction.redact(
            JsonObject(
                mapOf(
                    "id" to JsonPrimitive(record.id),
                    "label" to JsonPrimitive(record.label),
                    "status" to JsonPrimitive(record.status),
                    "title" to JsonPrimitive(record.title),
                    "divergentOperations" to JsonArray(record.divergentOperations.map(::JsonPrimitive)),
                    "evidenceReference" to JsonPrimitive(record.evidenceReference),
                    "evidence" to JsonPrimitive(record.evidence),
                    "occurrences" to JsonPrimitive(record.occurrences),
                    "proposedRebaseline" to JsonPrimitive(record.proposedRebaseline),
                    "manifestVersionGuidance" to JsonPrimitive(record.manifestVersionGuidance),
                ) + record.filedIssueUrl?.let { url -> mapOf("filedIssueUrl" to JsonPrimitive(url)) }.orEmpty(),
            ).toString(),
            workspaceRoot,
        ) + "\n"

    private fun parseRecord(line: String): DriftIssueRecord {
        val root = Json.parseToJsonElement(line).jsonObject
        return DriftIssueRecord(
            id = root.getValue("id").jsonPrimitive.content,
            label = root.getValue("label").jsonPrimitive.content,
            status = root.getValue("status").jsonPrimitive.content,
            title = root.getValue("title").jsonPrimitive.content,
            divergentOperations = root.getValue("divergentOperations").jsonArray.map { it.jsonPrimitive.content },
            evidenceReference = root.getValue("evidenceReference").jsonPrimitive.content,
            evidence = root.getValue("evidence").jsonPrimitive.content,
            occurrences = root.getValue("occurrences").jsonPrimitive.content.toInt(),
            proposedRebaseline = root.getValue("proposedRebaseline").jsonPrimitive.content,
            manifestVersionGuidance = root.getValue("manifestVersionGuidance").jsonPrimitive.content,
            filedIssueUrl = root["filedIssueUrl"]?.jsonPrimitive?.content,
        )
    }
}
