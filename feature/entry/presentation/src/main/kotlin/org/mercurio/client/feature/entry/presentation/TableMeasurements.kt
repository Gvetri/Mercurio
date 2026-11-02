package org.mercurio.client.feature.entry.presentation

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
internal fun rememberTableColumnWidths(block: MarkdownBlock.Table): List<Dp> {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val horizontalPadding = LocalHermesDesignTokens.current.spacing.s
    val headerStyle = MaterialTheme.typography.labelSmall
    val cellStyle = MaterialTheme.typography.bodySmall
    return remember(block, measurer, density, horizontalPadding, headerStyle, cellStyle) {
        List(block.header.size) { column ->
            val headerWidth = measurer.measureCell(block.header[column], headerStyle)
            val bodyWidth = block.rows.maxOfOrNull { row -> measurer.measureCell(row[column], cellStyle) } ?: 0
            with(density) { maxOf(headerWidth, bodyWidth).toDp() + horizontalPadding * 2 }
        }
    }
}

private fun TextMeasurer.measureCell(
    text: String,
    style: TextStyle,
): Int {
    val annotated = tableCellText(text = text, linkColor = Color.Unspecified, onOpenLink = {})
    return measure(text = annotated, style = style, maxLines = 1, softWrap = false).size.width
}

private fun List<Dp>.total(): Dp = fold(0.dp) { total, width -> total + width }

internal fun fillViewport(
    naturalWidths: List<Dp>,
    viewportWidth: Dp,
): List<Dp> {
    val naturalTotal = naturalWidths.total()
    if (naturalTotal >= viewportWidth || naturalWidths.isEmpty()) return naturalWidths
    val extra = (viewportWidth - naturalTotal) / naturalWidths.size
    return naturalWidths.map { width -> width + extra }
}
