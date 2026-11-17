@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.ichiyotsu.tokifonts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

data class NavEntry(val icon: String, val label: String)

/**
 * 按压缩放：幅度 0.92，动画规格不写死弹簧常数，直接读当前主题的
 * MotionScheme.fastSpatialSpec() —— 切换 expressive/standard 或开减少
 * 动效时，全 App 的按压手感随主题一起变。
 */
@Composable
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    scale: Float = 0.92f,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val s by animateFloatAsState(
        targetValue = if (pressed) scale else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "pressScale",
    )
    return this.graphicsLayer {
        scaleX = s
        scaleY = s
        clip = true
    }
}

/**
 * 只显示图标的控件，长按弹出功能名称。Material 3 的 [PlainTooltip]：
 * 短按仍走原来的点击，长按才出现名称。名称同时是无障碍朗读文本。
 */
@Composable
fun TokiLongPressLabel(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
        modifier = modifier,
    ) {
        content()
    }
}

/**
 * 底部导航：悬浮毛玻璃胶囊，未选中只有描边图标，选中项展开成实色胶囊加文字。
 *（对照 TokiStudio `ui/components/Common.kt` 的 TokiFloatingNavBar。）
 *
 * - 颜色：全部取当前 MD3E ColorScheme 的 roles —— 选中 pill = primary /
 *   onPrimary，未选中图标 = onSurfaceVariant，涟漪 = primary 28%；
 * - 弹簧：不写死数值，全部映射到 MaterialTheme.motionScheme 的槽位 ——
 *   胶囊尺寸变化 → defaultSpatialSpec；标签展开 → fastSpatialSpec；
 *   标签收起 → defaultSpatialSpec；淡入淡出 → defaultEffectsSpec；
 * - 形状：胶囊 = MD3E 的 full 角（50% 圆角）；条目 pill = CircleShape；
 * - 玻璃：只挂一层 [glass]（Glass.kt 的全局 tokiGlass）。磨砂打开时
 *   胶囊不加粗描边；[solidStroke] 为真（玻璃关闭或采样不可用）时，外沿加
 *   一道与滚动标题色块同粗的强调色描边，选中 pill 和描边之间留出
 *   [TokiChromeStroke.barGap]。选中 pill 是实色块，可读性不依赖模糊；
 * - 高度：条目 56dp + 胶囊内边距。磨砂时上下各 8dp，总高 72dp；实心描边时
 *   内边距含描边厚度和间隙。悬浮间距（导航栏 inset + 8dp）由调用方挂载。
 *
 * 胶囊实测高 72.dp（56.dp 导航项 + 上下各 8.dp），内容区底部留白按这个高度算，
 * 见 TokiApp。
 */
val BottomBarHeight = 72.dp

@Composable
fun ExpressiveBottomBar(
    entries: List<NavEntry>,
    selected: Int,
    onSelect: (Int) -> Unit,
    actionIcon: String,
    actionDescription: String,
    onAction: () -> Unit,
    glass: Modifier,
    solidStroke: Boolean,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val insetPx = maxOf(WindowInsets.navigationBars.getBottom(density), WindowInsets.systemGestures.getBottom(density))
    val inset = with(density) { insetPx.toDp() } + 8.dp
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = inset)
            .semantics { contentDescription = "主导航" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        val shape = RoundedCornerShape(percent = 50)
        val barInset = if (solidStroke) TokiChromeStroke.width + TokiChromeStroke.barGap else 8.dp
        Row(
            Modifier
                .clipToBounds()
                .animateContentSize(animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec())
                .clip(shape)
                .then(glass)
                .then(
                    if (solidStroke) {
                        Modifier.border(TokiChromeStroke.width, MaterialTheme.colorScheme.primary, shape)
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = barInset, vertical = barInset),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            entries.forEachIndexed { index, entry ->
                ExpressiveNavItem(entry, index == selected) { onSelect(index) }
            }
        }
        TokiSideActionButton(
            icon = actionIcon,
            contentDescription = actionDescription,
            onClick = onAction,
            glass = glass,
        )
    }
}

/**
 * 宽窗口导航：官方 NavigationRail（TokiStudio 同款结构），动作钮放 header。
 * 替换掉之前的自绘侧栏，目的地与动作钮保持不变。
 */
@Composable
fun TokiNavRail(
    entries: List<NavEntry>,
    selected: Int,
    onSelect: (Int) -> Unit,
    actionIcon: String,
    actionDescription: String,
    onAction: () -> Unit,
    glass: Modifier,
    modifier: Modifier = Modifier,
) {
    NavigationRail(
        modifier = modifier.fillMaxHeight(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        header = {
            TokiSideActionButton(
                icon = actionIcon,
                contentDescription = actionDescription,
                onClick = onAction,
                glass = glass,
            )
        },
    ) {
        entries.forEachIndexed { index, entry ->
            NavigationRailItem(
                selected = index == selected,
                onClick = { onSelect(index) },
                icon = {
                    Symbol(entry.icon, filled = index == selected, size = 22.dp)
                },
                label = { Text(entry.label) },
            )
        }
    }
}

@Composable
private fun ExpressiveNavItem(entry: NavEntry, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val haptic = LocalHapticFeedback.current
    val scheme = MaterialTheme.colorScheme
    TokiLongPressLabel(entry.label) {
        Row(
            Modifier
                .height(56.dp)
                .clip(CircleShape)
                .background(if (selected) scheme.primary else Color.Transparent)
                .clickable(
                    interactionSource = interaction,
                    indication = ripple(color = scheme.primary.copy(alpha = 0.28f)),
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onClick()
                    },
                )
                .pressScale(interaction)
                .padding(horizontal = 16.dp)
                .semantics {
                    role = Role.Tab
                    this.selected = selected
                    contentDescription = entry.label
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Symbol(
                entry.icon,
                filled = selected,
                size = 22.dp,
                tint = if (selected) scheme.onPrimary else scheme.onSurfaceVariant,
            )
            AnimatedVisibility(
                visible = selected,
                enter = expandHorizontally(animationSpec = MaterialTheme.motionScheme.fastSpatialSpec()) +
                    fadeIn(animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec()),
                exit = shrinkHorizontally(animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()) +
                    fadeOut(animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec()),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = entry.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = scheme.onPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * 底栏旁的独立圆形玻璃按钮：胶囊外右侧、与胶囊间隔 8dp 的一颗独立按钮，
 * 56dp 圆形玻璃，与胶囊条目同高，玻璃与胶囊同一层 tokiGlass。
 *（对照 TokiStudio `ui/components/Common.kt` 的 TokiSideActionButton。）
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TokiSideActionButton(
    icon: String,
    contentDescription: String,
    onClick: () -> Unit,
    glass: Modifier,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val haptic = LocalHapticFeedback.current
    // 布局和命中都锁在 56dp 圆里。模糊和按压缩放不能画出这个圆，
    // 否则动画过程中会盖住旁边的胶囊。可见间距仍由外层 8dp 决定。
    Box(modifier.requiredSize(56.dp).clipToBounds()) {
        TooltipBox(
            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
            tooltip = { PlainTooltip { Text(contentDescription) } },
            state = rememberTooltipState(),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .then(glass)
                    .combinedClickable(
                        interactionSource = interaction,
                        indication = ripple(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)),
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onClick()
                        },
                        onLongClick = null,
                    )
                    .pressScale(interaction),
                contentAlignment = Alignment.Center,
            ) {
                Symbol(
                    icon,
                    filled = true,
                    size = 24.dp,
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
