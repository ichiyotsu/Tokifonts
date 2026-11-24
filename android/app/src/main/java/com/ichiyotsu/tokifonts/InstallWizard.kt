package com.ichiyotsu.tokifonts

import android.content.Context
import android.content.Intent

/**
 * 安装页四步向导（Shizuku → root 工具 → KernelSU → 模块）的纯逻辑：
 * 步骤状态判定与第 2 步的推荐展示映射都放这里，不塞进 Composable，方便单测。
 *
 * 步骤状态（[WizardStepState]）：
 * - DONE：这步已完成；
 * - IN_PROGRESS：轮到这步了，正在做 / 可以开始做；
 * - TODO：还没轮到（前一步没完成）或还没检查；
 * - BLOCKED：被拦住了，说明里写清原因。
 * 前一步没完成时下一步一律是 TODO（被锁住），绝不提前放行。
 */

/** 四步向导里要打开的外部应用包名。 */
object WizardApps {
    /**
     * Shizuku 应用包名。来源：本仓库 InstallSolutions.kt 里 Shizuku 教程
     * 第 1 步原文（“包名 moe.shizuku.privileged.api”）；
     * root-my-s24 README（rms24-readme.md）里没有出现这个包名，别以它为据。
     */
    const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"

    /**
     * KernelSU Manager 包名。来源：KernelSU 上游 manager 源码包路径
     * `manager/app/src/main/java/me/weishu/kernelsu/`（tiann/KernelSU）。
     */
    const val KERNELSU_MANAGER_PACKAGE = "me.weishu.kernelsu"

    /**
     * root-my-s24 应用包名：按上游仓库源码包路径
     * `app/src/main/kotlin/cn/nanoturtle/rootmys9280/` 推断，
     * 未经真机核对 applicationId 是否与源码包一致；
     * 启动失败时界面一律回落到“去下载”，绝不假装已装。
     */
    const val ROOT_MY_S24_PACKAGE = "cn.nanoturtle.rootmys9280"

    /**
     * Root-My-Galaxy 的应用包名：已从上游 `app/build.gradle.kts` 核实
     * （v0.2.65：`namespace = "dev.busung.s25uroot"` 且
     * `applicationId = "dev.busung.s25uroot"`）；剩下没核实的只有真机启动——
     * 已安装的 release APK 是否响应这个包名的启动 intent，发版前真机点一次验证。
     * 启动失败时界面一律回落到“去下载”，绝不假装已装（两个工具都适用）。
     */
    const val ROOT_MY_GALAXY_PACKAGE = "dev.busung.s25uroot"

    fun launchPackageFor(tool: RootTool): String? = when (tool) {
        RootTool.ROOT_MY_S24 -> ROOT_MY_S24_PACKAGE
        RootTool.ROOT_MY_GALAXY -> ROOT_MY_GALAXY_PACKAGE
    }
}

/** 向导一步的状态。 */
enum class WizardStepState(val label: String) {
    DONE("已完成"),
    IN_PROGRESS("进行中"),
    TODO("待办"),
    BLOCKED("被拦住"),
}

/** 向导一步的判定结果：状态 + 给用户看的说明。 */
data class WizardStepStatus(
    val state: WizardStepState,
    /** 为什么是这个状态（被拦住时写清原因；待办时写清要先做什么）。 */
    val note: String,
)

/** 四步向导的全部状态（按第 1–4 步顺序）。 */
data class InstallWizardState(
    val shizuku: WizardStepStatus,
    val rootTool: WizardStepStatus,
    val kernelSu: WizardStepStatus,
    val module: WizardStepStatus,
) {
    operator fun get(index: Int): WizardStepStatus = when (index) {
        0 -> shizuku
        1 -> rootTool
        2 -> kernelSu
        else -> module
    }
}

