@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.ichiyotsu.tokifonts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(vm: FontsViewModel, padding: PaddingValues) {
    val catalog = vm.catalog
    var confirmClear by remember { mutableStateOf(false) }
    val downloaded = catalog?.families?.flatMap { it.faces }?.count { vm.status(it.id) is FaceStatus.Ready } ?: 0
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(padding.asPagePadding()),
        verticalArrangement = Arrangement.spacedBy(TokiSpacing.lg),
    ) {
        Section(title = "外观") {
            Text("主题", style = MaterialTheme.typography.titleMedium)
            val options = listOf("跟随系统", "浅色", "深色")
            TokiToggleButtonGroup(options, vm.themeMode, { vm.persistTheme(it) })
            ListItem(
                headlineContent = { Text("动态取色") },
                supportingContent = { Text(if (supportsDynamicColor) "用壁纸颜色生成界面配色" else "当前系统不支持动态取色") },
                trailingContent = {
                    Switch(
                        checked = vm.dynamicColor && supportsDynamicColor,
                        onCheckedChange = { vm.setDynamic(it) },
                        enabled = supportsDynamicColor,
                    )
                },
                colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
            )
            ListItem(
                headlineContent = { Text("减少动效") },
                supportingContent = { Text("动效方案切换为 standard 弹簧") },
                trailingContent = { Switch(checked = vm.reduceMotion, onCheckedChange = vm::persistReduceMotion) },
                colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
            )
            ListItem(
                headlineContent = { Text("玻璃模糊") },
                supportingContent = { Text("关闭后底栏改为不透明表面，并加上略厚描边") },
                trailingContent = { Switch(checked = vm.backdropBlur, onCheckedChange = vm::persistBackdropBlur) },
                colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
            )
        }
        Section(title = "预览文字") {
            Text("字体列表和还没单独改过的详情页都用这段文字。某一套字体在详情页改过的，不会被这里覆盖。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = vm.previewText,
                onValueChange = vm::updatePreview,
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                // 输入框圆角用平台默认值（TokiUI 语义：shape 只用于自己画的表面）。
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                label = { Text("全局预览文字") },
            )
            // 恢复默认与下面的检查更新同一套按钮样式（56dp 实心按钮、粗体标题字）。
            Button(
                onClick = vm::resetPreview,
                modifier = Modifier.heightIn(min = 56.dp),
            ) {
                Text("恢复默认", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
        Section(title = "更新") {
            Button(
                onClick = { vm.checkUpdate(manual = true) },
                enabled = !vm.updateChecking,
                modifier = Modifier.heightIn(min = 56.dp),
            ) {
                Text(
                    if (vm.updateChecking) "正在检查" else "检查更新",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Section(title = "存储") {
            Text("已下载 $downloaded 个字体文件，占用 ${vm.cacheBytes.toSizeLabel()}。", style = MaterialTheme.typography.bodyMedium)
            Text(
                "文件在应用私有目录。清除后需要重新下载。界面附带的 Google Sans 三档字重会在下次启动时自动补回。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            BulkDownloadEntry(vm)
            vm.bulk?.let { bulk ->
                BulkProgressCard(bulk, onCancel = vm::cancelBulkDownload)
            }
            OutlinedButton(
                onClick = { vm.settingsPage = "files" },
                enabled = downloaded > 0,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) {
                Text("字体文件管理", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = { confirmClear = true },
                enabled = downloaded > 0 || vm.cacheBytes > 0,
                modifier = Modifier.heightIn(min = 56.dp),
            ) {
                Text("清除下载", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
        Section(title = "关于") {
            Text("TokiFonts", style = MaterialTheme.typography.headlineMedium)
            Text("版本 ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleMedium)
            Text("versionCode ${BuildConfig.VERSION_CODE}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清除下载") },
            text = { Text("删除已经下载到本应用的字体文件。系统下载目录里另存的副本不会被删掉。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    vm.clearAll()
                }) { Text("清除") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("取消") } },
        )
    }
}

/**
 * MD3E 官方 ButtonGroup 的单选封装：组内是 toggleableItem（选中态由组管理）。
 *（对照 TokiStudio `ui/components/Common.kt` 的 TokiToggleButtonGroup。）
 */
@Composable
private fun TokiToggleButtonGroup(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    ButtonGroup(
        overflowIndicator = { state ->
            TokiLongPressLabel("更多选项") {
                IconButton(
                    onClick = { state.show() },
                    modifier = Modifier.semantics { contentDescription = "更多选项" },
                ) {
                    Symbol("more_vert", size = 24.dp)
                }
            }
        },
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEachIndexed { i, label ->
            toggleableItem(
                checked = i == selectedIndex,
                label = label,
                onCheckedChange = { checked -> if (checked) onSelect(i) },
                weight = 1f,
            )
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Surface(shape = TokiCardShape, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(TokiSpacing.xl), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
}

/**
 * 字体文件管理（设置 → 存储 → 字体文件管理）：只列已经下载到本机的字体文件，
 * 按套分组，每行一个文件，点删除就卸载这个文件（删本机文件，可重新下载）。
 */
@Composable
fun FontFileManagerScreen(vm: FontsViewModel, padding: PaddingValues) {
    val catalog = vm.catalog
    val groups = catalog?.families?.mapNotNull { family ->
        val ready = family.faces.filter { vm.status(it.id) is FaceStatus.Ready }
        if (ready.isEmpty()) null else family to ready
    }.orEmpty()
    val total = groups.sumOf { it.second.size }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = padding.asPagePadding(),
        verticalArrangement = Arrangement.spacedBy(TokiSpacing.lg),
    ) {
        item {
            Text(
                if (total == 0) "还没有下载的字体文件。" else "已下载 $total 个字体文件，占用 ${vm.cacheBytes.toSizeLabel()}。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        groups.forEach { (family, faces) ->
            item {
                Text(family.name, style = MaterialTheme.typography.titleLarge)
            }
            items(faces, key = { it.id }) { face ->
                Surface(shape = TokiCardShape, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = TokiSpacing.xl, vertical = TokiSpacing.lg),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                if (face.variable) "可变" else face.weightLabel,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                face.fileName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { vm.delete(face) }) { Text("删除") }
                    }
                }
            }
        }
    }
}
