package com.ichiyotsu.tokifonts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState

/**
 * 全 App 唯一的顶栏（主页面、字体详情、教程列表与详情共用）：
 * 标题只用一个文字角色（headlineMedium、粗体），返回键是 56dp 圆 chip。
 * 返回键与标题默认就带同一块磨砂（或实色退路），不随滚动出现或消失；
 * 两者间距固定 12dp。能采样时色块只做与底栏相同的磨砂，标题直接放在
 * 磨砂上，不再叠实色。不能采样时是不透明表面加与实心底栏同粗的描边。
 * 字始终用 onSurface。
 *（对照 TokiStudio `ui/components/Common.kt` 的 OverlayTitleHeader，同一 commit 的 TokiUI 仓库；
 * 返回图标用本 App 的 Symbol 字体字形，与底栏图标同一套。）
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OverlayTitleHeader(
    title: String,
    onBack: (() -> Unit)?,
    onHeightMeasured: (Dp) -> Unit,
    backdropBlur: Boolean,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    /**
     * 字体列表页的菜单键：有它时顶栏左端显示三条线（开左边栏）而不是返回键；
     * 与 onBack 二选一，详情页、教程页仍用返回键。
     */
    onMenuClick: (() -> Unit)? = null,
    /** 顶栏右端的动作位（字体列表页放一键下载的圆形按钮），其他页不传。 */
    trailing: (@Composable () -> Unit)? = null,
) {
    val density = LocalDensity.current
    val content = MaterialTheme.colorScheme.onSurface
    val titleShape = RoundedCornerShape(percent = 50)
    Row(
        modifier
            .fillMaxWidth()
            .onSizeChanged { onHeightMeasured(with(density) { it.height.toDp() }) }
            .statusBarsPadding()
            .heightIn(min = 64.dp)
            .padding(horizontal = TokiSpacing.pageMargin),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            TokiScrollBackChip(
                backdropBlur = backdropBlur,
                hazeState = hazeState,
                onClick = onBack,
            )
            Spacer(Modifier.width(TokiSpacing.md))
        } else if (onMenuClick != null) {
            TokiCircleIconChip(
                icon = "menu",
                label = "菜单",
                backdropBlur = backdropBlur,
                hazeState = hazeState,
                onClick = onMenuClick,
            )
            Spacer(Modifier.width(TokiSpacing.md))
        }
        Box(
            Modifier
                .weight(1f, fill = false)
                .heightIn(min = 56.dp)
                .clip(titleShape),
            contentAlignment = Alignment.CenterStart,
        ) {
            TokiScrollChipBackdrop(
                shape = titleShape,
                backdropBlur = backdropBlur,
                hazeState = hazeState,
                modifier = Modifier.matchParentSize(),
            )
            Text(
                title,
                modifier = Modifier.padding(horizontal = TokiSpacing.titleGlyphInset),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(TokiSpacing.md))
            trailing()
        }
    }
}

/**
 * 顶栏的 56dp 圆形玻璃图标键（返回、菜单、一键下载共用同一套）：
 * 默认就带磨砂（与标题同一套），不随滚动变化；长按弹出名称。
 */
@Composable
fun TokiCircleIconChip(
    icon: String,
    label: String,
    backdropBlur: Boolean,
    hazeState: HazeState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val content = MaterialTheme.colorScheme.onSurface
    TokiLongPressLabel(label, modifier) {
        Box(
            Modifier
                .size(56.dp)
                .clip(CircleShape)
                .clickable(
                    interactionSource = interaction,
                    indication = ripple(),
                    enabled = enabled,
                ) { onClick() }
                .pressScale(interaction)
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            TokiScrollChipBackdrop(
                shape = CircleShape,
                backdropBlur = backdropBlur,
                hazeState = hazeState,
                modifier = Modifier.matchParentSize(),
            )
            Symbol(icon, filled = true, size = 24.dp, tint = content)
        }
    }
}

/** 返回键自己的大圆角色块，高度和标题胶囊对齐。默认就带磨砂（与标题同一套），不随滚动变化。 */
@Composable
fun TokiScrollBackChip(
    backdropBlur: Boolean,
    hazeState: HazeState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TokiCircleIconChip(
        icon = "arrow_back",
        label = "返回",
        backdropBlur = backdropBlur,
        hazeState = hazeState,
        onClick = onClick,
        modifier = modifier,
    )
}
