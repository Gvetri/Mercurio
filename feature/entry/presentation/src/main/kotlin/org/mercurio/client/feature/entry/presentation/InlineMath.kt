package org.mercurio.client.feature.entry.presentation

private val INLINE_MARKUP_CHARACTERS = setOf('*', '`', '[')

internal data class InlineMath(
    val startIndex: Int,
    val endIndex: Int,
    val expression: String,
)

/**
 * The inline math run at [startIndex], or null when there is none or its expression
 * carries Markdown markup: such a run stays the markup the literal parse renders today.
 */
internal fun inlineMathAt(
    text: String,
    startIndex: Int,
): InlineMath? {
    val contentStart = startIndex + 1
    val endIndex = if (opensInlineMath(text, startIndex)) nextDollarClose(text, contentStart) else -1
    val expression = if (endIndex > contentStart) text.substring(contentStart, endIndex) else ""
    val unmarked =
        expression.isNotEmpty() &&
            expression.none { character -> character in INLINE_MARKUP_CHARACTERS }
    return if (unmarked) InlineMath(startIndex = startIndex, endIndex = endIndex + 1, expression = expression) else null
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
