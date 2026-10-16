package org.hermesnative.client.feature.entry.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.util.TreeMap

class DriftProtocolDrillTest {
    private val repositoryRoot =
        File(
            requireNotNull(System.getProperty("fixture.repositoryRoot")) {
                "fixture.repositoryRoot must identify the repository root"
            },
        )
    private val contractsRoot = repositoryRoot.resolve("fixtures/hermes/contracts")

    @Test
    fun staged_divergence_files_the_issue_and_restoring_resolves_it_without_residue() {
        val guardedPaths = guardedSourcePaths()
        val before = digests(guardedPaths)
        val drillDirectory = drillDirectory()
        val drillContractsRoot = File(drillDirectory, "contracts")
        val originalFixtures = drillFixturePaths.associateWith { path -> contractsRoot.resolve(path).readText() }
        originalFixtures.forEach { (path, content) ->
            val drillFixture = drillContractsRoot.resolve(path)
            drillFixture.parentFile.mkdirs()
            drillFixture.writeText(content)
        }
        val ledgerFile = File(drillDirectory, DRIFT_LEDGER_FILE_NAME)
        val artifactFile = File(drillDirectory, LIVE_PARITY_ARTIFACT_FILE_NAME)
        val drillLogFile = File(drillDirectory, DRILL_LOG_FILE_NAME)
        val ledger = LocalIssueLedger(ledgerFile, repositoryRoot)
        val log = StringBuilder()

        val stagedFixture =
            originalFixtures
                .getValue(STAGED_FIXTURE_PATH)
                .replace(DRIFT_ENVELOPE_OBJECT, "\"$STAGED_OBJECT_NAME\"")
        drillContractsRoot.resolve(STAGED_FIXTURE_PATH).writeText(stagedFixture)
        val stagedEntries = driftPass(drillContractsRoot, artifactFile, ledger)
        val stagedRecord = ledger.openIssues().single()
        log.appendLine("pass 1 staged divergence in scratch fixture $STAGED_FIXTURE_PATH")
        log.appendLine(
            "  parity verdict: ${stagedEntries.single().verdict} " +
                "(${stagedEntries.single().observedDifference})",
        )
        log.appendLine(
            "  filed issue: id=${stagedRecord.id} status=${stagedRecord.status} " +
                "occurrences=${stagedRecord.occurrences}",
        )
        log.appendLine("  ledger: ${ledgerFile.readText().trim()}")

        drillContractsRoot.resolve(STAGED_FIXTURE_PATH).writeText(originalFixtures.getValue(STAGED_FIXTURE_PATH))
        val restoredEntries = driftPass(drillContractsRoot, artifactFile, ledger)
        log.appendLine("pass 2 scratch fixture restored")
        log.appendLine("  parity verdict: ${restoredEntries.single().verdict}")
        log.appendLine("  open issues: ${ledger.openIssues().size}")
        log.appendLine("  ledger: ${ledgerFile.readText().trim()}")

        val resolvedBytes = ledgerFile.readBytes()
        driftPass(drillContractsRoot, artifactFile, ledger)
        val residue = ledgerFile.readLines().filter(String::isNotBlank)
        log.appendLine("pass 3 green rerun")
        log.appendLine("  ledger byte-identical: ${resolvedBytes.contentEquals(ledgerFile.readBytes())}")
        log.appendLine("  ledger rows: ${residue.size}")

        drillLogFile.writeText(log.toString())

        assertEquals(FixtureParity.VERDICT_MISMATCH, stagedEntries.single().verdict)
        assertEquals(DRIFT_STATUS_OPEN, stagedRecord.status)
        assertEquals(1, ledgerFile.readLines().filter(String::isNotBlank).size)
        assertTrue(ledgerFile.readLines().single().contains("\"status\":\"resolved\""))
        assertTrue(ledger.openIssues().isEmpty())
        assertTrue(resolvedBytes.contentEquals(ledgerFile.readBytes()))
        assertEquals(before, digests(guardedPaths))
    }

    private fun driftPass(
        drillContractsRoot: File,
        artifactFile: File,
        ledger: LocalIssueLedger,
    ): List<FixtureParityEntry> {
        val entries = FixtureParity.compare(drillCaptures(), drillContractsRoot)
        FixtureParity.writeArtifact(entries, artifactFile)
        DriftProtocol.reconcile(entries, ledger)
        return entries
    }

    private fun drillCaptures(): List<LiveParityCapture> =
        listOf(
            LiveParityCapture(
                operation = "session open",
                method = "GET",
                path = "/api/sessions/synthetic-created-session-1",
                queryNames = emptyList(),
                requestBodyJson = null,
                status = 200,
                responseJson = SESSION_OPEN_ENVELOPE,
                sseLines = emptyList(),
            ),
        )

    private fun guardedSourcePaths(): List<File> =
        buildList {
            addAll(contractsRoot.walkTopDown().filter { it.isFile })
            add(repositoryRoot.resolve(PARSER_SOURCE_PATH))
            add(repositoryRoot.resolve(MANIFEST_SOURCE_PATH))
        }.sortedBy { it.path }

    private fun digests(paths: List<File>) =
        TreeMap(
            paths.associate { path ->
                path.relativeTo(repositoryRoot).path to
                    MessageDigest.getInstance("SHA-256").digest(path.readBytes()).joinToString("") { "%02x".format(it) }
            },
        )

    private fun drillDirectory(): File {
        val directory = File(System.getProperty("java.io.tmpdir"), "drift-protocol-drill")
        directory.deleteRecursively()
        directory.mkdirs()
        return directory
    }

    private companion object {
        const val DRILL_LOG_FILE_NAME = "drift-protocol-drill.log"
        const val PARSER_SOURCE_PATH =
            "feature/entry/data/src/main/kotlin/org/hermesnative/client/feature/entry/data/GatewayJsonParser.kt"
        const val MANIFEST_SOURCE_PATH =
            "feature/entry/domain/src/main/kotlin/org/hermesnative/client/feature/entry/domain" +
                "/GatewayCapabilityManifest.kt"
        const val STAGED_FIXTURE_PATH = "sessions/open-response.json"
        const val DRIFT_ENVELOPE_OBJECT = "\"hermes.session\""
        const val STAGED_OBJECT_NAME = "hermes.session.staged"

        val drillFixturePaths = listOf("sessions/open-request.json", "sessions/open-response.json")

        val SESSION_OPEN_ENVELOPE =
            """
            {"object":"hermes.session","session":{
            "id":"synthetic-created-session-1","source":"api_server","title":"Live lane created session","preview":null,"pinned":false,"message_count":0}}
            """.trimIndent()
    }
}
