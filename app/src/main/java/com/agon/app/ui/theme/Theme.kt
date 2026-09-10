package com.agon.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agon.app.R

private val DarkColorScheme = darkColorScheme(
    primary = ShieldPrimaryDark,
    onPrimary = ShieldOnPrimaryDark,
    primaryContainer = ShieldPrimaryContainerDark,
    onPrimaryContainer = ShieldOnPrimaryContainerDark,
    secondary = ShieldSecondaryDark,
    onSecondary = ShieldOnSecondaryDark,
    secondaryContainer = ShieldSecondaryContainerDark,
    onSecondaryContainer = ShieldOnSecondaryContainerDark,
    tertiary = ShieldTertiaryDark,
    onTertiary = ShieldOnTertiaryDark,
    tertiaryContainer = ShieldTertiaryContainerDark,
    onTertiaryContainer = ShieldOnTertiaryContainerDark,
    error = ShieldErrorDark,
    onError = ShieldOnErrorDark,
    errorContainer = ShieldErrorContainerDark,
    onErrorContainer = ShieldOnErrorContainerDark,
    background = ShieldBackgroundDark,
    onBackground = ShieldOnBackgroundDark,
    surface = ShieldSurfaceDark,
    onSurface = ShieldOnSurfaceDark,
    surfaceVariant = ShieldSurfaceVariantDark,
    onSurfaceVariant = ShieldOnSurfaceVariantDark,
    outline = ShieldOutlineDark,
    outlineVariant = ShieldOutlineVariantDark,
    surfaceDim = ShieldSurfaceDimDark,
    surfaceBright = ShieldSurfaceBrightDark,
    surfaceContainerLowest = ShieldSurfaceContainerLowestDark,
    surfaceContainerLow = ShieldSurfaceContainerLowDark,
    surfaceContainer = ShieldSurfaceContainerDark,
    surfaceContainerHigh = ShieldSurfaceContainerHighDark,
    surfaceContainerHighest = ShieldSurfaceContainerHighestDark,
    inverseSurface = ShieldInverseSurfaceDark,
    inverseOnSurface = ShieldInverseOnSurfaceDark,
    inversePrimary = ShieldInversePrimaryDark,
    scrim = Color.Black,
)

private val LightColorScheme = lightColorScheme(
    primary = ShieldPrimaryLight,
    onPrimary = ShieldOnPrimaryLight,
    primaryContainer = ShieldPrimaryContainerLight,
    onPrimaryContainer = ShieldOnPrimaryContainerLight,
    secondary = ShieldSecondaryLight,
    onSecondary = ShieldOnSecondaryLight,
    secondaryContainer = ShieldSecondaryContainerLight,
    onSecondaryContainer = ShieldOnSecondaryContainerLight,
    tertiary = ShieldTertiaryLight,
    onTertiary = ShieldOnTertiaryLight,
    tertiaryContainer = ShieldTertiaryContainerLight,
    onTertiaryContainer = ShieldOnTertiaryContainerLight,
    error = ShieldErrorLight,
    onError = ShieldOnErrorLight,
    errorContainer = ShieldErrorContainerLight,
    onErrorContainer = ShieldOnErrorContainerLight,
    background = ShieldBackgroundLight,
    onBackground = ShieldOnBackgroundLight,
    surface = ShieldSurfaceLight,
    onSurface = ShieldOnSurfaceLight,
    surfaceVariant = ShieldSurfaceVariantLight,
    onSurfaceVariant = ShieldOnSurfaceVariantLight,
    outline = ShieldOutlineLight,
    outlineVariant = ShieldOutlineVariantLight,
    surfaceDim = ShieldSurfaceDimLight,
    surfaceBright = ShieldSurfaceBrightLight,
    surfaceContainerLowest = ShieldSurfaceContainerLowestLight,
    surfaceContainerLow = ShieldSurfaceContainerLowLight,
    surfaceContainer = ShieldSurfaceContainerLight,
    surfaceContainerHigh = ShieldSurfaceContainerHighLight,
    surfaceContainerHighest = ShieldSurfaceContainerHighestLight,
    inverseSurface = ShieldInverseSurfaceLight,
    inverseOnSurface = ShieldInverseOnSurfaceLight,
    inversePrimary = ShieldInversePrimaryLight,
    scrim = Color.Black,
)

/**
 * Noto Sans Arabic carries Kurdish Sorani (ڕ ڵ ڤ ێ), Arabic and Latin in one
 * variable file, so every weight below renders with correct glyph shaping.
 * On API 26+ the variable `wght` axis is honoured; older devices gracefully
 * fall back to the regular instance with synthesised bold.
 */
private val ShieldFontFamily = FontFamily(
    Font(R.font.noto_sans_arabic, weight = FontWeight.Normal),
    Font(R.font.noto_sans_arabic, weight = FontWeight.Medium),
    Font(R.font.noto_sans_arabic, weight = FontWeight.SemiBold),
    Font(R.font.noto_sans_arabic, weight = FontWeight.Bold),
    Font(R.font.noto_sans_arabic, weight = FontWeight.ExtraBold),
)

private val PremiumTypography = Typography(
    displayLarge = TextStyle(fontFamily = ShieldFontFamily, fontWeight = FontWeight.ExtraBold, fontSize = 44.sp, lineHeight = 50.sp, letterSpacing = (-0.6).sp),
    displayMedium = TextStyle(fontFamily = ShieldFontFamily, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.4).sp),
    displaySmall = TextStyle(fontFamily = ShieldFontFamily, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.3).sp),
    headlineLarge = TextStyle(fontFamily = ShieldFontFamily, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.2).sp),
    headlineMedium = TextStyle(fontFamily = ShieldFontFamily, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
    headlineSmall = TextStyle(fontFamily = ShieldFontFamily, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 24.sp),
    titleLarge = TextStyle(fontFamily = ShieldFontFamily, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 24.sp),
    titleMedium = TextStyle(fontFamily = ShieldFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 21.sp, letterSpacing = 0.1.sp),
    titleSmall = TextStyle(fontFamily = ShieldFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp),
    bodyLarge = TextStyle(fontFamily = ShieldFontFamily, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = ShieldFontFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = ShieldFontFamily, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontFamily = ShieldFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.4.sp),
    labelMedium = TextStyle(fontFamily = ShieldFontFamily, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
    labelSmall = TextStyle(fontFamily = ShieldFontFamily, fontWeight = FontWeight.Medium, fontSize = 10.sp, lineHeight = 14.sp, letterSpacing = 0.5.sp),
)

private val PremiumShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun AgonAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = PremiumTypography,
        shapes = PremiumShapes,
        content = content,
    )
}
