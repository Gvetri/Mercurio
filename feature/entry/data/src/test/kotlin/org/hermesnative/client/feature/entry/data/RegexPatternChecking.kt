package org.hermesnative.client.feature.entry.data

internal data class ResolvedFragment(
    val text: String,
    val line: Int,
)

internal data class LineViolation(
    val line: Int,
    val message: String,
)

private data class IndexedPattern(
    val text: String,
    val lines: List<Int>,
)

/**
 * Checks one statically resolved pattern for the constructs the Android regex engine rejects:
 * the JVM-only `\z`/`\Z` anchors and unescaped literal braces outside quantifier intervals and
 * character classes. Every violation carries the source line of the offending fragment, so a
 * multi-line construction reports where it actually went wrong.
 */
internal object RegexPatternChecking {
    fun violations(fragments: List<ResolvedFragment>): List<LineViolation> {
        val joined = joinedPattern(fragments)
        return anchorViolations(joined) + braceViolations(joined)
    }

    private fun joinedPattern(fragments: List<ResolvedFragment>): IndexedPattern {
        val text = StringBuilder()
        val lines = mutableListOf<Int>()
        fragments.forEach { fragment ->
            fragment.text.forEach { character ->
                text.append(character)
                lines.add(fragment.line)
            }
        }
        return IndexedPattern(text.toString(), lines)
    }

    private fun anchorViolations(pattern: IndexedPattern): List<LineViolation> {
        val violations = mutableListOf<LineViolation>()
        var index = 0
        while (index < pattern.text.length) {
            val current = pattern.text[index]
            if (current == '\\' && index + 1 < pattern.text.length) {
                val next = pattern.text[index + 1]
                if (next == 'z' || next == 'Z') {
                    violations +=
                        LineViolation(
                            pattern.lines[index],
                            "JVM-only anchor \\$next is rejected by the Android regex engine; " +
                                "full-match semantics come from matches or matchEntire",
                        )
                }
                index += 2
            } else {
                index += 1
            }
        }
        return violations
    }

    private fun braceViolations(pattern: IndexedPattern): List<LineViolation> {
        val violations = mutableListOf<LineViolation>()
        var index = 0
        var insideCharacterClass = false
        while (index < pattern.text.length) {
            val current = pattern.text[index]
            when {
                current == '\\' -> index = escapedStep(pattern, index)
                current == '[' -> {
                    insideCharacterClass = true
                    index += 1
                }
                current == ']' -> {
                    insideCharacterClass = false
                    index += 1
                }
                current == '{' && !insideCharacterClass -> index = openBraceStep(pattern, index, violations)
                current == '}' && !insideCharacterClass -> {
                    violations += LineViolation(pattern.lines[index], CLOSE_BRACE_MESSAGE)
                    index += 1
                }
                else -> index += 1
            }
        }
        return violations
    }

    private fun escapedStep(
        pattern: IndexedPattern,
        index: Int,
    ): Int {
        if (index + 1 >= pattern.text.length) return index + 1
        return index + 2
    }

    private fun openBraceStep(
        pattern: IndexedPattern,
        index: Int,
        violations: MutableList<LineViolation>,
    ): Int {
        val quantifierEnd = quantifierEnd(pattern.text, index)
        if (quantifierEnd == null) {
            violations += LineViolation(pattern.lines[index], OPEN_BRACE_MESSAGE)
        }
        return quantifierEnd ?: index + 1
    }

    private fun quantifierEnd(
        pattern: String,
        open: Int,
    ): Int? {
        val digitsEnd = readDigits(pattern, open + 1)
        val hasLeadingDigits = digitsEnd > open + 1
        var index = digitsEnd
        if (hasLeadingDigits && index < pattern.length && pattern[index] == ',') {
            index = readDigits(pattern, index + 1)
        }
        val closed = hasLeadingDigits && index < pattern.length && pattern[index] == '}'
        return if (closed) index + 1 else null
    }

    private fun readDigits(
        pattern: String,
        start: Int,
    ): Int {
        var index = start
        while (index < pattern.length && pattern[index].isDigit()) {
            index += 1
        }
        return index
    }

    private const val OPEN_BRACE_MESSAGE =
        "unescaped literal '{' outside a quantifier interval is rejected by the Android regex " +
            "engine; escape it as \\{"
    private const val CLOSE_BRACE_MESSAGE =
        "unescaped literal '}' is rejected by the Android regex engine; escape it as \\}"
}
