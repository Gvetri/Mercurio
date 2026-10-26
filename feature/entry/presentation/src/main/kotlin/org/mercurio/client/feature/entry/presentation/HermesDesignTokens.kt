package org.mercurio.client.feature.entry.presentation

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The application-owned spacing scale. Shell surfaces use these steps instead of
 * literal values; the extracted Session surfaces still carry their original
 * literal spacing and move onto the scale as they migrate.
 */
@Immutable
data class HermesSpacing(
    val xs: Dp,
    val s: Dp,
    val m: Dp,
    val l: Dp,
    val xl: Dp,
)

/**
 * The application-owned elevation scale. [raised] marks the active conversation
 * pane on larger displays; every other surface stays on the base content plane.
 */
@Immutable
data class HermesElevation(
    val raised: Dp,
)

@Immutable
data class HermesSyntaxColors(
    val keyword: Color,
    val string: Color,
    val number: Color,
    val comment: Color,
    val function: Color,
)

/**
 * The complete local design-token set for one theme mode: color, typography,
 * spacing, shape, and elevation. [HermesLightTokens] and [HermesDarkTokens] are
 * the two designed modes; the shell follows the system setting by default.
 */
@Immutable
data class HermesDesignTokens(
    val colorScheme: ColorScheme,
    val typography: Typography,
    val spacing: HermesSpacing,
    val shapes: Shapes,
    val elevation: HermesElevation,
    val syntaxColors: HermesSyntaxColors,
)

private val HermesTypography = Typography()

private val HermesShapes = Shapes()

private const val LIGHT_PRIMARY_COLOR = 0xFF4E5D8C
private const val LIGHT_SECONDARY_COLOR = 0xFF5D5F71
private const val LIGHT_BACKGROUND_COLOR = 0xFFF9F9FF
private const val LIGHT_SURFACE_COLOR = 0xFFF9F9FF
private const val DARK_PRIMARY_COLOR = 0xFFB9C4FF
private const val DARK_ON_PRIMARY_COLOR = 0xFF1F2A58
private const val DARK_SECONDARY_COLOR = 0xFFC5C5DC
private const val DARK_BACKGROUND_COLOR = 0xFF121318
private const val DARK_SURFACE_COLOR = 0xFF121318
private const val LIGHT_SYNTAX_KEYWORD_COLOR = 0xFF7B1FA2
private const val LIGHT_SYNTAX_STRING_COLOR = 0xFF1B6B2A
private const val LIGHT_SYNTAX_NUMBER_COLOR = 0xFF8F4708
private const val LIGHT_SYNTAX_COMMENT_COLOR = 0xFF5F5F5F
private const val LIGHT_SYNTAX_FUNCTION_COLOR = 0xFF1055B8
private const val DARK_SYNTAX_KEYWORD_COLOR = 0xFFD4A5FF
private const val DARK_SYNTAX_STRING_COLOR = 0xFF9CE38F
private const val DARK_SYNTAX_NUMBER_COLOR = 0xFFFFD07A
private const val DARK_SYNTAX_COMMENT_COLOR = 0xFF87D2CA
private const val DARK_SYNTAX_FUNCTION_COLOR = 0xFF9EC6FF

private val HermesLightColorScheme =
    lightColorScheme(
        primary = Color(LIGHT_PRIMARY_COLOR),
        onPrimary = Color.White,
        secondary = Color(LIGHT_SECONDARY_COLOR),
        background = Color(LIGHT_BACKGROUND_COLOR),
        surface = Color(LIGHT_SURFACE_COLOR),
    )

private val HermesDarkColorScheme =
    darkColorScheme(
        primary = Color(DARK_PRIMARY_COLOR),
        onPrimary = Color(DARK_ON_PRIMARY_COLOR),
        secondary = Color(DARK_SECONDARY_COLOR),
        background = Color(DARK_BACKGROUND_COLOR),
        surface = Color(DARK_SURFACE_COLOR),
    )

private val HermesSpacingScale =
    HermesSpacing(
        xs = 4.dp,
        s = 8.dp,
        m = 12.dp,
        l = 16.dp,
        xl = 24.dp,
    )

private val HermesElevationScale =
    HermesElevation(
        raised = 3.dp,
    )

private val HermesLightSyntaxColors =
    HermesSyntaxColors(
        keyword = Color(LIGHT_SYNTAX_KEYWORD_COLOR),
        string = Color(LIGHT_SYNTAX_STRING_COLOR),
        number = Color(LIGHT_SYNTAX_NUMBER_COLOR),
        comment = Color(LIGHT_SYNTAX_COMMENT_COLOR),
        function = Color(LIGHT_SYNTAX_FUNCTION_COLOR),
    )

private val HermesDarkSyntaxColors =
    HermesSyntaxColors(
        keyword = Color(DARK_SYNTAX_KEYWORD_COLOR),
        string = Color(DARK_SYNTAX_STRING_COLOR),
        number = Color(DARK_SYNTAX_NUMBER_COLOR),
        comment = Color(DARK_SYNTAX_COMMENT_COLOR),
        function = Color(DARK_SYNTAX_FUNCTION_COLOR),
    )

val HermesLightTokens: HermesDesignTokens =
    HermesDesignTokens(
        colorScheme = HermesLightColorScheme,
        typography = HermesTypography,
        spacing = HermesSpacingScale,
        shapes = HermesShapes,
        elevation = HermesElevationScale,
        syntaxColors = HermesLightSyntaxColors,
    )

val HermesDarkTokens: HermesDesignTokens =
    HermesDesignTokens(
        colorScheme = HermesDarkColorScheme,
        typography = HermesTypography,
        spacing = HermesSpacingScale,
        shapes = HermesShapes,
        elevation = HermesElevationScale,
        syntaxColors = HermesDarkSyntaxColors,
    )

/**
 * The current token set. Provided by [HermesTheme] and read by shell surfaces
 * that need spacing or elevation steps.
 */
val LocalHermesDesignTokens = staticCompositionLocalOf { HermesLightTokens }
