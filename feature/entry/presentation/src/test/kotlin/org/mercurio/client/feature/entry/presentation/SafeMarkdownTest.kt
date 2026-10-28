package org.mercurio.client.feature.entry.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The supported untrusted-content contract: Gateway message content is parsed into a
 * closed set of inert blocks and spans, so nothing in the source text can become
 * executable content.
 */
class SafeMarkdownTest {
    @Test
    fun paragraph_emphasis_renders_as_plain_text_without_markers() {
        val blocks = parseSafeMarkdown("Safe **Markdown** text")

        assertEquals("Safe Markdown text", blocks.plainText())
        assertEquals(listOf("Markdown"), blocks.spans().filter { it.bold }.map { it.text })
        assertTrue(blocks.spans().none { it.text.contains("**") })
    }

    @Test
    fun italic_emphasis_renders_as_plain_text_without_markers() {
        val blocks = parseSafeMarkdown("Use *care* when reading")

        assertEquals("Use care when reading", blocks.plainText())
        assertEquals(listOf("care"), blocks.spans().filter { it.italic }.map { it.text })
        assertTrue(blocks.spans().none { it.text.contains("*") })
    }

    @Test
    fun bullet_list_keeps_items_and_drops_the_bullet_markers() {
        val blocks = parseSafeMarkdown("- first item\n- second **item**\n- third item")

        assertTrue(blocks.single() is MarkdownBlock.BulletList)
        val list = blocks.single() as MarkdownBlock.BulletList
        assertEquals(listOf("first item", "second item", "third item"), list.items.map { it.text() })
        assertEquals(listOf("item"), list.items.flatten().filter { it.bold }.map { it.text })
    }

    @Test
    fun numbered_list_renders_the_authored_ordinals() {
        val blocks = parseSafeMarkdown("1. step one\n2. step two")

        assertTrue(blocks.single() is MarkdownBlock.NumberedList)
        val list = blocks.single() as MarkdownBlock.NumberedList
        assertEquals(listOf("1.", "2."), list.items.map { it.marker })
        assertEquals(listOf("step one", "step two"), list.items.map { it.spans.text() })
    }

    @Test
    fun a_numbered_list_never_invents_numbering_the_content_did_not_have() {
        val blocks = parseSafeMarkdown("5. five\n9. nine")

        val list = blocks.single() as MarkdownBlock.NumberedList
        assertEquals(listOf("5.", "9."), list.items.map { it.marker })
    }

    @Test
    fun a_paragraph_between_lists_keeps_its_own_block() {
        val blocks = parseSafeMarkdown("- first item\n\nAfter the list.\n\n1. step one")

        assertEquals(
            listOf("BulletList", "Paragraph", "NumberedList"),
            blocks.map { it::class.simpleName },
        )
        assertEquals("After the list.", blocks.plainText().lines()[1])
    }

    @Test
    fun fenced_code_block_keeps_its_source_literal_and_unparsed() {
        val blocks = parseSafeMarkdown("Before.\n\n```kotlin\nval x = \"**not bold**\"\n```\n\nAfter.")

        assertEquals(listOf("Paragraph", "CodeBlock", "Paragraph"), blocks.map { it::class.simpleName })
        val code = blocks[1] as MarkdownBlock.CodeBlock
        assertEquals("kotlin", code.language)
        assertEquals("val x = \"**not bold**\"", code.code)
    }

    @Test
    fun a_fence_like_line_does_not_close_a_longer_fenced_code_block() {
        val blocks =
            parseSafeMarkdown(
                "````kotlin\n```\n````python\n**not bold** [x](https://example.com)\n````",
            )

        val code = blocks.single() as MarkdownBlock.CodeBlock
        assertEquals("kotlin", code.language)
        assertEquals("```\n````python\n**not bold** [x](https://example.com)", code.code)
    }

