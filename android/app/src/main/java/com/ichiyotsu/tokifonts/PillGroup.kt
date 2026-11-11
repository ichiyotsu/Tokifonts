package com.ichiyotsu.tokifonts

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Tokinote 的分段按钮：所有选项共享一个连续的全圆角外容器，
 * 选中项只通过内部的实色块切换，不再是彼此分离的胶囊。
 */
@Composable
fun <T> PillGroup(
    options: List<Pair<T, String>>,
    selected: T?,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        options.forEach { (value, label) ->
            PillSegment(
                label = label,
                selected = value == selected,
                onClick = { onSelect(value) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun PillSegment(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.97f else 1f,
        spring(dampingRatio = 0.82f, stiffness = 380f),
        label = "pill-press",
    )
    Row(
        modifier = modifier
            .height(42.dp)
            .clip(CircleShape)
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .graphicsLayer { scaleX = scale; scaleY = scale },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
