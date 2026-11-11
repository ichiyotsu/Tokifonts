package com.ichiyotsu.tokifonts

import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState

data class NavEntry(val icon: String, val label: String)

/**
 * 底部导航按 Tokinote 的 FloatingNavBar 来做：悬浮毛玻璃胶囊，未选中只有描边图标，
 * 选中项展开成实色胶囊加文字，弹簧动效；旁边的操作按钮脱离胶囊，圆角 18dp。
 * 宽度达到 840dp 时改用侧栏。
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
    haze: HazeState,
    dark: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val insetPx = maxOf(WindowInsets.navigationBars.getBottom(density), WindowInsets.systemGestures.getBottom(density))
    val inset = with(density) { insetPx.toDp() } + 8.dp
    val reduce = rememberAnimatorScale() == 0f
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = inset)
            .semantics { contentDescription = "主导航" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        Row(
            Modifier
                .animateContentSize(if (reduce) snap() else spring(dampingRatio = 0.72f, stiffness = 380f))
                .clip(RoundedCornerShape(36.dp))
                .tokiGlass(haze, dark = dark)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            entries.forEachIndexed { index, entry ->
                ExpressiveNavItem(entry.icon, entry.label, index == selected, reduce) { onSelect(index) }
            }
        }
        ExpressiveAction(actionIcon, actionDescription, reduce, onAction)
    }
}

@Composable
fun ExpressiveNavRail(
    entries: List<NavEntry>,
    selected: Int,
    onSelect: (Int) -> Unit,
    actionIcon: String,
    actionDescription: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduce = rememberAnimatorScale() == 0f
    Column(
        modifier.width(96.dp).fillMaxSize().padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        entries.forEachIndexed { index, entry ->
            val scheme = MaterialTheme.colorScheme
            val on = index == selected
            val container = if (on) scheme.primary else scheme.surface
            val content = if (on) scheme.onPrimary else scheme.onSurfaceVariant
            Column(
                Modifier
                    .height(64.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .clip(RoundedCornerShape(if (on) 16.dp else 8.dp))
                    .background(container)
                    .clickable { onSelect(index) }
                    .semantics {
                        role = Role.Tab
                        this.selected = on
                        contentDescription = entry.label
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Symbol(entry.icon, filled = on, size = 22.dp, tint = content)
                Text(entry.label, style = MaterialTheme.typography.labelMedium, color = content, maxLines = 1)
            }
        }
        Spacer(Modifier.weight(1f))
        ExpressiveAction(actionIcon, actionDescription, reduce, onAction)
    }
}

@Composable
private fun rememberAnimatorScale(): Float {
    val view = LocalView.current
    return remember(view) {
        runCatching {
            Settings.Global.getFloat(view.context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }.getOrDefault(1f)
    }
}

@Composable
private fun Modifier.pressScale(interaction: MutableInteractionSource, scale: Float, reduce: Boolean): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val value by animateFloatAsState(
        if (!reduce && pressed) scale else 1f,
        spring(dampingRatio = 0.82f, stiffness = 380f),
        label = "press",
    )
    return graphicsLayer {
        scaleX = value
        scaleY = value
    }
}

@Composable
private fun ExpressiveNavItem(icon: String, label: String, selected: Boolean, reduce: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val haptic = LocalHapticFeedback.current
    val fadeInSpec = if (reduce) snap<Float>() else spring<Float>(dampingRatio = 0.6f, stiffness = 440f)
    val fadeOutSpec = if (reduce) snap<Float>() else spring<Float>(dampingRatio = 0.8f, stiffness = 440f)
    val grow = if (reduce) snap<IntSize>() else spring(dampingRatio = 0.6f, stiffness = 440f)
    val shrink = if (reduce) snap<IntSize>() else spring(dampingRatio = 0.8f, stiffness = 440f)
    val pill = MaterialTheme.colorScheme.primary
    val onPill = MaterialTheme.colorScheme.onPrimary
    val idle = MaterialTheme.colorScheme.onSurfaceVariant
    val background by animateColorAsState(
        if (selected) pill else Color.Transparent,
        if (reduce) snap() else spring(dampingRatio = 0.86f, stiffness = 380f),
        label = "nav-pill",
    )
    val iconTint by animateColorAsState(
        if (selected) onPill else idle,
        if (reduce) snap() else spring(dampingRatio = 0.86f, stiffness = 380f),
        label = "nav-icon",
    )
    Row(
        Modifier
            .height(56.dp)
            .clip(CircleShape)
            .background(background)
            .clickable(interaction, ripple(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f))) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            .pressScale(interaction, 0.92f, reduce)
            .padding(horizontal = 16.dp)
            .semantics {
                role = Role.Tab
                this.selected = selected
                contentDescription = label
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Symbol(
            icon,
            filled = selected,
            size = 22.dp,
            tint = iconTint,
        )
        AnimatedVisibility(
            visible = selected,
            enter = expandHorizontally(grow) + fadeIn(fadeInSpec),
            exit = shrinkHorizontally(shrink) + fadeOut(fadeOutSpec),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(8.dp))
                Text(label, style = MaterialTheme.typography.labelLarge, color = onPill, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ExpressiveAction(icon: String, description: String, reduce: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val haptic = LocalHapticFeedback.current
    Surface(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shadowElevation = 2.dp,
        modifier = Modifier
            .size(64.dp)
            .pressScale(interaction, 0.9f, reduce)
            .semantics { contentDescription = description },
        interactionSource = interaction,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Symbol(icon, filled = true, size = 28.dp, tint = MaterialTheme.colorScheme.onPrimary)
        }
    }
}