    @Test
    fun an_unterminated_fence_keeps_its_lines_as_code_while_a_response_streams() {
        val blocks = parseSafeMarkdown("```\nstreaming line 1\nstreaming line 2")

        assertEquals(
            "streaming line 1\nstreaming line 2",
            (blocks.single() as MarkdownBlock.CodeBlock).code,
        )
    }

    @Test
    fun inline_code_stays_literal() {
        val blocks = parseSafeMarkdown("Run `esbuild --minify` now")

        assertEquals("Run esbuild --minify now", blocks.plainText())
        assertEquals(listOf("esbuild --minify"), blocks.spans().filter { it.code }.map { it.text })
    }

    @Test
    fun embedded_html_and_script_stay_literal_text() {
        val blocks = parseSafeMarkdown("<script>alert(1)</script> <b>bold</b>")

        assertEquals("<script>alert(1)</script> <b>bold</b>", blocks.plainText())
        assertTrue(blocks.spans().all { it.link == null })
    }

    @Test
    fun long_content_is_parsed_without_truncation() {
        val content = (1..500).joinToString(separator = "\n\n") { index -> "Paragraph $index with **bold** text" }

        val blocks = parseSafeMarkdown(content)

        assertEquals(500, blocks.size)
        assertEquals("Paragraph 500 with bold text", blocks.plainText().lines().last())
    }

    @Test
    fun https_link_keeps_its_label_and_its_destination() {
        val blocks = parseSafeMarkdown("See [the release notes](https://example.com/notes?x=1#top) for details.")

        assertEquals("See the release notes for details.", blocks.plainText())
        assertEquals(listOf("https://example.com/notes?x=1#top"), blocks.spans().mapNotNull { it.link })
    }

    @Test
    fun blocked_link_schemes_render_as_inert_label_text() {
        val content =
            """
            [a](javascript:alert(1))
            [b](data:text/html,<script>alert(1)</script>)
            [c](file:///etc/passwd)
            [d](content://com.example/secret)
            [e](http://example.com/insecure)
            [f](mailto:person@example.com)
            [g](https:/example.com/single-slash)
            """.trimIndent()

        val blocks = parseSafeMarkdown(content)
        val text = blocks.plainText()

        assertTrue(blocks.spans().all { it.link == null })
        assertEquals("a b c d e f g", text)
        listOf("javascript:", "data:", "file:", "content:", "http:", "mailto:", "single-slash").forEach { blocked ->
            assertTrue("Rendered text still contains $blocked", !text.contains(blocked))
        }
    }

    @Test
    fun an_image_construct_stays_literal_instead_of_becoming_a_link() {
        val blocks = parseSafeMarkdown("Shot: ![chart](https://example.com/chart.png)")

        assertEquals("Shot: ![chart](https://example.com/chart.png)", blocks.plainText())
        assertTrue(blocks.spans().all { it.link == null })
    }

    @Test
    fun an_image_construct_stays_literal_around_a_link_nested_in_its_label() {
        val content = "![alt [inner](https://example.com)](https://example.com/pixel.png)"
        val blocks = parseSafeMarkdown(content)

        assertEquals(content, blocks.plainText())
        assertTrue("An image construct must stay inert", blocks.spans().all { it.link == null })
    }

    @Test
    fun an_image_construct_stays_literal_around_every_link_nested_in_its_label() {
        val content = "![alt [one](https://a.example) [two](https://b.example)](https://image.example)"
        val blocks = parseSafeMarkdown(content)

        assertEquals(content, blocks.plainText())
        assertTrue("An image construct must stay inert", blocks.spans().all { it.link == null })
    }

    @Test
    fun unpaired_link_syntax_stays_literal_text() {
        val blocks = parseSafeMarkdown("Broken [link(https://example.com) and [label] without a target")

        assertEquals("Broken [link(https://example.com) and [label] without a target", blocks.plainText())
        assertTrue(blocks.spans().all { it.link == null })
    }

