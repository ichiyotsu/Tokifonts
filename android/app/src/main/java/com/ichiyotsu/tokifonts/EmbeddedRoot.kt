package com.ichiyotsu.tokifonts

/**
 * 保守内嵌一键 root 的预检与运行状态机（纯逻辑，可单测）。
 *
 * 依据 `docs/embed-design.md` (c)/(e) 节。硬规则：
 * - 预检先行：机型 / 固件 / 内核精确匹配；待验证与移植行须用户额外明确确认，
 *   绝不自动放绿灯；先查已修复 43499 表；Shizuku 运行中；一次启动只跑一次。
 * - 任何一步失败立即停、清理、状态复位、只报失败原因。
 * - exploit 跑起来后没有中途暂停：界面绝不能放“暂停”按钮、不能给虚假进度百分比、
 *   不能承诺成功率；失败只给“重启手机后重试”的指引，不给“立即重试”按钮。
 * - 98260 失败绝不重试：失败 / 软重启后先重启手机再跑；GhostLock 失败可多试
 *   （这是 GhostLock 的规则，不要套到 98260 头上）。
 *
 * 本版范围：预检 + 状态机 + 内嵌产物清单核对。exploit 的实际执行链
 * （Shizuku UserService 胶水 + 载荷加载器）尚未接通——[EmbeddedRootState.NOT_WIRED]
 * 是诚实状态，不是占位文案；接通前绝不假装能跑。见 `docs/embed-inventory.md` §D。
 */

/** 内嵌一键 root 的一次预检项。 */
data class EmbeddedRootCheck(
    val label: String,
    val passed: Boolean,
    /** 没通过时写清原因；通过时可为空。 */
    val reason: String = "",
)

/** 内嵌一键 root 的运行状态（界面只能有这三种诚实状态）。 */
enum class EmbeddedRootState {
    /** 还没预检。 */
    IDLE,
    /** 预检中。 */
    CHECKING,
    /** 预检通过，等待执行——执行链未接通，本版停在这里，不执行。 */
    READY_NOT_WIRED,
    /** 正在跑 exploit（只有执行链接通后才会进入；本版进不到）。 */
    RUNNING,
    /** 成功：已取得 root（执行链跑完才有）。 */
    SUCCEEDED,
    /** 失败：只报原因 + “重启手机后重试”的指引，绝不给“立即重试”按钮。 */
    FAILED,
}

/** 一次启动只跑一次的守卫（98260 的硬规则，上游 App v3.6.2 起直接拦截二次尝试）。 */
class EmbeddedRootAttemptGuard(
    /**
     * 当前开机已流逝毫秒（默认取 [android.os.SystemClock.elapsedRealtime]；
     * 单测可注入）。普通 JVM 单测里 SystemClock 是 stub 会抛异常，用 0 兜底，
     * 不影响“重启后复位”的判定逻辑（注入时钟的测试走真值）。
     */
    private val nowElapsedRealtimeMs: () -> Long = {
        runCatching { android.os.SystemClock.elapsedRealtime() }.getOrDefault(0L)
    },
) {
    /** 本次开机内已跑过一次（成功、失败、软重启后都算跑过）。 */
    var attemptedThisBoot: Boolean = false
        private set

    /** 本次开机内 98260 已失败过：必须完整重启手机才能再试。 */
    var failed98260ThisBoot: Boolean = false
        private set

    /** 记录一次尝试开始（成功失败都算）；调用方在真正开始跑之前调。 */
    fun markAttempted() {
        attemptedThisBoot = true
    }

    /** 记录一次 98260 失败：此后同一次开机内一律拦截。 */
    fun markFailed98260() {
        failed98260ThisBoot = true
    }

    /**
     * 开机是否已变（elapsedRealtime 回绕 / 变小 = 重启过）：重启后守卫自动复位，
     * 不用用户手动清。调用方在每次预检前调一次。
     */
    fun resetIfRebooted() {
        val now = nowElapsedRealtimeMs()
        if (now < lastSeenElapsedRealtimeMs) {
            attemptedThisBoot = false
            failed98260ThisBoot = false
        }
        lastSeenElapsedRealtimeMs = now
    }

    private var lastSeenElapsedRealtimeMs: Long = nowElapsedRealtimeMs()
}

/**
 * 内嵌一键 root 的预检（纯函数）：全部通过才能进下一步，任何一项不过就停在向导里。
 *
 * @param recommendation [FirmwareSupport.recommendTool] 的结果；null = 设备还没检查。
 * @param shizukuRunning Shizuku 是否正在运行（pingBinder 结果）；null = 还没检查。
 * @param calibratedConfirmed 待验证 / 移植 / 跨机型候选的用户额外明确确认。
 * @param guard 一次启动一次的守卫。
 * @param embeddedAssetsPresent 内嵌产物是否都在（[EmbeddedAssets.checkPresent] 的结果）；
 *   缺任何一个都拦截，并写清缺了哪个。
 */
