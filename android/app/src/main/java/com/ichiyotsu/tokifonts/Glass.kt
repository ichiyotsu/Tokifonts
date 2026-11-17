package com.ichiyotsu.tokifonts

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeFeatureFlags
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur

/**
 * 全局统一的玻璃（高斯模糊）参数与修饰符 —— 全 App 只有这一处定义模糊，
 * 悬浮底栏、滚动标题色块共用同一个 [tokiGlass]。
 *（对照 TokiStudio `ui/theme/Glass.kt`，同一 commit 的 TokiUI 仓库。）
 *
 * - 模糊半径 **24dp**：落在 20–30dp 区间内。半径越大背景越糊、玻璃上的
 *   文字越容易发虚；24dp 仍有明确的高斯模糊感，同时底栏未选中图标/文字的
 *   边缘保持清楚。
 * - frost：深色仍是一层 20% 白霜，不加黑罩。浅色在白霜下多一层很轻的暖色暗罩，
 *   白霜也略加到 46%，让浅色玻璃比过透的白更沉一点，背后内容少透一些，
 *   但盖不住内容，仍然是玻璃而不是实色条。
 * - fallback（模糊不可用时兜底）：浅色 黑 30%、深色 黑 45%。正常路径不走它。
 * - noise 0.02：极轻的噪点，压掉模糊渐变的色带。
 *
 * 玻璃只画一层：消费方把 [tokiGlass] 挂在已裁好形状的容器上，不再叠加
 * 任何半透明 Surface / background —— 多层叠加正是模糊发闷、文字发虚的
 * 常见根源。选中态等需要强调的元素在玻璃之上另画实色（见悬浮底栏的
 * primary pill），可读性不依赖模糊本身。
 */
/**
 * 窗口内背景采样是否可能成立。
 *
 * Compose [androidx.compose.ui.draw.blur] 和 Haze 的 [HazeInput.Content]
 * 都只模糊控件自己画出来的那一层，不能当作悬浮栏背后的实时模糊。
 * 底栏走 [HazeInput.Backdrop]：先采同一窗口里已经画好的像素，采不到时
 * 再回退到挂了 `hazeSource` 的内容根。底层仍是 API 31 的 RenderEffect。
 * minSdk 36 上条件为真；能力判断仍集中在这一处。
 */
@OptIn(ExperimentalHazeApi::class)
object TokiBlurCapability {
    init {
        // 必须在效果节点挂上之前打开。true 只是让原生窗口背景采样有资格，
        // 设备不支持时 Haze 会改用下面的内容根回退，不会退回 Modifier.blur。
        HazeFeatureFlags.isPlatformBackdropEnabled = true
    }

    val canSampleInWindow: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
}

/**
 * 悬浮胶囊、独立按钮共用的表面。
 * 开启且设备能采样时是 Haze 背景模糊；否则是不透明的 surfaceContainer。
 */
@Composable
fun tokiChrome(state: HazeState, backdropBlur: Boolean): Modifier {
    return if (backdropBlur && TokiBlurCapability.canSampleInWindow) {
        Modifier.tokiGlass(state)
    } else {
        Modifier.background(MaterialTheme.colorScheme.surfaceContainer)
    }
}

object TokiGlassParams {
    val blurRadius: Dp = 24.dp
    const val noise: Float = 0.02f

    /**
     * 磨砂罩色。深色维持一层薄白霜，不再加黑罩。浅色先铺一层很轻的暖色暗罩，
     * 再盖略强一点的白霜：比原先 40% 纯白更有存在感，也稍深，但两层加起来
     * 仍然透得见背后的内容。
     */
    fun tints(dark: Boolean): List<HazeColorEffect> = if (dark) {
        listOf(HazeColorEffect.tint(Color.White.copy(alpha = 0.20f)))
    } else {
        listOf(
            HazeColorEffect.tint(Color(0xFF2A2622).copy(alpha = 0.16f)),
            HazeColorEffect.tint(Color.White.copy(alpha = 0.46f)),
        )
    }

    /** 兜底色：模糊这条路径不可用时，退化为这层纯色 tint。 */
    fun fallback(dark: Boolean): Color = Color.Black.copy(alpha = if (dark) 0.45f else 0.30f)
}

/** 全 App 共用的模糊样式。 [fade] 非空时沿纵向做渐进模糊（只给顶部淡化带）。 */
internal fun tokiBlurStyle(dark: Boolean, fade: HazeProgressive? = null): HazeBlurStyle = HazeBlurStyle {
    blurEnabled(true)
    blurRadius(TokiGlassParams.blurRadius)
    noiseFactor(TokiGlassParams.noise)
    backgroundColor(Color.Transparent)
    colorEffects(TokiGlassParams.tints(dark))
    fallbackColorEffect(HazeColorEffect.tint(TokiGlassParams.fallback(dark)))
    if (fade != null) progressive(fade)
}

