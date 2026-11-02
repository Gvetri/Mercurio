package org.mercurio.client.feature.entry.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import java.util.Locale

/**
 * The code block the approved Chat design draws: a 24 dp head with the language label and one
 * `[ copy ]` control, then the body on its own inset surface. The body scrolls instead of
 * wrapping, and it keeps the syntax roles the token set declares.
 */
@Composable
internal fun MarkdownCodeBlock(
    block: MarkdownBlock.CodeBlock,
    onCopy: (String) -> Unit,
) {
    val tokens = LocalHermesDesignTokens.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = tokens.componentColors.inset,
        shape = tokens.shapes.small,
        border = BorderStroke(1.dp, tokens.colorScheme.outlineVariant),
    ) {
        Column {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = CODE_HEAD_HEIGHT)
                        .padding(start = tokens.spacing.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = block.language.orEmpty().uppercase(Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 0.12.em,
                    color = tokens.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                ChatTextControl(
                    label = stringResource(R.string.chat_copy),
                    enabled = true,
                    onClick = { onCopy(block.code) },
                    modifier = Modifier.testTag("code-copy"),
                )
            }
            HorizontalDivider(thickness = 1.dp, color = tokens.colorScheme.outlineVariant)
            MarkdownCodeText(block = block)
        }
    }
}

@Composable
private fun MarkdownCodeText(block: MarkdownBlock.CodeBlock) {
    val syntaxColors = LocalHermesDesignTokens.current.syntaxColors
    val code = block.code
    val language = block.language
    val modifier =
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 8.dp)
    val tokens = remember(code, language) { language?.let { highlightCode(it, code) }.orEmpty() }
    val text =
        remember(code, tokens, syntaxColors) {
            buildAnnotatedString {
                append(code)
                tokens.forEach { token ->
                    addStyle(SpanStyle(color = syntaxColors.colorFor(token.kind)), token.start, token.end)
                }
            }
        }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        lineHeight = MaterialTheme.typography.bodyMedium.lineHeight,
        fontFamily = FontFamily.Monospace,
        color = syntaxColors.punctuation,
        softWrap = false,
        modifier = modifier,
    )
}

private fun HermesSyntaxColors.colorFor(kind: CodeTokenKind): Color =
    when (kind) {
        CodeTokenKind.Keyword -> keyword
        CodeTokenKind.String -> string
        CodeTokenKind.Number -> number
        CodeTokenKind.Comment -> comment
        CodeTokenKind.Function -> function
    }

private val CODE_HEAD_HEIGHT = 24.dp
