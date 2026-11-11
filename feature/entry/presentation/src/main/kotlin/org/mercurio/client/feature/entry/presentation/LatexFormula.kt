package org.mercurio.client.feature.entry.presentation

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
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