/**
 * 四步向导状态机（纯函数）。
 *
 * @param shizukuInstalled Shizuku 是否已装：true 已装 / false 未装 / null 还没检查。
 * @param recommendation [FirmwareSupport.recommendTool] 的结果：null = 设备还没检查，
 *   还没法推荐。推荐本身不改变设备是否支持换字体，只决定第 2 步用哪个工具。
 * @param installedRootTools 已装的 root 工具集合（按包名检测到、有启动 intent 的）。
 *   推荐的工具没装、但另一个受支持的工具已装时，第 2 步同样算完成。
 * @param confirmedRootTools 用户手动确认“已装好”的 root 工具：
 *   个别工具可能因包名变化无法自动检测（root-my-s24 的包名已从上游
 *   build.gradle.kts 核实；Root-My-Galaxy 的包名同上，只剩真机启动未核对），装好后点“我已装好”
 *   也可以放行；状态说明里会写明是用户确认的，不是自动检测到的。
 * @param root root 检查结果：true 通过 / false 没通过 / null 还没检查。
 * @param moduleState 模块状态（[SystemFontModuleState]）。
 * @param installAllowed 第 4 步的安装放行条件（[FontsViewModel.sysFontInstallAllowed]）：
 *   已实测直接放行，待验证 / 移植 / 跨机型候选须用户先勾选风险确认。
 * @param working 一键 / 分步流程正在跑。
 */
fun installWizardState(
    shizukuInstalled: Boolean?,
    recommendation: ToolRecommendation?,
    installedRootTools: Set<RootTool>,
    root: Boolean?,
    moduleState: SystemFontModuleState,
    installAllowed: Boolean,
    working: Boolean,
    confirmedRootTools: Set<RootTool> = emptySet(),
): InstallWizardState {
    val step1 = when (shizukuInstalled) {
        true -> WizardStepStatus(
            WizardStepState.DONE,
            "已安装 Shizuku（运行状态请在 Shizuku 内确认）。",
        )
        false -> WizardStepStatus(
            WizardStepState.BLOCKED,
            "还没装 Shizuku。先装好，再按下面提示用无线调试启动它。",
        )
        null -> WizardStepStatus(
            WizardStepState.IN_PROGRESS,
            "还没检查 Shizuku 是否已装。",
        )
    }
    val step2 = when {
        step1.state != WizardStepState.DONE -> WizardStepStatus(
            WizardStepState.TODO,
            "先完成第 1 步（装好并启动 Shizuku）。",
        )
        recommendation == null -> WizardStepStatus(
            WizardStepState.TODO,
            "等设备检查完成后再推荐 root 工具。",
        )
        recommendation.recommendedTool == null -> WizardStepStatus(
            WizardStepState.BLOCKED,
            "两个工具都不支持这台设备：${recommendation.reason}",
        )
        recommendation.recommendedTool in installedRootTools ||
            installedRootTools.intersect(recommendation.supportedTools).isNotEmpty() ||
            recommendation.recommendedTool in confirmedRootTools ||
            confirmedRootTools.intersect(recommendation.supportedTools).isNotEmpty() -> {
            val usable = installedRootTools.intersect(recommendation.supportedTools) +
                confirmedRootTools.intersect(recommendation.supportedTools)
            val used = usable.firstOrNull() ?: recommendation.recommendedTool
            val confirmedNote = if (used in confirmedRootTools && used !in installedRootTools) "（你确认的，未能自动检测）" else ""
            WizardStepStatus(
                WizardStepState.DONE,
                "已装 ${used.label}$confirmedNote，可以继续。",
            )
        }
        else -> WizardStepStatus(
            WizardStepState.IN_PROGRESS,
            "推荐用 ${recommendation.recommendedTool.label}，还没装：去下载安装，装好回来点“重新检测”。",
        )
    }
    val step3 = when {
        step2.state != WizardStepState.DONE -> WizardStepStatus(
            WizardStepState.TODO,
            "先完成第 2 步（装好推荐的 root 工具）。",
        )
        root == true -> WizardStepStatus(
            WizardStepState.DONE,
            "已检测到 root，可以继续。",
        )
        root == false -> WizardStepStatus(
            WizardStepState.BLOCKED,
            "没有检测到 root。请先在 root 工具里取得 root，再回来点“重新检测”。",
        )
        else -> WizardStepStatus(
            WizardStepState.IN_PROGRESS,
            "还没检查 root。",
        )
    }
    val step4 = when {
        step3.state != WizardStepState.DONE -> WizardStepStatus(
            WizardStepState.TODO,
            "先完成第 3 步（取得 root）。",
        )
        moduleState == SystemFontModuleState.INSTALLED -> WizardStepStatus(
            WizardStepState.DONE,
            "模块已安装，系统字体已换成 Inter。",
        )
        working -> WizardStepStatus(
            WizardStepState.IN_PROGRESS,
            "正在换字体…",
        )
        !installAllowed -> WizardStepStatus(
            WizardStepState.BLOCKED,
            "现在不能安装：设备未通过检查，或待验证固件还没在下方「设备检查」里勾选风险确认。",
        )
        else -> WizardStepStatus(
            WizardStepState.IN_PROGRESS,
            "可以开始：点“一键换字体”，自动跑完下载 → 生成 → 安装 → 软重启。",
        )
    }
    return InstallWizardState(step1, step2, step3, step4)
}

