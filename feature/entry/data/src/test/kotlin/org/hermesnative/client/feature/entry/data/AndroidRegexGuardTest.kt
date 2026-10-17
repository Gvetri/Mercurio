package org.hermesnative.client.feature.entry.data

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Focused tests for the Android regex guard's construct contract, plus the repository-wide
 * scan. The rejected constructs are the ones issue #198 traces to the device lane: the
 * pre-#183 `LocalDiagnosticsRecords` built `\z` anchors and an unescaped literal `}` that
 * every JVM engine accepted and the API 24 device engine rejected.
 */
class AndroidRegexGuardTest {
    @Test
    fun historic_anchors_and_unescaped_braces_are_rejected() {
        val historicSource =
            """
            private val eventRecord =
                Regex(
                    "\\A\\{\"schema_version\":${'$'}SCHEMA_VERSION,\"record_type\":\"${'$'}EVENT_RECORD_TYPE\"," +
                        "\"event_type\":\"(?:${'$'}approvedEventTypes)\",\"occurred_at\":\"${'$'}UTC_INSTANT_PATTERN\"" +
                        "(?:,\"status\":\"(?:${'$'}approvedStatuses)\")?}\\z",
                )
            """.trimIndent()

        val result = AndroidRegexGuard.scanSource(historicSource)

        assertTrue("expected violations, got: ${result.violations}", result.violations.isNotEmpty())
        assertTrue(
            "the JVM-only \\z anchor must be rejected",
            result.violations.any { it.message.contains("\\z") },
        )
        assertTrue(
            "the unescaped literal } must be rejected",
            result.violations.any { it.message.contains("unescaped literal '}'") },
        )
        assertTrue(
            "violations must point at the offending fragment line",
            result.violations.all { it.line == 5 },
        )
    }

    @Test
    fun quantifier_intervals_escaped_braces_and_class_braces_stay_approved() {
        val safeSource =
            """
            private const val SAFE_FIELD_VALUE_PATTERN = "[a-z0-9_]{1,64}"
            private const val UTC_INSTANT_PATTERN = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(?:\\.\\d{1,9})?Z"

            private val safeFieldValue: Regex by lazy { Regex(SAFE_FIELD_VALUE_PATTERN) }

            private val eventRecord: Regex
                by lazy {
                    Regex(
                        "\\{\"schema_version\":${'$'}SCHEMA_VERSION,\"record_type\":\"event\"," +
                            "\"occurred_at\":\"${'$'}UTC_INSTANT_PATTERN\"" +
                            "(?:,\"status\":\"ok\")?\\}",
                    )
                }

            private val percentEscape = Regex("%([0-9a-fA-F]{2})")
            private val keyPattern = Regex("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)*")
            private val bracesInClass = Regex("[0-9{}]{2}")
            """.trimIndent()

        val result = AndroidRegexGuard.scanSource(safeSource)

        assertTrue("expected no violations, got: ${result.violations}", result.violations.isEmpty())
        assertTrue(
            "all five constructions must be statically checkable",
            result.checkedPatternSites == 5,
        )
    }

    @Test
    fun raw_string_patterns_stay_approved() {
        val rawSource =
            "private val heading = Regex(\"\"\"^#{1,6}\\s+.+\$\"\"\")\n" +
                "private val endpoint = Regex(\"\"\"(?i)^https://[a-z.]{4,}(:[0-9]{1,5})?\"\"\")\n"

        val result = AndroidRegexGuard.scanSource(rawSource)

        assertTrue("expected no violations, got: ${result.violations}", result.violations.isEmpty())
        assertTrue(result.checkedPatternSites == 2)
    }

    @Test
    fun pattern_text_in_documentation_is_not_scanned() {
        val documentedSource =
            """
            /**
             * Use Regex("\\z") or an unescaped } only outside this guard's reach.
             */
            private val unrelated = 1
            """.trimIndent()

        val result = AndroidRegexGuard.scanSource(documentedSource)

        assertTrue("expected no violations, got: ${result.violations}", result.violations.isEmpty())
        assertTrue(result.checkedPatternSites == 0)
    }

    @Test
    fun annotated_type_declarations_do_not_hide_later_violations() {
        val annotatedSource =
            """
            private val discover: (endpoint: String) -> GatewayCapabilities = ::discoverCapabilities
            private val endpointPattern = Regex("((?:/[a-z0-9._~!&'()*+,;=:@%/-]*)?)\\z")

            private val fields: Map<String, String> = emptyMap()
            private val brokenBrace = Regex("[a-z-]*\\z")
            """.trimIndent()

        val result = AndroidRegexGuard.scanSource(annotatedSource)

        assertTrue(
            "both annotated-prefix sites must be checked, got: ${result.checkedPatternSites}",
            result.checkedPatternSites == 2,
        )
        assertTrue(
            "the violation after a function-type annotation must be reported, got: ${result.violations}",
            result.violations.count { it.message.contains("\\z") } == 2,
        )
    }

