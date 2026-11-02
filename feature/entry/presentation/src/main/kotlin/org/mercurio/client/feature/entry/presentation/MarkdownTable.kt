package org.mercurio.client.feature.entry.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.zIndex
import java.util.Locale

/**
 * The pipe table the approved Chat design draws: a bordered card with a caption and one
 * `[ expand ]` control, then the grid. A grid wider than the row scrolls horizontally with its
 * first column held in place; a grid that fits stretches its columns and never offers the viewer.
 * Cell text keeps the inline renderer's rules, so emphasis, code, and allowed links render as they
 * do in a paragraph.
 */
@Composable
internal fun MarkdownTableCard(
    block: MarkdownBlock.Table,
    actions: MessageActions,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalHermesDesignTokens.current
    val naturalWidths = rememberTableColumnWidths(block)
    val scrollState = rememberScrollState()
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val viewportWidth = maxWidth
        val gridWidth = naturalWidths.fold(0.dp) { total, width -> total + width }
        val widths = fillViewport(naturalWidths, viewportWidth)
        val grid = TableGrid(widths = widths, alignments = block.alignments, scrollState = scrollState)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = tokens.colorScheme.surface,
            shape = tokens.shapes.small,
            border = BorderStroke(1.dp, tokens.colorScheme.outlineVariant),
        ) {
            Column {
                TableCaption(
                    columnCount = block.header.size,
                    onExpand = onExpand.takeIf { gridWidth > viewportWidth },
                )
                HorizontalDivider(thickness = 1.dp, color = tokens.colorScheme.outlineVariant)
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(scrollState)
                            .semantics { collectionInfo = block.collectionInfo() }
                            .testTag(TABLE_GRID_TAG),
                ) {
                    TableRowCells(
                        cells = block.header,
                        rowIndex = 0,
                        header = true,
                        grid = grid,
                        actions = actions,
                    )
                    block.rows.forEachIndexed { index, row ->
                        TableRowCells(
                            cells = row,
                            rowIndex = index + 1,
                            header = false,
                            grid = grid,
                            actions = actions,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The full-screen table viewer: the grid fills the surface, the header row stays while the body
 * scrolls vertically, and the first column stays while the columns scroll horizontally.
 */
@Composable
internal fun MarkdownTableViewer(
    block: MarkdownBlock.Table,
    actions: MessageActions,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalHermesDesignTokens.current
    val horizontal = rememberScrollState()
    val vertical = rememberScrollState()
    val naturalWidths = rememberTableColumnWidths(block)
    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxSize()
                .background(tokens.colorScheme.surface)
                .semantics { collectionInfo = block.collectionInfo() }
                .testTag(TABLE_GRID_TAG),
    ) {
        val widths = fillViewport(naturalWidths, maxWidth)
        val grid = TableGrid(widths = widths, alignments = block.alignments, scrollState = horizontal)
        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(horizontal)) {
                TableRowCells(
                    cells = block.header,
                    rowIndex = 0,
                    header = true,
                    grid = grid,
                    actions = actions,
                )
            }
            HorizontalDivider(thickness = 1.dp, color = tokens.colorScheme.outlineVariant)
            Box(modifier = Modifier.weight(1f).fillMaxWidth().horizontalScroll(horizontal)) {
                Column(modifier = Modifier.verticalScroll(vertical)) {
                    block.rows.forEachIndexed { index, row ->
                        TableRowCells(
                            cells = row,
                            rowIndex = index + 1,
                            header = false,
                            grid = grid,
                            actions = actions,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TableCaption(
    columnCount: Int,
    onExpand: (() -> Unit)?,
) {
    val tokens = LocalHermesDesignTokens.current
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = tokens.spacing.xl)
                .padding(start = tokens.spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = tableTitle(columnCount).uppercase(Locale.getDefault()),
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = 0.12.em,
            color = tokens.colorScheme.onSurfaceVariant,
            maxLines = TABLE_CAPTION_LINES,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (onExpand != null) {
            ChatTextControl(
                label = "[ expand ]",
                enabled = true,
                onClick = onExpand,
                modifier = Modifier.testTag(TABLE_EXPAND_TAG),
            )
        }
    }
}

@Composable
private fun TableRowCells(
    cells: List<String>,
    rowIndex: Int,
    header: Boolean,
    grid: TableGrid,
    actions: MessageActions,
) {
    val style = if (header) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall
    Row {
        cells.forEachIndexed { column, cell ->
            TableCell(
                spec = TableCellSpec(text = cell, header = header, rowIndex = rowIndex, columnIndex = column),
                style = style,
                grid = grid,
                actions = actions,
            )
        }
    }
}

@Composable
private fun TableCell(
    spec: TableCellSpec,
    style: TextStyle,
    grid: TableGrid,
    actions: MessageActions,
) {
    val tokens = LocalHermesDesignTokens.current
    val sticky = spec.columnIndex == 0
    val background = if (spec.header) tokens.componentColors.inset else tokens.colorScheme.surface
    val linkColor = MaterialTheme.colorScheme.primary
    val annotated = remember(spec.text, linkColor, actions) { tableCellText(spec.text, linkColor, actions.openLink) }
    Text(
        text = annotated,
        style = style,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
        textAlign = grid.alignments[spec.columnIndex].textAlign(),
        modifier =
            Modifier
                .width(grid.widths[spec.columnIndex])
                .zIndex(if (sticky) 1f else 0f)
                .then(
                    if (sticky) {
                        Modifier.graphicsLayer { translationX = grid.scrollState.value.toFloat() }
                    } else {
                        Modifier
                    },
                )
                .background(background)
                .drawBehind { if (sticky) drawStickyColumnEdge(tokens.colorScheme.outlineVariant) }
                .padding(horizontal = tokens.spacing.s, vertical = tokens.spacing.xs)
                .semantics {
                    if (spec.header) heading()
                    collectionItemInfo = CollectionItemInfo(spec.rowIndex, 1, spec.columnIndex, 1)
                },
    )
}

internal fun tableCellText(
    text: String,
    linkColor: Color,
    onOpenLink: (String) -> Unit,
): AnnotatedString =
    parseInline(text).toAnnotatedString(
        linkColor = linkColor,
        onOpenLink = onOpenLink,
        codeBackground = null,
    )

private fun MarkdownBlock.Table.collectionInfo(): CollectionInfo {
    val rowCount = rows.size + 1
    return CollectionInfo(rowCount = rowCount, columnCount = header.size)
}

internal fun tableTitle(columnCount: Int): String =
    if (columnCount == 1) {
        "Table · 1 column"
    } else {
        "Table · $columnCount columns"
    }

private fun TableAlignment.textAlign(): TextAlign =
    when (this) {
        TableAlignment.Start -> TextAlign.Start
        TableAlignment.Center -> TextAlign.Center
        TableAlignment.End -> TextAlign.End
    }

private fun DrawScope.drawStickyColumnEdge(color: Color) {
    val stroke = 1.dp.toPx()
    drawLine(
        color = color,
        start = Offset(size.width - stroke / 2, 0f),
        end = Offset(size.width - stroke / 2, size.height),
        strokeWidth = stroke,
    )
}

private class TableGrid(
    val widths: List<Dp>,
    val alignments: List<TableAlignment>,
    val scrollState: ScrollState,
)

private class TableCellSpec(
    val text: String,
    val header: Boolean,
    val rowIndex: Int,
    val columnIndex: Int,
)

private const val TABLE_EXPAND_TAG = "table-expand"

private const val TABLE_GRID_TAG = "table-grid"

private const val TABLE_CAPTION_LINES = 2
