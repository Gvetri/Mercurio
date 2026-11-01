package org.mercurio.client.feature.entry.presentation

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The application-owned spacing scale: the eight Pack D steps. Surfaces read these
 * steps instead of literal values.
 */
@Immutable
data class HermesSpacing(
    val xxs: Dp,
    val xs: Dp,
    val s: Dp,
    val m: Dp,
    val l: Dp,
    val xl: Dp,
    val xxl: Dp,
    val xxxl: Dp,
)

/**
 * The application-owned elevation scale. [raised] marks the active conversation
 * pane on larger displays; every other surface stays on the base content plane.
 */
@Immutable
data class HermesElevation(
    val level0: Dp,
    val level1: Dp,
    val level2: Dp,
    val level3: Dp,
    val level4: Dp,
    val level5: Dp,
) {
    val raised: Dp
        get() = level2
}

@Immutable
data class HermesSyntaxColors(
    val keyword: Color,
    val string: Color,
    val number: Color,
    val function: Color,
    val type: Color,
    val comment: Color,
    val punctuation: Color,
    val codeSurface: Color,
)

/**
 * The component tokens the Material role set cannot express: the island inset
 * fill and the selected session row tint with its two derived foregrounds.
 */
@Immutable
data class HermesComponentColors(
    val inset: Color,
    val sessionSelected: Color,
    val onSessionSelected: Color,
    val onSessionSelectedDim: Color,
)

/**
 * The complete local design-token set for one theme mode: color, typography,
 * spacing, shape, elevation, syntax, and the session row component tokens.
 * [HermesLightTokens] and [HermesDarkTokens] are the two designed modes; the
 * shell follows the system setting by default.
 */
@Immutable
data class HermesDesignTokens(
    val colorScheme: ColorScheme,
    val typography: Typography,
    val spacing: HermesSpacing,
    val shapes: Shapes,
    val elevation: HermesElevation,
    val syntaxColors: HermesSyntaxColors,
    val componentColors: HermesComponentColors,
)

private val IbmPlexMono =
    FontFamily(
        Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
        Font(R.font.ibm_plex_mono_medium, FontWeight.Medium),
    )

private val HermesTypography =
    Typography(
        displayLarge = packDTextStyle(40f, 48f, FontWeight.Medium),
        displayMedium = packDTextStyle(36f, 44f, FontWeight.Medium),
        displaySmall = packDTextStyle(32f, 40f, FontWeight.Medium),
        headlineLarge = packDTextStyle(28f, 36f, FontWeight.Medium),
        headlineMedium = packDTextStyle(26f, 34f, FontWeight.Medium),
        headlineSmall = packDTextStyle(24f, 32f, FontWeight.Medium),
        titleLarge = packDTextStyle(18f, 26f, FontWeight.Medium),
        titleMedium = packDTextStyle(14f, 20f, FontWeight.Medium),
        titleSmall = packDTextStyle(12.5f, 18f, FontWeight.Medium),
        bodyLarge = packDTextStyle(13.5f, 20f, FontWeight.Normal),
        bodyMedium = packDTextStyle(12f, 18f, FontWeight.Normal),
        bodySmall = packDTextStyle(12f, 16f, FontWeight.Normal),
        labelLarge = packDTextStyle(14f, 20f, FontWeight.Medium),
        labelMedium = packDTextStyle(12f, 16f, FontWeight.Medium),
        labelSmall = packDTextStyle(11f, 16f, FontWeight.Medium),
    )

private fun packDTextStyle(
    size: Float,
    lineHeight: Float,
    weight: FontWeight,
): TextStyle =
    TextStyle(
        fontFamily = IbmPlexMono,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = lineHeight.sp,
    )

private val HermesShapes =
    Shapes(
        extraSmall = RoundedCornerShape(4.dp),
        small = RoundedCornerShape(6.dp),
        medium = RoundedCornerShape(8.dp),
        large = RoundedCornerShape(10.dp),
        extraLarge = RoundedCornerShape(16.dp),
    )

/** The pill shape. CSS `999px` resolves to a half-height corner on a finite box. */
val HermesFullShape = RoundedCornerShape(percent = 50)

