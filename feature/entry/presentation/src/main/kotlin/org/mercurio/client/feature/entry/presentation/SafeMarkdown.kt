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
        index = consumeBlock(lines, index, blocks, paragraphLines, ::flushParagraphRun)
    }
    flushParagraphRun()
    return blocks
}

private fun consumeBlock(
    lines: List<String>,
    index: Int,
    blocks: MutableList<MarkdownBlock>,
    paragraphLines: MutableList<String>,
    flushParagraph: () -> Unit,
): Int {
    val line = lines[index]
    return when {
        isFenceLine(line) -> {
            flushParagraph()
            val (block, nextIndex) = readFenceBlock(lines, index, line)
            blocks += block
            nextIndex
        }
        line.isBlank() -> {
            flushParagraph()
            index + 1
        }
        ATX_HEADING.matches(line.trim()) -> {
            flushParagraph()
            blocks += headingBlock(line)
            index + 1
        }
        isBlockquoteLine(line) -> {
            flushParagraph()
            val (spans, nextIndex) = readBlockquote(lines, index)
            blocks += MarkdownBlock.Blockquote(spans)
            nextIndex
        }
        isThematicBreak(line) -> {
            flushParagraph()
            blocks += MarkdownBlock.HorizontalRule
            index + 1
        }
        bulletItem(line) != null -> {
            flushParagraph()
            val (items, nextIndex) = readListItemRun(lines, index, ::bulletItem)
            blocks += MarkdownBlock.BulletList(items)
            nextIndex
        }
        numberedItem(line) != null -> {
            flushParagraph()
            val (items, nextIndex) = readListItemRun(lines, index, ::numberedItem)
            blocks += MarkdownBlock.NumberedList(items)
            nextIndex
        }
        else -> {
            paragraphLines += line.trim()
            index + 1
        }
    }
}

private val ATX_HEADING = Regex("""^#{1,6}\s+.+$""")

private fun headingBlock(line: String): MarkdownBlock.Heading {
    val trimmed = line.trim()
    return MarkdownBlock.Heading(
        trimmed.takeWhile { it == '#' }.length,
        parseInline(trimmed.dropWhile { it == '#' }.trim()),
    )
}

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
    val openingFenceLength = openingLine.trimStart().takeWhile { it == '`' }.length
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

private fun isFenceLine(line: String): Boolean = line.trimStart().takeWhile { it == '`' }.length >= CODE_FENCE.length

private fun isClosingFence(
    line: String,
    openingFenceLength: Int,
): Boolean {
    val trimmed = line.trim()
    val closingFenceLength = trimmed.trimStart().takeWhile { it == '`' }.length
    return closingFenceLength >= openingFenceLength && trimmed.drop(closingFenceLength).isEmpty()
}

private fun fenceLanguage(line: String): String? =
    line.trimStart().drop(line.trimStart().takeWhile { it == '`' }.length).trim().substringBefore(' ').ifEmpty {
        null
    }
