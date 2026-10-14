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
    val tokens = mutableListOf<CodeToken>()
    var index = 0
    while (index < code.length) {
        index = scanStep(code, grammar, index, tokens)
    }
    return tokens
}

private fun scanStep(
    code: String,
    grammar: CodeGrammar,
    index: Int,
    tokens: MutableList<CodeToken>,
): Int {
    val char = code[index]
    val block = grammar.blockComment
    val line = grammar.lineComment
    return when {
        block != null && code.startsWith(block.first, index) -> scanComment(code, grammar, index, tokens)
        line != null && code.startsWith(line, index) -> scanComment(code, grammar, index, tokens)
        char in grammar.quotes -> scanString(code, grammar, index, tokens)
        char.isDigit() -> scanNumber(code, index, tokens)
        char == '_' || char.isLetter() -> scanWord(code, grammar, index, tokens)
        else -> index + 1
    }
}

private fun scanComment(
    code: String,
    grammar: CodeGrammar,
    index: Int,
    tokens: MutableList<CodeToken>,
): Int {
    val block = grammar.blockComment
    val end =
        if (block != null && code.startsWith(block.first, index)) {
            val close = code.indexOf(block.second, startIndex = index + block.first.length)
            if (close < 0) code.length else close + block.second.length
        } else {
            val newline = code.indexOf('\n', startIndex = index)
            if (newline < 0) code.length else newline
        }
    tokens += CodeToken(index, end, CodeTokenKind.Comment)
    return end
}

private fun scanString(
    code: String,
    grammar: CodeGrammar,
    index: Int,
    tokens: MutableList<CodeToken>,
): Int {
    val quote = code[index]
    val triple = quote in grammar.tripleQuotes && code.startsWith("$quote$quote$quote", startIndex = index)
    val closing = if (triple) "$quote$quote$quote" else quote.toString()
    val limit =
        if (quote in grammar.tripleQuotes) {
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
    tokens += CodeToken(index, end, CodeTokenKind.String)
    return end
}

private fun scanNumber(
    code: String,
    index: Int,
    tokens: MutableList<CodeToken>,
): Int {
    val hex = code[index] == '0' && index + 1 < code.length && (code[index + 1] == 'x' || code[index + 1] == 'X')
    val digitsStart = if (hex) index + 2 else index
    val digits =
        if (hex) {
            consume(code, digitsStart) { it.isLetterOrDigit() || it == '_' }
        } else {
            consume(code, digitsStart) { it.isDigit() || it == '_' }
        }
    val cursor = consumeNumericTail(code, digits)
    tokens += CodeToken(index, cursor, CodeTokenKind.Number)
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
    code: String,
    grammar: CodeGrammar,
    index: Int,
    tokens: MutableList<CodeToken>,
): Int {
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
        tokens += CodeToken(index, end, kind)
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
