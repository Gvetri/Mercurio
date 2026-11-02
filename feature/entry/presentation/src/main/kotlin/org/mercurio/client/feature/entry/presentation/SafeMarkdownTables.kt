package org.mercurio.client.feature.entry.presentation

private const val MINIMUM_TABLE_LINES = 2

private const val TABLE_PIPE = '|'

private const val TABLE_ESCAPE = '\\'

private val DELIMITER_CELL = Regex("""^:?-+:?$""")

/**
 * The pipe table a paragraph run starts with, and how many of its lines the table consumes.
 * A table ends at the first line that is not a row, as GFM does, so prose written directly
 * under the last row stays prose instead of turning the whole run literal.
 */
internal fun leadingPipeTable(lines: List<String>): Pair<MarkdownBlock.Table, Int>? {
    val header = if (lines.size >= MINIMUM_TABLE_LINES) tableCells(lines[0]) else emptyList()
    val delimiter = if (lines.size >= MINIMUM_TABLE_LINES) tableCells(lines[1]) else emptyList()
    val shaped =
        lines.size >= MINIMUM_TABLE_LINES &&
            header.isNotEmpty() &&
            delimiter.size == header.size &&
            delimiter.all { DELIMITER_CELL.matches(it) } &&
            TABLE_PIPE in lines[0]
    if (!shaped) return null
    val body = lines.drop(MINIMUM_TABLE_LINES).takeWhile { line -> TABLE_PIPE in line }
    val table =
        MarkdownBlock.Table(
            header = header,
            rows = body.map { line -> tableRow(tableCells(line), header.size) },
            alignments = delimiter.map(::tableAlignment),
        )
    return table to MINIMUM_TABLE_LINES + body.size
}

private fun tableAlignment(cell: String): TableAlignment =
    when {
        cell.startsWith(':') && cell.endsWith(':') -> TableAlignment.Center
        cell.endsWith(':') -> TableAlignment.End
        else -> TableAlignment.Start
    }

private fun tableCells(line: String): List<String> {
    val source = line.trim()
    val cells = mutableListOf<String>()
    val current = StringBuilder()
    var index = 0
    while (index < source.length) {
        val character = source[index]
        if (character == TABLE_ESCAPE && index + 1 < source.length && source[index + 1] == TABLE_PIPE) {
            current.append(TABLE_PIPE)
            index += 2
            continue
        }
        if (character == TABLE_PIPE) {
            cells += current.toString().trim()
            current.clear()
        } else {
            current.append(character)
        }
        index++
    }
    cells += current.toString().trim()
    if (source.startsWith(TABLE_PIPE)) cells.removeAt(0)
    if (source.endsWithUnescapedPipe() && cells.isNotEmpty()) cells.removeAt(cells.lastIndex)
    return cells
}

private fun String.endsWithUnescapedPipe(): Boolean {
    if (!endsWith(TABLE_PIPE)) return false
    val precedingBackslashes = substring(0, lastIndex).takeLastWhile { it == '\\' }.length
    return precedingBackslashes % 2 == 0
}

private fun tableRow(
    cells: List<String>,
    width: Int,
): List<String> = List(width) { position -> cells.getOrElse(position) { "" } }
