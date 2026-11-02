package org.mercurio.client.feature.entry.presentation

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.noties.jlatexmath.JLatexMathAndroid
import ru.noties.jlatexmath.JLatexMathDrawable

/** The formula engine seam: what parses, what refuses, how it scales, and the ink it paints. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LatexFormulaTest {
    @Before
    fun setUp() {
        JLatexMathAndroid.init(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun a_valid_expression_lays_out_a_formula() {
        val formula = LatexFormula.of("E = mc^2", sizePx = 40f, color = Color.BLACK.toComposeColor())

        assertNotNull("A common formula shape must lay out", formula)
        assertTrue(formula!!.widthPx > 0)
        assertTrue(formula.heightPx > 0)
    }

    @Test
    fun an_expression_the_engine_refuses_has_no_formula() {
        assertNull(LatexFormula.of("\\undefinedcmd{x}", sizePx = 40f, color = Color.BLACK.toComposeColor()))
    }

    @Test
    fun an_empty_expression_has_no_formula() {
        assertNull(LatexFormula.of("", sizePx = 40f, color = Color.BLACK.toComposeColor()))
    }

    @Test
    fun a_larger_text_size_lays_out_a_larger_formula() {
        val small = LatexFormula.of("x^2", sizePx = 20f, color = Color.BLACK.toComposeColor())!!
        val large = LatexFormula.of("x^2", sizePx = 40f, color = Color.BLACK.toComposeColor())!!

        assertTrue("The formula must follow the text size, not ignore it", large.heightPx > small.heightPx)
        assertTrue(large.widthPx > small.widthPx)
    }

    @Test
    fun the_requested_colour_reaches_the_painted_ink() {
        val dark = LatexFormula.of("x^2", sizePx = 40f, color = Color.BLACK.toComposeColor())!!
        val light = LatexFormula.of("x^2", sizePx = 40f, color = Color.WHITE.toComposeColor())!!

        val darkInk = dark.averageInk()
        val lightInk = light.averageInk()

        assertTrue("A dark light-theme ink must be darker than the dark-theme ink", darkInk < lightInk)
    }

    @Test
    fun a_deeply_nested_expression_falls_back_instead_of_crashing() {
        val nested = "{".repeat(NESTED_DEPTH) + "x" + "}".repeat(NESTED_DEPTH)

        val engineRefusal = runCatching { JLatexMathDrawable.builder(nested).textSize(40f).build() }

        assertTrue(
            "The engine itself must be the thing that refuses this input, or the guard is dead code",
            engineRefusal.exceptionOrNull() is StackOverflowError,
        )
        assertNull(LatexFormula.of(nested, sizePx = 40f, color = Color.BLACK.toComposeColor()))
    }

    private fun LatexFormula.averageInk(): Int {
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        paint(android.graphics.Canvas(bitmap))
        var sum = 0
        var count = 0
        for (x in 0 until bitmap.width) {
            for (y in 0 until bitmap.height) {
                val pixel = bitmap.getPixel(x, y)
                if (pixel ushr 24 > 0) {
                    sum += Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)
                    count++
                }
            }
        }
        assertTrue("Every pixel of the bitmap was transparent; the formula painted nothing", count > 0)
        return sum / count
    }

    private fun Int.toComposeColor(): androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color(this)

    private companion object {
        private const val NESTED_DEPTH = 5_000
    }
}
