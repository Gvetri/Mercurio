package org.mercurio.client.feature.entry.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RoborazziRule
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The markdown rendering contract for untrusted Gateway content: a fenced code block in
 * its horizontally scrollable region, long multi-block content, and a message carrying
 * inline, display and refused math, each captured in an intentional light and dark theme on a
 * fixed phone viewport.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-notnight")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SelectedMarkdownVisualRegressionTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @get:Rule
    val roborazziRule = RoborazziRule()

    @Test
    fun markdown_code_block_light() {
        capture(darkTheme = false, content = CODE_BLOCK_MESSAGE)
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-night")
    fun markdown_code_block_dark() {
        capture(darkTheme = true, content = CODE_BLOCK_MESSAGE)
    }

    @Test
    fun markdown_long_content_light() {
        capture(darkTheme = false, content = LONG_MESSAGE)
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-night")
    fun markdown_long_content_dark() {
        capture(darkTheme = true, content = LONG_MESSAGE)
    }

    @Test
    fun markdown_math_light() {
        capture(darkTheme = false, content = MATH_MESSAGE)
        assertRefusedMathFellBack()
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-night")
    fun markdown_math_dark() {
        capture(darkTheme = true, content = MATH_MESSAGE)
        assertRefusedMathFellBack()
    }

    private fun assertRefusedMathFellBack() {
        composeTestRule.onNodeWithTag(MARKDOWN_MATH_LITERAL_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(MARKDOWN_MATH_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("P(A \\mid B) = \\frac{P(B \\mid A) P(A)}{P(B)}").assertExists()
    }

    private fun capture(
        darkTheme: Boolean,
        content: String,
    ) {
        composeTestRule.setContent {
            HermesTheme(darkTheme = darkTheme) {
                EntryScreen(state = markdownState(content), onEvent = {})
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage()
    }

    private fun markdownState(content: String): EntryUiState {
        val session = session("markdown-session", "Markdown Session", preview = "Synthetic preview")
        return entryState(
            SessionListUiState(
                sessions = listOf(session),
                openedSession =
                    OpenSessionUiState(
                        session = session,
                        messages =
                            listOf(
                                SessionMessageUiState(
                                    id = "message-1",
                                    role = "user",
                                    content = "Show the release notes",
                                ),
                                SessionMessageUiState(
                                    id = "message-2",
                                    role = "assistant",
                                    content = content,
                                ),
                            ),
                    ),
            ),
        )
    }

    private companion object {
        val CODE_BLOCK_MESSAGE =
            """
            Run **this** build command and read [the notes](https://example.com/notes):

            ```shell
            ./gradlew :feature:entry:presentation:verifyRoborazziDebug --console=plain --no-daemon --stacktrace
            ```

            ```yaml
            # gateway settings
            endpoint: "wss://example.invalid/ws"
            retry: on # keep the last session
            ```

            ```toml
            # compose draft
            title = "Weekly notes" # shown in the list
            keep = 8080
            ```

            A rejected scheme such as [danger](javascript:alert(1)) stays inert text.
            """.trimIndent()

        val LONG_MESSAGE =
            """
            **Summary**

            - first finding with `inline code`
            - second finding with [the release notes](https://example.com/notes)
            - third finding that also proves the list keeps its items

            The response continues with more paragraphs than a phone pane can show at once, so the
            conversation scrolls natively instead of truncating the message.
            """.trimIndent() +
                "\n\n" +
                (1..12).joinToString(separator = "\n\n") { index -> "Paragraph $index of the long response." } +
                "\n\n```kotlin\nval total = 12\n```"

        val MATH_MESSAGE =
            """
            Inline math such as ${'$'}E = mc^2${'$'} stays on the line.

            ${'$'}${'$'}
            P(A \mid B) = \frac{P(B \mid A) P(A)}{P(B)}
            ${'$'}${'$'}

            An expression the engine refuses keeps its exact source:

            ${'$'}${'$'}
            \frac{a}{b}
            = \left(
            ${'$'}${'$'}
            """.trimIndent()
    }
}
