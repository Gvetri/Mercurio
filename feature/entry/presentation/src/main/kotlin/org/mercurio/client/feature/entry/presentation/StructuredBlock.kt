package org.mercurio.client.feature.entry.presentation

private const val DISPLAY_MATH_DELIMITER = "$$"

/**
 * The structured block a paragraph run is, or null when the run stays a literal
 * paragraph. A construct is recognised only when the whole run is that construct,
 * so block grouping and rendered text stay exactly what the literal parse produced.
 */
internal fun structuredBlock(lines: List<String>): MarkdownBlock? = displayMathBlock(lines)

private fun displayMathBlock(lines: List<String>): MarkdownBlock.DisplayMath? {
    val source = lines.joinToString(separator = " ")
    val expression = displayMathExpression(source) ?: return null
    return MarkdownBlock.DisplayMath(expression = expression, spans = parseInline(source))
}

private fun displayMathExpression(source: String): String? {
    val delimiter = DISPLAY_MATH_DELIMITER.length
    val bounded =
        source.length >= delimiter * 2 &&
            source.startsWith(DISPLAY_MATH_DELIMITER) &&
            source.endsWith(DISPLAY_MATH_DELIMITER)
    if (!bounded) return null
    val expression = source.substring(delimiter, source.length - delimiter).trim()
    return if (expression.isEmpty() || DISPLAY_MATH_DELIMITER in expression) null else expression
}
