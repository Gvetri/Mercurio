package org.hermesnative.client.feature.entry.data

import java.io.File

/**
 * Guards main-source `Regex(` pattern constructions against constructs the JVM regex engine
 * accepts and the Android engine rejects with `PatternSyntaxException`. The historic trigger
 * (issue #198) built validation patterns with `\z` anchors and an unescaped literal `}`; every
 * JVM unit test stayed green while the device threw and diagnostics recording died.
 *
 * The Android-safe construct contract:
 * - Anchors: `\z` and `\Z` are rejected. Both are end-of-input anchors the device engine
 *   throws on; a pattern that needs full-match semantics uses `matches` or `matchEntire`
 *   instead of a trailing anchor.
 * - Braces: outside a character class, `{` must open a valid quantifier interval
 *   `{\d+(,\d*)?}` and `}` must be escaped as `\}`. The device engine rejects every other
 *   unescaped brace that the JVM engine tolerates as a literal.
 * - Inside a character class both braces are literal and pass.
 *
 * Pattern text is resolved statically: string literals passed to a `Regex(` construction,
 * `+` chains of them, and references to `val`/`const val` declarations whose initializer is
 * the same shape. Template holes (`$x`, `${expr}`), unresolved references, and expression
 * tails decode as opaque text: the static text around them is checked, the injected part is
 * not.
 *
 * The scan is fail-closed: a scanned root that resolves to no Kotlin main source, a scope
 * with no `Regex(` construction, and a scope with no statically checkable pattern text each
 * report a violation instead of passing vacuously.
 */
object AndroidRegexGuard {
    /** The module roots whose `src/main` Kotlin trees the guard scans. */
    val scannedRoots = listOf("app", "feature", "fixtures")

    data class Violation(
        val file: String,
        val line: Int,
        val message: String,
    ) {
        override fun toString(): String = "$file:$line: $message"
    }

    data class ScanResult(
        val violations: List<Violation>,
        val scannedFiles: Int,
        val checkedPatternSites: Int,
    )

    fun scanMainSources(repositoryRoot: File): ScanResult {
        val violations = mutableListOf<Violation>()
        val sourceFiles = mutableListOf<File>()
        scannedRoots.forEach { root ->
            val files = collectMainSources(File(repositoryRoot, root))
            if (files.isEmpty()) {
                violations += Violation(root, 0, "scanned root resolves to no Kotlin main sources: $root")
            }
            sourceFiles += files
        }

        var foundSites = 0
        var checkedSites = 0
        sourceFiles.forEach { file ->
            val source =
                runCatching { file.readText() }.getOrElse { error ->
                    violations += Violation(file.path, 0, "unreadable source file: ${error.message}")
                    return@forEach
                }
            val scanned = scanText(source)
            foundSites += scanned.regexSites
            checkedSites += scanned.checkedSites
            scanned.violations.forEach { violations += Violation(file.path, it.line, it.message) }
        }
        if (foundSites == 0) {
            violations += Violation("<scope>", 0, "no Regex( construction found in the scanned main sources")
        }
        if (checkedSites == 0) {
            violations +=
                Violation("<scope>", 0, "no static Regex( pattern text was checkable in the scanned main sources")
        }
        return ScanResult(violations, sourceFiles.size, checkedSites)
    }

    fun scanSource(source: String): ScanResult {
        val scanned = scanText(source)
        return ScanResult(
            scanned.violations.map { Violation("<source>", it.line, it.message) },
            scannedFiles = 1,
            checkedPatternSites = scanned.checkedSites,
        )
    }

    private fun collectMainSources(root: File): List<File> =
        root.walkTopDown()
            .onEnter { it.name != "build" && it.name != ".git" }
            .filter { it.isFile && it.extension == "kt" && it.path.contains("/src/main/") }
            .toList()

    private fun scanText(source: String): TextScan {
        val facts = scanRegexPatterns(source)
        val violations = mutableListOf<LineViolation>()
        var checkedSites = 0
        facts.callSites.forEach { site ->
            val fragments = mutableListOf<ResolvedFragment>()
            if (resolveChain(site, facts.declarations, fragments)) {
                checkedSites += 1
                violations += RegexPatternChecking.violations(fragments)
            }
        }
        return TextScan(violations, facts.callSites.size, checkedSites)
    }

    private fun resolveChain(
        site: RegexCallSite,
        declarations: Map<String, List<PatternChainToken>>,
        target: MutableList<ResolvedFragment>,
    ): Boolean {
        if (site.hasDynamicTail) target += ResolvedFragment(DYNAMIC_HOLE_TEXT, site.line)
        val pending = ArrayDeque<PatternChainToken>()
        site.chain.forEach(pending::addLast)
        val visited = mutableSetOf<String>()
        while (pending.isNotEmpty()) {
            when (val token = pending.removeFirst()) {
                is PatternChainToken.Literal -> target += ResolvedFragment(token.text, token.line)
                is PatternChainToken.Reference -> {
                    val referenced = if (visited.add(token.name)) declarations[token.name] else null
                    if (referenced == null) {
                        target += ResolvedFragment(DYNAMIC_HOLE_TEXT, site.line)
                    } else {
                        referenced.asReversed().forEach(pending::addFirst)
                    }
                }
            }
        }
        return target.any { fragment -> fragment.text.any { it != REGEX_PATTERN_DYNAMIC_HOLE } }
    }

    private data class TextScan(
        val violations: List<LineViolation>,
        val regexSites: Int,
        val checkedSites: Int,
    )

    private const val DYNAMIC_HOLE_TEXT = "$REGEX_PATTERN_DYNAMIC_HOLE"
}