    @Test
    fun a_bare_url_stays_inert_text_and_is_never_linkified() {
        val blocks = parseSafeMarkdown("See https://example.com/secure for details")

        assertEquals("See https://example.com/secure for details", blocks.plainText())
        assertTrue(blocks.spans().all { it.link == null })
    }

    @Test
    fun emphasis_renders_the_links_and_emphasis_nested_inside_it() {
        val blocks = parseSafeMarkdown("**see [x](https://example.com/y)** and **a *b* c**")
        val spans = blocks.spans()

        assertEquals("see x and a b c", blocks.plainText())
        assertTrue(spans.none { span -> span.text.any { it in "*[(" } })
        assertEquals(listOf("x"), spans.filter { it.bold && it.link != null }.map { it.text })
        assertEquals(listOf("https://example.com/y"), spans.mapNotNull { it.link })
        assertEquals(listOf("b"), spans.filter { it.bold && it.italic }.map { it.text })
    }

    @Test
    fun a_link_label_keeps_its_own_emphasis_and_a_blocked_label_stays_inert() {
        val blocks = parseSafeMarkdown("[*release*](https://example.com) and [*blocked*](javascript:alert(1))")
        val spans = blocks.spans()

        assertEquals("release and blocked", blocks.plainText())
        assertEquals(listOf("release"), spans.filter { it.italic && it.link != null }.map { it.text })
        assertEquals(listOf("blocked"), spans.filter { it.italic && it.link == null }.map { it.text })
    }

    @Test
    fun adversarial_delimiter_and_bracket_runs_parse_without_failing_or_losing_text() {
        val content = "*a **b [c](d) ".repeat(2_000) + "tail-marker"

        val blocks = parseSafeMarkdown(content)

        assertTrue(blocks.isNotEmpty())
        assertTrue("The parse lost its trailing content", blocks.plainText().endsWith("tail-marker"))
    }

    @Test(timeout = 2_000)
    fun many_unmatched_link_brackets_do_not_make_parsing_quadratic() {
        val content = "[".repeat(500_000)

        val blocks = parseSafeMarkdown(content)

        assertEquals(content, blocks.plainText())
    }

    @Test(timeout = 1_500)
    fun link_candidates_sharing_a_closing_bracket_do_not_rescan_the_destination() {
        val content = "[".repeat(60_000) + "](https://example.com/" + "(".repeat(60_000)

        val blocks = parseSafeMarkdown(content)

        assertEquals(content, blocks.plainText())
    }

    @Test(timeout = 1_500)
    fun many_unclosed_link_destinations_do_not_make_parsing_quadratic() {
        val content = "[](((".repeat(38_000)

        val blocks = parseSafeMarkdown(content)

        assertEquals(content, blocks.plainText())
    }

    @Test
    fun atx_headings_parse_as_heading_blocks_without_their_marker() {
        val blocks = parseSafeMarkdown("# Alpha Heading\n\n## Beta **Heading**")

        assertEquals(listOf("Heading", "Heading"), blocks.map { it::class.simpleName })
        assertEquals("Alpha Heading\nBeta Heading", blocks.plainText())
        assertEquals(listOf(1, 2), blocks.filterIsInstance<MarkdownBlock.Heading>().map { it.level })
        assertEquals(listOf("Heading"), blocks.spans().filter { it.bold }.map { it.text })
    }

    @Test
    fun a_heading_needs_a_space_after_the_hashes() {
        val blocks = parseSafeMarkdown("#tag stays text")

        assertEquals(listOf("Paragraph"), blocks.map { it::class.simpleName })
        assertEquals("#tag stays text", blocks.plainText())
    }

    @Test
    fun a_run_of_seven_or_more_hashes_is_not_a_heading() {
        val blocks = parseSafeMarkdown("####### not a heading")

        assertEquals(listOf("Paragraph"), blocks.map { it::class.simpleName })
        assertEquals("####### not a heading", blocks.plainText())
    }

