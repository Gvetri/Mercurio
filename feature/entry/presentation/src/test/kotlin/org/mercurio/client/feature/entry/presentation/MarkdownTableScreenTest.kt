package org.mercurio.client.feature.entry.presentation

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.LinkAnnotation
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The pipe-table block and its full-screen viewer inside the approved Chat frame. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-notnight")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MarkdownTableScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun a_pipe_table_renders_header_and_body_cells_without_the_pipe_syntax() {
        composeTestRule.setContent {
            HermesTheme {
                MarkdownContent(
                    markdown = "| gate | compares |\n| --- | --- |\n| unit | cases |",
                    actions = RecordingActions().asMessageActions(),
                )
            }
        }

        composeTestRule.onNodeWithText("gate").assertIsDisplayed()
        composeTestRule.onNodeWithText("compares").assertIsDisplayed()
        composeTestRule.onNodeWithText("unit").assertIsDisplayed()
        composeTestRule.onNodeWithText("cases").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("|", substring = true).assertCountEquals(0)
        composeTestRule.onAllNodesWithText("---").assertCountEquals(0)
    }

    @Test
    fun a_table_cell_keeps_the_inline_renderer_rules() {
        val recorded = RecordingActions()
        composeTestRule.setContent {
            HermesTheme {
                MarkdownContent(
                    markdown =
                        "| mark | detail |\n| --- | --- |\n" +
                            "| **bold** | `code` and [docs](https://example.com/docs) |",
                    actions = recorded.asMessageActions(),
                )
            }
        }

        composeTestRule.onNodeWithText("bold").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("**", substring = true).assertCountEquals(0)
        val annotated =
            composeTestRule
                .onNodeWithText("code and docs")
                .fetchSemanticsNode()
                .config[SemanticsProperties.Text]
                .single()
        assertEquals(
            listOf("https://example.com/docs"),
            annotated.getLinkAnnotations(0, annotated.length).map { range -> (range.item as LinkAnnotation.Url).url },
        )
    }

    @Test
    fun degenerate_tables_render_without_crashing() {
        composeTestRule.setContent {
            HermesTheme {
                MarkdownContent(
                    markdown =
                        "| only |\n| --- |\n| row |\n\n" +
                            "| empty | body |\n| --- | --- |\n\n" +
                            "| a | b | c |\n| --- | --- | --- |\n| 1 |",
                    actions = RecordingActions().asMessageActions(),
                )
            }
        }

        composeTestRule.onNodeWithText("only").assertIsDisplayed()
        composeTestRule.onNodeWithText("row").assertIsDisplayed()
        composeTestRule.onNodeWithText("empty").assertIsDisplayed()
        composeTestRule.onNodeWithText("1").assertIsDisplayed()
    }

    @Test
    fun the_table_exposes_its_row_and_column_structure() {
        composeTestRule.setContent {
            HermesTheme {
                MarkdownContent(
                    markdown = "| gate | compares |\n| --- | --- |\n| unit | cases |",
                    actions = RecordingActions().asMessageActions(),
                )
            }
        }

        val collectionInfo =
            composeTestRule
                .onNodeWithTag("table-grid")
                .fetchSemanticsNode()
                .config[SemanticsProperties.CollectionInfo]
        assertEquals(2, collectionInfo.rowCount)
        assertEquals(2, collectionInfo.columnCount)
        composeTestRule.onNodeWithText("gate").assert(hasCellPosition(row = 0, column = 0))
        composeTestRule.onNodeWithText("unit").assert(hasCellPosition(row = 1, column = 0))
        composeTestRule.onNodeWithText("cases").assert(hasCellPosition(row = 1, column = 1))
    }

    @Test
    fun a_wide_table_offers_the_full_screen_viewer() {
        composeTestRule.setContent {
            HermesTheme {
                SessionDetailContent(detail = ChatDesignFixtures.pipeTable(), onEvent = {})
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onAllNodesWithTag("table-expand").assertCountEquals(1)
    }

    @Test
    fun a_table_that_fits_the_row_never_offers_the_viewer() {
        composeTestRule.setContent {
            HermesTheme {
                SessionDetailContent(detail = ChatDesignFixtures.narrowTable(), onEvent = {})
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onAllNodesWithTag("table-expand").assertCountEquals(0)
    }

    @Test
    fun expand_opens_the_full_screen_viewer_without_a_composer_and_back_returns() {
        composeTestRule.setContent {
            HermesTheme {
                SessionDetailContent(detail = ChatDesignFixtures.tableExpanded(), onEvent = {})
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("table-expand").performClick()
        composeTestRule.onNodeWithText("Table · 4 columns").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("[ send ]").assertCountEquals(0)
        composeTestRule.onNodeWithText("compose-jvm-tests").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Back to transcript").performClick()
        composeTestRule.onNodeWithText("Table · 4 columns").assertDoesNotExist()
        composeTestRule.onNodeWithText("[ send ]").assertExists()
        composeTestRule.onNodeWithText("Which checks gate the release?").assertExists()
    }

    private fun hasCellPosition(
        row: Int,
        column: Int,
    ): SemanticsMatcher =
        SemanticsMatcher("cell at row $row column $column") { node ->
            node.config.getOrNull(SemanticsProperties.CollectionItemInfo)?.let { info ->
                info.rowIndex == row && info.columnIndex == column
            } == true
        }
}
