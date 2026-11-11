package com.ichiyotsu.tokifonts

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
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
import android.app.Activity
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
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
    // 字阶覆写照 Tokinote 的 Type.kt：大标题更紧凑有力，其余走 M3 默认。
    return Typography(
        displayLarge = displayLarge.with().copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-1.5).sp),
        displayMedium = displayMedium.with(),
        displaySmall = displaySmall.with(),
        headlineLarge = headlineLarge.with().copy(
            fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.4).sp,
        ),
        headlineMedium = headlineMedium.with().copy(
            fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.3).sp,
        ),
        headlineSmall = headlineSmall.with(),
        titleLarge = titleLarge.with().copy(fontWeight = FontWeight.Medium),
        titleMedium = titleMedium.with().copy(fontWeight = FontWeight.SemiBold),
        titleSmall = titleSmall.with().copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = bodyLarge.with().copy(lineHeight = 26.sp),
        bodyMedium = bodyMedium.with(),
        bodySmall = bodySmall.with(),
        labelLarge = labelLarge.with().copy(fontWeight = FontWeight.SemiBold),
        labelMedium = labelMedium.with().copy(fontWeight = FontWeight.SemiBold),
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

/** Tokinote 的圆角刻度：比 M3 默认更圆，共 8 档。 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
val TokiShapes = androidx.compose.material3.Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(36.dp),
    largeIncreased = RoundedCornerShape(32.dp),
    extraLargeIncreased = RoundedCornerShape(44.dp),
    extraExtraLarge = RoundedCornerShape(56.dp),
)

/** 字体卡片：四角不对称，避免通用圆角的“模板感”。 */
val ExpressiveCardShape = RoundedCornerShape(
    topStart = 34.dp,
    topEnd = 22.dp,
    bottomEnd = 34.dp,
    bottomStart = 22.dp,
)

/** 分组胶囊：一端更圆、一端收紧，像一片被压过的叶子。 */
val ExpressiveChipShape = RoundedCornerShape(
    topStart = 20.dp,
    topEnd = 20.dp,
    bottomEnd = 20.dp,
    bottomStart = 7.dp,
)

/**
 * Tokinote 的 Matcha 配色（种子色橄榄绿，取自 Tomato/nsh07 的 M3 Expressive token）：
 * 动态取色关掉时用这套；动态取色开着时仍走系统取色，和 Tokinote 一致。
 */
private val ExpressiveDark = darkColorScheme(
    primary = Color(0xFFB1D18A),
    onPrimary = Color(0xFF1F3701),
    primaryContainer = Color(0xFF354E16),
    onPrimaryContainer = Color(0xFFCDEDA3),
    secondary = Color(0xFFBFCBAD),
    onSecondary = Color(0xFF2A331E),
    secondaryContainer = Color(0xFF404A33),
    onSecondaryContainer = Color(0xFFDCE7C8),
    tertiary = Color(0xFFA0D0CB),
    onTertiary = Color(0xFF003735),
    tertiaryContainer = Color(0xFF1F4E4B),
    onTertiaryContainer = Color(0xFFBCECE7),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF12140E),
    onBackground = Color(0xFFE2E3D8),
    surface = Color(0xFF12140E),
    onSurface = Color(0xFFE2E3D8),
    surfaceVariant = Color(0xFF44483D),
    onSurfaceVariant = Color(0xFFC5C8BA),
    outline = Color(0xFF8F9285),
    outlineVariant = Color(0xFF44483D),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFFE2E3D8),
    inverseOnSurface = Color(0xFF2F312A),
    inversePrimary = Color(0xFF4C662B),
    surfaceDim = Color(0xFF12140E),
    surfaceBright = Color(0xFF383A32),
    surfaceContainerLowest = Color(0xFF0C0F09),
    surfaceContainerLow = Color(0xFF1A1C16),
    surfaceContainer = Color(0xFF1E201A),
    surfaceContainerHigh = Color(0xFF282B24),
    surfaceContainerHighest = Color(0xFF33362E),
)

private val ExpressiveLight = lightColorScheme(
    primary = Color(0xFF4C662B),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCDEDA3),
    onPrimaryContainer = Color(0xFF354E16),
    secondary = Color(0xFF586249),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDCE7C8),
    onSecondaryContainer = Color(0xFF404A33),
    tertiary = Color(0xFF386663),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFBCECE7),
    onTertiaryContainer = Color(0xFF1F4E4B),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    background = Color(0xFFF9FAEF),
    onBackground = Color(0xFF1A1C16),
    surface = Color(0xFFF9FAEF),
    onSurface = Color(0xFF1A1C16),
    surfaceVariant = Color(0xFFE1E4D5),
    onSurfaceVariant = Color(0xFF44483D),
    outline = Color(0xFF75796C),
    outlineVariant = Color(0xFFC5C8BA),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFF2F312A),
    inverseOnSurface = Color(0xFFF1F2E6),
    inversePrimary = Color(0xFFB1D18A),
    surfaceDim = Color(0xFFDADBD0),
    surfaceBright = Color(0xFFF9FAEF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF3F4E9),
    surfaceContainer = Color(0xFFEEEFE3),
    surfaceContainerHigh = Color(0xFFE8E9DE),
    surfaceContainerHighest = Color(0xFFE2E3D8),
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
    }.getOrElse { if (dark) ExpressiveDark else ExpressiveLight }.ensureTextContrast(dark)
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialExpressiveTheme(
        colorScheme = scheme,
        shapes = TokiShapes,
        motionScheme = MotionScheme.expressive(),
        typography = Typography().withFont(GoogleSans),
        content = content,
    )
}

private fun androidx.compose.material3.ColorScheme.ensureTextContrast(dark: Boolean): androidx.compose.material3.ColorScheme {
    // 动态取色可能给出对比不足的组合：成对的容器色也按 0.45 的亮度差兜底，
    // 不只补 onSurface 三个槽（教程卡片用的 primaryContainer/onPrimaryContainer 也在内）。
    return copy(
        onSurface = if (dark) {
            if (onSurface.luminance() < 0.72f) Color(0xFFF6F1FA) else onSurface
        } else {
            if (onSurface.luminance() > 0.42f) Color(0xFF1C1B1F) else onSurface
        },
        onSurfaceVariant = if (dark) {
            if (onSurfaceVariant.luminance() < 0.55f) Color(0xFFE0D7EA) else onSurfaceVariant
        } else {
            if (onSurfaceVariant.luminance() > 0.5f) Color(0xFF49454F) else onSurfaceVariant
        },
        onBackground = if (dark) {
            if (onBackground.luminance() < 0.72f) Color(0xFFF6F1FA) else onBackground
        } else {
            if (onBackground.luminance() > 0.42f) Color(0xFF1C1B1F) else onBackground
        },
        onPrimaryContainer = contrasting(primaryContainer, onPrimaryContainer),
        onSecondaryContainer = contrasting(secondaryContainer, onSecondaryContainer),
        onTertiaryContainer = contrasting(tertiaryContainer, onTertiaryContainer),
    )
}

internal fun contrasting(background: Color, foreground: Color): Color {
    if (kotlin.math.abs(background.luminance() - foreground.luminance()) >= 0.45f) return foreground
    return if (background.luminance() > 0.5f) Color(0xFF1C1B1F) else Color(0xFFF6F1FA)
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
