package org.mercurio.client.feature.entry.presentation

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
internal fun rememberTableColumnWidths(block: MarkdownBlock.Table): List<Dp> {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val headerStyle = MaterialTheme.typography.labelSmall
    val cellStyle = MaterialTheme.typography.bodySmall
    return remember(block, measurer, density, headerStyle, cellStyle) {
        List(block.header.size) { column ->
            val headerWidth = measurer.measureCell(block.header[column], headerStyle)
            val bodyWidth = block.rows.maxOfOrNull { row -> measurer.measureCell(row[column], cellStyle) } ?: 0
            with(density) {
                maxOf(headerWidth, bodyWidth).toDp() + TABLE_CELL_HORIZONTAL_PADDING * 2
            }
        }
    }
}

private fun TextMeasurer.measureCell(
    text: String,
    style: TextStyle,
): Int {
    val annotated =
        parseInline(text).toAnnotatedString(
            linkColor = Color.Unspecified,
            onOpenLink = {},
            codeBackground = null,
        )
    return measure(text = annotated, style = style, maxLines = 1, softWrap = false).size.width
}

internal fun List<Dp>.total(): Dp = fold(0.dp) { total, width -> total + width }

internal fun fillViewport(
    naturalWidths: List<Dp>,
    viewportWidth: Dp,
): List<Dp> {
    val naturalTotal = naturalWidths.total()
    if (naturalTotal >= viewportWidth || naturalWidths.isEmpty()) return naturalWidths
    val extra = (viewportWidth - naturalTotal) / naturalWidths.size
    return naturalWidths.map { width -> width + extra }
}

internal fun MarkdownBlock.Table.collectionInfo(): CollectionInfo {
    val rowCount = rows.size + 1
    return CollectionInfo(rowCount = rowCount, columnCount = header.size)
}

internal fun tableTitle(columnCount: Int): String =
    if (columnCount == 1) {
        "Table · 1 column"
    } else {
        "Table · $columnCount columns"
    }

internal fun TableAlignment.textAlign(): TextAlign =
    when (this) {
        TableAlignment.Start -> TextAlign.Start
        TableAlignment.Center -> TextAlign.Center
        TableAlignment.End -> TextAlign.End
    }

internal fun DrawScope.drawStickyColumnEdge(color: Color) {
    val stroke = 1.dp.toPx()
    drawLine(
        color = color,
        start = Offset(size.width - stroke / 2, 0f),
        end = Offset(size.width - stroke / 2, size.height),
        strokeWidth = stroke,
    )
}