fun embeddedRootPrecheck(
    recommendation: ToolRecommendation?,
    shizukuRunning: Boolean?,
    calibratedConfirmed: Boolean,
    guard: EmbeddedRootAttemptGuard,
    embeddedAssetsPresent: Map<String, Boolean>,
): List<EmbeddedRootCheck> {
    guard.resetIfRebooted()
    val checks = mutableListOf<EmbeddedRootCheck>()
    checks += EmbeddedRootCheck(
        label = "设备已检查",
        passed = recommendation != null,
        reason = if (recommendation == null) "还没检查设备，先等设备检查完成。" else "",
    )
    val tool = recommendation?.recommendedTool
    checks += EmbeddedRootCheck(
        label = "有内嵌引擎支持的工具",
        passed = tool == RootTool.ROOT_MY_S24 && recommendation?.rms24Entry != null,
        reason = when {
            recommendation == null -> "还没检查设备。"
            tool == null -> "两个工具都不支持这台设备：${recommendation.reason}"
            tool == RootTool.ROOT_MY_GALAXY -> "推荐的工具是 Root-My-Galaxy，它的 .so 能否塞进本 App 的 GhostLock 加载链路还没核实，只能去装 Root-My-Galaxy，不能内嵌跑。"
            recommendation.rms24Entry == null -> "推荐的是移植载荷表 / 跨机型候选，没有精确行，内嵌不直接跑。"
            else -> ""
        },
    )
    val entry = recommendation?.rms24Entry
    val needsConfirm = entry != null && entry.status != FirmwareStatus.TESTED
    checks += EmbeddedRootCheck(
        label = "待验证载荷已由你明确确认",
        passed = !needsConfirm || calibratedConfirmed,
        reason = if (needsConfirm && !calibratedConfirmed) {
            "这行是${entry.status.label}（已定标、未实测），须先在下方「设备检查」里勾选风险确认，绝不自动放行。"
        } else "",
    )
    val ghostLockPatched = entry != null && entry.engine == "GhostLock"
    checks += EmbeddedRootCheck(
        label = "GhostLock 载荷可用",
        passed = entry == null || entry.engine != "GhostLock",
        reason = if (ghostLockPatched) {
            "按固件分的 GhostLock 载荷 cve-2026-43499-<TAG> 不在上游公开仓库里（发版时按 flavor 注入），本版取不到，内嵌跑不了 GhostLock；只能走 98260 行。"
        } else "",
    )
    checks += EmbeddedRootCheck(
        label = "Shizuku 正在运行",
        passed = shizukuRunning == true,
        reason = when (shizukuRunning) {
            true -> ""
            false -> "Shizuku 没在运行。先在第 1 步里按提示用无线调试启动它，再回来。"
            null -> "还没检查 Shizuku 是否在运行。"
        },
    )
    val missing = embeddedAssetsPresent.filterValues { !it }.keys.sorted()
    checks += EmbeddedRootCheck(
        label = "内嵌产物齐全",
        passed = missing.isEmpty(),
        reason = if (missing.isEmpty()) "" else "缺内嵌产物：${missing.joinToString("、")}（见 docs/embed-inventory.md）",
    )
    checks += EmbeddedRootCheck(
        label = "本次开机还没跑过",
        passed = !guard.attemptedThisBoot,
        reason = if (guard.attemptedThisBoot) "本次开机已经跑过一次了（成功、失败、软重启后都算跑过），98260 一次开机只跑一次，请重启手机后再试。" else "",
    )
    checks += EmbeddedRootCheck(
        label = "本次开机没有失败过的 98260",
        passed = !guard.failed98260ThisBoot,
        reason = if (guard.failed98260ThisBoot) "本次开机的 98260 已经失败过，失败绝不重试：请完整重启手机后再试。" else "",
    )
    return checks
}

/** 预检是否全部通过。 */
fun List<EmbeddedRootCheck>.allPassed(): Boolean = all { it.passed }

/**
 * 内嵌产物清单（assets/embed/ 下的文件；与 `docs/embed-inventory.md` §A 一致）。
 * [checkPresent] 由调用方拿 Android assets 逐个 `open()` 试读后填表；
 * 这里只定清单，不读文件，方便单测。
 */
object EmbeddedAssets {
    /** GhostLock 通用引擎 + root helper（按固件分的 -<TAG> 载荷取不到，见清单 §D）。 */
    val GHOSTLOCK: List<String> = listOf(
        "embed/cve-2026-43499",
        "embed/cve-2026-43499-root",
    )

    /** 98260 samtimer 引擎：公共载荷 + 脚本 + 三个 ksud 驱动。 */
    val SAMTIMER: List<String> = listOf(
        "embed/samtimer/libtimer",
        "embed/samtimer/uhexec",
        "embed/samtimer/umh_action.sh",
        "embed/samtimer/umh_action-stock.sh",
        "embed/samtimer/ksud-32525",
        "embed/samtimer/ksud-32601",
        "embed/samtimer/ksud-331-kdp",
    )

    /** 98260 per-target 载荷（运行时优先 targets 目录，缺失回退公共载荷）。 */
    val SAMTIMER_TARGETS: List<String> = listOf(
        "embed/samtimer/targets/S9280ZCS6DZJ3/libtimer",
        "embed/samtimer/targets/S721BXXUEEZIF/libtimer",
    )

    /** 随包走的许可证文本。 */
    val LICENSES: List<String> = listOf(
        "embed/licenses/gpl-2.0.txt",
        "embed/licenses/gpl-3.0.txt",
        "embed/licenses/SHIZUKU-APACHE-2.0.txt",
    )

    val ALL: List<String> = GHOSTLOCK + SAMTIMER + SAMTIMER_TARGETS + LICENSES

    /**
     * 给定推荐的引擎，跑内嵌需要哪些产物：
     * - 98260 行：samtimer 全套（per-target 载荷有就用，没有回退公共载荷，
     *   所以 targets 缺失只警告、不拦截——由 [requiredFor] 的调用方决定）；
     * - GhostLock 行：通用引擎在，但按固件分的 -<TAG> 载荷取不到，预检里已拦截。
     */
    fun requiredFor(engine: String?): List<String> = when (engine) {
        "98260 UAF" -> SAMTIMER + LICENSES
        "GhostLock" -> GHOSTLOCK + LICENSES
        else -> ALL
    }
}
