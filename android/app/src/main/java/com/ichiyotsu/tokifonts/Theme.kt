package com.ichiyotsu.tokifonts

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text

private val GoogleSans = FontFamily(
    Font(R.font.google_sans_400, FontWeight.Normal),
    Font(R.font.google_sans_500, FontWeight.Medium),
    Font(R.font.google_sans_700, FontWeight.Bold),
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun Typography.withFont(family: FontFamily): Typography {
    fun TextStyle.with() = copy(fontFamily = family)
    return Typography(
        displayLarge = displayLarge.with(),
        displayMedium = displayMedium.with(),
        displaySmall = displaySmall.with(),
        headlineLarge = headlineLarge.with(),
        headlineMedium = headlineMedium.with(),
        headlineSmall = headlineSmall.with(),
        titleLarge = titleLarge.with(),
        titleMedium = titleMedium.with(),
        titleSmall = titleSmall.with(),
        bodyLarge = bodyLarge.with(),
        bodyMedium = bodyMedium.with(),
        bodySmall = bodySmall.with(),
        labelLarge = labelLarge.with(),
        labelMedium = labelMedium.with(),
        labelSmall = labelSmall.with(),
        displayLargeEmphasized = displayLargeEmphasized.with(),
        displayMediumEmphasized = displayMediumEmphasized.with(),
        displaySmallEmphasized = displaySmallEmphasized.with(),
        headlineLargeEmphasized = headlineLargeEmphasized.with(),
        headlineMediumEmphasized = headlineMediumEmphasized.with(),
        headlineSmallEmphasized = headlineSmallEmphasized.with(),
        titleLargeEmphasized = titleLargeEmphasized.with(),
        titleMediumEmphasized = titleMediumEmphasized.with(),
        titleSmallEmphasized = titleSmallEmphasized.with(),
        bodyLargeEmphasized = bodyLargeEmphasized.with(),
        bodyMediumEmphasized = bodyMediumEmphasized.with(),
        bodySmallEmphasized = bodySmallEmphasized.with(),
        labelLargeEmphasized = labelLargeEmphasized.with(),
        labelMediumEmphasized = labelMediumEmphasized.with(),
        labelSmallEmphasized = labelSmallEmphasized.with(),
    )
}

private val ExpressiveDark = darkColorScheme(
    primary = Color(0xFFD0BCFF),
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B),
    onPrimaryContainer = Color(0xFFEADDFF),
    secondaryContainer = Color(0xFF4A4458),
    onSecondaryContainer = Color(0xFFE8DEF8),
    tertiaryContainer = Color(0xFF633B48),
    onTertiaryContainer = Color(0xFFFFD8E4),
    surface = Color(0xFF141218),
    onSurface = Color(0xFFF6F1FA),
    onSurfaceVariant = Color(0xFFE0D7EA),
    outline = Color(0xFFB4A9C4),
    outlineVariant = Color(0xFF6E6578),
    surfaceContainer = Color(0xFF211F26),
    surfaceContainerHigh = Color(0xFF2B2930),
    surfaceContainerHighest = Color(0xFF36343B),
    surfaceContainerLow = Color(0xFF1D1B20),
)

private val ExpressiveLight = lightColorScheme(
    primary = Color(0xFF6750A4),
    primaryContainer = Color(0xFFEADDFF),
    onPrimaryContainer = Color(0xFF21005D),
    secondaryContainer = Color(0xFFE8DEF8),
    tertiaryContainer = Color(0xFFFFD8E4),
    onTertiaryContainer = Color(0xFF31111D),
    surface = Color(0xFFFEF7FF),
    surfaceContainer = Color(0xFFF3EDF7),
    surfaceContainerHigh = Color(0xFFECE6F0),
    surfaceContainerHighest = Color(0xFFE6E0E9),
    surfaceContainerLow = Color(0xFFF7F2FA),
)

/** Material 3 Expressive：动态取色、表现力动效，界面字体用 Google Sans。 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TokiTheme(dark: Boolean = isSystemInDarkTheme(), dynamic: Boolean = true, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val scheme = runCatching {
        when {
            dynamic && dark -> dynamicDarkColorScheme(context)
            dynamic -> dynamicLightColorScheme(context)
            dark -> ExpressiveDark
            else -> ExpressiveLight
        }
    }.getOrElse { if (dark) ExpressiveDark else ExpressiveLight }.readableDarkText(dark)
    MaterialExpressiveTheme(
        colorScheme = scheme,
        motionScheme = MotionScheme.expressive(),
        typography = Typography().withFont(GoogleSans),
        content = content,
    )
}

/** 深色下把正文和次要文字抬到能看清的亮度。动态取色如果给得太暗，也按同样的下限补。 */
private fun androidx.compose.material3.ColorScheme.readableDarkText(dark: Boolean): androidx.compose.material3.ColorScheme {
    if (!dark) return this
    return copy(
        onSurface = if (onSurface.luminance() < 0.78f) Color(0xFFF6F1FA) else onSurface,
        onSurfaceVariant = if (onSurfaceVariant.luminance() < 0.62f) Color(0xFFE0D7EA) else onSurfaceVariant,
        outline = if (outline.luminance() < 0.32f) Color(0xFFB4A9C4) else outline,
    )
}

@OptIn(ExperimentalTextApi::class)
private val symbolFonts = HashMap<Int, FontFamily>()

@OptIn(ExperimentalTextApi::class)
private fun symbolFont(fill: Int, weight: Int) = symbolFonts.getOrPut(fill * 1000 + weight) {
    FontFamily(
        Font(
            R.font.material_symbols,
            variationSettings = FontVariation.Settings(
                FontVariation.Setting("FILL", fill.toFloat()),
                FontVariation.weight(weight),
            ),
        ),
    )
}

@OptIn(ExperimentalTextApi::class)
@Composable
fun Symbol(
    name: String,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    size: Dp = 24.dp,
    tint: Color = LocalContentColor.current,
    weight: Int = 400,
) {
    val fill by animateFloatAsState(if (filled) 1f else 0f, label = "fill")
    Text(
        name,
        modifier = modifier,
        color = tint,
        fontSize = size.value.sp,
        lineHeight = size.value.sp,
        fontFamily = symbolFont(if (fill > 0.5f) 1 else 0, weight),
    )
}
