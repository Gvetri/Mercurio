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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
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
    modifier: Modifier = Modifier,
    onExpand: (() -> Unit)? = null,
) {
    val tokens = LocalHermesDesignTokens.current
    val naturalWidths = rememberTableColumnWidths(block)
    val scrollState = rememberScrollState()
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val viewportWidth = maxWidth
        val gridWidth = naturalWidths.total()
        val widths = fillViewport(naturalWidths, viewportWidth)
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
                    val grid = TableGrid(widths = widths, alignments = block.alignments, scrollState = scrollState)
                    TableGridRows(block = block, grid = grid, actions = actions)
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
                .heightIn(min = TABLE_HEAD_HEIGHT)
                .padding(start = tokens.spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = tableTitle(columnCount).uppercase(Locale.getDefault()),
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = 0.12.em,
            color = tokens.colorScheme.onSurfaceVariant,
            maxLines = 1,
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
private fun TableGridRows(
    block: MarkdownBlock.Table,
    grid: TableGrid,
    actions: MessageActions,
) {
    Column {
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
    val spans = remember(spec.text) { parseInline(spec.text) }
    val annotated =
        remember(spans, linkColor, actions) {
            spans.toAnnotatedString(linkColor = linkColor, onOpenLink = actions.openLink, codeBackground = null)
        }
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
                .padding(horizontal = TABLE_CELL_HORIZONTAL_PADDING, vertical = TABLE_CELL_VERTICAL_PADDING)
                .semantics {
                    collectionItemInfo = CollectionItemInfo(spec.rowIndex, 1, spec.columnIndex, 1)
                },
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

internal const val TABLE_EXPAND_TAG = "table-expand"

internal const val TABLE_GRID_TAG = "table-grid"

internal val TABLE_HEAD_HEIGHT = 24.dp

internal val TABLE_CELL_HORIZONTAL_PADDING = 8.dp

private val TABLE_CELL_VERTICAL_PADDING = 4.dp
