package org.mercurio.client.feature.entry.presentation

internal data class InlineMath(
    val startIndex: Int,
    val endIndex: Int,
    val expression: String,
)

/**
 * The inline math run at [startIndex], or null when there is none. Every character between a
 * matched pair belongs to the expression, so common shapes such as `a * b`, `x^{*}`, and `A[0]`
 * reach the formula layout instead of staying markup.
 */
internal fun inlineMathAt(
    text: String,
    startIndex: Int,
): InlineMath? {
    val contentStart = startIndex + 1
    val endIndex = if (opensInlineMath(text, startIndex)) nextDollarClose(text, contentStart) else -1
    val expression = if (endIndex > contentStart) text.substring(contentStart, endIndex) else ""
    if (expression.isEmpty()) return null
    return InlineMath(startIndex = startIndex, endIndex = endIndex + 1, expression = expression)
}

private fun opensInlineMath(
    text: String,
    index: Int,
): Boolean =
    text[index] == '$' &&
        index + 1 < text.length &&
        text[index + 1] != '$' &&
        !text[index + 1].isWhitespace()

private fun nextDollarClose(
    text: String,
    contentStart: Int,
): Int {
    val index = text.indexOf('$', startIndex = contentStart)
    val closes = index > contentStart && closesInlineMath(text, index)
    return if (closes) index else -1
}

private fun closesInlineMath(
    text: String,
    index: Int,
): Boolean = !text[index - 1].isWhitespace() && (index + 1 >= text.length || text[index + 1] != '$')
