@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.ichiyotsu.tokifonts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(vm: FontsViewModel, padding: PaddingValues) {
    val catalog = vm.catalog
    var confirmClear by remember { mutableStateOf(false) }
    val downloaded = catalog?.families?.flatMap { it.faces }?.count { vm.status(it.id) is FaceStatus.Ready } ?: 0
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(padding).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("设置", style = MaterialTheme.typography.displaySmallEmphasized)
        Section(title = "外观") {
            Text("主题", style = MaterialTheme.typography.titleMedium)
            val options = listOf("跟随系统", "浅色", "深色")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                options.forEachIndexed { index, label ->
                    SegmentedButton(
                        selected = vm.themeMode == index,
                        onClick = { vm.persistTheme(index) },
                        shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    ) { Text(label) }
                }
            }
            ListItem(
                headlineContent = { Text("动态取色") },
                supportingContent = { Text("用壁纸颜色生成界面配色") },
                trailingContent = { Switch(checked = vm.dynamicColor, onCheckedChange = { vm.setDynamic(it) }) },
            )
        }
        Section(title = "更新") {
            ListItem(
                headlineContent = { Text("启动时检查更新") },
                supportingContent = { Text("打开应用时查看 GitHub 上有没有新的正式版") },
                trailingContent = { Switch(checked = vm.checkOnLaunch, onCheckedChange = vm::persistCheckOnLaunch) },
            )
            Button(onClick = { vm.checkUpdate(manual = true) }, enabled = !vm.updateChecking) { Text(if (vm.updateChecking) "正在检查" else "检查更新") }
        }
        Section(title = "存储") {
            Text("已下载 $downloaded 个字体文件，占用 ${vm.cacheBytes.toSizeLabel()}。", style = MaterialTheme.typography.bodyLarge)
            Text(
                "文件在应用私有目录。清除后需要重新下载。界面附带的 Google Sans 三档字重只在第一次启动时放入，清除后不会自动回来。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
            Text("界面字体 Google Sans 使用 SIL OFL 1.1，图标使用 Material Symbols（Apache 2.0）。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
}
