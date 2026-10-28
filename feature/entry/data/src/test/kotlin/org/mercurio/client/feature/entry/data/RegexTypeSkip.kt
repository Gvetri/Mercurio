package org.mercurio.client.feature.entry.data

/**
 * Skips a `val x: SomeType` type annotation without consuming the initializer that follows
 * it. Delimiters nest (generic angle brackets, function-type parentheses) and every boundary
 * is only a boundary at depth zero, so an annotation like `Map<String, Int>` is skipped whole
 * while `Regex?` stops at the initializer's `=`. A close delimiter decrements its depth, so
 * the skip always ends at the annotation's end instead of running to end of file.
 */
internal class RegexTypeSkip(
    private val cursor: RegexSourceCursor,
    private val lexing: RegexSourceLexing,
) {
    private var angleDepth = 0
    private var parenDepth = 0

    fun skip() {
        angleDepth = 0
        parenDepth = 0
        cursor.advance()
        var inside = true
        while (inside && !cursor.atEnd) {
            lexing.skipTrivia()
            inside =
                when {
                    cursor.atEnd -> false
                    cursor.startsWith("->") -> skipArrow()
                    cursor.current() == '<' -> openAngle()
                    cursor.current() == '>' -> closeAngle()
                    cursor.current() == '(' -> openParen()
                    cursor.current() == ')' -> closeParen()
                    cursor.current().isIdentifierStart() -> cursor.readIdentifier() != DELEGATE_KEYWORD
                    else -> consumeTypeTail()
                }
        }
    }

    private fun skipArrow(): Boolean {
        cursor.advance()
        cursor.advance()
        return true
    }

    private fun openAngle(): Boolean {
        cursor.advance()
        angleDepth += 1
        return true
    }

    private fun closeAngle(): Boolean {
        val wasInside = angleDepth > 0
        if (wasInside) {
            cursor.advance()
            angleDepth -= 1
        }
        return wasInside
    }

    private fun openParen(): Boolean {
        cursor.advance()
        parenDepth += 1
        return true
    }

    private fun closeParen(): Boolean {
        val wasInside = parenDepth > 0
        if (wasInside) {
            cursor.advance()
            parenDepth -= 1
        }
        return wasInside
    }

    private fun consumeTypeTail(): Boolean {
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
