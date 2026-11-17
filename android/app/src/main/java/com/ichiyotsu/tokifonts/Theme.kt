package com.ichiyotsu.tokifonts

import android.app.Activity
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/** 是否处于深色（玻璃配方等需要知道时读）。 */
val LocalDarkTheme = staticCompositionLocalOf { false }

/**
 * 卡片形状：全 App 定死一档 —— 形状阶梯的 extraLarge（28dp 对称大圆角矩形）。
 * 功能界面不使用非对称圆角。
 */
val TokiCardShape: Shape = RoundedCornerShape(28.dp)

private fun Typography.withTokiType(family: FontFamily): Typography {
    fun TextStyle.bold() = copy(fontFamily = family, fontWeight = FontWeight.Bold)
    fun TextStyle.regular() = copy(fontFamily = family, fontWeight = FontWeight.Normal)
    return Typography(
        displayLarge = displayLarge.bold(), displayMedium = displayMedium.bold(), displaySmall = displaySmall.bold(),
        headlineLarge = headlineLarge.bold(), headlineMedium = headlineMedium.bold(), headlineSmall = headlineSmall.bold(),
        titleLarge = titleLarge.bold(), titleMedium = titleMedium.bold(), titleSmall = titleSmall.bold(),
        bodyLarge = bodyLarge.regular(), bodyMedium = bodyMedium.regular(), bodySmall = bodySmall.regular(),
        labelLarge = labelLarge.bold(), labelMedium = labelMedium.bold(), labelSmall = labelSmall.bold(),
    )
}

/**
 * 主题：Material 3 Expressive 官方入口 MaterialExpressiveTheme。
 *（对照 TokiStudio `ui/theme/Theme.kt`，同一 commit 的 TokiUI 仓库。）
 *
 * - colorScheme：动态取色或种子色 HCT 生成（见 Color.kt）；
 * - motionScheme：MotionScheme.expressive()；「减少动效」时切回
 *   MotionScheme.standard() —— MD3E 的两套官方弹簧方案，全 App 组件与
 *   自定义动效都读 MaterialTheme.motionScheme 的规格，不再有手写弹簧常数；
 * - shapes：MD3E 官方形状阶梯（Shapes() 默认值）；
 * - typography：M3 官方字阶的字号和行高。字体族是 Inter + 思源黑体。
 *   标题、标签用 FontWeight.Bold（wght 700）；正文用 FontWeight.Normal（wght 400）。
 *   层级靠字号和行距，不再在 regular 和 medium 之间混一档。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TokiTheme(
    dark: Boolean = isSystemInDarkTheme(),
    dynamic: Boolean = true,
    seedIndex: Int = 0,
    reduceMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    val scheme = resolveColorScheme(dark, dynamic, seedIndex)
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
    CompositionLocalProvider(LocalDarkTheme provides dark) {
        MaterialExpressiveTheme(
            colorScheme = scheme,
            motionScheme = if (reduceMotion) MotionScheme.standard() else MotionScheme.expressive(),
            shapes = Shapes(),
            typography = Typography().withTokiType(InterFamily),
            content = content,
        )
    }
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
