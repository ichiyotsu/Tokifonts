package com.ichiyotsu.tokifonts

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import com.materialkolor.dynamiccolor.MaterialDynamicColors
import com.materialkolor.hct.Hct
import com.materialkolor.scheme.SchemeTonalSpot

/**
 * 色彩：完全走 MD3/MD3E 官方路线，没有任何手写的静态色值表。
 *（对照 TokiStudio `ui/theme/Color.kt`，同一 commit 的 TokiUI 仓库。）
 *
 * 1. 动态取色（默认，Android 12+）：平台的 dynamicLight/DarkColorScheme，
 *    由系统壁纸经 Material You 的 HCT 方案生成。
 * 2. 种子色方案：用 Material Color Utilities 的官方 HCT 算法
 *    （com.materialkolor:material-color-utilities 是 Google 官方
 *    material-color-utilities 的 Kotlin 实现）以种子色运行 SchemeTonalSpot
 *    ——与 Android 平台生成动态色时所用的同一套方案与色调表——再把
 *    MaterialDynamicColors 的全部角色映射为完整的 Compose ColorScheme。
 *    种子列表来自语义 token（见 [TokiUiTokens]），不是手抄的色值。
 *
 * 3. 深色模式的底色对比微调（[ColorScheme.withDarkContrast]）：
 *    官方深色阶梯里背景与容器只差几档色调，卡片浮不起来；这里把背景与
 *    surface 压暗一点、把 surfaceContainer 阶梯抬起一点，色差只大一点点，
 *    色相与饱和度不动。浅色模式不做任何改动。
 */

/** 由种子色生成完整 ColorScheme（HCT / SchemeTonalSpot，官方算法）。 */
fun schemeFromSeed(seed: Color, dark: Boolean): ColorScheme {
    val scheme = SchemeTonalSpot(Hct.fromInt(seed.toArgb()), dark, 0.0)
    val m = MaterialDynamicColors()
    fun c(get: MaterialDynamicColors.() -> com.materialkolor.dynamiccolor.DynamicColor): Color =
        Color(m.get().getArgb(scheme))
    return if (dark) {
        darkColorScheme(
            primary = c { primary() }, onPrimary = c { onPrimary() },
            primaryContainer = c { primaryContainer() }, onPrimaryContainer = c { onPrimaryContainer() },
            inversePrimary = c { inversePrimary() },
            secondary = c { secondary() }, onSecondary = c { onSecondary() },
            secondaryContainer = c { secondaryContainer() }, onSecondaryContainer = c { onSecondaryContainer() },
            tertiary = c { tertiary() }, onTertiary = c { onTertiary() },
            tertiaryContainer = c { tertiaryContainer() }, onTertiaryContainer = c { onTertiaryContainer() },
            background = c { background() }, onBackground = c { onBackground() },
            surface = c { surface() }, onSurface = c { onSurface() },
            surfaceVariant = c { surfaceVariant() }, onSurfaceVariant = c { onSurfaceVariant() },
            surfaceDim = c { surfaceDim() }, surfaceBright = c { surfaceBright() },
            surfaceContainerLowest = c { surfaceContainerLowest() }, surfaceContainerLow = c { surfaceContainerLow() },
            surfaceContainer = c { surfaceContainer() }, surfaceContainerHigh = c { surfaceContainerHigh() },
            surfaceContainerHighest = c { surfaceContainerHighest() },
            inverseSurface = c { inverseSurface() }, inverseOnSurface = c { inverseOnSurface() },
            error = c { error() }, onError = c { onError() },
            errorContainer = c { errorContainer() }, onErrorContainer = c { onErrorContainer() },
            outline = c { outline() }, outlineVariant = c { outlineVariant() }, scrim = c { scrim() },
            surfaceTint = c { surfaceTint() },
            primaryFixed = c { primaryFixed() }, primaryFixedDim = c { primaryFixedDim() },
            onPrimaryFixed = c { onPrimaryFixed() }, onPrimaryFixedVariant = c { onPrimaryFixedVariant() },
            secondaryFixed = c { secondaryFixed() }, secondaryFixedDim = c { secondaryFixedDim() },
            onSecondaryFixed = c { onSecondaryFixed() }, onSecondaryFixedVariant = c { onSecondaryFixedVariant() },
            tertiaryFixed = c { tertiaryFixed() }, tertiaryFixedDim = c { tertiaryFixedDim() },
            onTertiaryFixed = c { onTertiaryFixed() }, onTertiaryFixedVariant = c { onTertiaryFixedVariant() },
        )
    } else {
        lightColorScheme(
            primary = c { primary() }, onPrimary = c { onPrimary() },
            primaryContainer = c { primaryContainer() }, onPrimaryContainer = c { onPrimaryContainer() },
            inversePrimary = c { inversePrimary() },
            secondary = c { secondary() }, onSecondary = c { onSecondary() },
            secondaryContainer = c { secondaryContainer() }, onSecondaryContainer = c { onSecondaryContainer() },
            tertiary = c { tertiary() }, onTertiary = c { onTertiary() },
            tertiaryContainer = c { tertiaryContainer() }, onTertiaryContainer = c { onTertiaryContainer() },
            background = c { background() }, onBackground = c { onBackground() },
            surface = c { surface() }, onSurface = c { onSurface() },
            surfaceVariant = c { surfaceVariant() }, onSurfaceVariant = c { onSurfaceVariant() },
            surfaceDim = c { surfaceDim() }, surfaceBright = c { surfaceBright() },
            surfaceContainerLowest = c { surfaceContainerLowest() }, surfaceContainerLow = c { surfaceContainerLow() },
            surfaceContainer = c { surfaceContainer() }, surfaceContainerHigh = c { surfaceContainerHigh() },
            surfaceContainerHighest = c { surfaceContainerHighest() },
            inverseSurface = c { inverseSurface() }, inverseOnSurface = c { inverseOnSurface() },
            error = c { error() }, onError = c { onError() },
            errorContainer = c { errorContainer() }, onErrorContainer = c { onErrorContainer() },
            outline = c { outline() }, outlineVariant = c { outlineVariant() }, scrim = c { scrim() },
            surfaceTint = c { surfaceTint() },
            primaryFixed = c { primaryFixed() }, primaryFixedDim = c { primaryFixedDim() },
            onPrimaryFixed = c { onPrimaryFixed() }, onPrimaryFixedVariant = c { onPrimaryFixedVariant() },
            secondaryFixed = c { secondaryFixed() }, secondaryFixedDim = c { secondaryFixedDim() },
            onSecondaryFixed = c { onSecondaryFixed() }, onSecondaryFixedVariant = c { onSecondaryFixedVariant() },
            tertiaryFixed = c { tertiaryFixed() }, tertiaryFixedDim = c { tertiaryFixedDim() },
            onTertiaryFixed = c { onTertiaryFixed() }, onTertiaryFixedVariant = c { onTertiaryFixedVariant() },
        )
    }
}

