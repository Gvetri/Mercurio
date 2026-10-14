package org.hermesnative.client.feature.entry.presentation

internal enum class CodeTokenKind {
    Keyword,
    String,
    Number,
    Comment,
    Function,
}

internal data class CodeToken(
    val start: Int,
    val end: Int,
    val kind: CodeTokenKind,
)

internal fun highlightCode(
    language: String,
    code: String,
): List<CodeToken> {
    val grammar = codeGrammar(language) ?: return emptyList()
    val state = ScannerState(code, grammar, mutableListOf())
    var index = 0
    while (index < state.code.length) {
        index = scanStep(state, index)
    }
    return state.tokens
}

private data class ScannerState(
    val code: String,
    val grammar: CodeGrammar,
    val tokens: MutableList<CodeToken>,
)

private fun scanStep(
    state: ScannerState,
    index: Int,
): Int {
    val char = state.code[index]
    val block = state.grammar.blockComment
    val line = state.grammar.lineComment
    return when {
        block != null && state.code.startsWith(block.first, index) -> {
            val close = state.code.indexOf(block.second, startIndex = index + block.first.length)
            emitComment(state, index, if (close < 0) state.code.length else close + block.second.length)
        }
        line != null && state.code.startsWith(line, index) -> {
            val newline = state.code.indexOf('\n', startIndex = index)
            emitComment(state, index, if (newline < 0) state.code.length else newline)
        }
        char in state.grammar.quotes -> scanString(state, index)
        char.isDigit() -> scanNumber(state, index)
        char == '_' || char.isLetter() -> scanWord(state, index)
        else -> index + 1
    }
}

private fun emitComment(
    state: ScannerState,
    index: Int,
    end: Int,
): Int {
    state.tokens += CodeToken(index, end, CodeTokenKind.Comment)
    return end
}

private fun scanString(
    state: ScannerState,
    index: Int,
): Int {
    val code = state.code
    val quote = code[index]
    val triple = quote in state.grammar.tripleQuotes && code.startsWith("$quote$quote$quote", startIndex = index)
    val closing = if (triple) "$quote$quote$quote" else quote.toString()
    val limit =
        if (quote in state.grammar.tripleQuotes) {
            code.length
        } else {
            val newline = code.indexOf('\n', startIndex = index + 1)
            if (newline < 0) code.length else newline
        }
    var cursor = index + closing.length
    var end = limit
    var open = true
    while (open && cursor < limit) {
        when {
            code.startsWith(closing, cursor) -> {
                end = cursor + closing.length
                open = false
            }
            code[cursor] == '\\' -> cursor += 2
            else -> cursor += 1
        }
    }
    state.tokens += CodeToken(index, end, CodeTokenKind.String)
    return end
}

private fun scanNumber(
    state: ScannerState,
    index: Int,
): Int {
    val code = state.code
    val hex = code[index] == '0' && index + 1 < code.length && (code[index + 1] == 'x' || code[index + 1] == 'X')
    val digitsStart = if (hex) index + 2 else index
    val digits =
        if (hex) {
            consume(code, digitsStart) { it.isLetterOrDigit() || it == '_' }
        } else {
            consume(code, digitsStart) { it.isDigit() || it == '_' }
        }
    val cursor = consumeNumericTail(code, digits)
    state.tokens += CodeToken(index, cursor, CodeTokenKind.Number)
    return cursor
}

private fun consumeNumericTail(
    code: String,
    index: Int,
): Int {
    var cursor = index
    if (cursor + 1 < code.length && code[cursor] == '.' && code[cursor + 1].isDigit()) {
        cursor = consume(code, cursor + 1) { it.isDigit() || it == '_' }
    }
    if (cursor < code.length && (code[cursor] == 'e' || code[cursor] == 'E')) {
        cursor = consumeExponent(code, cursor)
    }
    return consume(code, cursor) { it in "fFuUlL" }
}

private fun consumeExponent(
    code: String,
    index: Int,
): Int {
    val afterMarker = index + 1
    val withSign = afterMarker < code.length && (code[afterMarker] == '+' || code[afterMarker] == '-')
    val digitsStart = afterMarker + if (withSign) 1 else 0
    return if (digitsStart < code.length && code[digitsStart].isDigit()) {
        consume(code, digitsStart) { it.isDigit() || it == '_' }
    } else {
        index
    }
}

private fun scanWord(
    state: ScannerState,
    index: Int,
): Int {
    val code = state.code
    val grammar = state.grammar
    val end = consume(code, index) { it.isLetterOrDigit() || it == '_' }
    val word = code.substring(index, end)
    val isKeyword =
        grammar.keywords.contains(if (grammar.caseInsensitiveKeywords) word.lowercase() else word)
    val kind =
        when {
            isKeyword -> CodeTokenKind.Keyword
            followsOpenParen(code, end) -> CodeTokenKind.Function
            else -> null
        }
    if (kind != null) {
        state.tokens += CodeToken(index, end, kind)
    }
    return end
}

private fun followsOpenParen(
    code: String,
    index: Int,
): Boolean {
    var cursor = index
    while (cursor < code.length && code[cursor].isWhitespace()) {
        cursor += 1
    }
    return cursor < code.length && code[cursor] == '('
}

private fun consume(
    code: String,
    index: Int,
    accepts: (Char) -> Boolean,
): Int {
    var cursor = index
    while (cursor < code.length && accepts(code[cursor])) {
        cursor += 1
    }
    return cursor
}
