package org.mercurio.client.feature.entry.presentation

private const val DISPLAY_MATH_DELIMITER = "$$"

private const val MINIMUM_TABLE_LINES = 2

private const val TABLE_PIPE = '|'

private val DELIMITER_CELL = Regex("""^:?-+:?$""")

/**
 * The structured block a paragraph run is, or null when the run stays a literal
 * paragraph. A construct is recognised only when the whole run is that construct,
 * so block grouping and rendered text stay exactly what the literal parse produced.
 */
internal fun structuredBlock(lines: List<String>): MarkdownBlock? {
    return displayMathBlock(lines) ?: pipeTableBlock(lines)
}

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

private fun pipeTableBlock(lines: List<String>): MarkdownBlock.Table? {
    val piped = lines.size >= MINIMUM_TABLE_LINES && lines.all { TABLE_PIPE in it }
    val header = if (piped) tableCells(lines.first()) else emptyList()
    val delimiter = if (piped) tableCells(lines[MINIMUM_TABLE_LINES - 1]) else emptyList()
    val shaped =
        piped &&
            delimiter.isNotEmpty() &&
            delimiter.all { DELIMITER_CELL.matches(it) } &&
            header.size == delimiter.size
    if (!shaped) return null
    val spans = parseInline(lines.joinToString(separator = " "))
    return MarkdownBlock.Table(
        header = header,
        rows = lines.drop(MINIMUM_TABLE_LINES).map { line -> tableRow(tableCells(line), header.size) },
        spans = spans,
    )
}

private fun tableCells(line: String): List<String> {
    val cells = line.split(TABLE_PIPE).map { cell -> cell.trim() }.toMutableList()
    if (line.startsWith(TABLE_PIPE)) cells.removeAt(0)
    if (line.endsWith(TABLE_PIPE)) cells.removeAt(cells.lastIndex)
    return cells
}

private fun tableRow(
    cells: List<String>,
    width: Int,
): List<String> = List(width) { position -> cells.getOrElse(position) { "" } }

internal data class InlineMath(
    val startIndex: Int,
    val endIndex: Int,
    val expression: String,
)

internal fun inlineMathAt(
    text: String,
    startIndex: Int,
): InlineMath? {
    val contentStart = startIndex + 1
    val endIndex = if (opensInlineMath(text, startIndex)) nextDollarClose(text, contentStart) else -1
    if (endIndex <= contentStart) return null
    val expression = text.substring(contentStart, endIndex)
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
