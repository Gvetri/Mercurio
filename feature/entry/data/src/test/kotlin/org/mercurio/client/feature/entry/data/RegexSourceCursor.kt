package org.mercurio.client.feature.entry.data

/**
 * Positioning cursor over Kotlin source text: the single owner of the position and the line
 * number, plus the raw skipping primitives for everything that is not a string literal, so
 * every lexer and parser above it reports the same coordinates.
 */
internal class RegexSourceCursor(private val source: String) {
    private var position = 0

    var line = 1
        private set

    val atEnd: Boolean
        get() = position >= source.length

    fun current(): Char = source[position]

    fun peek(offset: Int): Char? = source.getOrNull(position + offset)

    fun advance(): Char {
        val consumed = source[position]
        position += 1
        if (consumed == '\n') line += 1
        return consumed
    }

    fun startsWith(text: String): Boolean = source.startsWith(text, position)

    fun readIdentifier(): String {
        val start = position
        while (!atEnd && source[position].isIdentifierPart()) {
            position += 1
        }
        return source.substring(start, position)
    }

    fun save(): Int = position

    fun restore(saved: Int) {
        position = saved
        line = source.substring(0, saved).count { it == '\n' } + 1
    }

    fun skipLineComment() {
        while (!atEnd) {
            if (advance() == '\n') return
        }
    }

    fun skipBlockComment() {
        advance()
        advance()
        var depth = 1
        while (!atEnd && depth > 0) {
            when {
                startsWith("/*") -> {
                    advance()
                    advance()
                    depth += 1
                }
                startsWith("*/") -> {
                    advance()
                    advance()
                    depth -= 1
                }
                else -> advance()
            }
        }
    }

    fun skipCharLiteral(): Boolean {
        val opened = !atEnd && current() == '\''
        if (!opened) return false
        advance()
        var closed = false
        var unterminated = false
        while (!closed && !unterminated && !atEnd) {
            when (val consumed = advance()) {
                '\\' -> if (!atEnd) advance()
                '\'' -> closed = true
                '\n' -> unterminated = true
                else -> Unit
            }
        }
        return closed
    }
}

internal fun Char.isIdentifierStart(): Boolean = isLetter() || this == '_'

internal fun Char.isIdentifierPart(): Boolean = isLetterOrDigit() || this == '_'