    @Test
    fun a_blockquote_gathers_its_lines_and_drops_the_quote_markers() {
        val blocks = parseSafeMarkdown("> quoted wisdom\n> continues **here**")

        assertEquals(listOf("Blockquote"), blocks.map { it::class.simpleName })
        assertEquals("quoted wisdom continues here", blocks.plainText())
        assertEquals(listOf("here"), blocks.spans().filter { it.bold }.map { it.text })
    }

    @Test
    fun a_thematic_break_renders_as_a_rule_block() {
        val dashes = parseSafeMarkdown("---")
        val asterisks = parseSafeMarkdown("***")
        val spaced = parseSafeMarkdown("- - -")

        assertEquals(listOf("HorizontalRule"), dashes.map { it::class.simpleName })
        assertEquals(listOf("HorizontalRule"), asterisks.map { it::class.simpleName })
        assertEquals(listOf("HorizontalRule"), spaced.map { it::class.simpleName })
    }

    @Test
    fun a_line_that_is_not_only_rule_characters_stays_a_paragraph() {
        val blocks = parseSafeMarkdown("-- not a rule --")

        assertEquals(listOf("Paragraph"), blocks.map { it::class.simpleName })
        assertEquals("-- not a rule --", blocks.plainText())
    }

    @Test
    fun block_elements_keep_their_authored_order_around_paragraphs() {
        val blocks = parseSafeMarkdown("# Title\n\nIntro text\n\n> a quote\n\n---\n\n- an item")

        assertEquals(
            listOf("Heading", "Paragraph", "Blockquote", "HorizontalRule", "BulletList"),
            blocks.map { it::class.simpleName },
        )
        assertEquals("Title\nIntro text\na quote\n\nan item", blocks.plainText())
    }

    @Test
    fun a_pipe_table_parses_into_a_structured_table_block() {
        val blocks = parseSafeMarkdown("| Name | Value |\n| ---- | ----- |\n| a | 1 |\n| b | 2 |")

        val table = blocks.single() as MarkdownBlock.Table
        assertEquals(listOf("Name", "Value"), table.header)
        assertEquals(listOf(listOf("a", "1"), listOf("b", "2")), table.rows)
    }

    @Test
    fun a_pipe_table_keeps_its_literal_source_text_until_its_renderer_lands() {
        val source = "| Name | Value |\n| ---- | ----- |\n| a | 1 |"

        val blocks = parseSafeMarkdown(source)

        assertEquals(listOf("Table"), blocks.map { it::class.simpleName })
        assertEquals(source.replace("\n", " "), blocks.plainText())
    }

    @Test
    fun a_table_without_a_delimiter_row_stays_literal_text() {
        val source = "| Name | Value |\n| a | 1 |"

        val blocks = parseSafeMarkdown(source)

        assertEquals(listOf("Paragraph"), blocks.map { it::class.simpleName })
        assertEquals(source.replace("\n", " "), blocks.plainText())
    }

    @Test
    fun a_table_whose_header_and_delimiter_disagree_on_columns_stays_literal() {
        val source = "| Name | Value |\n| --- | --- | --- |"

        val blocks = parseSafeMarkdown(source)

        assertEquals(listOf("Paragraph"), blocks.map { it::class.simpleName })
        assertEquals(source.replace("\n", " "), blocks.plainText())
    }

    @Test
    fun a_table_run_containing_a_plain_line_stays_literal_text() {
        val source = "| a | b |\n| --- | --- |\nplain line"

        val blocks = parseSafeMarkdown(source)

        assertEquals(listOf("Paragraph"), blocks.map { it::class.simpleName })
        assertEquals(source.replace("\n", " "), blocks.plainText())
    }

    @Test
    fun a_table_next_to_a_fence_keeps_its_own_block() {
        val blocks = parseSafeMarkdown("| a | b |\n| --- | --- |\n```\n|x|\n```")

        assertEquals(listOf("Table", "CodeBlock"), blocks.map { it::class.simpleName })
        assertEquals("|x|", (blocks[1] as MarkdownBlock.CodeBlock).code)
    }

