package org.mercurio.client.feature.entry.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pipe-table model: GFM syntax, its alignment row, and the shapes a Message can carry. */
class MarkdownTableParsingTest {
    private fun tables(s: String) = parseSafeMarkdown(s).filterIsInstance<MarkdownBlock.Table>()

    @Test
    fun a_pipe_table_keeps_its_header_body_and_column_alignment() {
        val block =
            tables(
                "| gate | compares | blocks merge |\n" +
                    "| :--- | :---: | ---: |\n" +
                    "| unit | cases | yes |",
            ).single()

        assertEquals(listOf("gate", "compares", "blocks merge"), block.header)
        assertEquals(listOf(listOf("unit", "cases", "yes")), block.rows)
        assertEquals(listOf(TableAlignment.Start, TableAlignment.Center, TableAlignment.End), block.alignments)
    }

    @Test
    fun a_separator_without_a_marker_aligns_its_column_start() {
        val block = tables("| a | b |\n| --- | --- |\n| 1 | 2 |").single()

        assertEquals(listOf(TableAlignment.Start, TableAlignment.Start), block.alignments)
    }

    @Test
    fun an_escaped_pipe_stays_inside_its_cell() {
        val block = tables("| left | right |\n| --- | --- |\n| a \\| b | c |").single()

        assertEquals(listOf("a | b", "c"), block.rows.single())
    }

    @Test
    fun a_boundary_pipe_is_optional_on_every_row() {
        val block = tables("a | b\n--- | ---\n1 | 2").single()

        assertEquals(listOf("a", "b"), block.header)
        assertEquals(listOf(listOf("1", "2")), block.rows)
    }

    @Test
    fun a_ragged_row_pads_to_the_header_width_and_never_widens_it() {
        val block = tables("| a | b | c |\n| --- | --- | --- |\n| 1 |\n| 1 | 2 | 3 | 4 |").single()

        assertEquals(listOf(listOf("1", "", ""), listOf("1", "2", "3")), block.rows)
    }

    @Test
    fun a_table_without_a_body_still_parses() {
        val block = tables("| a | b |\n| --- | --- |").single()

        assertEquals(listOf("a", "b"), block.header)
        assertTrue(block.rows.isEmpty())
    }

    @Test
    fun a_single_column_table_parses() {
        val block = tables("| only |\n| --- |\n| row |").single()

        assertEquals(listOf("only"), block.header)
        assertEquals(listOf(listOf("row")), block.rows)
    }

    @Test
    fun a_separator_whose_width_differs_from_the_header_is_not_a_table() {
        assertTrue(tables("| a | b |\n| --- | --- | --- |\n| 1 | 2 |").isEmpty())
    }

    @Test
    fun a_lone_pipe_line_stays_a_paragraph() {
        assertTrue(tables("a | b").isEmpty())
    }
}
