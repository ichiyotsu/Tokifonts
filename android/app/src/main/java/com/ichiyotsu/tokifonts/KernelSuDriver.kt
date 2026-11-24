package com.ichiyotsu.tokifonts

/**
 * KernelSU 驱动选择（纯逻辑，可单测）：按引擎 + 内核版本选驱动与管理器版本。
 *
 * 依据 `docs/embed-design.md` (b) 节（root-my-s24 README「驱动版本」表）：
 * - 98260 引擎（6.1.162 / One UI 9.0）→ 3.2.5（32525），默认；管理器官方 v3.2.5。
 * - GhostLock 旧固件（6.1.145 及更早）→ 3.3.0（32601），实验；管理器官方 v3.3.0。
 * - 3.3.1 KDP（含 One UI 9）→ 实验，v3.7.0-alpha.1 起可用；目前没有哪一行默认用它，
 *   绝不默认，界面也不主动推。
 *
 * 硬规则（违反就拦截，不要只写文案提醒）：
 * - 6.1.145 / 6.1.162 的 98260 引擎只能用 3.2.5：3.3.0 驱动在 6.1.162 上会让
 *   system_server 崩溃循环（→ RescueParty → 整机重启）。
 * - 版本错配时 `su` 可能还能用，但管理器会显示“未安装”或装不了模块：
 *   预检必须同时核对驱动与管理器版本，不要只核对驱动。
 * - RMG 行的驱动没有 KernelSU 版本号（feed 里根本没有）：驱动就是
 *   “随载荷提供的那份 ksud 工件”，用 feed 里的 url + size 校验完整性，绝不编版本号。
 */
enum class KsudDriver(val artifact: String, val versionLabel: String, val managerLabel: String) {
    /** KernelSU 3.2.5（32525）：98260 引擎默认；管理器官方 v3.2.5（32525）。 */
    V32525("ksud-32525", "KernelSU 3.2.5", "v3.2.5"),
    /** KernelSU 3.3.0（32601）：GhostLock 旧固件 / 实验；管理器官方 v3.3.0（32601）。 */
    V32601("ksud-32601", "KernelSU 3.3.0（实验）", "v3.3.0"),
    /** KernelSU 3.3.1 KDP：实验；目前没有哪一行默认用它，绝不默认。 */
    V331_KDP("ksud-331-kdp", "KernelSU 3.3.1 KDP（实验）", "待上游明确"),
}

/** 驱动选择结果：可用 / 被禁（写清原因）/ 无可用驱动（RMG 行，驱动随载荷提供）。 */
sealed interface DriverDecision {
    data class Allowed(val driver: KsudDriver, val note: String) : DriverDecision
    data class Forbidden(val reason: String) : DriverDecision
    /** RMG 载荷行：驱动没有版本号，就是 feed 里随载荷提供的那份 ksud 工件。 */
    data class Bundled(val note: String) : DriverDecision
}

/**
 * 按引擎 + 内核选驱动。
 *
 * @param engine [FirmwareEntry.engine] / [RmgPayloadEntry.engine]，如 "98260 UAF"、
 *   "GhostLock"、"GhostLock (CVE-2026-43499)"；null = 移植载荷表，没有引擎可填。
 * @param kernelFull 三段式内核版本，如 "6.1.162"（[SystemFont.parseKernelFullVersion]
 *   的返回值）；null = 内核读不到，不选。
 * @param requested 调用方明确要求的驱动；null = 按默认规则选。
 *   要求 3.3.0 但内核是 6.1.162 时一律拒绝；要求 3.3.1 KDP 时一律拒绝（绝不默认）。
 */
fun selectKsudDriver(
    engine: String?,
    kernelFull: String?,
    requested: KsudDriver? = null,
): DriverDecision {
    if (engine == null) {
        return DriverDecision.Forbidden("移植载荷表没有引擎字段，驱动以工具内实际载荷为准，本 App 不替它选驱动。")
    }
    if (engine.startsWith("GhostLock (CVE-2026-43499)")) {
        // RMG 行：feed 里没有 KernelSU 版本号，绝不编。
        return DriverDecision.Bundled("驱动随载荷提供（KernelSU 工件，见 feed 里的 kernelsu.url + size），版本号 feed 里没有。")
    }
    if (kernelFull == null) {
        return DriverDecision.Forbidden("内核版本读不到，无法选驱动，请先确认机型与固件后再试。")
    }
    // 硬规则：3.3.0 在 6.1.162 上会让 system_server 崩溃循环——无论引擎是什么都禁。
    if (kernelFull == "6.1.162" && requested == KsudDriver.V32601) {
        return DriverDecision.Forbidden("3.3.0 驱动在 6.1.162 上会让 system_server 崩溃循环（→ RescueParty → 整机重启），已拦截。")
    }
    if (requested == KsudDriver.V331_KDP) {
        return DriverDecision.Forbidden("3.3.1 KDP 是实验驱动，目前没有哪一行默认用它，不提供主动选择。")
    }
    if (requested != null) {
        return DriverDecision.Allowed(requested, "已按你的选择使用 ${requested.versionLabel}。")
    }
    return when (engine) {
        "98260 UAF" -> DriverDecision.Allowed(
            KsudDriver.V32525,
            "98260 引擎默认用 KernelSU 3.2.5；管理器须用官方 v3.2.5，版本错配会显示“未安装”。",
        )
        "GhostLock" -> {
            if (kernelFull == "6.1.162") {
                // 6.1.162 没有 GhostLock 行（DZJ3 已修复 43499）；走到这里说明匹配异常。
                DriverDecision.Forbidden("6.1.162 上没有 GhostLock 载荷（DZJ3 已修复 CVE-2026-43499），只能走 98260。")
            } else {
                DriverDecision.Allowed(
                    KsudDriver.V32601,
                    "GhostLock 旧固件用 KernelSU 3.3.0（实验）；管理器须用官方 v3.3.0。",
                )
            }
        }
        else -> DriverDecision.Forbidden("未知引擎“$engine”，无法选驱动。")
    }
}