    @Test
    fun ragged_table_rows_pad_or_trim_to_the_header_width() {
        val blocks = parseSafeMarkdown("| a | b |\n| --- | --- |\n| only |\n| x | y | z |")

        val table = blocks.single() as MarkdownBlock.Table
        assertEquals(listOf(listOf("only", ""), listOf("x", "y")), table.rows)
    }

    @Test
    fun a_well_formed_image_construct_becomes_a_structured_element() {
        val blocks = parseSafeMarkdown("Shot: ![chart](https://example.com/chart.png)")

        val spans = (blocks.single() as MarkdownBlock.Paragraph).spans
        val image = spans.single { it.element != null }
        assertEquals(
            InlineElement.Image(alt = "chart", destination = "https://example.com/chart.png"),
            image.element,
        )
        assertEquals("![chart](https://example.com/chart.png)", image.text)
        assertEquals("Shot: ![chart](https://example.com/chart.png)", blocks.plainText())
        assertTrue(spans.all { it.link == null })
    }

    @Test
    fun a_malformed_image_construct_stays_literal_without_an_element() {
        val content = "Broken ![chart and ![alt](https://example.com"

        val blocks = parseSafeMarkdown(content)

        assertEquals(content, blocks.plainText())
        assertTrue(blocks.spans().all { it.element == null })
    }

    @Test
    fun inline_math_becomes_a_structured_element_and_keeps_its_delimiters() {
        val blocks = parseSafeMarkdown("Energy \$E = mc^2\$ here")

        val spans = (blocks.single() as MarkdownBlock.Paragraph).spans
        val math = spans.single { it.element != null }
        assertEquals(InlineElement.Math(expression = "E = mc^2"), math.element)
        assertEquals("\$E = mc^2\$", math.text)
        assertEquals("Energy \$E = mc^2\$ here", blocks.plainText())
    }

    @Test
    fun display_math_becomes_a_block_with_its_literal_fallback() {
        val blocks = parseSafeMarkdown("\$\$\nE = mc^2\n\$\$")

        val math = blocks.single() as MarkdownBlock.DisplayMath
        assertEquals("E = mc^2", math.expression)
        assertEquals("\$\$ E = mc^2 \$\$", blocks.plainText())
    }

    @Test
    fun empty_or_unclosed_display_math_stays_literal_text() {
        val empty = parseSafeMarkdown("\$\$")
        val unclosed = parseSafeMarkdown("\$\$\nx + y")

        assertEquals(listOf("Paragraph"), empty.map { it::class.simpleName })
        assertEquals(listOf("Paragraph"), unclosed.map { it::class.simpleName })
        assertEquals("\$\$", empty.plainText())
        assertEquals("\$\$ x + y", unclosed.plainText())
    }

    @Test
    fun unpaired_dollar_delimiters_stay_literal_text() {
        val content = "Costs \$5 today and \$ x \$ and \$x"

        val blocks = parseSafeMarkdown(content)

        assertEquals(content, blocks.plainText())
        assertTrue(blocks.spans().all { it.element == null })
    }

    @Test
    fun fence_content_never_becomes_table_image_or_math_markup() {
        val source = "| a | b |\n| --- | --- |\n![chart](https://example.com/chart.png)\n\$E = mc^2\$"

        val blocks = parseSafeMarkdown("```\n$source\n```")

        assertEquals(listOf("CodeBlock"), blocks.map { it::class.simpleName })
        assertEquals(source, (blocks.single() as MarkdownBlock.CodeBlock).code)
    }