/** 第 2 步里推荐结果的展示映射（纯函数；界面直接按它画，不再临场拼文案）。 */
data class RootToolStepDisplay(
    /** 推荐的工具：null = 两个工具都不支持。 */
    val recommendedTool: RootTool?,
    /** 首选工具是否直接对应本固件（精确行 / 精确载荷）；移植表与跨机型候选为 false。 */
    val supportsCurrentFirmware: Boolean,
    /** 状态文案：已实测 / 已定标、未实测 / 移植载荷、未实测 / 均不支持。 */
    val statusText: String,
    /** 引擎文案；第 3 级（RMS24 移植载荷表）没有引擎字段时写兜底，绝不编造。 */
    val engineText: String,
    /** 驱动文案；第 3 级没有驱动字段时写兜底，绝不编造。 */
    val driverText: String,
    /** [ToolRecommendation.reason] 原文。 */
    val reason: String,
    /** RMG 载荷要求 fresh P0 会话时为 true（界面追加提示）。 */
    val requiresFreshP0Session: Boolean,
    /** 另一个工具（两者都支持时才有；单工具支持时为 null）。 */
    val otherTool: RootTool?,
    /** 展示用工具名（两者都支持时为“两者都支持”）。 */
    val toolsLabel: String,
)

/**
 * 推荐结果 → 第 2 步展示。口径缺口：推荐第 3 级（RMS24 移植载荷表）时
 * [ToolRecommendation.engine]/[ToolRecommendation.driver] 为 null——
 * 移植表本身没有引擎/驱动字段；这里只写“以工具内实际载荷为准”，
 * 不把别的等级的引擎/驱动借过来，也不猜。
 */
fun rootToolStepDisplay(recommendation: ToolRecommendation?): RootToolStepDisplay? {
    if (recommendation == null) return null
    val statusText = when {
        recommendation.recommendedTool == null -> "均不支持"
        recommendation.status == FirmwareStatus.TESTED -> "已实测"
        recommendation.status == FirmwareStatus.CALIBRATED -> "已定标、未实测"
        recommendation.status == FirmwareStatus.PORTED -> "移植载荷、未实测"
        else -> "未确认"
    }
    val otherTool = when {
        recommendation.supportedTools.size > 1 && recommendation.recommendedTool != null ->
            recommendation.supportedTools.first { it != recommendation.recommendedTool }
        else -> null
    }
    return RootToolStepDisplay(
        recommendedTool = recommendation.recommendedTool,
        supportsCurrentFirmware = recommendation.supportsCurrentFirmware,
        statusText = statusText,
        engineText = recommendation.engine ?: "以工具内实际载荷为准",
        driverText = recommendation.driver ?: "以工具内实际载荷为准",
        reason = recommendation.reason,
        // 只有首选工具就是 RMG 且其载荷要求 fresh P0 会话时才为 true。
        // 第 0 级（RMS24 精确行）即使 RMG 也有对应载荷（otherTool），
        // 用户走的是 RMS24 路径，RMG 载荷不会被执行，绝不能把它的
        // fresh P0 要求贴到推荐路径上（reason 文案同样只在 RMG 首选
        // 分支里写“需 fresh P0 会话”）。
        requiresFreshP0Session = recommendation.recommendedTool == RootTool.ROOT_MY_GALAXY &&
            recommendation.rmgPayload?.requiresFreshP0Session == true,
        otherTool = otherTool,
        toolsLabel = recommendation.toolsLabel,
    )
}

/**
 * 按包名打开一个已装应用（getLaunchIntentForPackage）：
 * 打不开（没装，或没有启动入口）返回 false，调用方提示用户去下载，
 * 绝不崩溃。只做跳转联动，不嵌任何 exploit 本体。
 */
fun launchApp(context: Context, packageName: String): Boolean {
    return runCatching {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    }.getOrDefault(false)
}
