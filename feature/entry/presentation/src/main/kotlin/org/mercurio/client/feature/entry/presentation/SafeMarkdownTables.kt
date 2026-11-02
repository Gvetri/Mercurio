package org.mercurio.client.feature.entry.presentation

private const val MINIMUM_TABLE_LINES = 2

private const val TABLE_PIPE = '|'

private const val TABLE_ESCAPE = '\\'

private val DELIMITER_CELL = Regex("""^:?-+:?$""")

internal fun pipeTableBlock(lines: List<String>): MarkdownBlock.Table? {
    val piped = lines.size >= MINIMUM_TABLE_LINES && lines.all { TABLE_PIPE in it }
    val header = if (piped) tableCells(lines.first()) else emptyList()
    val delimiter = if (piped) tableCells(lines[MINIMUM_TABLE_LINES - 1]) else emptyList()
    val shaped =
        piped &&
            delimiter.isNotEmpty() &&
            delimiter.all { DELIMITER_CELL.matches(it) } &&
            header.size == delimiter.size
    if (!shaped) return null
    return MarkdownBlock.Table(
        header = header,
        rows = lines.drop(MINIMUM_TABLE_LINES).map { line -> tableRow(tableCells(line), header.size) },
        alignments = delimiter.map(::tableAlignment),
    )
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
