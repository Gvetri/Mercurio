package org.hermesnative.client.feature.entry.data

internal sealed interface PatternChainToken {
    data class Literal(
        val text: String,
        val line: Int,
    ) : PatternChainToken

    data class Reference(val name: String) : PatternChainToken
}

internal data class RegexCallSite(
    val line: Int,
    val chain: List<PatternChainToken>,
    val hasDynamicTail: Boolean,
)

internal data class RegexPatternFacts(
    val declarations: Map<String, List<PatternChainToken>>,
    val callSites: List<RegexCallSite>,
)

internal fun scanRegexPatterns(source: String): RegexPatternFacts {
    val cursor = RegexSourceCursor(source)
    return RegexPatternScanner(cursor, RegexSourceLexing(cursor)).scanFacts()
}

private enum class ChainContinuation { PLUS, TERMINAL, INVALID }

/**
 * Resolves `Regex(` constructions and the `val`/`const val` string-literal declarations they
 * reference. A construction whose argument is anything but a string-literal chain, references
 * that resolve, and `+` joins is reported with a dynamic tail: its static prefix is still
 * checked, the injected remainder is not.
 */
internal class RegexPatternScanner(
    private val cursor: RegexSourceCursor,
    private val lexing: RegexSourceLexing,
) {
    private val typeSkip = RegexTypeSkip(cursor, lexing)

    fun scanFacts(): RegexPatternFacts {
        val declarations = mutableMapOf<String, List<PatternChainToken>>()
        val callSites = mutableListOf<RegexCallSite>()
        while (!cursor.atEnd) {
            when {
                cursor.startsWith("//") -> cursor.skipLineComment()
                cursor.startsWith("/*") -> cursor.skipBlockComment()
                cursor.current() == '"' -> lexing.skipString()
                cursor.current() == '\'' -> cursor.skipCharLiteral()
                cursor.current().isIdentifierStart() -> {
                    val word = cursor.readIdentifier()
                    when (word) {
                        REGEX_CONSTRUCTOR_NAME -> parseCallSite()?.let(callSites::add)
                        "const", "val" -> parseDeclaration(word, declarations)
                        else -> Unit
                    }
                }
                else -> cursor.advance()
            }
        }
        return RegexPatternFacts(declarations, callSites)
    }

    private fun parseCallSite(): RegexCallSite? {
        val siteLine = cursor.line
        lexing.skipTrivia()
        if (cursor.atEnd || cursor.current() != '(') return null
        cursor.advance()
        val chain = mutableListOf<PatternChainToken>()
        var dynamicTail = false
        var closed = false
        while (!dynamicTail && !closed && !cursor.atEnd) {
            lexing.skipTrivia()
            val element = readChainElement()
            if (element == null) {
                dynamicTail = true
            } else {
                chain += element
                when (readChainContinuation()) {
                    ChainContinuation.PLUS -> Unit
                    ChainContinuation.TERMINAL -> closed = true
                    ChainContinuation.INVALID -> dynamicTail = true
                }
            }
        }
        return RegexCallSite(siteLine, chain, hasDynamicTail = dynamicTail || !closed)
    }

    private fun readChainElement(): PatternChainToken? {
        if (cursor.atEnd) return null
        return when {
            cursor.current() == '"' -> readLiteralElement()
            cursor.current().isIdentifierStart() -> readChainReference()
            else -> null
        }
    }

    private fun readLiteralElement(): PatternChainToken? {
        val literal = lexing.readStringLiteral() ?: return null
        return PatternChainToken.Literal(literal.text, literal.line)
    }

    private fun readChainReference(): PatternChainToken? {
        val saved = cursor.save()
        val name = cursor.readIdentifier()
        lexing.skipTrivia()
        val namedArgument = !cursor.atEnd && cursor.current() == '=' && cursor.peek(1) != '='
        var element: PatternChainToken? = null
        if (namedArgument) {
            cursor.advance()
            lexing.skipTrivia()
            if (!cursor.atEnd) {
                element =
                    when {
                        cursor.current() == '\"' -> readLiteralElement()
                        cursor.current().isIdentifierStart() -> PatternChainToken.Reference(cursor.readIdentifier())
                        else -> null
                    }
            }
        } else if (cursor.atEnd || !cursor.current().isChainContinuation()) {
            cursor.restore(saved)
        } else {
            element = PatternChainToken.Reference(name)
        }
        return element
    }

    private fun readChainContinuation(): ChainContinuation {
        lexing.skipTrivia()
        if (cursor.atEnd) return ChainContinuation.TERMINAL
        return when (cursor.current()) {
            '+' -> {
                cursor.advance()
                ChainContinuation.PLUS
            }
            ')' -> {
                cursor.advance()
                ChainContinuation.TERMINAL
            }
            ',' -> ChainContinuation.TERMINAL
            else -> ChainContinuation.INVALID
        }
    }

    private fun parseDeclaration(
        word: String,
        declarations: MutableMap<String, List<PatternChainToken>>,
    ) {
        if (word == "const" && !consumeConstValKeyword()) return
        parseValDeclaration(declarations)
    }

    private fun consumeConstValKeyword(): Boolean {
        val saved = cursor.save()
        lexing.skipTrivia()
        val keyword = !cursor.atEnd && cursor.current().isIdentifierStart() && cursor.readIdentifier() == "val"
        if (!keyword) cursor.restore(saved)
        return keyword
    }

    private fun parseValDeclaration(declarations: MutableMap<String, List<PatternChainToken>>) {
        val savedAfterKeyword = cursor.save()
        lexing.skipTrivia()
        if (cursor.atEnd || !cursor.current().isIdentifierStart()) {
            cursor.restore(savedAfterKeyword)
            return
        }
        val name = cursor.readIdentifier()
        lexing.skipTrivia()
        if (cursor.atEnd) return
        when (cursor.current()) {
            ':' -> parseTypedDeclaration(declarations, name)
            '=' -> {
                cursor.advance()
                parseInitializerChain(declarations, name)
            }
            else -> cursor.restore(savedAfterKeyword)
        }
    }

    private fun parseTypedDeclaration(
        declarations: MutableMap<String, List<PatternChainToken>>,
        name: String,
    ) {
        typeSkip.skip()
        lexing.skipTrivia()
        if (cursor.atEnd || cursor.current() != '=') return
        cursor.advance()
        parseInitializerChain(declarations, name)
    }

    private fun parseInitializerChain(
        declarations: MutableMap<String, List<PatternChainToken>>,
        name: String?,
    ): Boolean {
        val chain = mutableListOf<PatternChainToken>()
        var valid = true
        var recorded = false
        while (valid && !recorded) {
            val elementStart = cursor.save()
            lexing.skipTrivia()
            when {
                cursor.atEnd -> recorded = true
                cursor.current() == '"' -> appendLiteralElement(chain)
                cursor.current().isIdentifierStart() -> valid = appendReferenceElement(chain, elementStart)
                else -> valid = false
            }
            if (valid && !recorded) {
                when (readInitializerContinuation(cursor.line)) {
                    ChainContinuation.PLUS -> Unit
                    ChainContinuation.TERMINAL -> recorded = true
                    ChainContinuation.INVALID -> valid = false
                }
            }
        }
        if (valid && name != null) declarations[name] = chain
        return valid
    }

    private fun appendLiteralElement(chain: MutableList<PatternChainToken>) {
        val literal = lexing.readStringLiteral()
        if (literal != null) chain += PatternChainToken.Literal(literal.text, literal.line)
    }

    private fun appendReferenceElement(
        chain: MutableList<PatternChainToken>,
        elementStart: Int,
    ): Boolean {
        val reference = cursor.readIdentifier()
        lexing.skipTrivia()
        if (cursor.atEnd || cursor.current().isExpressionContinuation()) {
            cursor.restore(elementStart)
            return false
        }
        chain += PatternChainToken.Reference(reference)
        return true
    }

    private fun readInitializerContinuation(elementEndLine: Int): ChainContinuation {
        lexing.skipTrivia()
        if (cursor.atEnd || cursor.line > elementEndLine) return ChainContinuation.TERMINAL
        return when (cursor.current()) {
            '+' -> {
                cursor.advance()
                ChainContinuation.PLUS
            }
            ',', ')', ']', '}' -> ChainContinuation.TERMINAL
            else -> ChainContinuation.INVALID
        }
    }

    private companion object {
        private const val REGEX_CONSTRUCTOR_NAME = "Regex"
    }
}

private fun Char.isChainContinuation(): Boolean = this == '+' || this == ',' || this == ')'

private fun Char.isExpressionContinuation(): Boolean =
    this == '(' || this == '.' || this == '{' || this == '<' || this == '?' || this == '!' || this == ':'