/**
 * 把当前容器变成一层玻璃：模糊其身后由 [state] 收集的内容（内容根挂
 * `Modifier.hazeSource(state)`）。调用方负责先裁形状（如胶囊的全圆角），
 * 本修饰符不画任何额外的实色背景。
 */
@Composable
fun Modifier.tokiGlass(state: HazeState): Modifier {
    val dark = LocalDarkTheme.current
    return hazeBlur(
        input = HazeInput.Backdrop(state),
        style = tokiBlurStyle(dark),
        // 胶囊面积很小，用全分辨率采样，避免降采样把身后的内容糊成一块平色。
        performanceMode = HazePerformanceMode.Quality,
    )
}

/**
 * 顶部模糊渐变淡化带的延伸高度。底栏不再挂全宽淡化带：那一层盖住胶囊
 * 背后的内容，磨砂看起来就像一块实色。胶囊自己的 [tokiGlass] 才是底栏模糊。
 */
object TokiGlassFade {
    val topFadeExtra: Dp = 56.dp
}

/**
 * 渐变淡化玻璃：与 [tokiGlass] 同一套参数（半径/tint/fallback/noise 一个
 * 不改，文字可读优先），只多了 Haze 2.0.1 的 progressive 渐进模糊——
 * 模糊强度沿纵向线性变化，而不是整块均匀模糊：
 * - [fadeFromTop] = true（顶部带）：顶边 intensity 1（满格模糊）→ 底边 0；
 * - false（底部带）：顶边 0 → 底边 1（越靠近底栏越糊）。
 * progressive 是库的一等能力（HazeBlurStyleScope.progressive），强度是真
 * 的模糊半径缩放，不是拿半透明遮罩盖一层均匀模糊冒充。
 */
@Composable
fun Modifier.tokiGlassFade(state: HazeState, fadeFromTop: Boolean): Modifier {
    val dark = LocalDarkTheme.current
    val fade = if (fadeFromTop) {
        HazeProgressive.LinearGradient(
            start = Offset(0f, 0f), startIntensity = 1f,
            end = Offset(0f, 1f), endIntensity = 0f,
        )
    } else {
        HazeProgressive.LinearGradient(
            start = Offset(0f, 0f), startIntensity = 0f,
            end = Offset(0f, 1f), endIntensity = 1f,
        )
    }
    return hazeBlur(
        input = HazeInput.Backdrop(state),
        style = tokiBlurStyle(dark, fade),
        performanceMode = HazePerformanceMode.Quality,
    )
}

/**
 * 实心底栏描边的粗细。玻璃打开时底栏和滚动标题都不加这道描边。
 * 玻璃关闭或采样不可用时，底栏与滚动标题沿用同一道 [width]。
 */
object TokiChromeStroke {
    val width: Dp = 3.dp
    val barGap: Dp = 6.dp
}

/**
 * 滚动后出现的大标题 / 返回色块。
 *
 * 能采样时只铺与底栏相同的磨砂：[tokiGlass] 模糊身后内容，不再叠第二层填色，
 * 也不加粗描边（玻璃打开时底栏同样没有描边）。标题由调用方画在这一层上面。
 * 不能采样时改为不透明 `surfaceContainer`，并加上与实心底栏同粗的强调色描边。
 *
 * [applyBlur] 为真时色块自己采样；调用方已经铺了玻璃时传 false，避免叠两层模糊。
 *
 * 注：Tokifonts 当前各页的标题都在滚动内容内部、没有悬浮顶栏，内容不会滑到
 * 标题或返回键后面，所以暂无调用方；本组件随玻璃层落地，供后续悬浮顶栏使用。
 */
@Composable
fun TokiScrollChipBackdrop(
    shape: Shape,
    cover: Float,
    backdropBlur: Boolean,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    applyBlur: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val frosted = backdropBlur && TokiBlurCapability.canSampleInWindow
    val canBlur = applyBlur && hazeState != null && cover > 0.01f && frosted
    Box(
        modifier
            .clip(shape)
            .then(if (canBlur) Modifier.tokiGlass(hazeState) else Modifier)
            .then(
                if (!frosted && cover > 0f) {
                    Modifier
                        .background(scheme.surfaceContainer.copy(alpha = cover))
                        .border(TokiChromeStroke.width, scheme.primary.copy(alpha = cover), shape)
                } else {
                    Modifier
                },
            ),
    )
}
