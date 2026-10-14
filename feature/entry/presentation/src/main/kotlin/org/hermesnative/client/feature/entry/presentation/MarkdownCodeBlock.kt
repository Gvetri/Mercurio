package org.hermesnative.client.feature.entry.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

@Composable
internal fun MarkdownCodeBlock(
    block: MarkdownBlock.CodeBlock,
    onCopy: (String) -> Unit,
) {
    val spacing = LocalHermesDesignTokens.current.spacing
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                )
                .padding(spacing.s),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = block.language.orEmpty(),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = { onCopy(block.code) },
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(text = "Copy code")
            }
        }
        MarkdownCodeText(block = block)
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
    val tokens = remember(code, language) { language?.let { highlightCode(it, code) }.orEmpty() }
    if (tokens.isEmpty()) {
        Text(
            text = code,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            softWrap = false,
            modifier = modifier,
        )
    } else {
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
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            softWrap = false,
            modifier = modifier,
        )
    }
}

private fun HermesSyntaxColors.colorFor(kind: CodeTokenKind): Color =
    when (kind) {
        CodeTokenKind.Keyword -> keyword
        CodeTokenKind.String -> string
        CodeTokenKind.Number -> number
        CodeTokenKind.Comment -> comment
        CodeTokenKind.Function -> function
    }
