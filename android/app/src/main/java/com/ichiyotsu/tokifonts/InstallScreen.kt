package com.ichiyotsu.tokifonts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun InstallScreen(vm: FontsViewModel, padding: PaddingValues) {
    val catalog = vm.catalog ?: return
    val rows = catalog.families.flatMap { family ->
        family.faces.mapNotNull { face ->
            val state = vm.status(face.id)
            if (state is FaceStatus.Absent) null else Triple(family, face, state)
        }
    }
    LazyColumn(
        Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("安装", style = MaterialTheme.typography.displaySmallEmphasized)
            Text(
                "字体先下载到本应用。Android 不允许普通应用直接写成系统字体，所以安装要交给系统的下载目录，或其他字体安装器。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (rows.isEmpty()) {
            item {
                Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("还没有下载", style = MaterialTheme.typography.titleLarge)
                        Text("到字体库打开一套字体，点「下载并预览」。下载完成后会显示在这里。")
                    }
                }
            }
        }
        items(rows, key = { it.second.id }) { (family, face, state) ->
            InstallRow(vm, family, face, state)
        }
    }
}

@Composable
private fun InstallRow(vm: FontsViewModel, family: FontFamily, face: FontFace, state: FaceStatus) {
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(family.name, style = MaterialTheme.typography.titleMedium)
            Text(
                listOf(face.opticalLabel, if (face.variable) "可变" else face.weightLabel, if (face.italic) "斜体" else "")
                    .filter { it.isNotBlank() }
                    .joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(statusLabel(face, state, face.id in vm.exported), style = MaterialTheme.typography.bodySmall)
            when (state) {
                is FaceStatus.Downloading -> {
                    if (state.progress >= 0f) LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                    else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    TextButton(onClick = { vm.cancel(face) }) { Text("取消") }
                }
                FaceStatus.Queued -> {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    TextButton(onClick = { vm.cancel(face) }) { Text("取消") }
                }
                is FaceStatus.Failed -> {
                    Text(state.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { vm.download(face) }) { Text("重试") }
                }
                is FaceStatus.Ready -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { vm.export(face) }) { Text("保存到下载") }
                        OutlinedButton(onClick = { vm.openWith(face) }) { Text("打开") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { vm.share(face) }) { Text("分享") }
                        TextButton(onClick = { vm.delete(face) }) { Text("删除") }
                    }
                }
                FaceStatus.Absent -> Unit
            }
        }
    }
}

private fun statusLabel(face: FontFace, state: FaceStatus, exported: Boolean): String = when (state) {
    FaceStatus.Absent -> "未下载"
    FaceStatus.Queued -> "等待下载 · ${face.bytes.toSizeLabel()}"
    is FaceStatus.Downloading -> if (state.progress < 0f) "正在解压" else "下载中 ${(state.progress * 100).toInt()}%"
    is FaceStatus.Ready -> buildString {
        append("已下载 · ${state.file.length().toSizeLabel()}")
        if (exported) append(" · 已保存到下载目录")
    }
    is FaceStatus.Failed -> "下载失败"
}
