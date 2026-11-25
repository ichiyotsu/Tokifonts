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
 * 换系统字体（安装页默认内容）：四步向导
 * Shizuku → root 工具（root-my-s24 / Root-My-Galaxy）→ KernelSU → 模块。
 * 每步有明确状态（已完成 / 进行中 / 待办 / 被拦住），前一步没完成时下一步
 * 不可开始；每步都有“重新检测”。第 4 步就是原来的一键换字体
 * （下载 Inter → 生成模块 → 安装 → 软重启 zygote），全粗细覆盖
 * （400=常规标准映射）、自动取消、回滚、门禁逻辑、安装前变更清单全部保留。
 * 设备检查详情、出错/验证/重启卡、“万一出问题怎么办”、资源库入口都在向导下方保留。
 * 模块只做 KSU magic mount，不写系统分区；回滚 = 删模块 + 重启 zygote。
 * root 主路是本应用内嵌的 root-my-s24 引擎（98260 行，载荷随包带上，
 * 见第 2 步的“内嵌一键 root”）；外部 root 工具应用是备选。
 * exploit 跑起来后没有中途暂停，本版执行链尚未接通时只做预检、不执行。
 */
@Composable
fun SystemFontScreen(vm: FontsViewModel, padding: PaddingValues) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        if (vm.sysFontDevice == null) vm.checkSystemFontDevice()
        vm.checkWizardApps()
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
    val blockers = vm.sysFontInstallBlockers()
    val wizard = installWizardState(
        shizukuInstalled = vm.sysFontShizukuInstalled,
        recommendation = vm.sysFontToolRecommendation,
        installedRootTools = vm.sysFontInstalledRootTools,
        root = vm.sysFontRoot,
        moduleState = vm.sysFontModuleState,
        installAllowed = installAllowed,
        working = vm.sysFontWorking,
        confirmedRootTools = vm.sysFontConfirmedRootTools,
    )
    val toolDisplay = rootToolStepDisplay(vm.sysFontToolRecommendation)
    // 第 3 步的驱动选择：按推荐引擎 + 本机三段式内核版本算（硬规则见 KernelSuDriver.kt）。
    val driverDecision = selectKsudDriver(
        vm.sysFontToolRecommendation?.engine,
        device?.let { SystemFont.parseKernelFullVersion(it.kernelVersion) },
    )
    val shizukuTool = InstallerTools.firstOrNull { it.id == "shizuku" }
    val kernelsuTool = InstallerTools.firstOrNull { it.id == "kernelsu" }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = padding.asPagePadding(),
        verticalArrangement = Arrangement.spacedBy(TokiSpacing.lg),
    ) {
        item {
            Text(
                "把系统全部字重的字体换成 Inter：常规（400）用各语种的常规文件，粗体（700）仍可加粗，系统设置里的粗细切换照常可用。只支持三星 S24 系列 / Z Fold6 / Z Flip6，需要 root：主路是本应用内嵌的一键 root（第 2 步），外部 root 工具应用是备选。模块只做挂载覆盖，不写系统分区，删模块即可恢复原厂字体。",
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
        // ---- 第 1 步：Shizuku ----
        item {
            WizardStepCard(
                step = 1,
                title = "Shizuku",
                status = wizard.shizuku,
                onRecheck = vm::checkWizardApps,
            ) {
                Text(
                    "root 工具靠 Shizuku 拿到 ADB 权限来跑。Shizuku 的运行状态没有现成接口可查，" +
                        "这里只检测是否已安装；装好后请在 Shizuku 内确认它正在运行。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (vm.sysFontShizukuInstalled == true) {
                        Button(
                            onClick = {
                                if (!launchApp(context, WizardApps.SHIZUKU_PACKAGE)) {
                                    vm.flash("打不开 Shizuku，请在应用列表里手动打开")
                                }
                            },
                            modifier = Modifier.heightIn(min = 56.dp),
                        ) {
                            Text("打开 Shizuku", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                    } else if (shizukuTool != null) {
                        Button(
                            onClick = { if (!openLink(context, shizukuTool.downloadUrl)) vm.flash("没有可以打开链接的应用") },
                            modifier = Modifier.heightIn(min = 56.dp),
                        ) {
                            Text("去下载 Shizuku", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                if (vm.sysFontShizukuInstalled != true) {
                    Text(
                        "装好后开无线调试配对：在「开发者选项」里开「无线调试」，在 Shizuku 里点「通过无线调试启动」→「配对」，" +
                            "再在「无线调试」页点「使用配对码配对设备」，把 6 位配对码填入 Shizuku 弹出的通知；回到 Shizuku 点「启动」，" +
                            "看到「Shizuku 正在运行」即成功。重启手机后需重新启动（配对只需做一次）。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        // ---- 第 2 步：root 工具 ----
        item {
            WizardStepCard(
                step = 2,
                title = "root 工具",
                status = wizard.rootTool,
                onRecheck = vm::checkWizardApps,
            ) {
                when (val display = toolDisplay) {
                    null -> Text(
                        "等设备检查完成后再按机型推荐 root 工具。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    else -> {
                        if (display.recommendedTool == null) {
                            Text(
                                display.reason,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        } else {
                            Text(
                                "推荐：${display.recommendedTool.label}（${display.toolsLabel}）",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                "支持情况：${display.statusText}" +
                                    if (display.supportsCurrentFirmware) "（直接对应本固件）" else "（移植 / 候选回退，需额外确认）",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "引擎：${display.engineText}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "驱动：${display.driverText}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                display.reason,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (display.requiresFreshP0Session) {
                                Text(
                                    "这个载荷要求 fresh P0 会话：请按 Root-My-Galaxy 内的提示操作。",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.tertiary,
                                )
                            }
                            WizardToolRow(
                                tool = display.recommendedTool,
                                installed = display.recommendedTool in vm.sysFontInstalledRootTools ||
                                    display.recommendedTool in vm.sysFontConfirmedRootTools,
                                context = context,
                                vm = vm,
                            )
                            display.otherTool?.let { other ->
                                Text(
                                    "另一个工具：${other.label}（也支持这台设备）",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                WizardToolRow(
                                    tool = other,
                                    installed = other in vm.sysFontInstalledRootTools ||
                                        other in vm.sysFontConfirmedRootTools,
                                    context = context,
                                    vm = vm,
                                )
                            }
                            if (display.recommendedTool !in vm.sysFontInstalledRootTools &&
                                display.recommendedTool !in vm.sysFontConfirmedRootTools
                            ) {
                                Text(
                                    "如果已经装好但这里一直显示没装（有的工具包名查不到），点下面手动确认。",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                OutlinedButton(
                                    onClick = { vm.confirmRootToolInstalled(display.recommendedTool) },
                                    modifier = Modifier.heightIn(min = 56.dp),
                                ) {
                                    Text(
                                        "我已装好 ${display.recommendedTool.label}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    }
                }
                Text("内嵌一键 root（Pro）", style = MaterialTheme.typography.titleLarge)
                Text(
                    "主路是本应用内嵌的 root-my-s24 引擎（98260，载荷已随包带上），不用另外装 root 工具；外部工具是备选。先跑预检，全部通过才能继续。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                when (vm.embeddedRootState) {
                    EmbeddedRootState.IDLE -> Text(
                        "还没预检。点下面先跑预检。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    EmbeddedRootState.CHECKING -> Text(
                        "正在预检…",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    EmbeddedRootState.READY -> Text(
                        "预检通过。点下面开始跑内嵌 root（Shizuku UserService + 98260 载荷）；跑起来后没有暂停，不要切后台、不要锁屏。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    EmbeddedRootState.LOADING -> Text(
                        "正在往特权进程传载荷…",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    EmbeddedRootState.RUNNING -> Text(
                        "正在跑 exploit…跑起来后没有暂停，不要切后台、不要锁屏。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    EmbeddedRootState.VERIFYING -> Text(
                        "正在校验 root 是否真的起来了…",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    EmbeddedRootState.SUCCEEDED -> Text(
                        "已取得 root，可以继续第 3 步。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    EmbeddedRootState.FAILED -> Text(
                        "失败了，原因：${vm.embeddedRootFailureReason ?: "见下方预检"}。98260 失败后请完整重启手机再试，不要立即重试。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                vm.embeddedRootChecks.forEach { check ->
                    Text(
                        "${if (check.passed) "✓" else "✗"} ${check.label}" +
                            if (check.reason.isNotBlank()) "：${check.reason}" else "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (check.passed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    )
                }
                Button(
                    onClick = if (vm.embeddedRootState == EmbeddedRootState.READY) {
                        vm::runEmbeddedRoot
                    } else {
                        vm::runEmbeddedRootPrecheck
                    },
                    enabled = vm.embeddedRootState != EmbeddedRootState.CHECKING &&
                        vm.embeddedRootState != EmbeddedRootState.LOADING &&
                        vm.embeddedRootState != EmbeddedRootState.RUNNING &&
                        vm.embeddedRootState != EmbeddedRootState.VERIFYING,
                    modifier = Modifier.heightIn(min = 56.dp),
                ) {
                    Text(
                        if (vm.embeddedRootState == EmbeddedRootState.READY) "开始 root" else "开始预检",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        // ---- 第 3 步：KernelSU ----
        item {
            WizardStepCard(
                step = 3,
                title = "KernelSU",
                status = wizard.kernelSu,
                onRecheck = vm::checkRoot,
                rechecking = vm.sysFontRootChecking,
            ) {
                when (vm.sysFontRoot) {
                    true -> Text("已检测到 root，可以继续。", style = MaterialTheme.typography.bodyMedium)
                    false -> Text(
                        "没有检测到 root。请先在 root 工具里取得 root，再回来点“重新检测”。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    null -> Text("还没检查 root。", style = MaterialTheme.typography.bodyMedium)
                }
                when (val decision = driverDecision) {
                    is DriverDecision.Allowed -> {
                        Text(
                            "驱动：${decision.driver.versionLabel}（管理器须用官方 ${decision.driver.managerLabel}）",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            decision.note,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    is DriverDecision.Bundled -> Text(
                        "驱动：${decision.note}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    is DriverDecision.Forbidden -> Text(
                        "驱动选不出来：${decision.reason}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
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
                    if (vm.sysFontKsuManagerInstalled == true) {
                        OutlinedButton(
                            onClick = {
                                if (!launchApp(context, WizardApps.KERNELSU_MANAGER_PACKAGE)) {
                                    vm.flash("打不开 KernelSU Manager，请在应用列表里手动打开")
                                }
                            },
                            modifier = Modifier.heightIn(min = 56.dp),
                        ) {
                            Text("打开 KernelSU Manager", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                    } else if (kernelsuTool != null) {
                        OutlinedButton(
                            onClick = { if (!openLink(context, kernelsuTool.downloadUrl)) vm.flash("没有可以打开链接的应用") },
                            modifier = Modifier.heightIn(min = 56.dp),
                        ) {
                            Text("去装 KernelSU Manager", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Text(
                    "root 检查通过即本步完成。管理器版本要与 root 工具加载的驱动版本匹配（驱动 3.2.5 对应管理器 v3.2.5），" +
                        "版本错配时管理器可能显示“未安装”。不要点管理器里的「安装 / 刷入」。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // ---- 第 4 步：模块（一键换字体） ----
        item {
            WizardStepCard(
                step = 4,
                title = "模块：一键换字体",
                status = wizard.module,
                onRecheck = vm::checkSystemFontDevice,
            ) {
                Text(
                    "点一下自动跑完：下载 Inter → 生成模块 → 安装 → 软重启，全程显示当前步骤，出错会停在当步并告诉你原因。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                vm.sysFontStep?.let { step ->
                    Text(
                        "$step（${(vm.sysFontProgress * 100).toInt()}%）",
                        style = MaterialTheme.typography.bodyMedium,
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
                                "该固件不在 S24+ 支持列表里，但有 S24 Ultra 同构建号待验证（已定标）载荷可试；确认风险后勾选下方「设备检查」里的“我已知晓，仍要继续”才能一键换字体。"
                            } else {
                                "该固件不在 S24+ 支持列表里，但有 S24 Ultra 同构建号载荷可试（你以前用过这条路子）；确认风险后勾选下方「设备检查」里的“我已知晓，仍要继续”才能一键换字体。"
                            }
                            ported -> "该固件是移植载荷（未实测），需先在 root 工具设置里开启未经测试的载荷；确认风险后勾选下方「设备检查」里的“我已知晓，仍要继续”才能一键换字体。"
                            calibrated -> "该固件已定标但未实测，root 可能不成功；确认风险后勾选下方「设备检查」里的“我已知晓，仍要继续”才能一键换字体。"
                            else -> "当前设备不支持换系统字体，因此不提供一键按钮。"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
                Button(
                    onClick = vm::adjustAndReinstallModule,
                    enabled = installAllowed && !vm.sysFontWorking,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                ) {
                    Text("调整并重新安装", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Text(
                    "改了上面语种开关或粗细后点这里：重新生成模块 + 重装 + 重启 zygote，一次完成（字体文件不重下）。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
        // ---- 设备检查详情（保留） ----
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
                                            ported -> "我已知晓这是移植载荷（未实测），需在 root 工具设置里开启未经测试的载荷，root 可能不成功，仍要继续"
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
                        "本工具从不修改系统分区，删掉模块就恢复原厂字体。万一开机后字体异常或进不了系统：进 KernelSU 管理器，找到「Inter」模块关掉它再重启；或者用电脑连上手机执行 adb shell rm -rf /data/adb/modules/tokifontspro-font 后重启；安全模式下模块不会加载，开机后也能直接删模块。原版 fonts.xml 还备份在应用里（系统字体目录下的 fonts.xml.bak）和模块里（fonts.xml.original）。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
                    Text("会把模块装到 /data/adb/modules/tokifontspro-font，并软重启 zygote。模块只做挂载覆盖，不写系统分区，随时可以一键回滚。")
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

/** 向导一步的卡片：标题行带状态，内容由调用方给，底部固定一个“重新检测”。 */
@Composable
private fun WizardStepCard(
    step: Int,
    title: String,
    status: WizardStepStatus,
    onRecheck: () -> Unit,
    rechecking: Boolean = false,
    content: @Composable () -> Unit,
) {
    Surface(shape = TokiCardShape, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(TokiSpacing.xl), verticalArrangement = Arrangement.spacedBy(TokiSpacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "第 $step 步 · $title",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    status.state.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = when (status.state) {
                        WizardStepState.DONE -> MaterialTheme.colorScheme.primary
                        WizardStepState.BLOCKED -> MaterialTheme.colorScheme.error
                        WizardStepState.IN_PROGRESS -> MaterialTheme.colorScheme.tertiary
                        WizardStepState.TODO -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Text(
                status.note,
                style = MaterialTheme.typography.bodyMedium,
                color = when (status.state) {
                    WizardStepState.BLOCKED -> MaterialTheme.colorScheme.error
                    WizardStepState.DONE -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            content()
            OutlinedButton(
                onClick = onRecheck,
                enabled = !rechecking,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) {
                Text(
                    if (rechecking) "检测中…" else "重新检测",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/** 第 2 步里一个 root 工具的行：已装给“打开”，没装给“去下载”。 */
@Composable
private fun WizardToolRow(
    tool: RootTool,
    installed: Boolean,
    context: android.content.Context,
    vm: FontsViewModel,
) {
    val installerTool = InstallerTools.firstOrNull {
        it.id == when (tool) {
            RootTool.ROOT_MY_S24 -> "root-my-s24"
            RootTool.ROOT_MY_GALAXY -> "rootmygalaxy"
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val packageName = WizardApps.launchPackageFor(tool)
        if (installed && packageName != null) {
            Button(
                onClick = {
                    if (!launchApp(context, packageName)) {
                        vm.flash("打不开 ${tool.label}，请在应用列表里手动打开")
                    }
                },
                modifier = Modifier.heightIn(min = 56.dp),
            ) {
                Text("打开 ${tool.label}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        } else if (installed) {
            // 已装（用户确认的），但这个工具的包名没有可核对的来源，做不出启动按钮。
            Text(
                "已装好 ${tool.label}（你确认的）",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (installerTool != null) {
            OutlinedButton(
                onClick = { if (!openLink(context, installerTool.downloadUrl)) vm.flash("没有可以打开链接的应用") },
                modifier = Modifier.heightIn(min = 56.dp),
            ) {
                Text("去下载 ${tool.label}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}
