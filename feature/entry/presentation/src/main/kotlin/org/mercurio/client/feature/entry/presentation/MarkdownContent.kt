package org.mercurio.client.feature.entry.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Renders the supported Markdown subset of untrusted Gateway content as native Compose
 * content: headings, blockquotes, thematic breaks, paragraphs, lists, emphasis, HTTPS links,
 * fenced code blocks, pipe tables, and math. A table becomes the approved table card; inline
 * `$…$` math joins the inline text path and `$$…$$` display math gets its own surface. The
 * content is never interpreted as markup beyond that subset, so no HTML, script, or other
 * executable content can run, and no WebView is involved.
 *
 * Link destinations are carried as annotations built from [allowedExternalLinkDestination];
 * a blocked destination is rendered as its inert label and cannot be selected. [onTableExpand]
 * carries a table wider than its row to the host's full-screen viewer.
 */
@Composable
internal fun MarkdownContent(
    markdown: String,
    actions: MessageActions,
    onTableExpand: (MarkdownBlock.Table) -> Unit,
    modifier: Modifier = Modifier,
    blockSpacing: Dp? = null,
) {
    val tokens = LocalHermesDesignTokens.current
    val spacing = tokens.spacing
    val blocks = remember(markdown) { parseSafeMarkdown(markdown) }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(blockSpacing ?: spacing.s),
    ) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Paragraph -> MarkdownParagraph(spans = block.spans, actions = actions)
                is MarkdownBlock.Heading -> MarkdownHeading(heading = block, actions = actions)
                is MarkdownBlock.Blockquote -> MarkdownBlockquote(spans = block.spans, actions = actions)
                is MarkdownBlock.HorizontalRule -> MarkdownHorizontalRule()
                is MarkdownBlock.BulletList ->
                    MarkdownList(
                        items = block.items.map { item -> RenderedItem(marker = BULLET_MARKER, spans = item) },
                        actions = actions,
                    )
                is MarkdownBlock.NumberedList ->
                    MarkdownList(
                        items =
                            block.items.map { item ->
                                RenderedItem(marker = item.marker, spans = item.spans)
                            },
                        actions = actions,
                    )
                is MarkdownBlock.CodeBlock -> MarkdownCodeBlock(block = block, onCopy = actions.copy)
                is MarkdownBlock.Table ->
                    MarkdownTableCard(
                        block = block,
                        actions = actions,
                        onExpand = { onTableExpand(block) },
                    )
                is MarkdownBlock.DisplayMath ->
                    MarkdownDisplayMath(expression = block.expression, source = block.source)
            }
        }
    }
}

@Composable
private fun MarkdownParagraph(
    spans: List<MarkdownSpan>,
    actions: MessageActions,
) {
    val tokens = LocalHermesDesignTokens.current
    val math = rememberInlineMath(spans, LocalTextStyle.current, LocalContentColor.current)
    Text(
        text =
            spans.toAnnotatedString(
                linkColor = MaterialTheme.colorScheme.primary,
                onOpenLink = actions.openLink,
                codeBackground = tokens.componentColors.inset,
                math = math,
            ),
        inlineContent = math?.contents.orEmpty(),
    )
}

@Composable
private fun MarkdownHeading(
    heading: MarkdownBlock.Heading,
    actions: MessageActions,
) {
    val tokens = LocalHermesDesignTokens.current
    val style = headingStyle(heading.level)
    val math = rememberInlineMath(heading.spans, style, LocalContentColor.current)
    Text(
        text =
            heading.spans.toAnnotatedString(
                linkColor = MaterialTheme.colorScheme.primary,
                onOpenLink = actions.openLink,
                codeBackground = tokens.componentColors.inset,
                math = math,
            ),
        style = style,
        modifier = Modifier.semantics { heading() },
        inlineContent = math?.contents.orEmpty(),
    )
}

@Composable
private fun headingStyle(level: Int): TextStyle =
    when (level) {
        1 -> MaterialTheme.typography.headlineSmall
        2 -> MaterialTheme.typography.titleLarge
        HEADING_LEVEL_3 -> MaterialTheme.typography.titleMedium
        HEADING_LEVEL_4 -> MaterialTheme.typography.titleSmall
        else -> MaterialTheme.typography.bodyLarge
    }

@Composable
private fun MarkdownBlockquote(
    spans: List<MarkdownSpan>,
    actions: MessageActions,
) {
    val tokens = LocalHermesDesignTokens.current
    val spacing = tokens.spacing
    val math = rememberInlineMath(spans, LocalTextStyle.current, LocalContentColor.current)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(spacing.s),
    ) {
        Box(
            modifier =
                Modifier
                    .width(QUOTE_BAR_WIDTH)
                    .fillMaxHeight()
                    .background(color = MaterialTheme.colorScheme.outlineVariant),
        )
        Text(
            text =
                spans.toAnnotatedString(
                    linkColor = MaterialTheme.colorScheme.primary,
                    onOpenLink = actions.openLink,
                    codeBackground = tokens.componentColors.inset,
                    math = math,
                ),
            modifier = Modifier.weight(1f),
            inlineContent = math?.contents.orEmpty(),
        )
    }
}

@Composable
private fun MarkdownHorizontalRule() {
    HorizontalDivider()
}

@Composable
private fun MarkdownList(
    items: List<RenderedItem>,
    actions: MessageActions,
) {
    val tokens = LocalHermesDesignTokens.current
    val spacing = tokens.spacing
    Column(
        modifier = Modifier.padding(start = spacing.m),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        items.forEach { item ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                Text(text = item.marker)
                MarkdownListItemText(spans = item.spans, actions = actions, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MarkdownListItemText(
    spans: List<MarkdownSpan>,
    actions: MessageActions,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalHermesDesignTokens.current
    val math = rememberInlineMath(spans, LocalTextStyle.current, LocalContentColor.current)
    Text(
        text =
            spans.toAnnotatedString(
                linkColor = MaterialTheme.colorScheme.primary,
                onOpenLink = actions.openLink,
                codeBackground = tokens.componentColors.inset,
                math = math,
            ),
        modifier = modifier,
        inlineContent = math?.contents.orEmpty(),
    )
}

private data class RenderedItem(
    val marker: String,
    val spans: List<MarkdownSpan>,
)

private const val BULLET_MARKER = "•"

private val QUOTE_BAR_WIDTH = 3.dp

private const val HEADING_LEVEL_3 = 3

private const val HEADING_LEVEL_4 = 4

internal fun List<MarkdownSpan>.toAnnotatedString(
    linkColor: Color,
    onOpenLink: (String) -> Unit,
    codeBackground: Color?,
    math: InlineMathContent? = null,
): AnnotatedString =
    buildAnnotatedString {
        this@toAnnotatedString.forEachIndexed { index, span ->
            val mathId = math?.idFor(index)
            if (mathId != null) {
                appendInlineContent(mathId, alternateText = (span.element as InlineElement.Math).expression)
                return@forEachIndexed
            }
            val start = length
            append(span.text)
            val end = length
            if (start == end) return@forEachIndexed
            if (span.bold) addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
            if (span.italic) addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, end)
            if (span.code) {
                addStyle(
                    SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground ?: Color.Unspecified),
                    start,
                    end,
                )
            }
            span.link?.let { destination ->
                addLink(
                    LinkAnnotation.Url(
                        url = destination,
                        styles =
                            TextLinkStyles(
                                style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
                            ),
                        linkInteractionListener = { onOpenLink(destination) },
                    ),
                    start = start,
                    end = end,
                )
            }
        }
    }
