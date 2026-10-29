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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.unit.dp

/**
 * Renders the supported Markdown subset of untrusted Gateway content as native Compose
 * text: headings, blockquotes, thematic breaks, paragraphs, lists, emphasis, HTTPS links,
 * and fenced code blocks. The content is never interpreted as markup beyond that subset,
 * so no HTML, script, or other executable content can run, and no WebView is involved.
 * Pipe tables and `$$…$$` display math parse into their own blocks but still render as
 * their literal source text, until their dedicated renderers land.
 *
 * Link destinations are carried as annotations built from [allowedExternalLinkDestination];
 * a blocked destination is rendered as its inert label and cannot be selected.
 */
@Composable
internal fun MarkdownContent(
    markdown: String,
    actions: MessageActions,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalHermesDesignTokens.current.spacing
    val blocks = remember(markdown) { parseSafeMarkdown(markdown) }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.s),
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
                is MarkdownBlock.Table -> MarkdownParagraph(spans = block.spans, actions = actions)
                is MarkdownBlock.DisplayMath -> MarkdownParagraph(spans = block.spans, actions = actions)
            }
        }
    }
}

@Composable
private fun MarkdownParagraph(
    spans: List<MarkdownSpan>,
    actions: MessageActions,
) {
    Text(
        text = spans.toAnnotatedString(linkColor = MaterialTheme.colorScheme.primary, onOpenLink = actions.openLink),
    )
}

@Composable
private fun MarkdownHeading(
    heading: MarkdownBlock.Heading,
    actions: MessageActions,
) {
    Text(
        text =
            heading.spans.toAnnotatedString(
                linkColor = MaterialTheme.colorScheme.primary,
                onOpenLink = actions.openLink,
            ),
        style = headingStyle(heading.level),
        modifier = Modifier.semantics { heading() },
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
    val spacing = LocalHermesDesignTokens.current.spacing
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
                ),
            modifier = Modifier.weight(1f),
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
    val spacing = LocalHermesDesignTokens.current.spacing
    val linkColor = MaterialTheme.colorScheme.primary
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
                Text(
                    text = item.spans.toAnnotatedString(linkColor = linkColor, onOpenLink = actions.openLink),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private data class RenderedItem(
    val marker: String,
    val spans: List<MarkdownSpan>,
)

private const val BULLET_MARKER = "•"

private val QUOTE_BAR_WIDTH = 3.dp

private const val HEADING_LEVEL_3 = 3

private const val HEADING_LEVEL_4 = 4

private fun List<MarkdownSpan>.toAnnotatedString(
    linkColor: Color,
    onOpenLink: (String) -> Unit,
): AnnotatedString =
    buildAnnotatedString {
        this@toAnnotatedString.forEach { span ->
            val start = length
            append(span.text)
            val end = length
            if (start == end) return@forEach
            if (span.bold) addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
            if (span.italic) addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, end)
            if (span.code) addStyle(SpanStyle(fontFamily = FontFamily.Monospace), start, end)
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
