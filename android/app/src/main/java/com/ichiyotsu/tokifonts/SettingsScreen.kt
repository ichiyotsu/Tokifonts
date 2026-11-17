@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.ichiyotsu.tokifonts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(vm: FontsViewModel, padding: PaddingValues) {
    val catalog = vm.catalog
    var confirmClear by remember { mutableStateOf(false) }
    val downloaded = catalog?.families?.flatMap { it.faces }?.count { vm.status(it.id) is FaceStatus.Ready } ?: 0
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(padding.asPagePadding()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("设置", style = MaterialTheme.typography.displaySmallEmphasized)
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
            Text("种子色", style = MaterialTheme.typography.titleMedium)
            SeedRow(vm)
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
        Section(title = "界面字体") {
            Text("Inter + 思源黑体", style = MaterialTheme.typography.titleMedium)
            Text(
                "拉丁文用 Inter，中文和其他汉字用思源黑体。应用内嵌官方 Inter，以及思源黑体的官方 Noto Sans SC，离线可用。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Section(title = "预览文字") {
            Text("字体列表和还没单独改过的详情页都用这段文字。某一套字体在详情页改过的，不会被这里覆盖。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = vm.previewText,
                onValueChange = vm::updatePreview,
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                // 与搜索框、详情预览框统一圆角 28dp（此前漏传 shape、走默认 8dp，全 App 不一致）。
                shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                label = { Text("全局预览文字") },
            )
            TextButton(onClick = vm::resetPreview) { Text("恢复默认") }
        }
        Section(title = "更新") {
            ListItem(
                headlineContent = { Text("启动时检查更新") },
                supportingContent = { Text("打开应用时查看 GitHub 上有没有新的正式版") },
                trailingContent = { Switch(checked = vm.checkOnLaunch, onCheckedChange = vm::persistCheckOnLaunch) },
                colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
            )
            Button(onClick = { vm.checkUpdate(manual = true) }, enabled = !vm.updateChecking) { Text(if (vm.updateChecking) "正在检查" else "检查更新") }
        }
        Section(title = "存储") {
            Text("已下载 $downloaded 个字体文件，占用 ${vm.cacheBytes.toSizeLabel()}。", style = MaterialTheme.typography.bodyLarge)
            Text(
                "文件在应用私有目录。清除后需要重新下载。界面附带的 Google Sans 三档字重会在下次启动时自动补回。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            BulkDownloadEntry(vm)
            vm.bulk?.let { bulk ->
                BulkProgressCard(bulk, onCancel = vm::cancelBulkDownload)
            }
            Button(onClick = { confirmClear = true }, enabled = downloaded > 0 || vm.cacheBytes > 0) { Text("清除下载") }
        }
        Section(title = "关于") {
            Text("tokifonts", style = MaterialTheme.typography.headlineMedium)
            Text("版本 ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleMedium)
            Text("versionCode ${BuildConfig.VERSION_CODE}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (catalog != null) {
                Text("${catalog.families.size} 套字体，${catalog.families.sumOf { it.faces.size }} 个文件。", style = MaterialTheme.typography.bodyLarge)
            }
            Text(
                "安装包里只有索引和界面字体。其余字体在使用时下载。",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "界面字体 Inter 与 Noto Sans SC（思源黑体的官方发行）使用 SIL OFL 1.1；Google Sans 三档字重仍内置为预览种子字体，图标使用 Material Symbols（Apache 2.0）。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(12.dp))
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
 * 种子色点选（对照 TokiStudio 外观页）：种子列表来自语义 token，
 * 点选即切换为种子方案并关动态取色。
 */
@Composable
private fun SeedRow(vm: FontsViewModel) {
    val context = LocalContext.current
    val seeds = remember { TokiUiTokens.seeds(context) }
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        seeds.forEachIndexed { i, seed ->
            val selected = !vm.dynamicColor && vm.seedIndex == i
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape)
                        .background(seedColor(seed.value))
                        .then(if (selected) Modifier.border(3.dp, scheme.primary, CircleShape) else Modifier)
                        .clickable { vm.setSeed(i) },
                )
                Text(seed.name, style = MaterialTheme.typography.labelSmall)
            }
        }
        if (vm.dynamicColor) {
            Text(
                "当前使用动态取色，点选种子色即切换为种子方案",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
                IconButton(onClick = { state.show() }) {
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
    Surface(shape = TokiCardShape, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
}
