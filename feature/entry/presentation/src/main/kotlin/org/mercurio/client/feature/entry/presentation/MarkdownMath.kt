package org.mercurio.client.feature.entry.presentation

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.takeOrElse
import ru.noties.jlatexmath.JLatexMathAndroid
import ru.noties.jlatexmath.JLatexMathDrawable

/**
 * One formula the engine laid out: the size it needs and the drawable that paints it. A formula
 * the engine cannot parse has no instance, which is how every caller reaches its literal fallback.
 */
internal class LatexFormula private constructor(
    private val drawable: JLatexMathDrawable,
    val widthPx: Int,
    val heightPx: Int,
) {
    fun draw(scope: DrawScope) {
        scope.drawIntoCanvas { canvas -> paint(canvas.nativeCanvas) }
    }

    fun paint(canvas: android.graphics.Canvas) {
        drawable.setBounds(0, 0, widthPx, heightPx)
        drawable.draw(canvas)
    }

    companion object {
        fun of(
            expression: String,
            sizePx: Float,
            color: Color,
        ): LatexFormula? {
            val drawable = if (expression.isBlank()) null else renderDrawable(expression, sizePx, color)
            if (drawable == null) return null
            val width = drawable.intrinsicWidth
            val height = drawable.intrinsicHeight
            return if (width > 0 && height > 0) LatexFormula(drawable, width, height) else null
        }

        @Suppress("TooGenericExceptionCaught")
        private fun renderDrawable(
            expression: String,
            sizePx: Float,
            color: Color,
        ): JLatexMathDrawable? =
            try {
                JLatexMathDrawable
                    .builder(expression)
                    .textSize(sizePx)
                    .color(color.toArgb())
                    .build()
            } catch (_: Exception) {
                null
            } catch (_: LinkageError) {
                null
            } catch (_: StackOverflowError) {
                null
            }
    }
}

/**
 * Starts the engine once per process. The published port reads its fonts through this call and
 * throws from a static initialiser without it, so every formula entry point asks for it first.
 */
internal fun prepareLatexMath(context: Context) {
    if (latexMathPrepared) return
    JLatexMathAndroid.init(context.applicationContext)
    latexMathPrepared = true
}

@Volatile
private var latexMathPrepared = false

@Composable
internal fun rememberLatexFormula(
    expression: String,
    sizePx: Float,
    color: Color,
): LatexFormula? = remember(expression, sizePx, color) { LatexFormula.of(expression, sizePx, color) }

@Composable
private fun FormulaCanvas(
    formula: LatexFormula,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        formula.draw(this)
    }
}

/**
 * The inline content a paragraph of spanned text needs: one drawing slot per formula that parsed,
 * addressed by the span index that produced it. A formula that did not parse has no slot, so the
 * span keeps rendering its literal source text.
 */
internal class InlineMathContent private constructor(
    val contents: Map<String, InlineTextContent>,
    private val ids: Map<Int, String>,
) {
    fun idFor(spanIndex: Int): String? = ids[spanIndex]

    companion object {
        fun of(
            expressions: List<Pair<Int, String>>,
            sizePx: Float,
            color: Color,
            widthToSp: (Int) -> TextUnit,
            heightToSp: (Int) -> TextUnit,
        ): InlineMathContent? {
            val contents = mutableMapOf<String, InlineTextContent>()
            val ids = mutableMapOf<Int, String>()
            expressions.forEach { (index, expression) ->
                val formula = LatexFormula.of(expression, sizePx, color) ?: return@forEach
                val id = "math-$index"
                contents[id] =
                    InlineTextContent(
                        placeholder =
                            Placeholder(
                                width = widthToSp(formula.widthPx),
                                height = heightToSp(formula.heightPx),
                                placeholderVerticalAlign = PlaceholderVerticalAlign.TextBottom,
                            ),
                    ) { _ ->
                        FormulaCanvas(
                            formula = formula,
                            modifier = Modifier.fillMaxSize().testTag(MARKDOWN_INLINE_MATH_TAG),
                        )
                    }
                ids[index] = id
            }
            return if (contents.isEmpty()) null else InlineMathContent(contents, ids)
        }
    }
}

@Composable
internal fun rememberInlineMath(
    spans: List<MarkdownSpan>,
    textStyle: TextStyle,
    color: Color,
): InlineMathContent? {
    val expressions =
        remember(spans) {
            spans.mapIndexedNotNull { index, span ->
                (span.element as? InlineElement.Math)?.let { math -> index to math.expression }
            }
        }
    if (expressions.isEmpty()) return null
    prepareLatexMath(LocalContext.current)
    val density = LocalDensity.current
    val bodySize = MaterialTheme.typography.bodyLarge.fontSize
    val sizePx = with(density) { textStyle.fontSize.takeOrElse { bodySize }.toPx() }
    return remember(expressions, sizePx, color, density) {
        InlineMathContent.of(
            expressions = expressions,
            sizePx = sizePx,
            color = color,
            widthToSp = { width -> with(density) { width.toSp() } },
            heightToSp = { height -> with(density) { height.toSp() } },
        )
    }
}

/** One `$$…$$` block: the formula centred in its own inset surface, scrolling when it is wide. */
@Composable
internal fun MarkdownDisplayMath(
    expression: String,
    source: String,
) {
    val tokens = LocalHermesDesignTokens.current
    val density = LocalDensity.current
    prepareLatexMath(LocalContext.current)
    val style = MaterialTheme.typography.titleLarge
    val formula =
        rememberLatexFormula(
            expression = expression,
            sizePx = with(density) { style.fontSize.toPx() },
            color = tokens.colorScheme.onSurface,
        )
    if (formula == null) {
        MarkdownMathSourceText(source = source)
    } else {
        MarkdownMathSurface {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier =
                        Modifier
                            .horizontalScroll(rememberScrollState())
                            .widthIn(min = maxWidth),
                    contentAlignment = Alignment.Center,
                ) {
                    val formulaDensity = LocalDensity.current
                    FormulaCanvas(
                        formula = formula,
                        modifier =
                            Modifier
                                .testTag(MARKDOWN_MATH_TAG)
                                .semantics { contentDescription = expression }
                                .size(
                                    width = with(formulaDensity) { formula.widthPx.toDp() },
                                    height = with(formulaDensity) { formula.heightPx.toDp() },
                                ),
                    )
                }
            }
        }
    }
}

@Composable
private fun MarkdownMathSourceText(source: String) {
    val tokens = LocalHermesDesignTokens.current
    MarkdownMathSurface(centered = false) {
        Text(
            text = source,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = tokens.colorScheme.onSurfaceVariant,
            softWrap = false,
            modifier =
                Modifier
                    .testTag(MARKDOWN_MATH_LITERAL_TAG)
                    .horizontalScroll(rememberScrollState()),
        )
    }
}

@Composable
private fun MarkdownMathSurface(
    centered: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val tokens = LocalHermesDesignTokens.current
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(tokens.shapes.small)
                .background(tokens.componentColors.inset)
                .border(1.dp, tokens.colorScheme.outlineVariant, tokens.shapes.small)
                .padding(tokens.spacing.s),
        contentAlignment = if (centered) Alignment.Center else Alignment.CenterStart,
        content = content,
    )
}

internal const val MARKDOWN_INLINE_MATH_TAG = "markdown-inline-math"

internal const val MARKDOWN_MATH_TAG = "markdown-math"

internal const val MARKDOWN_MATH_LITERAL_TAG = "markdown-math-literal"
