package org.hermesnative.client.feature.entry.presentation

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Safe rendering of untrusted Gateway content: the supported Markdown subset renders
 * natively, code blocks stay readable and scrollable, and every action that leaves the
 * message surface is invoked only by an explicit user selection.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-notnight")
class MarkdownContentTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun emphasis_and_lists_render_as_native_text_without_markers() {
        composeTestRule.setContent {
            HermesTheme {
                MarkdownContent(
                    markdown = "Safe **Markdown** text\n\n- first item\n- second item",
                    actions = RecordingActions().asMessageActions(),
                )
            }
        }

        composeTestRule.onNodeWithText("Safe Markdown text").assertIsDisplayed()
        composeTestRule.onNodeWithText("first item").assertIsDisplayed()
        composeTestRule.onNodeWithText("second item").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("**", substring = true).assertCountEquals(0)
    }

    @Test
    fun a_code_block_stays_readable_in_a_horizontally_scrollable_region() {
        val code = "val total = \"a\" + \"b\" ".repeat(60)
        composeTestRule.setContent {
            HermesTheme {
                MarkdownContent(markdown = "```kotlin\n$code\n```", actions = RecordingActions().asMessageActions())
            }
        }

        composeTestRule.onNodeWithText("kotlin").assertIsDisplayed()
        val codeNode = composeTestRule.onNodeWithText(code)
        codeNode.assertIsDisplayed()
        val horizontalRange =
            codeNode.fetchSemanticsNode().config[SemanticsProperties.HorizontalScrollAxisRange]
        assertTrue(
            "Expected code content wider than the viewport, so the block scrolls instead of wrapping",
            horizontalRange.maxValue() > 0f,
        )
    }

    @Test
    fun copying_a_code_block_requires_the_explicit_copy_action() {
        val recorded = RecordingActions()
        composeTestRule.setContent {
            HermesTheme {
                MarkdownContent(markdown = "```\nval x = 1\n```", actions = recorded.asMessageActions())
            }
        }

        assertTrue("Nothing may be copied before the user acts", recorded.copied.isEmpty())
        composeTestRule.onNodeWithText("Copy code").performClick()
        assertEquals(listOf("val x = 1"), recorded.copied)
    }

    @Test
    fun a_numbered_list_renders_the_authored_ordinals_instead_of_renumbering() {
        composeTestRule.setContent {
            HermesTheme {
                MarkdownContent(
                    markdown = "5. five\n9. nine",
                    actions = RecordingActions().asMessageActions(),
                )
            }
        }

        composeTestRule.onNodeWithText("5.").assertIsDisplayed()
        composeTestRule.onNodeWithText("9.").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("1.").assertCountEquals(0)
    }

    @Test
    fun a_rendered_link_exposes_its_label_and_one_allowed_destination_annotation() {
        composeTestRule.setContent {
            HermesTheme {
                MarkdownContent(
                    markdown = "[secure link](https://example.com/secure)",
                    actions = RecordingActions().asMessageActions(),
                )
            }
        }

        val annotated =
            composeTestRule
                .onNodeWithText("secure link")
                .fetchSemanticsNode()
                .config[SemanticsProperties.Text]
                .single()

        assertEquals("secure link", annotated.text)
        assertEquals(
            listOf("https://example.com/secure"),
            annotated.getLinkAnnotations(0, annotated.length).map { range -> (range.item as LinkAnnotation.Url).url },
        )
    }

    @Test
    fun a_selected_https_link_opens_through_the_link_action() {
        val recorded = RecordingActions()
        composeTestRule.setContent {
            HermesTheme {
                MarkdownContent(
                    markdown = "[secure link](https://example.com/secure)",
                    actions = recorded.asMessageActions(),
                )
            }
        }

        assertTrue("Nothing may open before the user acts", recorded.openedLinks.isEmpty())
        composeTestRule.onNodeWithText("secure link").performClick()
        assertEquals(listOf("https://example.com/secure"), recorded.openedLinks)
    }

    @Test
    fun a_selected_blocked_scheme_link_is_inert() {
        val recorded = RecordingActions()
        composeTestRule.setContent {
            HermesTheme {
                MarkdownContent(
                    markdown = "[unsafe](javascript:alert(1))",
                    actions = recorded.asMessageActions(),
                )
            }
        }

        val label = composeTestRule.onNodeWithText("unsafe")
        label.assertIsDisplayed()
        label.performClick()
        assertTrue("A blocked scheme must never open", recorded.openedLinks.isEmpty())
        composeTestRule.onAllNodesWithText("javascript", substring = true).assertCountEquals(0)
        val annotated =
            label.fetchSemanticsNode().config[SemanticsProperties.Text].single()
        assertEquals(
            "A blocked label carries no link annotation",
            emptyList<Any>(),
            annotated.getLinkAnnotations(0, annotated.length),
        )
    }

    @Test
    fun embedded_html_stays_literal_text_and_offers_no_executable_action() {
        composeTestRule.setContent {
            HermesTheme {
                MarkdownContent(
                    markdown = "<script>alert(1)</script>",
                    actions = RecordingActions().asMessageActions(),
                )
            }
        }

        composeTestRule.onNodeWithText("<script>alert(1)</script>").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Copy code").assertCountEquals(0)
    }

    @Test
    fun long_content_is_fully_rendered_instead_of_truncated() {
        val content = (1..400).joinToString(separator = "\n\n") { index -> "Paragraph $index" }
        composeTestRule.setContent {
            HermesTheme { MarkdownContent(markdown = content, actions = RecordingActions().asMessageActions()) }
        }

        composeTestRule.onNodeWithText("Paragraph 1").assertExists()
        composeTestRule.onNodeWithText("Paragraph 400").assertExists()
    }

    @Test
    fun a_labeled_code_block_colors_its_tokens_by_kind() {
        val code = "fun greet() {\n  // plan\n  val total = \"x\" + 2\n}"
        composeTestRule.setContent {
            HermesTheme {
                MarkdownContent(markdown = "```kotlin\n$code\n```", actions = RecordingActions().asMessageActions())
            }
        }

        val annotated =
            composeTestRule.onNodeWithText(code).fetchSemanticsNode().config[SemanticsProperties.Text].single()
        val syntax = HermesLightTokens.syntaxColors
        assertEquals(code, annotated.text)
        assertEquals(syntax.function, annotated.styleColorAt(code.indexOf("greet(")))
        assertEquals(syntax.comment, annotated.styleColorAt(code.indexOf("//")))
        assertEquals(syntax.keyword, annotated.styleColorAt(code.indexOf("val")))
        assertEquals(syntax.string, annotated.styleColorAt(code.indexOf("\"x\"")))
        assertEquals(syntax.number, annotated.styleColorAt(code.indexOf("2")))
        assertEquals(5, annotated.spanStyles.map { style -> style.item.color }.distinct().size)
    }

    @Test
    fun the_language_label_decides_which_words_are_keywords() {
        val pythonCode = "def run(): pass"
        val kotlinCode = "val run = 1"
        composeTestRule.setContent {
            HermesTheme {
                MarkdownContent(
                    markdown = "```python\n$pythonCode\n```\n\n```kotlin\n$kotlinCode\n```",
                    actions = RecordingActions().asMessageActions(),
                )
            }
        }

        val python =
            composeTestRule.onNodeWithText(pythonCode).fetchSemanticsNode().config[SemanticsProperties.Text].single()
        val kotlin =
            composeTestRule.onNodeWithText(kotlinCode).fetchSemanticsNode().config[SemanticsProperties.Text].single()
        assertEquals(HermesLightTokens.syntaxColors.keyword, python.styleColorAt(0))
        assertEquals(HermesLightTokens.syntaxColors.keyword, kotlin.styleColorAt(0))
        assertNull("run is not a Kotlin keyword in this grammar", kotlin.styleColorAt(kotlinCode.indexOf("run")))
    }

    @Test
    fun an_unlabeled_code_block_renders_as_plain_text_without_token_styles() {
        val code = "val x = 1"
        composeTestRule.setContent {
            HermesTheme {
                MarkdownContent(markdown = "```\n$code\n```", actions = RecordingActions().asMessageActions())
            }
        }

        val annotated =
            composeTestRule.onNodeWithText(code).fetchSemanticsNode().config[SemanticsProperties.Text].single()
        assertEquals(code, annotated.text)
        assertTrue(annotated.spanStyles.isEmpty())
    }

    @Test
    fun an_unsupported_language_label_renders_as_plain_text_without_token_styles() {
        val code = "val x = 1"
        composeTestRule.setContent {
            HermesTheme {
                MarkdownContent(
                    markdown = "```brainfuck\n$code\n```",
                    actions = RecordingActions().asMessageActions(),
                )
            }
        }

        val annotated =
            composeTestRule.onNodeWithText(code).fetchSemanticsNode().config[SemanticsProperties.Text].single()
        assertEquals(code, annotated.text)
        assertTrue(annotated.spanStyles.isEmpty())
    }

    private fun AnnotatedString.styleColorAt(offset: Int): Color? =
        spanStyles.find { style -> style.start <= offset && offset < style.end }?.item?.color

    @Test
    fun a_heading_renders_styled_text_without_its_marker() {
        composeTestRule.setContent {
            HermesTheme {
                MarkdownContent(
                    markdown = "# Release Notes\n\nbody text",
                    actions = RecordingActions().asMessageActions(),
                )
            }
        }

        val heading = composeTestRule.onNodeWithText("Release Notes")
        heading.assertIsDisplayed()
        composeTestRule.onAllNodesWithText("#", substring = true).assertCountEquals(0)
        assertTrue(
            "The heading must expose heading semantics",
            composeTestRule
                .onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
                .fetchSemanticsNode()
                .config[SemanticsProperties.Text]
                .single()
                .text == "Release Notes",
        )
    }

    @Test
    fun a_blockquote_renders_its_text_without_the_quote_marker() {
        composeTestRule.setContent {
            HermesTheme {
                MarkdownContent(
                    markdown = "> quoted wisdom\n\nbody text",
                    actions = RecordingActions().asMessageActions(),
                )
            }
        }

        composeTestRule.onNodeWithText("quoted wisdom").assertIsDisplayed()
        composeTestRule.onAllNodesWithText(">", substring = true).assertCountEquals(0)
        val quoteBounds = composeTestRule.onNodeWithText("quoted wisdom").fetchSemanticsNode().boundsInRoot
        val bodyBounds = composeTestRule.onNodeWithText("body text").fetchSemanticsNode().boundsInRoot
        assertTrue(
            "The quoted text must be indented beside its bar, unlike the paragraph",
            quoteBounds.left > bodyBounds.left,
        )
    }

    @Test
    fun a_thematic_break_renders_without_literal_rule_text() {
        composeTestRule.setContent {
            HermesTheme {
                MarkdownContent(
                    markdown = "Above the rule\n\n---\n\nBelow the rule",
                    actions = RecordingActions().asMessageActions(),
                )
            }
        }

        composeTestRule.onNodeWithText("Above the rule").assertIsDisplayed()
        composeTestRule.onNodeWithText("Below the rule").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("---").assertCountEquals(0)
    }

    @Test
    fun list_items_render_indented_instead_of_flush_with_paragraphs() {
        composeTestRule.setContent {
            HermesTheme {
                MarkdownContent(
                    markdown = "Intro paragraph\n\n- first item",
                    actions = RecordingActions().asMessageActions(),
                )
            }
        }

        composeTestRule.onNodeWithText("first item").assertIsDisplayed()
        val listBounds = composeTestRule.onNodeWithText("first item").fetchSemanticsNode().boundsInRoot
        val paragraphBounds = composeTestRule.onNodeWithText("Intro paragraph").fetchSemanticsNode().boundsInRoot
        assertTrue(
            "The list content must start right of the paragraph text",
            listBounds.left > paragraphBounds.left,
        )
    }
}

/** A deterministic recording double for the message actions; no mocking framework is used. */
internal class RecordingActions {
    val openedLinks = mutableListOf<String>()
    val copied = mutableListOf<String>()
    val shared = mutableListOf<String>()

    fun asMessageActions(): MessageActions =
        MessageActions(
            openLink = { destination -> openedLinks += destination },
            copy = { text -> copied += text },
            share = { text -> shared += text },
        )
}
