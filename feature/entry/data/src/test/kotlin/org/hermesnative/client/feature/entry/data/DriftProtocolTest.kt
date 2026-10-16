package org.hermesnative.client.feature.entry.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DriftProtocolTest {
    private val repositoryRoot =
        File(
            requireNotNull(System.getProperty("fixture.repositoryRoot")) {
                "fixture.repositoryRoot must identify the repository root"
            },
        )
    private val contractsRoot = repositoryRoot.resolve("fixtures/hermes/contracts")

    @Test
    fun parity_mismatch_files_exactly_one_issue_for_the_divergence() {
        val ledgerFile = ledgerFile("filed-on-mismatch")

        DriftProtocol.reconcile(mismatchedEntries(), LocalIssueLedger(ledgerFile, repositoryRoot))

        val records = LocalIssueLedger(ledgerFile, repositoryRoot).openIssues()
        assertEquals(1, records.size)
        assertEquals(1, ledgerFile.readLines().filter(String::isNotBlank).size)
        val record = records.single()
        assertEquals("session open :: sessions/open-response.json", record.id)
        assertEquals(DRIFT_ISSUE_LABEL, record.label)
        assertEquals(DRIFT_STATUS_OPEN, record.status)
        assertEquals(listOf("session open"), record.divergentOperations)
        assertEquals(LIVE_PARITY_ARTIFACT_FILE_NAME, record.evidenceReference)
        assertEquals(1, record.occurrences)
        assertTrue(record.title.contains("Contract alignment"))
        assertTrue(record.title.contains("session open"))
        assertTrue(record.title.contains("sessions/open-response.json"))
        assertTrue(record.evidence.contains("live capture status 200"))
        assertTrue(record.evidence.contains("response.body.object"))
        assertTrue(record.proposedRebaseline.contains("sessions/open-response.json"))
        assertTrue(record.proposedRebaseline.contains("reviewed change"))
        assertEquals(DRIFT_MANIFEST_VERSION_GUIDANCE, record.manifestVersionGuidance)
    }

    @Test
    fun filed_issue_evidence_is_redacted_in_the_ledger() {
        val urlLedgerFile = ledgerFile("redacted-url")
        val urlEntries =
            FixtureParity.compare(
                listOf(
                    capture(path = "https://captured.invalid/api/sessions/synthetic-created-session-1")
                        .copy(responseJson = WRONG_OBJECT_ENVELOPE),
                ),
                contractsRoot,
            )

        DriftProtocol.reconcile(urlEntries, LocalIssueLedger(urlLedgerFile, repositoryRoot))

        val urlLine = urlLedgerFile.readText()
        assertFalse(urlLine.contains("captured.invalid"))
        assertTrue(urlLine.contains("<redacted-url>"))

        val workspaceLedgerFile = ledgerFile("redacted-workspace")
        val workspaceEntries =
            FixtureParity.compare(
                listOf(
                    capture(path = "${repositoryRoot.absolutePath}/api/sessions/synthetic-created-session-1")
                        .copy(responseJson = WRONG_OBJECT_ENVELOPE),
                ),
                contractsRoot,
            )

        DriftProtocol.reconcile(workspaceEntries, LocalIssueLedger(workspaceLedgerFile, repositoryRoot))

        val workspaceLine = workspaceLedgerFile.readText()
        assertFalse(workspaceLine.contains(repositoryRoot.absolutePath))
        assertTrue(workspaceLine.contains("<workspace>"))
    }

    @Test
    fun ledger_writes_carry_redacted_content_only() {
        val ledgerFile = ledgerFile("redaction-vocabulary")
        val sink = LocalIssueLedger(ledgerFile, repositoryRoot)

        sink.upsert(
            DriftIssueRecord(
                id = "session open :: sessions/open-response.json",
                label = DRIFT_ISSUE_LABEL,
                status = DRIFT_STATUS_OPEN,
                title = "Contract alignment: session open diverges from sessions/open-response.json",
                divergentOperations = listOf("session open"),
                evidenceReference = LIVE_PARITY_ARTIFACT_FILE_NAME,
                evidence =
                    "Bearer abc123456 at https://captured.invalid/api from " +
                        "${repositoryRoot.absolutePath}/contracts",
                occurrences = 1,
                proposedRebaseline = "Re-baseline sessions/open-response.json.",
                manifestVersionGuidance = DRIFT_MANIFEST_VERSION_GUIDANCE,
            ),
        )

        val line = ledgerFile.readText()
        assertFalse(line.contains("abc123456"))
        assertTrue(line.contains("<redacted>"))
        assertFalse(line.contains("captured.invalid"))
        assertTrue(line.contains("<redacted-url>"))
        assertFalse(line.contains(repositoryRoot.absolutePath))
        assertTrue(line.contains("<workspace>"))
    }

    @Test
    fun missing_capture_is_stated_as_missing_never_invented() {
        val ledgerFile = ledgerFile("missing-capture")
        val entries =
            FixtureParity.compare(
                listOf(
                    capture(CREATED_SESSION_PATH)
                        .copy(responseJson = WRONG_OBJECT_ENVELOPE, status = null),
                ),
                contractsRoot,
            )
        assertEquals(FixtureParity.VERDICT_MISMATCH, entries.single().verdict)
        assertEquals(null, entries.single().observedStatus)

        DriftProtocol.reconcile(entries, LocalIssueLedger(ledgerFile, repositoryRoot))

        val record = LocalIssueLedger(ledgerFile, repositoryRoot).openIssues().single()
        assertTrue(record.evidence.contains("live capture missing"))
        assertFalse(record.evidence.contains("live capture status"))
    }

    @Test
    fun repeated_mismatch_updates_the_existing_issue_without_duplicates() {
        val ledgerFile = ledgerFile("update-not-duplicate")
        val sink = LocalIssueLedger(ledgerFile, repositoryRoot)
        val entries = mismatchedEntries()

        DriftProtocol.reconcile(entries, sink)
        DriftProtocol.reconcile(entries, sink)

        val records = LocalIssueLedger(ledgerFile, repositoryRoot).openIssues()
        assertEquals(1, records.size)
        assertEquals(1, ledgerFile.readLines().filter(String::isNotBlank).size)
        assertEquals(2, records.single().occurrences)
        assertEquals(DRIFT_STATUS_OPEN, records.single().status)
    }

    @Test
    fun a_resolved_identity_that_regresses_reopens_as_a_fresh_record() {
        val ledgerFile = ledgerFile("resolved-then-regressed")
        val sink = LocalIssueLedger(ledgerFile, repositoryRoot)

        DriftProtocol.reconcile(mismatchedEntries(), sink)
        DriftProtocol.reconcile(matchedEntries(), sink)
        DriftProtocol.reconcile(mismatchedEntries(), sink)

        val records = LocalIssueLedger(ledgerFile, repositoryRoot).openIssues()
        assertEquals(1, records.size)
        assertEquals(1, ledgerFile.readLines().filter(String::isNotBlank).size)
        assertEquals(DRIFT_STATUS_OPEN, records.single().status)
        assertEquals(1, records.single().occurrences)
    }

    @Test
    fun a_different_divergence_files_a_new_issue() {
        val ledgerFile = ledgerFile("different-identity")
        val sink = LocalIssueLedger(ledgerFile, repositoryRoot)

        DriftProtocol.reconcile(mismatchedEntries(), sink)
        DriftProtocol.reconcile(renameMismatchEntries(), sink)

        val records = LocalIssueLedger(ledgerFile, repositoryRoot).openIssues()
        assertEquals(2, records.size)
        assertEquals(
            setOf(
                "session open :: sessions/open-response.json",
                "session rename :: sessions/rename-response.json",
            ),
            records.map { it.id }.toSet(),
        )
    }

    @Test
    fun restoring_parity_resolves_the_open_issue_without_residue() {
        val ledgerFile = ledgerFile("resolve")
        val sink = LocalIssueLedger(ledgerFile, repositoryRoot)

        DriftProtocol.reconcile(mismatchedEntries(), sink)
        DriftProtocol.reconcile(matchedEntries(), sink)

        assertTrue(LocalIssueLedger(ledgerFile, repositoryRoot).openIssues().isEmpty())
        val lines = ledgerFile.readLines().filter(String::isNotBlank)
        assertEquals(1, lines.size)
        assertTrue(lines.single().contains("\"status\":\"resolved\""))
    }

    @Test
    fun green_parity_run_files_nothing() {
        val ledgerFile = ledgerFile("refusal-no-file")

        DriftProtocol.reconcile(matchedEntries(), LocalIssueLedger(ledgerFile, repositoryRoot))

        assertFalse(ledgerFile.exists())
    }

    @Test
    fun green_parity_run_leaves_the_ledger_byte_identical() {
        val resolvedFile = ledgerFile("refusal-resolved")
        val resolvedSink = LocalIssueLedger(resolvedFile, repositoryRoot)
        DriftProtocol.reconcile(mismatchedEntries(), resolvedSink)
        DriftProtocol.reconcile(matchedEntries(), resolvedSink)
        val resolvedBytes = resolvedFile.readBytes()

        DriftProtocol.reconcile(matchedEntries(), resolvedSink)

        assertTrue(resolvedBytes.contentEquals(resolvedFile.readBytes()))

        val uncoveredFile = ledgerFile("refusal-uncovered")
        val uncoveredSink = LocalIssueLedger(uncoveredFile, repositoryRoot)
        DriftProtocol.reconcile(renameMismatchEntries(), uncoveredSink)
        val uncoveredBytes = uncoveredFile.readBytes()

        DriftProtocol.reconcile(matchedEntries(), uncoveredSink)

        assertTrue(uncoveredBytes.contentEquals(uncoveredFile.readBytes()))
        assertEquals(1, LocalIssueLedger(uncoveredFile, repositoryRoot).openIssues().size)
    }

    @Test
    fun the_parity_step_reconciles_the_drift_ledger() {
        val artifactDir = scratchDirectory("parity-step-wiring")
        LiveParityCaptureRecorder(File(artifactDir, "$LIVE_SESSION_PARITY_CAPTURE_SINK.jsonl"), repositoryRoot)
            .record(capture(CREATED_SESSION_PATH).copy(responseJson = WRONG_OBJECT_ENVELOPE))

        val mismatched = runFixtureParityStep(repositoryRoot, artifactDir)

        assertEquals(FixtureParity.VERDICT_MISMATCH, mismatched.first { it.operation == "session open" }.verdict)
        val ledgerFile = File(artifactDir, DRIFT_LEDGER_FILE_NAME)
        assertEquals(1, LocalIssueLedger(ledgerFile, repositoryRoot).openIssues().size)

        LiveParityCaptureRecorder(File(artifactDir, "$LIVE_SESSION_PARITY_CAPTURE_SINK.jsonl"), repositoryRoot)
            .record(capture(CREATED_SESSION_PATH))

        runFixtureParityStep(repositoryRoot, artifactDir)

        assertTrue(LocalIssueLedger(ledgerFile, repositoryRoot).openIssues().isEmpty())
        assertEquals(1, ledgerFile.readLines().filter(String::isNotBlank).size)
    }

    private fun matchedEntries(): List<FixtureParityEntry> =
        FixtureParity.compare(
            listOf(capture(CREATED_SESSION_PATH)),
            contractsRoot,
        )

    private fun renameMismatchEntries(): List<FixtureParityEntry> =
        FixtureParity.compare(
            listOf(
                LiveParityCapture(
                    operation = "session rename",
                    method = "PATCH",
                    path = CREATED_SESSION_PATH,
                    queryNames = emptyList(),
                    requestBodyJson = SESSION_RENAME_REQUEST_BODY,
                    status = 200,
                    responseJson = WRONG_OBJECT_ENVELOPE,
                    sseLines = emptyList(),
                ),
            ),
            contractsRoot,
        )

    @Test
    fun filer_builds_the_tracker_command_for_open_issues() {
        val ledgerFile = ledgerFile("filer-command")
        DriftProtocol.reconcile(mismatchedEntries(), LocalIssueLedger(ledgerFile, repositoryRoot))
        val issue = LocalIssueLedger(ledgerFile, repositoryRoot).openIssues().single()

        val command = DriftIssueFiler("Gvetri/hermes-native-client", LocalIssueLedger(ledgerFile, repositoryRoot)).ghCreateCommand(issue)

        assertEquals(
            listOf(
                "gh",
                "issue",
                "create",
                "--repo",
                "Gvetri/hermes-native-client",
                "--label",
                DRIFT_ISSUE_LABEL,
                "--title",
                issue.title,
                "--body",
                listOf(
                    issue.evidence,
                    "Divergent operations: session open",
                    "Occurrences: 1",
                    "Evidence reference: $LIVE_PARITY_ARTIFACT_FILE_NAME",
                    issue.proposedRebaseline,
                    DRIFT_MANIFEST_VERSION_GUIDANCE,
                ).joinToString(separator = "\n\n"),
            ),
            command,
        )
    }

    @Test
    fun filer_files_exactly_one_issue_per_divergence_across_reruns() {
        val ledgerFile = ledgerFile("filer-idempotent")
        val filer = DriftIssueFiler("Gvetri/hermes-native-client", LocalIssueLedger(ledgerFile, repositoryRoot))
        val commands = mutableListOf<List<String>>()

        DriftProtocol.reconcile(mismatchedEntries(), LocalIssueLedger(ledgerFile, repositoryRoot))
        val firstUrls =
            filer.fileOpenIssues { command ->
                commands += command
                "https://tracker.invalid/issues/1"
            }
        DriftProtocol.reconcile(mismatchedEntries(), LocalIssueLedger(ledgerFile, repositoryRoot))
        val secondUrls =
            filer.fileOpenIssues { command ->
                commands += command
                "https://tracker.invalid/issues/1"
            }

        assertEquals(listOf("https://tracker.invalid/issues/1"), firstUrls)
        assertEquals(emptyList<String>(), secondUrls)
        assertEquals(1, commands.size)
        val record = LocalIssueLedger(ledgerFile, repositoryRoot).openIssues().single()
        assertEquals("<redacted-url>", record.filedIssueUrl)
        assertEquals(2, record.occurrences)
        val ledgerLine = ledgerFile.readLines().filter(String::isNotBlank).single()
        assertTrue(ledgerLine.contains("\"filedIssueUrl\":\"<redacted-url>\""))
    }

    @Test
    fun filer_files_nothing_when_no_divergence_is_open() {
        val ledgerFile = ledgerFile("filer-nothing")

        DriftProtocol.reconcile(matchedEntries(), LocalIssueLedger(ledgerFile, repositoryRoot))
        val urls =
            DriftIssueFiler("Gvetri/hermes-native-client", LocalIssueLedger(ledgerFile, repositoryRoot)).fileOpenIssues { commands ->
                commands
                "https://tracker.invalid/issues/1"
            }

        assertEquals(emptyList<String>(), urls)
    }

    private fun mismatchedEntries(): List<FixtureParityEntry> =
        FixtureParity.compare(
            listOf(capture(CREATED_SESSION_PATH).copy(responseJson = WRONG_OBJECT_ENVELOPE)),
            contractsRoot,
        )

    private fun capture(path: String) =
        LiveParityCapture(
            operation = "session open",
            method = "GET",
            path = path,
            queryNames = emptyList(),
            requestBodyJson = null,
            status = 200,
            responseJson = SESSION_OPEN_ENVELOPE,
            sseLines = emptyList(),
        )

    private fun ledgerFile(name: String): File {
        val directory = File(System.getProperty("java.io.tmpdir"), "drift-protocol-test/$name")
        directory.mkdirs()
        val file = File(directory, DRIFT_LEDGER_FILE_NAME)
        file.delete()
        return file
    }

    private fun scratchDirectory(name: String): File {
        val directory = File(System.getProperty("java.io.tmpdir"), "drift-protocol-test/$name-artifacts")
        directory.deleteRecursively()
        directory.mkdirs()
        return directory
    }

    private companion object {
        const val CREATED_SESSION_PATH = "/api/sessions/synthetic-created-session-1"
        const val SESSION_RENAME_REQUEST_BODY = """{"title":"Live lane renamed session"}"""

        val WRONG_OBJECT_ENVELOPE = SESSION_OPEN_ENVELOPE.replace("\"hermes.session\"", "\"hermes.session.wrong\"")
    }
}