private const val LIGHT_PRIMARY_COLOR = 0xFF2F66D0
private const val LIGHT_ON_PRIMARY_COLOR = 0xFFFFFFFF
private const val LIGHT_PRIMARY_CONTAINER_COLOR = 0xFFD9E2FF
private const val LIGHT_ON_PRIMARY_CONTAINER_COLOR = 0xFF001945
private const val LIGHT_SECONDARY_COLOR = 0xFF545F71
private const val LIGHT_ON_SECONDARY_COLOR = 0xFFFFFFFF
private const val LIGHT_SECONDARY_CONTAINER_COLOR = 0xFFD8E3F8
private const val LIGHT_ON_SECONDARY_CONTAINER_COLOR = 0xFF111C2B
private const val LIGHT_TERTIARY_COLOR = 0xFF871094
private const val LIGHT_ON_TERTIARY_COLOR = 0xFFFFFFFF
private const val LIGHT_TERTIARY_CONTAINER_COLOR = 0xFFFFD7F5
private const val LIGHT_ON_TERTIARY_CONTAINER_COLOR = 0xFF3B063F
private const val LIGHT_ERROR_COLOR = 0xFFC7222D
private const val LIGHT_ON_ERROR_COLOR = 0xFFFFFFFF
private const val LIGHT_ERROR_CONTAINER_COLOR = 0xFFFFDAD6
private const val LIGHT_ON_ERROR_CONTAINER_COLOR = 0xFF410002
private const val LIGHT_BACKGROUND_COLOR = 0xFFECEEF1
private const val LIGHT_ON_BACKGROUND_COLOR = 0xFF1A1C1F
private const val LIGHT_SURFACE_COLOR = 0xFFFFFFFF
private const val LIGHT_ON_SURFACE_COLOR = 0xFF1A1C1F
private const val LIGHT_SURFACE_VARIANT_COLOR = 0xFFE1E2E8
private const val LIGHT_ON_SURFACE_VARIANT_COLOR = 0xFF5B6168
private const val LIGHT_SURFACE_DIM_COLOR = 0xFFE3E6EA
private const val LIGHT_SURFACE_BRIGHT_COLOR = 0xFFFFFFFF
private const val LIGHT_SURFACE_CONTAINER_LOWEST_COLOR = 0xFFFFFFFF
private const val LIGHT_SURFACE_CONTAINER_LOW_COLOR = 0xFFF4F5F7
private const val LIGHT_SURFACE_CONTAINER_COLOR = 0xFFECEEF1
private const val LIGHT_SURFACE_CONTAINER_HIGH_COLOR = 0xFFE3E6EA
private const val LIGHT_SURFACE_CONTAINER_HIGHEST_COLOR = 0xFFD7DBE0
private const val LIGHT_OUTLINE_COLOR = 0xFF8A9099
private const val LIGHT_OUTLINE_VARIANT_COLOR = 0xFFD7DBE0
private const val LIGHT_INVERSE_SURFACE_COLOR = 0xFF1A1C1F
private const val LIGHT_INVERSE_ON_SURFACE_COLOR = 0xFFF4F5F7
private const val LIGHT_INVERSE_PRIMARY_COLOR = 0xFFA9C7FF
private const val LIGHT_SCRIM_COLOR = 0xFF000000

private const val DARK_PRIMARY_COLOR = 0xFFA9C7FF
private const val DARK_ON_PRIMARY_COLOR = 0xFF002E6A
private const val DARK_PRIMARY_CONTAINER_COLOR = 0xFF00429B
private const val DARK_ON_PRIMARY_CONTAINER_COLOR = 0xFFD9E2FF
private const val DARK_SECONDARY_COLOR = 0xFFBCC7DC
private const val DARK_ON_SECONDARY_COLOR = 0xFF263141
private const val DARK_SECONDARY_CONTAINER_COLOR = 0xFF3C4758
private const val DARK_ON_SECONDARY_CONTAINER_COLOR = 0xFFD8E3F8
private const val DARK_TERTIARY_COLOR = 0xFFF0B0E8
private const val DARK_ON_TERTIARY_COLOR = 0xFF4F1350
private const val DARK_TERTIARY_CONTAINER_COLOR = 0xFF6A2A6B
private const val DARK_ON_TERTIARY_CONTAINER_COLOR = 0xFFFFD7F5
private const val DARK_ERROR_COLOR = 0xFFFFB4AB
private const val DARK_ON_ERROR_COLOR = 0xFF690005
private const val DARK_ERROR_CONTAINER_COLOR = 0xFF93000A
private const val DARK_ON_ERROR_CONTAINER_COLOR = 0xFFFFDAD6
private const val DARK_BACKGROUND_COLOR = 0xFF2B2D30
private const val DARK_ON_BACKGROUND_COLOR = 0xFFDFE1E5
private const val DARK_SURFACE_COLOR = 0xFF1E1F22
private const val DARK_ON_SURFACE_COLOR = 0xFFDFE1E5
private const val DARK_SURFACE_VARIANT_COLOR = 0xFF43474E
private const val DARK_ON_SURFACE_VARIANT_COLOR = 0xFFC3C7CF
private const val DARK_SURFACE_DIM_COLOR = 0xFF1E1F22
private const val DARK_SURFACE_BRIGHT_COLOR = 0xFF393B40
private const val DARK_SURFACE_CONTAINER_LOWEST_COLOR = 0xFF161719
private const val DARK_SURFACE_CONTAINER_LOW_COLOR = 0xFF1B1B1F
private const val DARK_SURFACE_CONTAINER_COLOR = 0xFF1E1F22
private const val DARK_SURFACE_CONTAINER_HIGH_COLOR = 0xFF26282C
private const val DARK_SURFACE_CONTAINER_HIGHEST_COLOR = 0xFF2E3035
private const val DARK_OUTLINE_COLOR = 0xFF8D9199
private const val DARK_OUTLINE_VARIANT_COLOR = 0xFF393B40
private const val DARK_INVERSE_SURFACE_COLOR = 0xFFDFE1E5
private const val DARK_INVERSE_ON_SURFACE_COLOR = 0xFF1A1C1F
private const val DARK_INVERSE_PRIMARY_COLOR = 0xFF2F66D0
private const val DARK_SCRIM_COLOR = 0xFF000000

