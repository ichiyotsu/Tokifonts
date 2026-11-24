package com.ichiyotsu.tokifonts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight

/**
 * 支持设备列表（安装页的二级页面，走 installPage = "supported-devices"，与“资源库”同级）：
 * 按状态分组展示固件表——已实测 / 待验证，每行是机型、固件构建号、内核、引擎；
 * 本机匹配到的那一行置顶并标“本机”。
 * root 主路由 Pro 内嵌的一键 root（98260 行），外部 root 工具应用是备选；
 * 本页只展示“哪些固件有对应载荷”。列表跟随当前生效的表（内置表或每日 feed 更新后的新表）。
 */
@Composable
fun SupportedDevicesScreen(vm: FontsViewModel, padding: PaddingValues) {
    LaunchedEffect(Unit) {
        if (vm.sysFontDevice == null) vm.checkSystemFontDevice()
    }
    val currentEntry = vm.sysFontDevice?.firmware?.entry
    val currentKind = vm.sysFontDevice?.firmware?.kind
    val currentPorted = vm.sysFontDevice?.firmware?.ported

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = padding.asPagePadding(),
        verticalArrangement = Arrangement.spacedBy(TokiSpacing.lg),
    ) {
        item {
            Text(
                "只有下面这些固件在 root-my-s24 / Root-My-Galaxy 里有对应载荷。换系统字体前先确认本机固件在列表里；不在列表里的固件 root 可能不成功。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Text(
                vm.feedStatus,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
        if (currentEntry != null) {
            item {
                Text("本机", style = MaterialTheme.typography.titleLarge)
            }
            item {
                FirmwareRow(
                    entry = currentEntry,
                    isCurrent = true,
                    statusText = when (currentKind) {
                        FirmwareMatchKind.EXACT_TESTED -> "已实测 · 支持"
                        FirmwareMatchKind.EXACT_CALIBRATED -> "已定标、未实测"
                        else -> currentEntry.status.label
                    },
                )
            }
        } else if (currentPorted != null) {
            item {
                Text("本机", style = MaterialTheme.typography.titleLarge)
            }
            item {
                PortedRow(entry = currentPorted, isCurrent = true)
            }
        }
        item {
            Text(
                "已实测（${FirmwareSupport.TESTED.size}）",
                style = MaterialTheme.typography.titleLarge,
            )
        }
        items(FirmwareSupport.TESTED, key = { "${it.model}-${it.firmwareBuild}" }) { entry ->
            FirmwareRow(entry = entry, isCurrent = entry == currentEntry, statusText = "已实测")
        }
        item {
            Text(
                "待验证（${FirmwareSupport.CALIBRATED.size}）",
                style = MaterialTheme.typography.titleLarge,
            )
        }
        item {
            Text(
                "以下固件只做了定标、还没人实测过，root 可能不成功；想试的话回安装页勾选风险确认。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(FirmwareSupport.CALIBRATED, key = { "${it.model}-${it.firmwareBuild}" }) { entry ->
            FirmwareRow(entry = entry, isCurrent = entry == currentEntry, statusText = "待验证")
        }
        item {
            Text(
                "移植载荷（未经本 App 真机实测，${FirmwareSupport.activeTables().portedPayloads.size}）",
                style = MaterialTheme.typography.titleLarge,
            )
        }
        item {
            Text(
                "以下系列共用移植载荷，运行时按 KASLR 定标；想用要先在 RootMyS24 设置里打开「启用未经测试的载荷」，再回安装页勾选风险确认。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(FirmwareSupport.activeTables().portedPayloads, key = { it.seriesLabel }) { entry ->
            PortedRow(entry = entry, isCurrent = entry == currentPorted)
        }
        item {
            Text(
                "Root-My-Galaxy（${FirmwareSupport.activeTables().rmgPayloads.size} 个载荷）",
                style = MaterialTheme.typography.titleLarge,
            )
        }
        item {
            Text(
                "以下载荷按机型 + 三段式内核版本匹配，不看固件构建号；引擎只有 GhostLock（CVE-2026-43499），驱动随载荷提供；已修复 43499 的固件（如 SM-S9280 DZJ3）不在此列。未经本 App 真机实测，想试的话回安装页勾选风险确认。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(FirmwareSupport.activeTables().rmgPayloads, key = { it.payloadId }) { entry ->
            RmgRow(entry = entry, isCurrent = false)
        }
        item {
            Surface(shape = TokiCardShape, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(TokiSpacing.xl), verticalArrangement = Arrangement.spacedBy(TokiSpacing.sm)) {
                    Text(FirmwareSupport.TABLE_SOURCE, style = MaterialTheme.typography.bodyMedium)
                    Text(FirmwareSupport.RMG_TABLE_SOURCE, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        FirmwareSupport.PROBABILISTIC_NOTE,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        FirmwareSupport.PORTED_SETTING_NOTE,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        FirmwareSupport.DZF2_COVERAGE_NOTE,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        FirmwareSupport.US_S24_NOTE,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun PortedRow(entry: PortedPayloadEntry, isCurrent: Boolean) {
    Surface(
        shape = TokiCardShape,
        color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        ListItem(
            headlineContent = {
                Text(
                    entry.seriesLabel + if (isCurrent) " · 本机" else "",
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                    color = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                )
            },
            supportingContent = {
                Column {
                    Text("机型：${entry.modelPrefixes.joinToString(" / ")}")
                    Text("内核 ${entry.kernelLabel}")
                    if (entry.firmwareSuffixes.isNotEmpty()) {
                        Text("固件后缀：${entry.firmwareSuffixes.joinToString(" / ")}")
                    }
                    Text(entry.regions)
                }
            },
            trailingContent = {
                Text(
                    "移植载荷",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            },
            colors = androidx.compose.material3.ListItemDefaults.colors(
                containerColor = androidx.compose.ui.graphics.Color.Transparent,
            ),
        )
    }
}

@Composable
private fun RmgRow(entry: RmgPayloadEntry, isCurrent: Boolean) {
    Surface(
        shape = TokiCardShape,
        color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        ListItem(
            headlineContent = {
                Text(
                    entry.displayName + if (isCurrent) " · 本机" else "",
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                    color = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                )
            },
            supportingContent = {
                Column {
                    Text("机型：${entry.models.joinToString(" / ")}")
                    Text("内核 ${entry.kernelVersions.joinToString(" / ")} · ${entry.engine}")
                    Text("工具：${entry.tool.label} · 驱动：${entry.driver}")
                    if (entry.requiresFreshP0Session) Text("需 fresh P0 会话")
                }
            },
            trailingContent = {
                Text(
                    entry.status.label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            },
            colors = androidx.compose.material3.ListItemDefaults.colors(
                containerColor = androidx.compose.ui.graphics.Color.Transparent,
            ),
        )
    }
}

@Composable
private fun FirmwareRow(entry: FirmwareEntry, isCurrent: Boolean, statusText: String) {
    Surface(
        shape = TokiCardShape,
        color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        ListItem(
            headlineContent = {
                Text(
                    entry.modelLabel + if (isCurrent) " · 本机" else "",
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                    color = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                )
            },
            supportingContent = {
                Column {
                    Text("固件：${entry.firmwareBuild}")
                    Text("内核 ${entry.kernel} · ${entry.engine}")
                    Text("工具：${entry.tool.label} · 驱动：${entry.driver}")
                    if (entry.note.isNotBlank()) Text(entry.note)
                }
            },
            trailingContent = {
                Text(
                    statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (entry.status == FirmwareStatus.TESTED) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.tertiary,
                )
            },
            colors = androidx.compose.material3.ListItemDefaults.colors(
                containerColor = androidx.compose.ui.graphics.Color.Transparent,
            ),
        )
    }
}
