package org.hermesnative.client.feature.entry.data

/**
 * One decoded Kotlin string literal: the pattern text with every template hole replaced by
 * [REGEX_PATTERN_DYNAMIC_HOLE], and the source line the literal starts on.
 */
internal data class PatternLiteral(
    val text: String,
    val line: Int,
)

/**
 * Stands in for template-injected pattern text that no static scan can see through. The
 * sentinel is a control character real pattern text never contains, so a literal like a
 * space stays fully checkable instead of being mistaken for a hole.
 */
internal const val REGEX_PATTERN_DYNAMIC_HOLE = '\u0000'

/**
 * Lexes the string-literal and trivia surface of Kotlin source: comments, character literals,
 * and escaped or raw string literals with their `$` templates. Everything it returns is
 * already decoded, so the guard checks the pattern text the regex engine will actually see.
 */
internal class RegexSourceLexing(private val cursor: RegexSourceCursor) {
    fun skipTrivia() {
        while (!cursor.atEnd) {
            when {
                cursor.current().isWhitespace() -> cursor.advance()
                cursor.startsWith("//") -> cursor.skipLineComment()
                cursor.startsWith("/*") -> cursor.skipBlockComment()
                else -> return
            }
        }
    }

    fun skipString() {
        readStringLiteral()
    }

    fun readStringLiteral(): PatternLiteral? {
        val startLine = cursor.line
        return when {
            cursor.startsWith(RAW_DELIMITER) -> readRawString(startLine)
            !cursor.atEnd && cursor.current() == '"' -> readEscapedString(startLine)
            else -> null
        }
    }

    fun skipBalanced(
        open: Char,
        close: Char,
    ): Boolean {
        if (cursor.atEnd || cursor.current() != open) return false
        cursor.advance()
        var depth = 1
        while (!cursor.atEnd && depth > 0) {
            when {
                cursor.startsWith("//") -> cursor.skipLineComment()
                cursor.startsWith("/*") -> cursor.skipBlockComment()
                cursor.current() == '"' -> skipString()
                cursor.current() == '\'' -> if (!cursor.skipCharLiteral()) cursor.advance()
                cursor.current() == open -> {
                    cursor.advance()
                    depth += 1
                }
                cursor.current() == close -> {
                    cursor.advance()
                    depth -= 1
                }
                else -> cursor.advance()
            }
        }
        return depth == 0
    }

    private fun readEscapedString(startLine: Int): PatternLiteral {
        cursor.advance()
        val decoded = StringBuilder()
        while (!cursor.atEnd) {
            when (val current = cursor.current()) {
                '"' -> {
                    cursor.advance()
                    return PatternLiteral(decoded.toString(), startLine)
                }
                '\\' -> {
                    cursor.advance()
                    decodeEscape(decoded)
                }
                '$' -> decodeDollarTemplate(decoded)
                else -> {
                    decoded.append(current)
                    cursor.advance()
                }
            }
        }
        return PatternLiteral(decoded.toString(), startLine)
    }

    private fun readRawString(startLine: Int): PatternLiteral {
        repeat(RAW_DELIMITER_LENGTH) { cursor.advance() }
        val decoded = StringBuilder()
        while (!cursor.atEnd) {
            when {
                cursor.startsWith(RAW_DELIMITER) -> {
                    repeat(RAW_DELIMITER_LENGTH) { cursor.advance() }
                    return PatternLiteral(decoded.toString(), startLine)
                }
                cursor.current() == '\\' && cursor.peek(1) == '$' -> {
                    cursor.advance()
                    decoded.append(cursor.advance())
                }
                cursor.current() == '$' -> decodeDollarTemplate(decoded)
                else -> decoded.append(cursor.advance())
            }
        }
        return PatternLiteral(decoded.toString(), startLine)
    }

    private fun decodeEscape(decoded: StringBuilder) {
        if (cursor.atEnd) return
        when (val escaped = cursor.advance()) {
            '\\' -> decoded.append('\\')
            '"' -> decoded.append('"')
            '$' -> decoded.append('$')
            'n' -> decoded.append('\n')
            't' -> decoded.append('\t')
            'r' -> decoded.append('\r')
            'u' -> decodeUnicodeEscape(decoded)
            else -> {
                decoded.append('\\')
                decoded.append(escaped)
            }
        }
    }

    private fun decodeUnicodeEscape(decoded: StringBuilder) {
        val digits = StringBuilder()
        while (digits.length < UNICODE_ESCAPE_LENGTH && !cursor.atEnd && cursor.current().isHexDigit()) {
            digits.append(cursor.advance())
        }
        val codePoint = if (digits.length == UNICODE_ESCAPE_LENGTH) digits.toString().toIntOrNull(HEX_RADIX) else null
        if (codePoint == null) {
            decoded.append('\\').append('u').append(digits)
        } else {
            decoded.append(codePoint.toChar())
        }
    }

    private fun decodeDollarTemplate(decoded: StringBuilder) {
        cursor.advance()
        if (cursor.atEnd) {
            decoded.append('$')
            return
        }
        when {
            cursor.current() == '{' -> {
                decoded.append(REGEX_PATTERN_DYNAMIC_HOLE)
                skipBalanced('{', '}')
            }
            cursor.current().isIdentifierStart() -> {
                decoded.append(REGEX_PATTERN_DYNAMIC_HOLE)
                while (!cursor.atEnd && cursor.current().isIdentifierPart()) {
                    cursor.advance()
                }
            }
            else -> decoded.append('$')
        }
    }

    private companion object {
        private const val RAW_DELIMITER = "\"\"\""
        private const val RAW_DELIMITER_LENGTH = 3
        private const val UNICODE_ESCAPE_LENGTH = 4
        private const val HEX_RADIX = 16
    }
}

private fun Char.isHexDigit(): Boolean = isDigit() || this in 'a'..'f' || this in 'A'..'F'
