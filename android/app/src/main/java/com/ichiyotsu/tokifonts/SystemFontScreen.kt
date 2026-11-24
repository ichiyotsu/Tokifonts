package com.ichiyotsu.tokifonts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 换系统字体（安装页默认内容）：
 * 一键换字体（下载 Inter → 生成模块 → 安装 → 软重启 zygote）为主入口，
 * 下方保留分步操作（高级 / 手动）；再往下是设备检查、Root、语种开关。
 * 底部是「资源库」入口，点进去是原来的安装工具、教程与官方链接。
 * 模块只做 KSU magic mount，不写系统分区；回滚 = 删模块 + 重启 zygote。
 * root 来自独立的 RootMyS24 应用，本应用不含任何 exploit / 内核代码。
 */
@Composable
fun SystemFontScreen(vm: FontsViewModel, padding: PaddingValues) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        if (vm.sysFontDevice == null) vm.checkSystemFontDevice()
    }
    var confirmInstall by remember { mutableStateOf(false) }
    var confirmRollback by remember { mutableStateOf(false) }
    val device = vm.sysFontDevice
    val calibrated = device?.support == DeviceSupport.CALIBRATED
    val ported = device?.support == DeviceSupport.PORTED
    val candidate = device?.support == DeviceSupport.CANDIDATE
    // 跨机型候选的来源行是待验证（已定标）时（如 S9280 DZJ3），文案必须写
    // “待验证（已定标）载荷”，绝不能写“已实测”；已实测来源行才保持原话。
    val crossCalibrated = device?.firmware?.crossModel?.entry?.status == FirmwareStatus.CALIBRATED
    // 待验证、移植载荷与跨机型候选同级：都要用户额外勾选风险确认才放行。
    val needsConfirm = calibrated || ported || candidate
    // 一键 / 下载 / 生成共用这个放行条件：已实测直接放行，待验证须用户先勾选风险确认。
    val installAllowed = vm.sysFontInstallAllowed()
    val rootTool = InstallerTools.firstOrNull { it.id == "root-my-s24" }
    val blockers = vm.sysFontInstallBlockers()

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = padding.asPagePadding(),
        verticalArrangement = Arrangement.spacedBy(TokiSpacing.lg),
    ) {
        item {
            Text(
                "把系统全部字重的字体换成 Inter：常规（400）用各语种的常规文件，粗体（700）仍可加粗，系统设置里的粗细切换照常可用。只支持三星 S24 系列 / Z Fold6 / Z Flip6，需要 root（由独立的 RootMyS24 应用提供）。模块只做挂载覆盖，不写系统分区，删模块即可恢复原厂字体。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Surface(shape = TokiCardShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(TokiSpacing.xl), verticalArrangement = Arrangement.spacedBy(TokiSpacing.sm)) {
                    Text("一键换字体", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(
                        "点一下自动跑完：下载 Inter → 生成模块 → 安装 → 软重启，全程显示当前步骤，出错会停在当步并告诉你原因。root 需先在 RootMyS24 里取得。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    vm.sysFontStep?.let { step ->
                        Text(
                            "$step（${(vm.sysFontProgress * 100).toInt()}%）",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    Button(
                        onClick = vm::oneClickChangeFont,
                        enabled = installAllowed && !vm.sysFontWorking,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    ) {
                        Text(
                            if (vm.sysFontWorking && vm.sysFontStep != null) "正在换字体…" else "一键换字体",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    if (!installAllowed) {
                        Text(
                            when {
                                candidate -> if (crossCalibrated) {
                                    "该固件不在 S24+ 支持列表里，但有 S24 Ultra 同构建号待验证（已定标）载荷可试；确认风险后勾选下方“我已知晓，仍要继续”才能一键换字体。"
                                } else {
                                    "该固件不在 S24+ 支持列表里，但有 S24 Ultra 同构建号载荷可试（你以前用过这条路子）；确认风险后勾选下方“我已知晓，仍要继续”才能一键换字体。"
                                }
                                ported -> "该固件是移植载荷（未实测），需先在 RootMyS24 设置里开启未经测试的载荷；确认风险后勾选下方“我已知晓，仍要继续”才能一键换字体。"
                                calibrated -> "该固件已定标但未实测，root 可能不成功；确认风险后勾选下方“我已知晓，仍要继续”才能一键换字体。"
                                else -> "当前设备不支持换系统字体，因此不提供一键按钮。"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
        }
        item {
            Surface(shape = TokiCardShape, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(TokiSpacing.xl), verticalArrangement = Arrangement.spacedBy(TokiSpacing.sm)) {
                    Text("设备检查", style = MaterialTheme.typography.titleLarge)
                    when (device) {
                        null -> Text("正在检查…", style = MaterialTheme.typography.bodyMedium)
                        else -> {
                            Text(
                                "机型：${device.model}（${device.manufacturer}）",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                "固件：${device.firmwareDisplay.ifBlank { device.firmwareIncremental }.ifBlank { "未知" }}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "内核：${device.kernelVersion.ifBlank { "未知" }}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                device.reason,
                                style = MaterialTheme.typography.bodyMedium,
                                color = when (device.support) {
                                    DeviceSupport.SUPPORTED -> MaterialTheme.colorScheme.onSurfaceVariant
                                    DeviceSupport.CALIBRATED, DeviceSupport.PORTED, DeviceSupport.CANDIDATE -> MaterialTheme.colorScheme.tertiary
                                    else -> MaterialTheme.colorScheme.error
                                },
                            )
                            device.firmware?.crossModel?.let { cross ->
                                Text(
                                    if (crossCalibrated) {
                                        "跨机型候选：可用 S24 Ultra 同构建号待验证（已定标）载荷一试（${cross.entry.firmwareBuild}），社区定标、App 内未实测，需点确认才尝试，不默认走。"
                                    } else {
                                        "跨机型候选：可用 S24 Ultra 同构建号载荷一试（${cross.entry.firmwareBuild}），你以前用这条路子通过过，需点确认才尝试，不默认走。"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.tertiary,
                                )
                            }
                            if (device.firmware?.kind == FirmwareMatchKind.MODEL_ONLY) {
                                Text(
                                    "该机型已知固件：${device.firmware.knownBuilds.joinToString("、") { it.firmwareBuild }}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (needsConfirm) {
                                Text(
                                    FirmwareSupport.PROBABILISTIC_NOTE,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.tertiary,
                                )
                                if (ported) {
                                    Text(
                                        FirmwareSupport.PORTED_SETTING_NOTE,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.tertiary,
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = vm.sysFontCalibratedConfirmed,
                                        onCheckedChange = vm::setSystemFontCalibratedConfirmed,
                                    )
                                    Text(
                                        when {
                                            candidate -> if (crossCalibrated) {
                                                "我已知晓这是用 S24 Ultra 同构建号待验证（已定标）载荷跨机型尝试，仍可能不成功，仍要继续"
                                            } else {
                                                "我已知晓这是用 S24 Ultra 同构建号载荷跨机型尝试（你以前通过过），仍可能不成功，仍要继续"
                                            }
                                            ported -> "我已知晓这是移植载荷（未实测），需在 RootMyS24 设置里开启未经测试的载荷，root 可能不成功，仍要继续"
                                            else -> "我已知晓该固件未实测，root 可能不成功，仍要继续"
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }
                        }
                    }
                    OutlinedButton(
                        onClick = { vm.installPage = "supported-devices" },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    ) {
                        Text("查看支持设备列表", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = vm::checkSystemFontDevice,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    ) {
                        Text("重新检查", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        item {
            Surface(shape = TokiCardShape, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(TokiSpacing.xl), verticalArrangement = Arrangement.spacedBy(TokiSpacing.sm)) {
                    Text("Root", style = MaterialTheme.typography.titleLarge)
                    when (vm.sysFontRoot) {
                        true -> Text("已检测到 root，可以继续。", style = MaterialTheme.typography.bodyMedium)
                        false -> Text(
                            "没有检测到 root。请先安装 RootMyS24 取得 root，再回来点“重新检查”。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        null -> Text("还没检查 root。", style = MaterialTheme.typography.bodyMedium)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = vm::checkRoot,
                            enabled = !vm.sysFontRootChecking,
                            modifier = Modifier.heightIn(min = 56.dp),
                        ) {
                            Text(
                                if (vm.sysFontRootChecking) "检查中…" else "检查 root",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        if (vm.sysFontRoot != true && rootTool != null) {
                            OutlinedButton(
                                onClick = { if (!openLink(context, rootTool.downloadUrl)) vm.flash("没有可以打开链接的应用") },
                                modifier = Modifier.heightIn(min = 56.dp),
                            ) {
                                Text("去装 RootMyS24", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Text(
                        "RootMyS24 是独立应用（三星部分固件上加载 KernelSU），不在本应用内，也不由本应用安装。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            Surface(shape = TokiCardShape, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(TokiSpacing.xl), verticalArrangement = Arrangement.spacedBy(TokiSpacing.sm)) {
                    Text("Inter（全部字重）", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "每种语言按字重各一个文件，按开关选装。拉丁用 Inter 各字重（从 Inter 4.1 官方包下载），简繁中文用苹方各字重（走字库下载，只用已下载的本地文件），日文 / 韩文安装时从官方下载。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SystemFontScript.entries.forEach { script ->
                        ListItem(
                            headlineContent = { Text(script.displayName) },
                            supportingContent = { Text(script.sourceNote) },
                            trailingContent = {
                                Switch(
                                    checked = vm.sysFontScripts[script] == true,
                                    onCheckedChange = { vm.setSystemFontScript(script, it) },
                                )
                            },
                            colors = androidx.compose.material3.ListItemDefaults.colors(
                                containerColor = androidx.compose.ui.graphics.Color.Transparent,
                            ),
                        )
                    }
                    Button(
                        onClick = vm::downloadSystemFontFiles,
                        enabled = installAllowed && !vm.sysFontWorking,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    ) {
                        Text(
                            if (vm.sysFontWorking) "正在准备 ${(vm.sysFontProgress * 100).toInt()}%" else "下载 Inter 字体文件",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
        item {
            Surface(shape = TokiCardShape, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(TokiSpacing.xl), verticalArrangement = Arrangement.spacedBy(TokiSpacing.sm)) {
                    Text("分步操作（高级）", style = MaterialTheme.typography.titleLarge)
                    val stateText = when (vm.sysFontModuleState) {
                        SystemFontModuleState.NOT_GENERATED -> "模块还没生成"
                        SystemFontModuleState.GENERATED -> "模块已生成，可以安装"
                        SystemFontModuleState.INSTALLED -> "模块已安装" + (vm.sysFontInstallMethod?.let { "（通过${it}安装）" } ?: "")
                        SystemFontModuleState.FAILED -> "上一步失败，见下方错误"
                    }
                    Text(stateText, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "安装后会软重启 zygote（桌面会闪一下）。如果软重启后字体没换，说明挂载没在当前会话生效，请重启手机。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = vm::generateSystemFontModule,
                        enabled = installAllowed && !vm.sysFontWorking,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    ) {
                        Text("生成模块", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { confirmInstall = true },
                        enabled = blockers.isEmpty() && !vm.sysFontWorking,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    ) {
                        Text("安装并软重启", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    if (blockers.isNotEmpty()) {
                        Text(
                            "现在不能安装：",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        blockers.forEach { blocker ->
                            Text(
                                "· $blocker",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = { confirmRollback = true },
                        enabled = !vm.sysFontWorking,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    ) {
                        Text("一键回滚（恢复原厂字体）", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = vm::rebootSystemFontPhone,
                        enabled = vm.sysFontRoot == true && !vm.sysFontWorking,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    ) {
                        Text("重启手机", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        vm.sysFontError?.let { error ->
            item {
                Surface(shape = TokiCardShape, color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(TokiSpacing.xl), verticalArrangement = Arrangement.spacedBy(TokiSpacing.sm)) {
                        Text("出错了", style = MaterialTheme.typography.titleLarge)
                        Text(error, style = MaterialTheme.typography.bodyMedium)
                        OutlinedButton(
                            onClick = vm::checkSystemFontDevice,
                            modifier = Modifier.heightIn(min = 56.dp),
                        ) {
                            Text("重新检查设备", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        vm.sysFontVerifyWarning?.let { warning ->
            item {
                Surface(shape = TokiCardShape, color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(TokiSpacing.xl), verticalArrangement = Arrangement.spacedBy(TokiSpacing.sm)) {
                        Text("安装后验证没通过", style = MaterialTheme.typography.titleLarge)
                        Text(warning, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "模块可能装上了但新字体没挂上，建议先一键回滚，再重新检查 root 与模块后重试。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedButton(
                            onClick = { confirmRollback = true },
                            enabled = !vm.sysFontWorking,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                        ) {
                            Text("一键回滚（恢复原厂字体）", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        if (vm.sysFontNeedsReboot) {
            item {
                Surface(shape = TokiCardShape, color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(TokiSpacing.xl), verticalArrangement = Arrangement.spacedBy(TokiSpacing.sm)) {
                        Text("模块已删，请重启手机", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "模块目录已经删掉，但软重启 zygote 没有确认，字体可能还没恢复。点下面重启手机，重启后就是原厂字体。",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Button(
                            onClick = vm::rebootSystemFontPhone,
                            enabled = vm.sysFontRoot == true && !vm.sysFontWorking,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                        ) {
                            Text("重启手机", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        item {
            Surface(shape = TokiCardShape, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(TokiSpacing.xl), verticalArrangement = Arrangement.spacedBy(TokiSpacing.sm)) {
                    Text("万一出问题怎么办", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "本工具从不修改系统分区，删掉模块就恢复原厂字体。万一开机后字体异常或进不了系统：进 KernelSU 管理器，找到「Inter」模块关掉它再重启；或者用电脑连上手机执行 adb shell rm -rf /data/adb/modules/tokifonts-font 后重启；安全模式下模块不会加载，开机后也能直接删模块。原版 fonts.xml 还备份在应用里（系统字体目录下的 fonts.xml.bak）和模块里（fonts.xml.original）。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (!installAllowed) {
            item {
                Text(
                    when {
                        candidate -> if (crossCalibrated) {
                            "该固件不在 S24+ 支持列表里，但有 S24 Ultra 同构建号待验证（已定标）载荷可试，勾选风险确认后才能安装。"
                        } else {
                            "该固件不在 S24+ 支持列表里，但有 S24 Ultra 同构建号载荷可试，勾选风险确认后才能安装。"
                        }
                        ported -> "该固件是移植载荷（未实测），需先在 RootMyS24 设置里开启未经测试的载荷，勾选风险确认后才能安装。"
                        calibrated -> "该固件已定标但未实测，勾选风险确认后才能安装。"
                        else -> "当前设备不支持换系统字体，因此不提供安装按钮。"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            Surface(
                onClick = { vm.installPage = "resources" },
                shape = TokiCardShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(TokiSpacing.xl), verticalArrangement = Arrangement.spacedBy(TokiSpacing.sm)) {
                    Text("资源库", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(
                        "安装工具、教程与官方下载链接。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
    }
    if (confirmInstall) {
        AlertDialog(
            onDismissRequest = { confirmInstall = false },
            title = { Text("安装 Inter 模块") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("会把模块装到 /data/adb/modules/tokifonts-font，并软重启 zygote。模块只做挂载覆盖，不写系统分区，随时可以一键回滚。")
                    Text("这次会改的字体（字重 · 原系统文件 → Inter）：", fontWeight = FontWeight.Bold)
                    if (vm.sysFontChangeList.isEmpty()) {
                        Text("没有可安装的改动")
                    } else {
                        vm.sysFontChangeList.forEach { change ->
                            Text("${change.weight} · ${change.oldFile ?: "新增条目"} → ${change.newFile}（${change.script.displayName}）")
                        }
                    }
                    Text(
                        "已选语种：${vm.enabledSystemFontScripts().joinToString("、") { it.displayName }}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmInstall = false
                    vm.installSystemFontModule()
                }) { Text("安装") }
            },
            dismissButton = { TextButton(onClick = { confirmInstall = false }) { Text("取消") } },
        )
    }
    if (confirmRollback) {
        AlertDialog(
            onDismissRequest = { confirmRollback = false },
            title = { Text("回滚系统字体") },
            text = { Text("删除字体模块并软重启 zygote，系统字体恢复原厂。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRollback = false
                    vm.rollbackSystemFont()
                }) { Text("回滚") }
            },
            dismissButton = { TextButton(onClick = { confirmRollback = false }) { Text("取消") } },
        )
    }
}