private const val LIGHT_SYNTAX_KEYWORD_COLOR = 0xFF0033B3
private const val LIGHT_SYNTAX_STRING_COLOR = 0xFF067D17
private const val LIGHT_SYNTAX_NUMBER_COLOR = 0xFF1750EB
private const val LIGHT_SYNTAX_FUNCTION_COLOR = 0xFF00627A
private const val LIGHT_SYNTAX_TYPE_COLOR = 0xFF871094
private const val LIGHT_SYNTAX_COMMENT_COLOR = 0xFF5F6368
private const val LIGHT_SYNTAX_PUNCTUATION_COLOR = 0xFF1A1C1F
private const val LIGHT_SYNTAX_CODE_SURFACE_COLOR = 0xFFF4F5F7

private const val DARK_SYNTAX_KEYWORD_COLOR = 0xFFCF8E6D
private const val DARK_SYNTAX_STRING_COLOR = 0xFF6AAB73
private const val DARK_SYNTAX_NUMBER_COLOR = 0xFF2AACB8
private const val DARK_SYNTAX_FUNCTION_COLOR = 0xFF56A8F5
private const val DARK_SYNTAX_TYPE_COLOR = 0xFFC77DBB
private const val DARK_SYNTAX_COMMENT_COLOR = 0xFF8F949C
private const val DARK_SYNTAX_PUNCTUATION_COLOR = 0xFFDFE1E5
private const val DARK_SYNTAX_CODE_SURFACE_COLOR = 0xFF26282C

private const val LIGHT_SESSION_SELECTED_COLOR = 0xFFD4E2FF
private const val LIGHT_ON_SESSION_SELECTED_COLOR = 0xFF001945
private const val LIGHT_ON_SESSION_SELECTED_DIM_COLOR = 0xFF2F456E
private const val DARK_SESSION_SELECTED_COLOR = 0xFF2E436E
private const val DARK_ON_SESSION_SELECTED_COLOR = 0xFFFFFFFF
private const val DARK_ON_SESSION_SELECTED_DIM_COLOR = 0xFFD1D6DF