/** 当前是否可用平台动态取色（Android 12 / API 31 起；minSdk 36 上恒为真）。 */
val supportsDynamicColor: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/** `#RRGGBB` 种子色值转不透明 [Color]。 */
internal fun seedColor(value: String): Color {
    val hex = value.removePrefix("#")
    require(hex.length == 6) { "种子色不是 #RRGGBB：$value" }
    return Color(("FF$hex").toLong(16).toInt())
}

/**
 * 解析当前 ColorScheme：
 * - useDynamic = true 且系统支持 → 平台动态取色；
 * - 否则用选中的种子色生成（种子列表来自语义 token）。
 * 深色模式下两种来源都过一遍底色对比微调（见文件头说明）。
 */
@Composable
fun resolveColorScheme(dark: Boolean, useDynamic: Boolean, seedIndex: Int): ColorScheme {
    val context = LocalContext.current
    val base = if (useDynamic && supportsDynamicColor) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        val seeds = TokiUiTokens.seeds(context)
        val seed = seeds[seedIndex.coerceIn(0, seeds.lastIndex)]
        schemeFromSeed(seedColor(seed.value), dark)
    }
    return if (dark) base.withDarkContrast() else base
}

/**
 * 深色底色对比微调：背景与 surface 压暗 8%，surfaceContainer 阶梯从
 * 最低档的 3% 起、每档多抬一点、最高档 9%。叠黑/叠白只改变明度，
 * 色相与饱和度保持官方方案的原样。
 */
private fun ColorScheme.withDarkContrast(): ColorScheme {
    fun Color.darken(alpha: Float): Color = Color.Black.copy(alpha = alpha).compositeOver(this)
    fun Color.lighten(alpha: Float): Color = Color.White.copy(alpha = alpha).compositeOver(this)
    return copy(
        background = background.darken(0.08f),
        surface = surface.darken(0.08f),
        surfaceDim = surfaceDim.darken(0.08f),
        surfaceContainerLowest = surfaceContainerLowest.lighten(0.03f),
        surfaceContainerLow = surfaceContainerLow.lighten(0.04f),
        surfaceContainer = surfaceContainer.lighten(0.05f),
        surfaceContainerHigh = surfaceContainerHigh.lighten(0.07f),
        surfaceContainerHighest = surfaceContainerHighest.lighten(0.09f),
    )
}

/**
 * 全 App 页面底色：surface 家族内部的纵向台阶——不再用纯平的主题色，
 * 改走 surfaceContainer → surface 的同色系内部过渡；底部不再落到
 * background（深色下近黑），与 TokiUI 0.1.11 的页面渐变一致。
 * 深色模式同一套 role 自动是深色系内部过渡。深色下台阶的对比由
 * [ColorScheme.withDarkContrast] 统一抬起，这里不另写数值。
 */
@Composable
fun tokiPageBackground(): Brush {
    val scheme = MaterialTheme.colorScheme
    return Brush.verticalGradient(listOf(scheme.surfaceContainer, scheme.surface))
}
