package org.mercurio.client.feature.entry.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.noties.jlatexmath.JLatexMathAndroid

/**
 * Math in the Message body: a formula that parses is laid out by the native engine, and one that
 * does not keeps its exact literal source. Nothing is ever blank and nothing is ever truncated.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-notnight")
class MarkdownMathTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Before
    fun setUp() {
        JLatexMathAndroid.init(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun inline_math_renders_as_a_formula_instead_of_its_dollar_source() {
        composeTestRule.setContent {
            HermesTheme {
                MarkdownContent(
                    markdown = "Energy \$E = mc^2\$ is conserved",
                    actions = RecordingActions().asMessageActions(),
                    onTableExpand = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Energy", substring = true).assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(MARKDOWN_INLINE_MATH_TAG).assertCountEquals(1)
        composeTestRule.onAllNodesWithText("\$", substring = true).assertCountEquals(0)
    }

    @Test
    fun an_unparseable_inline_expression_keeps_its_exact_literal_source() {
        val source = "\$\\undefinedcmd{x}\$"
        composeTestRule.setContent {
            HermesTheme {
                MarkdownContent(
                    markdown = "Broken $source stays",
                    actions = RecordingActions().asMessageActions(),
                    onTableExpand = {},
                )
            }
        }

        composeTestRule.onNodeWithText(source, substring = true).assertIsDisplayed()
    }

    @Test
    fun display_math_renders_as_a_formula_without_its_delimiters() {
        composeTestRule.setContent {
            HermesTheme {
                MarkdownContent(
                    markdown = "\$\$\nP(A | B) = \\frac{P(B | A) P(A)}{P(B)}\n\$\$",
                    actions = RecordingActions().asMessageActions(),
                    onTableExpand = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(MARKDOWN_MATH_TAG).assertIsDisplayed()
        composeTestRule.onAllNodesWithText("\$\$", substring = true).assertCountEquals(0)
    }

    @Test
    fun an_unparseable_display_expression_keeps_its_exact_literal_source() {
        composeTestRule.setContent {
            HermesTheme {
                MarkdownContent(
                    markdown = "\$\$\n\\frac{a}{b}\n= \\left(\n\$\$",
                    actions = RecordingActions().asMessageActions(),
                    onTableExpand = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(MARKDOWN_MATH_LITERAL_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("\\frac{a}{b}\n= \\left(", substring = true).assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(MARKDOWN_MATH_TAG).assertCountEquals(0)
    }

    @Test
    fun math_inside_a_list_item_renders_in_the_existing_inline_path() {
        composeTestRule.setContent {
            HermesTheme {
                MarkdownContent(
                    markdown = "- the slope is \$m = 2\$ here",
                    actions = RecordingActions().asMessageActions(),
                    onTableExpand = {},
                )
            }
        }

        composeTestRule.onNodeWithText("the slope is", substring = true).assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(MARKDOWN_INLINE_MATH_TAG).assertCountEquals(1)
        composeTestRule.onAllNodesWithText("\$", substring = true).assertCountEquals(0)
    }

    @Test
    fun the_formula_follows_the_message_font_scale() {
        var base = Density(1f)
        composeTestRule.setContent {
            HermesTheme {
                base = LocalDensity.current
                Column {
                    MarkdownContent(
                        markdown = "\$\$\nx^2\n\$\$",
                        actions = RecordingActions().asMessageActions(),
                        onTableExpand = {},
                    )
                    CompositionLocalProvider(LocalDensity provides Density(base.density, 2f)) {
                        MarkdownContent(
                            markdown = "\$\$\nx^2\n\$\$",
                            actions = RecordingActions().asMessageActions(),
                            onTableExpand = {},
                        )
                    }
                }
            }
        }

        val heights =
            composeTestRule
                .onAllNodesWithTag(MARKDOWN_MATH_TAG)
                .fetchSemanticsNodes()
                .map { node -> node.size.height }
        assertEquals("Both formulas must be on screen", 2, heights.size)
        assertTrue(
            "The formula must grow with the Message font scale, not stay at one size",
            heights[1] > heights[0] * FONT_SCALE_GROWTH_FLOOR,
        )
    }

    @Test
    fun a_message_with_inline_display_and_refused_math_renders_each_part_once() {
        composeTestRule.setContent {
            HermesTheme {
                MarkdownContent(
                    markdown =
                        "Inline \$E = mc^2\$ here.\n\n" +
                            "\$\$\nP(A \\mid B) = \\frac{P(B \\mid A) P(A)}{P(B)}\n\$\$\n\n" +
                            "Refused:\n\n\$\$\n\\left(\n\$\$",
                    actions = RecordingActions().asMessageActions(),
                    onTableExpand = {},
                )
            }
        }

        composeTestRule.onAllNodesWithTag(MARKDOWN_MATH_TAG).assertCountEquals(1)
        composeTestRule.onAllNodesWithTag(MARKDOWN_MATH_LITERAL_TAG).assertCountEquals(1)
        composeTestRule.onAllNodesWithTag(MARKDOWN_INLINE_MATH_TAG).assertCountEquals(1)
        composeTestRule.onNodeWithText("Inline E = mc^2 here.", substring = true).assertIsDisplayed()
        composeTestRule.onAllNodesWithText("\$\$", substring = true).assertCountEquals(0)
        composeTestRule
            .onNodeWithContentDescription("P(A \\mid B) = \\frac{P(B \\mid A) P(A)}{P(B)}")
            .assertExists()
    }

    @Test
    fun math_inside_a_table_cell_renders_in_the_inline_path_too() {
        composeTestRule.setContent {
            HermesTheme {
                MarkdownContent(
                    markdown = "| gate | value |\n| --- | --- |\n| slope | \$m = 2\$ |",
                    actions = RecordingActions().asMessageActions(),
                    onTableExpand = {},
                )
            }
        }

        composeTestRule.onNodeWithText("slope", substring = true).assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(MARKDOWN_INLINE_MATH_TAG).assertCountEquals(1)
        composeTestRule.onAllNodesWithText("\$", substring = true).assertCountEquals(0)
    }

    private companion object {
        private const val FONT_SCALE_GROWTH_FLOOR = 1.5f
    }
}