private val HermesLightColorScheme =
    lightColorScheme(
        primary = Color(LIGHT_PRIMARY_COLOR),
        onPrimary = Color(LIGHT_ON_PRIMARY_COLOR),
        primaryContainer = Color(LIGHT_PRIMARY_CONTAINER_COLOR),
        onPrimaryContainer = Color(LIGHT_ON_PRIMARY_CONTAINER_COLOR),
        inversePrimary = Color(LIGHT_INVERSE_PRIMARY_COLOR),
        secondary = Color(LIGHT_SECONDARY_COLOR),
        onSecondary = Color(LIGHT_ON_SECONDARY_COLOR),
        secondaryContainer = Color(LIGHT_SECONDARY_CONTAINER_COLOR),
        onSecondaryContainer = Color(LIGHT_ON_SECONDARY_CONTAINER_COLOR),
        tertiary = Color(LIGHT_TERTIARY_COLOR),
        onTertiary = Color(LIGHT_ON_TERTIARY_COLOR),
        tertiaryContainer = Color(LIGHT_TERTIARY_CONTAINER_COLOR),
        onTertiaryContainer = Color(LIGHT_ON_TERTIARY_CONTAINER_COLOR),
        background = Color(LIGHT_BACKGROUND_COLOR),
        onBackground = Color(LIGHT_ON_BACKGROUND_COLOR),
        surface = Color(LIGHT_SURFACE_COLOR),
        onSurface = Color(LIGHT_ON_SURFACE_COLOR),
        surfaceVariant = Color(LIGHT_SURFACE_VARIANT_COLOR),
        onSurfaceVariant = Color(LIGHT_ON_SURFACE_VARIANT_COLOR),
        surfaceTint = Color(LIGHT_PRIMARY_COLOR),
        surfaceDim = Color(LIGHT_SURFACE_DIM_COLOR),
        surfaceBright = Color(LIGHT_SURFACE_BRIGHT_COLOR),
        surfaceContainerLowest = Color(LIGHT_SURFACE_CONTAINER_LOWEST_COLOR),
        surfaceContainerLow = Color(LIGHT_SURFACE_CONTAINER_LOW_COLOR),
        surfaceContainer = Color(LIGHT_SURFACE_CONTAINER_COLOR),
        surfaceContainerHigh = Color(LIGHT_SURFACE_CONTAINER_HIGH_COLOR),
        surfaceContainerHighest = Color(LIGHT_SURFACE_CONTAINER_HIGHEST_COLOR),
        error = Color(LIGHT_ERROR_COLOR),
        onError = Color(LIGHT_ON_ERROR_COLOR),
        errorContainer = Color(LIGHT_ERROR_CONTAINER_COLOR),
        onErrorContainer = Color(LIGHT_ON_ERROR_CONTAINER_COLOR),
        outline = Color(LIGHT_OUTLINE_COLOR),
        outlineVariant = Color(LIGHT_OUTLINE_VARIANT_COLOR),
        inverseSurface = Color(LIGHT_INVERSE_SURFACE_COLOR),
        inverseOnSurface = Color(LIGHT_INVERSE_ON_SURFACE_COLOR),
        scrim = Color(LIGHT_SCRIM_COLOR),
    )

private val HermesDarkColorScheme =
    darkColorScheme(
        primary = Color(DARK_PRIMARY_COLOR),
        onPrimary = Color(DARK_ON_PRIMARY_COLOR),
        primaryContainer = Color(DARK_PRIMARY_CONTAINER_COLOR),
        onPrimaryContainer = Color(DARK_ON_PRIMARY_CONTAINER_COLOR),
        inversePrimary = Color(DARK_INVERSE_PRIMARY_COLOR),
        secondary = Color(DARK_SECONDARY_COLOR),
        onSecondary = Color(DARK_ON_SECONDARY_COLOR),
        secondaryContainer = Color(DARK_SECONDARY_CONTAINER_COLOR),
        onSecondaryContainer = Color(DARK_ON_SECONDARY_CONTAINER_COLOR),
        tertiary = Color(DARK_TERTIARY_COLOR),
        onTertiary = Color(DARK_ON_TERTIARY_COLOR),
        tertiaryContainer = Color(DARK_TERTIARY_CONTAINER_COLOR),
        onTertiaryContainer = Color(DARK_ON_TERTIARY_CONTAINER_COLOR),
        background = Color(DARK_BACKGROUND_COLOR),
        onBackground = Color(DARK_ON_BACKGROUND_COLOR),
        surface = Color(DARK_SURFACE_COLOR),
        onSurface = Color(DARK_ON_SURFACE_COLOR),
        surfaceVariant = Color(DARK_SURFACE_VARIANT_COLOR),
        onSurfaceVariant = Color(DARK_ON_SURFACE_VARIANT_COLOR),
        surfaceTint = Color(DARK_PRIMARY_COLOR),
        surfaceDim = Color(DARK_SURFACE_DIM_COLOR),
        surfaceBright = Color(DARK_SURFACE_BRIGHT_COLOR),
        surfaceContainerLowest = Color(DARK_SURFACE_CONTAINER_LOWEST_COLOR),
        surfaceContainerLow = Color(DARK_SURFACE_CONTAINER_LOW_COLOR),
        surfaceContainer = Color(DARK_SURFACE_CONTAINER_COLOR),
        surfaceContainerHigh = Color(DARK_SURFACE_CONTAINER_HIGH_COLOR),
        surfaceContainerHighest = Color(DARK_SURFACE_CONTAINER_HIGHEST_COLOR),
        error = Color(DARK_ERROR_COLOR),
        onError = Color(DARK_ON_ERROR_COLOR),
        errorContainer = Color(DARK_ERROR_CONTAINER_COLOR),
        onErrorContainer = Color(DARK_ON_ERROR_CONTAINER_COLOR),
        outline = Color(DARK_OUTLINE_COLOR),
        outlineVariant = Color(DARK_OUTLINE_VARIANT_COLOR),
        inverseSurface = Color(DARK_INVERSE_SURFACE_COLOR),
        inverseOnSurface = Color(DARK_INVERSE_ON_SURFACE_COLOR),
        scrim = Color(DARK_SCRIM_COLOR),
    )

