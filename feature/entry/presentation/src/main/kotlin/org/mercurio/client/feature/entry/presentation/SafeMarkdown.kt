package org.mercurio.client.feature.entry.presentation

import org.mercurio.client.feature.entry.domain.MAX_EXTERNAL_DESTINATION_LENGTH

/**
 * The HTTPS destination an external browser intent may be built for, or null when the
 * destination is not an absolute HTTPS URL. Only this function decides what a link may
 * carry out of the client, so no parser or renderer path can widen it.
 */
internal fun allowedExternalLinkDestination(destination: String): String? {
    val candidate = destination.trim()
    val authority = candidate.drop(HTTPS_SCHEME.length).takeWhile { it !in "/?#" }
    return when {
        candidate.length > MAX_EXTERNAL_DESTINATION_LENGTH -> null
        candidate.any { it.isWhitespace() || it.isISOControl() || it == '\\' } -> null
        !candidate.startsWith(HTTPS_SCHEME, ignoreCase = true) -> null
        authority.isEmpty() -> null
        else -> candidate
    }
}

private const val HTTPS_SCHEME = "https://"

private const val CODE_FENCE = "```"

/**
 * Parses the supported Markdown subset of untrusted Gateway content: headings,
 * blockquotes, thematic breaks, paragraphs, lists, emphasis, HTTPS links, fenced code
 * blocks, pipe tables, and math. A pipe table becomes its own block with the alignment its
 * separator row declares; `$$…$$` display math becomes its own block, and `![…](…)` and `$…$`
 * become marked spans. Every one keeps its literal source text in the model, so a renderer opts
 * in explicitly and an unhandled construct stays text. Unsupported constructs stay literal text,
 * so nothing in the source can become executable content.
 */
internal fun parseSafeMarkdown(content: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val lines = content.lines()
    val paragraphLines = mutableListOf<String>()

    fun flushParagraphRun() {
        if (paragraphLines.isEmpty()) return
        val leading = leadingPipeTable(paragraphLines)
        if (leading != null) {
            blocks += leading.first
            paragraphLines.subList(0, leading.second).clear()
            flushParagraphRun()
            return
        }
        val source = paragraphLines.joinToString(separator = " ")
        blocks += structuredBlock(paragraphLines) ?: MarkdownBlock.Paragraph(parseInline(source))
        paragraphLines.clear()
    }

    var index = 0
    while (index < lines.size) {
        val line = lines[index]
        when {
            isFenceLine(line) -> {
                flushParagraphRun()
                val (block, nextIndex) = readFenceBlock(lines, index, line)
                blocks += block
                index = nextIndex
            }
            line.isBlank() -> {
                flushParagraphRun()
                index++
            }
            ATX_HEADING.matches(line.trim()) -> {
                flushParagraphRun()
                blocks +=
                    MarkdownBlock.Heading(
                        level = line.trim().takeWhile { it == '#' }.length,
                        spans = parseInline(line.trim().dropWhile { it == '#' }.trim()),
                    )
                index++
            }
            isBlockquoteLine(line) -> {
                flushParagraphRun()
                val (spans, nextIndex) = readBlockquote(lines, index)
                blocks += MarkdownBlock.Blockquote(spans)
                index = nextIndex
            }
            isThematicBreak(line) -> {
                flushParagraphRun()
                blocks += MarkdownBlock.HorizontalRule
                index++
            }
            bulletItem(line) != null -> {
                flushParagraphRun()
                val (items, nextIndex) = readListItemRun(lines, index, ::bulletItem)
                blocks += MarkdownBlock.BulletList(items)
                index = nextIndex
            }
            numberedItem(line) != null -> {
                flushParagraphRun()
                val (items, nextIndex) = readListItemRun(lines, index, ::numberedItem)
                blocks += MarkdownBlock.NumberedList(items)
                index = nextIndex
            }
            else -> {
                paragraphLines += line.trim()
                index++
            }
        }
    }
    flushParagraphRun()
    return blocks
}

private val ATX_HEADING = Regex("""^#{1,6}\s+.+$""")

private fun isBlockquoteLine(line: String): Boolean = line.trimStart().startsWith(">")

private fun readBlockquote(
    lines: List<String>,
    startIndex: Int,
): Pair<List<MarkdownSpan>, Int> {
    val quoted = mutableListOf<String>()
    var index = startIndex
    while (index < lines.size && isBlockquoteLine(lines[index])) {
        quoted += lines[index].trimStart().drop(1).removePrefix(" ")
        index++
    }
    return parseInline(quoted.joinToString(separator = " ")) to index
}

private fun isThematicBreak(line: String): Boolean {
    val compact = line.filter { it != ' ' && it != '\t' }
    return compact.length >= MIN_THEMATIC_BREAK_LENGTH &&
        compact.first() in THEMATIC_BREAK_CHARACTERS &&
        compact.all { it == compact.first() }
}

private const val MIN_THEMATIC_BREAK_LENGTH = 3

private const val THEMATIC_BREAK_CHARACTERS = "-_*"

private fun readFenceBlock(
    lines: List<String>,
    startIndex: Int,
    openingLine: String,
): Pair<MarkdownBlock.CodeBlock, Int> {
    val code = mutableListOf<String>()
    val openingFenceLength = fenceLength(openingLine)
    var index = startIndex + 1
    while (index < lines.size && !isClosingFence(lines[index], openingFenceLength)) {
        code += lines[index]
        index++
    }
    val terminated = index < lines.size
    if (terminated) index++
    if (!terminated) {
        while (code.isNotEmpty() && code.last().isEmpty()) code.removeLast()
    }
    return MarkdownBlock.CodeBlock(language = fenceLanguage(openingLine), code = code.joinToString("\n")) to index
}

private fun fenceLength(line: String): Int = line.trimStart().takeWhile { it == '`' }.length

private fun isFenceLine(line: String): Boolean = fenceLength(line) >= CODE_FENCE.length

private fun isClosingFence(
    line: String,
    openingFenceLength: Int,
): Boolean {
    val trimmed = line.trim()
    val closingFenceLength = fenceLength(trimmed)
    return closingFenceLength >= openingFenceLength && trimmed.drop(closingFenceLength).isEmpty()
}

private fun fenceLanguage(line: String): String? =
    line.trimStart().drop(fenceLength(line)).trim().substringBefore(' ').ifEmpty {
        null
    }
