package org.hermesnative.client.feature.entry.data

/**
 * Skips a `val x: SomeType` type annotation without consuming the initializer that follows
 * it. Delimiters nest (generic angle brackets, function-type parentheses) and every boundary
 * is only a boundary at depth zero, so an annotation like `Map<String, Int>` is skipped whole
 * while `Regex?` stops at the initializer's `=`.
 */
internal class RegexTypeSkip(
    private val cursor: RegexSourceCursor,
    private val lexing: RegexSourceLexing,
) {
    fun skip() {
        cursor.advance()
        var angleDepth = 0
        var parenDepth = 0
        var inside = true
        while (inside && !cursor.atEnd) {
            lexing.skipTrivia()
            inside =
                when {
                    cursor.atEnd -> false
                    cursor.startsWith("->") -> skipArrow()
                    cursor.current() == '<' -> {
                        cursor.advance()
                        angleDepth += 1
                        true
                    }
                    cursor.current() == '>' -> advanceAngleClose(angleDepth)
                    cursor.current() == '(' -> {
                        cursor.advance()
                        parenDepth += 1
                        true
                    }
                    cursor.current() == ')' -> advanceParenClose(parenDepth)
                    cursor.current().isIdentifierStart() -> cursor.readIdentifier() != DELEGATE_KEYWORD
                    else -> consumeTypeTail(angleDepth, parenDepth)
                }
        }
    }

    private fun skipArrow(): Boolean {
        cursor.advance()
        cursor.advance()
        return true
    }

    private fun advanceAngleClose(angleDepth: Int): Boolean {
        val inside = angleDepth > 0
        if (inside) {
            cursor.advance()
        }
        return inside
    }

    private fun advanceParenClose(parenDepth: Int): Boolean {
        val inside = parenDepth > 0
        if (inside) {
            cursor.advance()
        }
        return inside
    }

    private fun consumeTypeTail(
        angleDepth: Int,
        parenDepth: Int,
    ): Boolean {
        val current = cursor.current()
        val boundary =
            angleDepth == 0 && parenDepth == 0 &&
                (current in BOUNDARY_CHARS || current == '>')
        if (!boundary) cursor.advance()
        return !boundary
    }

    private companion object {
        private const val DELEGATE_KEYWORD = "by"
        private const val BOUNDARY_CHARS = "={,});]"
    }
}