private val HermesSpacingScale =
    HermesSpacing(
        xxs = 2.dp,
        xs = 4.dp,
        s = 8.dp,
        m = 12.dp,
        l = 16.dp,
        xl = 24.dp,
        xxl = 32.dp,
        xxxl = 48.dp,
    )

private val HermesElevationScale =
    HermesElevation(
        level0 = 0.dp,
        level1 = 1.dp,
        level2 = 3.dp,
        level3 = 6.dp,
        level4 = 8.dp,
        level5 = 12.dp,
    )

private val HermesLightSyntaxColors =
    HermesSyntaxColors(
        keyword = Color(LIGHT_SYNTAX_KEYWORD_COLOR),
        string = Color(LIGHT_SYNTAX_STRING_COLOR),
        number = Color(LIGHT_SYNTAX_NUMBER_COLOR),
        function = Color(LIGHT_SYNTAX_FUNCTION_COLOR),
        type = Color(LIGHT_SYNTAX_TYPE_COLOR),
        comment = Color(LIGHT_SYNTAX_COMMENT_COLOR),
        punctuation = Color(LIGHT_SYNTAX_PUNCTUATION_COLOR),
        codeSurface = Color(LIGHT_SYNTAX_CODE_SURFACE_COLOR),
    )

private val HermesDarkSyntaxColors =
    HermesSyntaxColors(
        keyword = Color(DARK_SYNTAX_KEYWORD_COLOR),
        string = Color(DARK_SYNTAX_STRING_COLOR),
        number = Color(DARK_SYNTAX_NUMBER_COLOR),
        function = Color(DARK_SYNTAX_FUNCTION_COLOR),
        type = Color(DARK_SYNTAX_TYPE_COLOR),
        comment = Color(DARK_SYNTAX_COMMENT_COLOR),
        punctuation = Color(DARK_SYNTAX_PUNCTUATION_COLOR),
        codeSurface = Color(DARK_SYNTAX_CODE_SURFACE_COLOR),
    )

private val HermesLightComponentColors =
    HermesComponentColors(
        inset = Color(LIGHT_SURFACE_CONTAINER_LOW_COLOR),
        sessionSelected = Color(LIGHT_SESSION_SELECTED_COLOR),
        onSessionSelected = Color(LIGHT_ON_SESSION_SELECTED_COLOR),
        onSessionSelectedDim = Color(LIGHT_ON_SESSION_SELECTED_DIM_COLOR),
    )

private val HermesDarkComponentColors =
    HermesComponentColors(
        inset = Color(DARK_SURFACE_CONTAINER_HIGH_COLOR),
        sessionSelected = Color(DARK_SESSION_SELECTED_COLOR),
        onSessionSelected = Color(DARK_ON_SESSION_SELECTED_COLOR),
        onSessionSelectedDim = Color(DARK_ON_SESSION_SELECTED_DIM_COLOR),
    )

val HermesLightTokens: HermesDesignTokens =
    HermesDesignTokens(
        colorScheme = HermesLightColorScheme,
        typography = HermesTypography,
        spacing = HermesSpacingScale,
        shapes = HermesShapes,
        elevation = HermesElevationScale,
        syntaxColors = HermesLightSyntaxColors,
        componentColors = HermesLightComponentColors,
    )

val HermesDarkTokens: HermesDesignTokens =
    HermesDesignTokens(
        colorScheme = HermesDarkColorScheme,
        typography = HermesTypography,
        spacing = HermesSpacingScale,
        shapes = HermesShapes,
        elevation = HermesElevationScale,
        syntaxColors = HermesDarkSyntaxColors,
        componentColors = HermesDarkComponentColors,
    )

/**
 * The current token set. Provided by [HermesTheme] and read by shell surfaces
 * that need spacing, elevation, syntax, or session row values.
 */
val LocalHermesDesignTokens = staticCompositionLocalOf { HermesLightTokens }
