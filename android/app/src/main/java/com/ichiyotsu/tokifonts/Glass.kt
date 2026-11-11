package com.ichiyotsu.tokifonts

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur

/**
 * Tokinote 同款单层毛玻璃配方：大模糊半径 + 很淡的两层 tint，模糊是主角，
 * 上面不再盖实色层。调用方只选半径和深浅，tint 与 fallback 集中在这里。
 *
 * 数值来源：Tokinote 的 FloatingNavBar / Glass 设计（浅/深两档）：
 * 模糊 30.dp、噪点 0.02；smoke 黑 0.03（浅）/ 0.16（深），frost 白 0.10（浅）/ 0.08（深）；
 * 设备不支持模糊时 fallback 黑 0.30（浅）/ 0.55（深）。
 */
fun Modifier.tokiGlass(
    hazeState: HazeState,
    blurRadius: Dp = 30.dp,
    dark: Boolean = false,
    noise: Float = 0.02f,
): Modifier = hazeBlur(
    input = HazeInput.Sources(hazeState),
    style = HazeBlurStyle {
        blurRadius(blurRadius)
        noiseFactor(noise)
        colorEffects(glassTints(dark).map { tint -> HazeColorEffect.tint(tint) })
        fallbackColorEffect(HazeColorEffect.tint(glassFallback(dark)))
    },
)

/** 两层 tint：先一层淡淡的黑烟，再一层淡淡的白霜，叠加后既提亮又压暗一点点。 */
fun glassTints(dark: Boolean): List<Color> = listOf(
    if (dark) Color.Black.copy(alpha = 0.16f) else Color.Black.copy(alpha = 0.03f),
    Color.White.copy(alpha = if (dark) 0.08f else 0.10f),
)

/** Haze 不可用时的纯色兜底。 */
fun glassFallback(dark: Boolean): Color =
    Color.Black.copy(alpha = if (dark) 0.55f else 0.30f)
