@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.ichiyotsu.tokifonts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState

/**
 * 下拉菜单的统一形状：形状阶梯 extraLarge 档（28dp 对称大圆角），
 * 全 App 的菜单定死这一档 —— material3 的默认菜单形状是 CornerExtraSmall
 * （4dp），视觉上近乎直角。
 *（对照 TokiStudio `ui/components/Common.kt` 的 TokiMenuShape，同一 commit 的 TokiUI 仓库。）
 */
val TokiMenuShape: Shape = RoundedCornerShape(28.dp)

/**
 * 全 App 统一的下拉菜单封装。圆角与容器处理都收在这里：
 * - 形状：[TokiMenuShape]（28dp），透过 DropdownMenu 的 shape 参数下发；
 * - 模糊：调用方传入 [hazeState] 时，菜单容器挂内容源玻璃（与顶栏标题、
 *   悬浮底栏同一套，见 Glass.kt），容器降到 35% 不透明度让模糊透出来；
 *   不传时是 86% 不透明度的半透明磨砂 + 一层细描边，菜单文字在任何背景上
 *   都有足够对比（模糊感让位于可读性）。
 * - 弹出/收起动效由 material3 组件内部管理，菜单项的按压涟漪走组件默认。
 */
@Composable
fun TokiDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier.clip(TokiMenuShape).then(if (hazeState != null) Modifier.tokiContentGlass(hazeState) else Modifier),
        shape = TokiMenuShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = if (hazeState != null) 0.35f else 0.86f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        content = content,
    )
}

/**
 * 下拉菜单里的可选项：文字一律 titleMedium 粗体；
 * 选中项是一只全圆角深色胶囊（inverseSurface 底、inverseOnSurface 字，不缀 ✓），
 * 未选中项是裸字。胶囊与菜单四边各留 8dp，选项之间留 16dp 缝，容器圆角见
 * [TokiMenuShape]。菜单宽度随内容：M3 菜单以 IntrinsicSize.Max 度量，取最长
 * 选项的文本宽，不设固定宽。点选项只切换选中态、不关菜单（多选用）。
 */
@Composable
fun TokiSelectableMenuItem(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    DropdownMenuItem(
        // M3 1.5 没有 MenuItemDefaults（以 AAR 实测为准），颜色直接写在内容上，
        // 不走 colors 参数；未选中态的文字色取 M3 菜单默认值。
        text = {
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (selected) scheme.inverseOnSurface else scheme.onSurface,
            )
        },
        onClick = onClick,
        modifier = Modifier
            .padding(horizontal = TokiSpacing.sm, vertical = TokiSpacing.sm)
            .then(if (selected) Modifier.clip(CircleShape).background(scheme.inverseSurface) else Modifier),
    )
}