    @Test
    fun image_and_math_markup_adjacent_to_a_fence_stay_literal_paragraph_content() {
        val source = "Shot: ![chart](https://example.com/chart.png)\n```kotlin\nval x = 1\n```\nValue \$y\$ here"

        val blocks = parseSafeMarkdown(source)

        assertEquals(listOf("Paragraph", "CodeBlock", "Paragraph"), blocks.map { it::class.simpleName })
        assertEquals("val x = 1", (blocks[1] as MarkdownBlock.CodeBlock).code)
        assertEquals(
            listOf(
                InlineElement.Image(alt = "chart", destination = "https://example.com/chart.png"),
                InlineElement.Math(expression = "y"),
            ),
            blocks.spans().mapNotNull { it.element },
        )
    }

    @Test
    fun image_and_math_markup_inside_an_unterminated_fence_stay_code() {
        val source = "![chart](https://example.com/chart.png)\n\$y\$"

        val blocks = parseSafeMarkdown("```\n$source")

        assertEquals(listOf("CodeBlock"), blocks.map { it::class.simpleName })
        assertEquals(source, (blocks.single() as MarkdownBlock.CodeBlock).code)
    }

    @Test
    fun display_math_inside_a_paragraph_run_stays_literal_text() {
        val content = "See \$\$E = mc^2\$\$ above"

        val blocks = parseSafeMarkdown(content)

        assertEquals(listOf("Paragraph"), blocks.map { it::class.simpleName })
        assertEquals(content, blocks.plainText())
        assertTrue(blocks.spans().all { it.element == null })
    }

    @Test
    fun inline_code_keeps_pipe_image_and_math_markup_literal() {
        val blocks = parseSafeMarkdown("`| a | b |` `![img](u)` `\$x\$`")

        assertEquals(
            listOf("| a | b |", "![img](u)", "\$x\$"),
            blocks.spans().filter { it.code }.map { it.text },
        )
        assertEquals(listOf("Paragraph"), blocks.map { it::class.simpleName })
        assertTrue(blocks.spans().all { it.element == null })
    }

    @Test(timeout = 1_500)
    fun many_unpaired_dollar_delimiters_do_not_make_parsing_quadratic() {
        val content = "\$a ".repeat(99_999) + "\$a"

        val blocks = parseSafeMarkdown(content)

        assertEquals(content, blocks.plainText())
    }
}

private fun List<MarkdownSpan>.text(): String = joinToString(separator = "") { it.text }

private fun List<MarkdownBlock>.spans(): List<MarkdownSpan> =
    flatMap { block ->
        when (block) {
            is MarkdownBlock.Paragraph -> block.spans
            is MarkdownBlock.Heading -> block.spans
            is MarkdownBlock.Blockquote -> block.spans
            is MarkdownBlock.BulletList -> block.items.flatten()
            is MarkdownBlock.NumberedList -> block.items.flatMap { it.spans }
            is MarkdownBlock.CodeBlock -> emptyList()
            is MarkdownBlock.HorizontalRule -> emptyList()
            is MarkdownBlock.Table -> block.spans
            is MarkdownBlock.DisplayMath -> block.spans
        }
    }

private fun List<MarkdownBlock>.plainText(): String =
    joinToString(separator = "\n") { block ->
        when (block) {
            is MarkdownBlock.Paragraph -> block.spans.joinToString(separator = "") { it.text }
            is MarkdownBlock.Heading -> block.spans.joinToString(separator = "") { it.text }
            is MarkdownBlock.Blockquote -> block.spans.joinToString(separator = "") { it.text }
            is MarkdownBlock.BulletList -> block.items.joinToString(separator = "\n") { item -> item.text() }
            is MarkdownBlock.NumberedList -> block.items.joinToString(separator = "\n") { item -> item.spans.text() }
            is MarkdownBlock.CodeBlock -> block.code
            is MarkdownBlock.HorizontalRule -> ""
            is MarkdownBlock.Table -> block.spans.joinToString(separator = "") { it.text }
            is MarkdownBlock.DisplayMath -> block.spans.joinToString(separator = "") { it.text }
        }
    }
