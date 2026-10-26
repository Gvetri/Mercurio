package org.mercurio.client.feature.entry.presentation

/**
 * A Gateway message rendered as a closed set of inert blocks. The model has no node
 * that can carry executable content: every span is literal text, and [MarkdownSpan.link]
 * only ever holds an allowed HTTPS destination.
 */
internal sealed interface MarkdownBlock {
    data class Paragraph(
        val spans: List<MarkdownSpan>,
    ) : MarkdownBlock

    /**
     * An ATX heading without its marker runs. The level is the authored hash count, so the
     * renderer can style the emphasis the source declared.
     */
    data class Heading(
        val level: Int,
        val spans: List<MarkdownSpan>,
    ) : MarkdownBlock

    /**
     * One quoted passage: the consecutive `>`-prefixed lines with their markers dropped.
     * A nested marker stays literal text inside the quote, so no level is invented.
     */
    data class Blockquote(
        val spans: List<MarkdownSpan>,
    ) : MarkdownBlock

    /** A thematic break: three or more of one rule character, optionally space separated. */
    data object HorizontalRule : MarkdownBlock

    data class BulletList(
        val items: List<List<MarkdownSpan>>,
    ) : MarkdownBlock

    data class NumberedList(
        val items: List<NumberedItem>,
    ) : MarkdownBlock

    data class CodeBlock(
        val language: String?,
        val code: String,
    ) : MarkdownBlock
}

/**
 * One ordered item with the ordinal its source authored. The renderer shows that marker as
 * written, so numbering the content never had is never invented.
 */
internal data class NumberedItem(
    val marker: String,
    val spans: List<MarkdownSpan>,
)

/**
 * One run of literal message text. [link] is set only for a destination that
 * [allowedExternalLinkDestination] accepts; every other link target stays as its
 * inert label text.
 */
internal data class MarkdownSpan(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val code: Boolean = false,
    val link: String? = null,
)

/**
 * The HTTPS destination an external browser intent may be built for, or null when the
 * destination is not an absolute HTTPS URL. Only this function decides what a link may
 * carry out of the client, so no parser or renderer path can widen it.
 */
internal fun allowedExternalLinkDestination(destination: String): String? {
    val candidate = destination.trim()
    val authority = candidate.drop(HTTPS_SCHEME.length).takeWhile { it !in "/?#" }
    return when {
        candidate.length > MAX_LINK_LENGTH -> null
        candidate.any { it.isWhitespace() || it.isISOControl() || it == '\\' } -> null
        !candidate.startsWith(HTTPS_SCHEME, ignoreCase = true) -> null
        authority.isEmpty() -> null
        else -> candidate
    }
}

private const val HTTPS_SCHEME = "https://"

private const val MAX_LINK_LENGTH = 2_048

private const val CODE_FENCE = "```"

/**
 * Parses the supported Markdown subset of untrusted Gateway content: headings,
 * blockquotes, thematic breaks, paragraphs, lists, emphasis, HTTPS links, and fenced code
 * blocks. Unsupported constructs stay literal text, so nothing in the source can become
 * executable content.
 */
internal fun parseSafeMarkdown(content: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val lines = content.lines()
    val paragraphLines = mutableListOf<String>()

    fun flushParagraph() {
        if (paragraphLines.isNotEmpty()) {
            blocks += MarkdownBlock.Paragraph(parseInline(paragraphLines.joinToString(separator = " ")))
            paragraphLines.clear()
        }
    }

    var index = 0
    while (index < lines.size) {
        val line = lines[index]
        when {
            isFenceLine(line) -> {
                flushParagraph()
                val (block, nextIndex) = readFenceBlock(lines, index, line)
                blocks += block
                index = nextIndex
            }
            line.isBlank() -> {
                flushParagraph()
                index++
            }
            ATX_HEADING.matches(line.trim()) -> {
                flushParagraph()
                blocks +=
                    MarkdownBlock.Heading(
                        level = line.trim().takeWhile { it == '#' }.length,
                        spans = parseInline(line.trim().dropWhile { it == '#' }.trim()),
                    )
                index++
            }
            isBlockquoteLine(line) -> {
                flushParagraph()
                val (spans, nextIndex) = readBlockquote(lines, index)
                blocks += MarkdownBlock.Blockquote(spans)
                index = nextIndex
            }
            isThematicBreak(line) -> {
                flushParagraph()
                blocks += MarkdownBlock.HorizontalRule
                index++
            }
            bulletItem(line) != null -> {
                flushParagraph()
                val (items, nextIndex) = readListItemRun(lines, index, ::bulletItem)
                blocks += MarkdownBlock.BulletList(items)
                index = nextIndex
            }
            numberedItem(line) != null -> {
                flushParagraph()
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
    flushParagraph()
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

private fun fenceLanguage(line: String): String? {
    return line.trimStart().drop(fenceLength(line)).trim().substringBefore(' ').ifEmpty { null }
}