    @Test
    fun named_argument_pattern_is_checked() {
        val namedSource = "private val endpoint = Regex(pattern = \"\\\\z\")\n"

        val result = AndroidRegexGuard.scanSource(namedSource)

        assertTrue(
            "the named-argument construction must be checked, got: ${result.violations}",
            result.violations.any { it.message.contains("\\z") },
        )
        assertTrue(
            "the named-argument construction must count as statically checkable",
            result.checkedPatternSites == 1,
        )
    }

    @Test
    fun space_only_pattern_literal_stays_checkable() {
        val spaceSource = "private val space = Regex(\" \")\n"

        val result = AndroidRegexGuard.scanSource(spaceSource)

        assertTrue("expected no violations, got: ${result.violations}", result.violations.isEmpty())
        assertTrue(
            "a space-only literal must count as statically checkable text",
            result.checkedPatternSites == 1,
        )
    }

    @Test
    fun repository_main_sources_pass_the_guard() {
        val repositoryRoot = System.getProperty("fixture.repositoryRoot")
        assertTrue(
            "fixture.repositoryRoot must be set for the repository-wide scan",
            !repositoryRoot.isNullOrEmpty(),
        )

        val result = AndroidRegexGuard.scanMainSources(File(repositoryRoot))

        assertTrue(
            "current main sources must pass the Android regex guard, got: ${result.violations}",
            result.violations.isEmpty(),
        )
        assertTrue(result.checkedPatternSites > 0)
        val uncheckedFiles = result.perFileSites.filterValues { it.checkedSites == 0 }
        assertTrue(
            "every main-source file containing a Regex( construction must have at least one " +
                "checked site, got: $uncheckedFiles",
            uncheckedFiles.isEmpty(),
        )
    }

    @Test
    fun scanned_roots_cover_the_declared_module_directories() {
        val repositoryRoot = File(System.getProperty("fixture.repositoryRoot"))
        assertTrue(
            "fixture.repositoryRoot must be set for the repository-wide scan",
            System.getProperty("fixture.repositoryRoot").isNullOrEmpty().not(),
        )

        val modulePaths =
            repositoryRoot.resolve("settings.gradle.kts").readLines()
                .mapNotNull { MODULE_INCLUDE.find(it)?.groupValues[1] }
                .map { it.removePrefix(":").replace(':', '/') }
        assertTrue("settings.gradle.kts must declare modules, got none", modulePaths.isNotEmpty())

        val uncovered =
            modulePaths.filter { path ->
                AndroidRegexGuard.scannedRoots.none { root -> path == root || path.startsWith("$root/") }
            }
        assertTrue(
            "scanned roots must cover the declared module directories, uncovered: $uncovered",
            uncovered.isEmpty(),
        )
    }

    @Test
    fun guard_fails_closed_when_a_scanned_root_is_missing() {
        withTemporaryRepository { root ->
            writeMainSource(root, "feature/entry/data", "Sample.kt", VALID_SAFE_SOURCE)

            val result = AndroidRegexGuard.scanMainSources(root)

            assertTrue(
                "the missing app root must be reported, got: ${result.violations}",
                result.violations.any { it.message.contains("app") && it.message.contains("no Kotlin main sources") },
            )
        }
    }

    @Test
    fun guard_fails_closed_when_no_main_sources_resolve() {
        withTemporaryRepository { root ->
            val result = AndroidRegexGuard.scanMainSources(root)

            assertTrue(
                "the empty scope must be reported, got: ${result.violations}",
                result.violations.any { it.message.contains("no Regex( construction found") },
            )
        }
    }

    @Test
    fun guard_fails_closed_when_no_regex_construction_is_found() {
        withTemporaryRepository { root ->
            writeMainSource(root, "feature/entry/data", "Sample.kt", "private val unrelated = 1")

            val result = AndroidRegexGuard.scanMainSources(root)

            assertTrue(
                "the Regex-free scope must be reported, got: ${result.violations}",
                result.violations.any { it.message.contains("no Regex( construction found") },
            )
        }
    }

    @Test
    fun guard_fails_closed_when_no_static_pattern_is_checkable() {
        withTemporaryRepository { root ->
            writeMainSource(
                root,
                "feature/entry/data",
                "Sample.kt",
                "private fun dynamic(source: String): Regex = Regex(source)",
            )

            val result = AndroidRegexGuard.scanMainSources(root)

            assertTrue(
                "the fully dynamic scope must be reported, got: ${result.violations}",
                result.violations.any { it.message.contains("no static Regex( pattern text was checkable") },
            )
        }
    }

    private fun withTemporaryRepository(block: (File) -> Unit) {
        val root = Files.createTempDirectory("regex-guard-repository").toFile()
        try {
            block(root)
        } finally {
            root.deleteRecursively()
        }
    }

    private fun writeMainSource(
        root: File,
        modulePath: String,
        fileName: String,
        source: String,
    ) {
        val file = root.resolve("$modulePath/src/main/kotlin/$fileName")
        file.parentFile.mkdirs()
        file.writeText(source)
    }

    private companion object {
        private val MODULE_INCLUDE = Regex("""include\("([^"]+)"\)""")

        private val VALID_SAFE_SOURCE =
            """
            private val percentEscape = Regex("%([0-9a-fA-F]{2})")
            """.trimIndent()
    }
}
