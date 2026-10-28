package org.mercurio.client.feature.entry.presentation

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-notnight")
class MarkdownSyntaxThemeTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun the_light_theme_resolves_syntax_colors_from_the_light_token_set() {
        assertEquals(HermesLightTokens.syntaxColors.keyword, renderedKeywordColor(darkTheme = false))
    }

    @Test
    fun the_dark_theme_resolves_syntax_colors_from_the_dark_token_set() {
        assertEquals(HermesDarkTokens.syntaxColors.keyword, renderedKeywordColor(darkTheme = true))
    }

    @Test
    fun the_two_themes_resolve_the_keyword_to_distinct_colors() {
        assertTrue(HermesLightTokens.syntaxColors.keyword != HermesDarkTokens.syntaxColors.keyword)
        assertTrue(HermesLightTokens.syntaxColors.function != HermesDarkTokens.syntaxColors.function)
    }

    @Test
    fun every_syntax_color_is_readable_on_the_code_panel_in_both_themes() {
        listOf(HermesLightTokens, HermesDarkTokens).forEach { tokens ->
            val panel = tokens.colorScheme.surfaceVariant
            val colors = with(tokens.syntaxColors) { listOf(keyword, string, number, comment, function) }
            colors.forEach { color ->
                val ratio = contrastRatio(color, panel)
                assertTrue(
                    "$color on the code panel $panel reads at only $ratio:1",
                    ratio >= MINIMUM_READABLE_CONTRAST,
                )
            }
            assertEquals(5, colors.distinct().size)
        }
    }

    private fun renderedKeywordColor(darkTheme: Boolean): Color? {
        val code = "val x = 1"
        composeTestRule.setContent {
            HermesTheme(darkTheme = darkTheme) {
                MarkdownContent(
                    markdown = "```kotlin\n$code\n```",
                    actions = RecordingActions().asMessageActions(),
                )
            }
        }
        val annotated =
            composeTestRule.onNodeWithText(code).fetchSemanticsNode().config[SemanticsProperties.Text].single()
        return annotated.spanStyles.first { style -> style.start == 0 }.item.color
    }

    private fun contrastRatio(
        foreground: Color,
        background: Color,
    ): Double {
        val brighter = maxOf(foreground.luminance(), background.luminance())
        val dimmer = minOf(foreground.luminance(), background.luminance())
        return (brighter + CONTRAST_OFFSET) / (dimmer + CONTRAST_OFFSET)
    }

    private companion object {
        const val MINIMUM_READABLE_CONTRAST = 4.5
        const val CONTRAST_OFFSET = 0.05
    }
}
