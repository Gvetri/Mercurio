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

    /**
     * A pipe table: the header row, the data rows padded or trimmed to the header
     * width, and the alignment each column's separator declared.
     */
    data class Table(
        val header: List<String>,
        val rows: List<List<String>>,
        val alignments: List<TableAlignment>,
    ) : MarkdownBlock

    /**
     * A `$$…$$` display-math block: the expression without its delimiters, the expression exactly
     * as the block authored it for the literal fallback, and [spans] holding the block's literal
     * source text.
     */
    data class DisplayMath(
        val expression: String,
        val source: String,
        val spans: List<MarkdownSpan>,
    ) : MarkdownBlock
}
