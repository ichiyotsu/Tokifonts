package com.ichiyotsu.tokifonts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** 批量下载的聚合进度（状态在 ViewModel，切标签页不丢失）。 */
data class BulkProgress(
    val totalFaces: Int,
    val doneFaces: Int,
    val failedFaces: Int,
    val totalBytes: Long,
    val doneBytes: Long,
    val currentLabel: String,
)

/** 待下载集合 = 全部 face 减去已 Ready 的。纯函数，见 BulkDownloadTest。 */
fun pendingDownloadFaces(families: List<FontFamily>, readyIds: Set<String>): List<FontFace> =
    families.flatMap { it.faces }.filter { it.id !in readyIds }

/**
 * 一键下载全部的入口按钮（字体列表页与设置页“存储”分组共用同一状态与对话框）。
 * 全部已下载时不显示；进行中时禁用（状态与取消走进度条）。
 */
@Composable
fun BulkDownloadEntry(vm: FontsViewModel, modifier: Modifier = Modifier) {
    val catalog = vm.catalog ?: return
    val readyIds = vm.downloads.filterValues { it is FaceStatus.Ready }.keys
    val pending = remember(catalog, vm.downloads) { pendingDownloadFaces(catalog.families, readyIds) }
    var confirm by remember { mutableStateOf(false) }
    if (pending.isNotEmpty()) {
        Button(
            onClick = { confirm = true },
            enabled = vm.bulk == null,
            modifier = modifier.fillMaxWidth(),
        ) {
            Text("一键下载全部（约 ${pending.sumOf { it.bytes }.toSizeLabel()}，未下载部分）")
        }
    }
    if (confirm) {
        BulkDownloadDialog(
            pending = pending,
            onConfirm = { confirm = false; vm.startBulkDownload() },
            onDismiss = { confirm = false },
        )
    }
}

/**
 * 一键下载的确认对话框：显示待下载个数与总大小，说明已下载的会跳过。
 * 不做 Wi-Fi 门禁、不新增权限，大小写清、由用户自己决定。
 */
@Composable
fun BulkDownloadDialog(pending: List<FontFace>, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("一键下载全部") },
        text = {
            Text(
                "将下载 ${pending.size} 个字体文件，共约 ${pending.sumOf { it.bytes }.toSizeLabel()}。" +
                    "已下载的文件会跳过，不会重复下载。下载在后台继续，切到别的页面也不会停。",
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("开始下载") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** 进行中的常驻进度条：个数、字节、当前文件、取消按钮。 */
@Composable
fun BulkProgressCard(progress: BulkProgress, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = TokiCardShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "${progress.doneFaces}/${progress.totalFaces} · " +
                        "${progress.doneBytes.toSizeLabel()} / ${progress.totalBytes.toSizeLabel()}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onCancel) { Text("取消") }
            }
            LinearProgressIndicator(
                progress = {
                    if (progress.totalBytes > 0) {
                        (progress.doneBytes.toFloat() / progress.totalBytes).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            if (progress.currentLabel.isNotBlank()) {
                Text(
                    "正在下载 ${progress.currentLabel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
