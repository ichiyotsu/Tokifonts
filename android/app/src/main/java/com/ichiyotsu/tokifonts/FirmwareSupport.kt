package com.ichiyotsu.tokifonts

/**
 * root-my-s24 支持的固件表 + 自动匹配：纯逻辑（无 Android 依赖，可单测）。
 *
 * 表的来源是 root-my-s24 README v3.7.0-alpha.1 的实测记录（见 [TABLE_SOURCE]），
 * 是本应用判断“当前设备能不能 root”的唯一依据；旧的机型白名单 + 内核 6.1.x
 * 检查退为辅助（见 [SystemFont.checkDevice] 里的内核二次校验）。
 *
 * 两点硬性规则：
 * - root 成功率不是 100%，98260 引擎每次开机只运行一次（见 [PROBABILISTIC_NOTE]）；
 * - 待验证（已定标、未实测）的固件绝不能被自动匹配放绿灯，必须由用户额外确认。
 */
/**
 * root 工具：换字体本身不嵌 exploit，只做数据合并与推荐——
 * 两个工具的载荷都由各自的上游应用提供，本 App 只记录“哪个工具支持哪台设备”。
 */
enum class RootTool(val label: String) {
    ROOT_MY_S24("root-my-s24"),
    ROOT_MY_GALAXY("root-my-galaxy"),
}

enum class FirmwareStatus(val label: String) {
    TESTED("已实测"),
    CALIBRATED("待验证"),
    /**
     * 移植：载荷来自另一工具的已发布 feed（如 Root-My-Galaxy），
     * 未经本 App / root-my-s24 真机实测；与待验证同级处理，需用户额外确认。
     */
    PORTED("移植"),
}

data class FirmwareEntry(
    /** Build.MODEL，如 SM-S9280。 */
    val model: String,
    /** 展示用机型，如“SM-S9280 国行”。 */
    val modelLabel: String,
    /** 展示用固件构建号；共用载荷的行写“共用 DZF2”这类文字，见 [matchKeys]。 */
    val firmwareBuild: String,
    /** 该固件的内核版本，如 "6.1.145"（只比对 major.minor）。 */
    val kernel: String,
    /** root 引擎："GhostLock" 或 "98260 UAF"。 */
    val engine: String,
    val status: FirmwareStatus,
    /** 提供这条载荷的工具；本表（ENTRIES）的行一律是 root-my-s24。 */
    val tool: RootTool = RootTool.ROOT_MY_S24,
    /**
     * KernelSU 驱动（README「驱动版本」表）：
     * 98260 引擎默认 3.2.5；GhostLock 旧固件走 3.3.0（实验）；
     * 3.3.1 KDP（实验）见 [DRIVER_KDP_EXPERIMENTAL]，v3.7.0-alpha.1 起可用，
     * 目前没有哪一行默认用它。
     */
    val driver: String = if (engine == "98260 UAF") FirmwareSupport.DRIVER_98260_DEFAULT else FirmwareSupport.DRIVER_GHOSTLOCK,
    val note: String = "",
    /**
     * 固件匹配用的子串（默认 = [firmwareBuild]）。三星的 Build.DISPLAY /
     * Build.VERSION.INCREMENTAL 里嵌着完整固件号，所以用子串比对。
     * 共用载荷的行用载荷标识，例如 SM-F9580 共用 SM-S9280 的 DZF2 载荷，
     * 就只用 "DZF2" 做匹配键（机型仍须先对上）。
     */
    val matchKeys: List<String> = listOf(firmwareBuild),
)

/** 固件自动匹配的结果。 */
enum class FirmwareMatchKind {
    /** 固件号精确命中，且该行已实测。 */
    EXACT_TESTED,
    /** 固件号精确命中，但该行只是已定标、未实测。 */
    EXACT_CALIBRATED,
    /** 命中移植载荷表（系列 + 内核 + 固件后缀），未经本 App 真机实测。 */
    PORTED,
    /** 机型在表里，但固件号没命中任何一行。 */
    MODEL_ONLY,
    /** 机型根本不在表里。 */
    UNKNOWN_MODEL,
}

data class FirmwareMatchResult(
    val kind: FirmwareMatchKind,
    /** 精确匹配到的那一行；[FirmwareMatchKind.MODEL_ONLY] / [FirmwareMatchKind.UNKNOWN_MODEL] / [FirmwareMatchKind.PORTED] 时为 null。 */
    val entry: FirmwareEntry?,
    /** 该机型在表里的所有行（MODEL_ONLY 时给用户看“该机型已知固件”）。 */
    val knownBuilds: List<FirmwareEntry>,
    /** 命中移植载荷表时的那一行；非 [FirmwareMatchKind.PORTED] 时为 null。 */
    val ported: PortedPayloadEntry? = null,
    /** 跨机型候选（S9260 用 S9280 同构建号载荷）；没有候选时为 null。 */
    val crossModel: CrossModelCandidate? = null,
)

/**
 * 跨机型候选（机主验证过的路子）：国行 S24+（SM-S9260）用 S24 Ultra（SM-S9280）
 * 同一条 6.1.145 内核线上同构建号（如 DZF2/DZG1/DZJ3）的载荷。
 * 已实测的行优先；没有已实测行时，待验证（已定标）的行也可以作候选，
 * 但必须按待验证对待（文案写“待验证（已定标）载荷”，绝不能写“已实测”）。
 * 只是一条候选建议，不改变主匹配等级；门禁里与待验证同级处理（琥珀提示 +
 * 需额外明确确认才尝试，不默认走）。
 */
data class CrossModelCandidate(
    val fromModel: String,
    val toModelLabel: String,
    /** S9280 那条行（已实测优先，否则为待验证已定标行）。 */
    val entry: FirmwareEntry,
    /** 命中的构建号后缀，如 "DZF2"。 */
    val suffix: String,
)

/**
 * 移植载荷行（root-my-s24 README 里的第三张表）：
 * 不是“机型 + 完整固件构建号”，而是“系列 + 内核 + 固件后缀”——
 * 同一系列共用一条载荷，运行时按 KASLR 定标，需在 RootMyS24 设置里打开
 * 「启用未经测试的载荷」才显示，未经本 App 真机实测。
 */
data class PortedPayloadEntry(
    /** 系列，如 "S23" / "S24" / "折叠屏" / "心系天下"。 */
    val seriesLabel: String,
    /** Build.MODEL 的前缀（大写比对），如 "SM-S9110"。 */
    val modelPrefixes: List<String>,
    /** 匹配用的内核 major.minor，如 ["5.15"]、["5.15", "6.1", "6.6"]。 */
    val kernels: List<String>,
    /** 展示用内核，如 "5.15"、"5.15 / 6.1 / 6.6"。 */
    val kernelLabel: String,
    /**
     * 固件后缀（大写子串比对），如 ["FZE3", "FZG1"]。
     * 为空表示只按机型 + 内核匹配（折叠屏行没有给出后缀）。
     */
    val firmwareSuffixes: List<String>,
    /** 展示用地区 / 固件说明，如 "国行 FZE3 / FZG1，港版 FZE3+"。 */
    val regions: String,
)

/**
 * Root-My-Galaxy 载荷行（support/targets-v3.json，schema 3）：
 * 匹配规则（上游 support/README.md）是精确机型（Build.MODEL）+
 * uname -r 的三段式内核版本（如 "6.6.98"）；固件构建号、指纹、
 * 安全补丁日期都不参与匹配——feed 里本来就没有固件构建号。
 * 引擎只有 CVE-2026-43499（GhostLock）这一条；feed 里没有 KernelSU
 * 版本号，驱动只记随载荷提供的 kernelsu 工件（url + size），绝不编版本号。
 * 状态一律是 [FirmwareStatus.PORTED]：这是另一工具已发布的载荷，
 * 未经本 App / root-my-s24 真机实测，与待验证同级处理。
 */
data class RmgPayloadEntry(
    /** feed 里的 payloadId，如 "e3q-S9280ZCS6DZF2"。 */
    val payloadId: String,
    /** feed 里的 displayName，如 "Galaxy S24 Ultra China | Kernel 6.1.145 (S9280ZCS6DZF2)"。 */
    val displayName: String,
    /** 精确的 Build.MODEL 值。 */
    val models: List<String>,
    /** 三段式内核版本，如 ["6.1.145"]。 */
    val kernelVersions: List<String>,
    /** exploit 工件（cve-2026-43499-app.so）的直链与字节数。 */
    val exploitUrl: String,
    val exploitSize: Long,
    /** 随载荷提供的 KernelSU 工件（ksud）的直链与字节数；版本号 feed 里没写。 */
    val kernelsuUrl: String,
    val kernelsuSize: Long,
    /** feed 里的 requiresFreshP0Session（缺省为 false）。 */
    val requiresFreshP0Session: Boolean = false,
) {
    val tool: RootTool get() = RootTool.ROOT_MY_GALAXY
    val engine: String get() = FirmwareSupport.RMG_ENGINE
    val driver: String get() = FirmwareSupport.RMG_DRIVER_NOTE
    val status: FirmwareStatus get() = FirmwareStatus.PORTED
}

/**
 * 双工具推荐结果（安装页第 2 步直接用）：
 * [recommendedTool] 是按等级排出来的首选（null = 两个工具都不支持）；
 * [supportedTools] 是实际命中的工具集合（两者都命中时含两项）；
 * [engine]/[driver] 取首选那一侧；移植载荷与跨机型候选以外的回退
 * （PORTED 表）没有引擎/驱动可填时为 null，见 [FirmwareSupport.recommendTool]；
 * [supportsCurrentFirmware] 为 true 表示首选直接对应本固件
 * （RMS24 精确行，或 RMG 机型 + 三段内核精确匹配），
 * 为 false 表示首选只是移植载荷表 / 跨机型候选的回退，需要额外确认。
 */
data class ToolRecommendation(
    val recommendedTool: RootTool?,
    val supportedTools: Set<RootTool>,
    val rms24Entry: FirmwareEntry?,
    val rmgPayload: RmgPayloadEntry?,
    val ported: PortedPayloadEntry?,
    val crossModel: CrossModelCandidate?,
    val status: FirmwareStatus?,
    val engine: String?,
    val driver: String?,
    val supportsCurrentFirmware: Boolean,
    val reason: String,
) {
    /** 展示用工具名：两者都支持时写“两者都支持”，否则写首选工具名，均不支持时写“均不支持”。 */
    val toolsLabel: String
        get() = when {
            supportedTools.size > 1 -> "两者都支持"
            recommendedTool != null -> recommendedTool.label
            else -> "均不支持"
        }
}

/**
 * App 内匹配用的整套表（内置快照或每日 feed 校验通过后的新表，二选一）：
 * 精确行 + RMG 载荷 + 移植系列 + 已修复 43499 表。[FirmwareSupport] 平时用
 * 内置表；feed 更新成功后由 ViewModel 调 [FirmwareSupport.useTables] 换成新表，
 * 校验失败则沿用上一份好的表，绝不用部分数据凑合。
 */
data class FirmwareTables(
    val entries: List<FirmwareEntry>,
    val rmgPayloads: List<RmgPayloadEntry>,
    val portedPayloads: List<PortedPayloadEntry>,
    val patched43499: Map<String, Set<String>>,
)

object FirmwareSupport {
    const val TABLE_SOURCE = "数据来自 root-my-s24 README v3.7.0-alpha.1"
    const val RMG_TABLE_SOURCE = "数据来自 Root-My-Galaxy-Payloads support/targets-v3.json（schema 3，20 个 payload）"
    const val PROBABILISTIC_NOTE = "root 成功率不是 100%，98260 引擎每次开机只运行一次，失败请重启后重试。"
    /** Root-My-Galaxy 唯一的 exploit 引擎。 */
    const val RMG_ENGINE = "GhostLock (CVE-2026-43499)"
    /** feed 里没有 KernelSU 版本号：驱动只记随载荷提供的工件，不编版本号。 */
    const val RMG_DRIVER_NOTE = "随载荷提供（KernelSU 工件）"
    /** README「驱动版本」表：98260 引擎默认。 */
    const val DRIVER_98260_DEFAULT = "KernelSU 3.2.5（默认）"
    /** README「驱动版本」表：GhostLock 旧固件 / 实验。 */
    const val DRIVER_GHOSTLOCK = "KernelSU 3.3.0（实验）"
    /** v3.7.0-alpha.1 起：KernelSU 3.3.1 KDP（含 One UI 9），实验；目前没有行默认用它。 */
    const val DRIVER_KDP_EXPERIMENTAL = "KernelSU 3.3.1 KDP（实验）"

    val ENTRIES: List<FirmwareEntry> = listOf(
        // ---- 已实测 ----
        FirmwareEntry("SM-S9280", "SM-S9280 国行", "S9280ZCS6DZF2", "6.1.145", "GhostLock", FirmwareStatus.TESTED),
        FirmwareEntry("SM-S9280", "SM-S9280 国行", "S9280ZCS6DZH3", "6.1.145", "GhostLock", FirmwareStatus.TESTED),
        FirmwareEntry("SM-S9280", "SM-S9280 国行", "S9280ZCS6DZG1", "6.1.145", "GhostLock", FirmwareStatus.TESTED),
        FirmwareEntry("SM-S9280", "SM-S9280 港版/台版", "S9280ZHS6DZE2", "6.1.145", "GhostLock", FirmwareStatus.TESTED),
        FirmwareEntry("SM-S9280", "SM-S9280 港版/台版", "S9280ZHS6DZH3", "6.1.145", "GhostLock", FirmwareStatus.TESTED),
        FirmwareEntry("SM-S9210", "SM-S9210 国行", "S9210ZCS6DZI1", "6.1.145", "GhostLock", FirmwareStatus.TESTED),
        FirmwareEntry("SM-S9260", "SM-S9260 台版", "S9260ZHS6DZG1", "6.1.145", "GhostLock", FirmwareStatus.TESTED),
        FirmwareEntry("SM-F741N", "SM-F741N 韩版 Z Flip6", "F741NKSS4DZE1", "6.1.145", "GhostLock", FirmwareStatus.TESTED),
        FirmwareEntry(
            model = "SM-F9580", modelLabel = "SM-F9580 国行 Z Fold6", firmwareBuild = "共用 DZF2",
            kernel = "6.1.145", engine = "GhostLock", status = FirmwareStatus.TESTED,
            note = "与 SM-S9280 共用 DZF2 载荷", matchKeys = listOf("DZF2"),
        ),
        FirmwareEntry("SM-S928B", "SM-S928B 欧版 S24 Ultra One UI 9.0", "S928BXXU6EZIF", "6.1.162", "98260 UAF", FirmwareStatus.TESTED),
        FirmwareEntry("SM-S9280", "SM-S9280 港版/国行 One UI 9.0", "S9280ZHU6EZIF", "6.1.162", "98260 UAF", FirmwareStatus.TESTED),
        FirmwareEntry("SM-S921E", "SM-S921E 国际版 S24 One UI 9.0", "S921EXXU3DZIF", "6.1.162", "98260 UAF", FirmwareStatus.TESTED),
        FirmwareEntry("SM-S928B", "SM-S928B 欧版", "S928BXXS6EZJ1", "6.1.162", "98260 UAF", FirmwareStatus.TESTED),
        FirmwareEntry("SM-S928B", "SM-S928B 欧版", "S928BXXU6EZJB", "6.1.162", "98260 UAF", FirmwareStatus.TESTED),
        FirmwareEntry("SM-S928B", "SM-S928B 欧版", "S928BXXU6EZJD", "6.1.162", "98260 UAF", FirmwareStatus.TESTED),
        FirmwareEntry("SM-S9280", "SM-S9280 港版/国行", "S9280ZHS6EZJ1", "6.1.162", "98260 UAF", FirmwareStatus.TESTED),
        FirmwareEntry(
            model = "SM-F9560", modelLabel = "SM-F9560 港版 Z Fold6", firmwareBuild = "F9560ZCS4DZH5",
            kernel = "6.1.145", engine = "GhostLock", status = FirmwareStatus.TESTED, note = "共用 DZF2 载荷",
        ),
        FirmwareEntry(
            model = "SM-F956N", modelLabel = "SM-F956N 韩版 Z Fold6", firmwareBuild = "F956NKSU4EZIH",
            kernel = "6.1.162", engine = "98260 UAF", status = FirmwareStatus.TESTED, note = "共用 EZIF 载荷",
        ),
        FirmwareEntry("W9025", "W9025 国行 W25", "W9025ZCS4DZH5", "6.1.145", "GhostLock", FirmwareStatus.TESTED),
        FirmwareEntry("W9025", "W9025 国行 W25", "W9025ZCS4DZG3", "6.1.145", "GhostLock", FirmwareStatus.TESTED),
        // ---- 待验证（已定标、未实测）----
        FirmwareEntry("SM-S9280", "SM-S9280 港版/台版", "S9280ZHS4CZA1", "6.1.128", "GhostLock", FirmwareStatus.CALIBRATED),
        FirmwareEntry("SM-S9280", "SM-S9280 国行", "S9280ZCS4CZA1", "6.1.128", "GhostLock", FirmwareStatus.CALIBRATED),
        FirmwareEntry("SM-S9280", "SM-S9280 国行", "S9280ZCS4CZB2", "6.1.128", "GhostLock", FirmwareStatus.CALIBRATED),
        FirmwareEntry("SM-S9210", "SM-S9210 国行 One UI 7", "S9210ZCU4BYH7", "6.1.99", "GhostLock", FirmwareStatus.CALIBRATED),
        FirmwareEntry("SM-S9310", "SM-S9310 国行 S25 One UI 8.5", "S9310ZCSCCZG1", "6.6.98", "GhostLock", FirmwareStatus.CALIBRATED),
        FirmwareEntry("SM-S9360", "SM-S9360 国行 S25+ One UI 8.5", "S9360ZCSCCZG1", "6.6.98", "GhostLock", FirmwareStatus.CALIBRATED),
        FirmwareEntry("SM-S9380", "SM-S9380 国行 S25 Ultra One UI 8.5", "S9380ZCSCCZG1", "6.6.98", "GhostLock", FirmwareStatus.CALIBRATED),
        FirmwareEntry("SM-S928B", "SM-S928B 欧版 One UI 8.0", "S928BXXS4CZA1", "6.1.128", "GhostLock", FirmwareStatus.CALIBRATED),
        FirmwareEntry("SM-S928B", "SM-S928B 欧版 One UI 7.0", "S928BXXU4BYD9", "6.1.99", "GhostLock", FirmwareStatus.CALIBRATED),
        FirmwareEntry("SM-S928B", "SM-S928B 欧版 One UI 6.1", "S928BXXU4AXK5", "6.1.75", "GhostLock", FirmwareStatus.CALIBRATED),
        FirmwareEntry(
            model = "SM-S9280", modelLabel = "SM-S9280 国行", firmwareBuild = "S9280ZCS6DZJ3",
            kernel = "6.1.145", engine = "98260 UAF", status = FirmwareStatus.CALIBRATED,
            note = "社区定标（PR #44），手动 staging 真机 root 成功；App 内待验证",
        ),
    )

    /**
     * Root-My-Galaxy 载荷表（targets-v3.json，20 个 payload，逐条照抄 feed；
     * exploit/kernelsu 的 url 与 size 都是 feed 原值）。
     * 同机型 + 同内核出现两个 payload 时（如 SM-F966U 的两个 6.6.98 行），
     * feed 里没有固件构建号可区分，[matchRmg] 取第一个。
     */
    val RMG_PAYLOADS: List<RmgPayloadEntry> = listOf(
        RmgPayloadEntry(
            payloadId = "galaxy-s25-series-2026-06-07",
            displayName = "Galaxy S25 series | Kernel 6.6.98",
            models = listOf("SM-S9310", "SM-S931B", "SM-S931N", "SM-S931Q", "SM-S931U", "SM-S931U1", "SM-S931W", "SM-S931Z", "SC-51F", "SCG31", "SM-S936B", "SM-S936N", "SM-S936U", "SM-S936U1", "SM-S936W", "SM-S937B", "SM-S937N", "SM-S937U", "SM-S937U1", "SM-S937W", "SM-S9380", "SM-S938B", "SM-S938N", "SM-S938Q", "SM-S938U", "SM-S938U1", "SM-S938W", "SM-S938Z", "SC-52F", "SCG32"),
            kernelVersions = listOf("6.6.98"),
            exploitUrl = "https://raw.githubusercontent.com/BuSung-dev/Root-My-Galaxy-Payloads/main/artifacts/pa3q-S938NKSUACZF1/cve-2026-43499-app.so",
            exploitSize = 104128L,
            kernelsuUrl = "https://raw.githubusercontent.com/BuSung-dev/Root-My-Galaxy-Payloads/main/kernelsu/ksud-s25u-kdp",
            kernelsuSize = 6407096L,
        ),
        RmgPayloadEntry(
            payloadId = "pa2q-S9360ZCSCCZG1",
            displayName = "Galaxy S25+ | Kernel 6.6.98",
            models = listOf("SM-S9360"),
            kernelVersions = listOf("6.6.98"),
            exploitUrl = "https://raw.githubusercontent.com/BuSung-dev/Root-My-Galaxy-Payloads/main/artifacts/pa2q-S9360ZCSCCZG1/cve-2026-43499-app.so",
            exploitSize = 125904L,
            kernelsuUrl = "https://raw.githubusercontent.com/BuSung-dev/Root-My-Galaxy-Payloads/main/kernelsu/ksud-s25u-kdp",
            kernelsuSize = 6407096L,
        ),
        RmgPayloadEntry(
            payloadId = "psq-S9370ZCS9CZG1",
            displayName = "Galaxy S25 Edge | Kernel 6.6.98",
            models = listOf("SM-S9370"),
            kernelVersions = listOf("6.6.98"),
            exploitUrl = "https://raw.githubusercontent.com/BuSung-dev/Root-My-Galaxy-Payloads/main/artifacts/psq-S9370ZCS9CZG1/cve-2026-43499-app.so",
            exploitSize = 125264L,
            kernelsuUrl = "https://raw.githubusercontent.com/BuSung-dev/Root-My-Galaxy-Payloads/main/kernelsu/ksud-s25u-kdp",
            kernelsuSize = 6407096L,
        ),
        RmgPayloadEntry(
            payloadId = "q7q-F966USQU9BZDN",
            displayName = "Galaxy Z Fold 7 | Kernel 6.6.98",
            models = listOf("SM-F966U", "SM-F966U1"),
            kernelVersions = listOf("6.6.98"),
            exploitUrl = "https://raw.githubusercontent.com/BuSung-dev/Root-My-Galaxy-Payloads/main/artifacts/q7q-F966USQU9BZDN/cve-2026-43499-app.so",
            exploitSize = 111216L,
            kernelsuUrl = "https://raw.githubusercontent.com/BuSung-dev/Root-My-Galaxy-Payloads/main/kernelsu/ksud-s25u-kdp",
            kernelsuSize = 6407096L,
        ),
        RmgPayloadEntry(
            payloadId = "q7q-F966USQSBBZG3",
            displayName = "Galaxy Z Fold 7 (SM-F966U BZG3) | Kernel 6.6.98",
            models = listOf("SM-F966U"),
            kernelVersions = listOf("6.6.98"),
            exploitUrl = "https://raw.githubusercontent.com/BuSung-dev/Root-My-Galaxy-Payloads/main/artifacts/q7q-F966USQSBBZG3/cve-2026-43499-app.so",
            exploitSize = 126264L,
            kernelsuUrl = "https://raw.githubusercontent.com/BuSung-dev/Root-My-Galaxy-Payloads/main/kernelsu/ksud-s25u-kdp",
            kernelsuSize = 6407096L,
        ),
        RmgPayloadEntry(
            payloadId = "q7q-F966BXXSBBZG3",
            displayName = "Galaxy Z Fold 7 (SM-F966B) | Kernel 6.6.98",
            models = listOf("SM-F966B"),
            kernelVersions = listOf("6.6.98"),
            exploitUrl = "https://raw.githubusercontent.com/BuSung-dev/Root-My-Galaxy-Payloads/main/artifacts/q7q-F966BXXSBBZG3/cve-2026-43499-app.so",
            exploitSize = 125872L,
            kernelsuUrl = "https://raw.githubusercontent.com/BuSung-dev/Root-My-Galaxy-Payloads/main/kernelsu/ksud-s25u-kdp",
            kernelsuSize = 6407096L,
        ),
        RmgPayloadEntry(
            payloadId = "e3q-S928USQS6DZF2",
            displayName = "Galaxy S24 Ultra | Kernel 6.1.145",
            models = listOf("SM-S928U1", "SM-S928W"),
            kernelVersions = listOf("6.1.145"),
            exploitUrl = "https://raw.githubusercontent.com/BuSung-dev/Root-My-Galaxy-Payloads/main/artifacts/e3q-S928USQS6DZF2/cve-2026-43499-app.so",
            exploitSize = 104128L,
            kernelsuUrl = "https://raw.githubusercontent.com/BuSung-dev/Root-My-Galaxy-Payloads/main/kernelsu/ksud-e3q-S928USQS6DZF2-kdp",
            kernelsuSize = 4998352L,
        ),
        RmgPayloadEntry(
            payloadId = "e3q-S928BXXS6DZF2",
            displayName = "Galaxy S24 Ultra (International) | Kernel 6.1.145",
            models = listOf("SM-S928B"),
            kernelVersions = listOf("6.1.145"),
            exploitUrl = "https://raw.githubusercontent.com/BuSung-dev/Root-My-Galaxy-Payloads/main/artifacts/e3q-S928BXXS6DZF2/cve-2026-43499-app.so",
            exploitSize = 104128L,
            kernelsuUrl = "https://raw.githubusercontent.com/BuSung-dev/Root-My-Galaxy-Payloads/main/kernelsu/ksud-e3q-S928BXXS6DZF2-kdp",
            kernelsuSize = 4748232L,
        ),
        RmgPayloadEntry(
            payloadId = "e2s-S926BXXUEDZDR",
            displayName = "Galaxy S24+ | Kernel 6.1.157",
            models = listOf("SM-S926B"),
            kernelVersions = listOf("6.1.157"),
            exploitUrl = "https://raw.githubusercontent.com/BuSung-dev/Root-My-Galaxy-Payloads/main/artifacts/e2s-S926BXXUEDZDR/cve-2026-43499-app.so",
            exploitSize = 104128L,
            kernelsuUrl = "https://raw.githubusercontent.com/BuSung-dev/Root-My-Galaxy-Payloads/main/kernelsu/ksud-e2s-S926BXXUEDZDR-kdp",
            kernelsuSize = 4780056L,
            requiresFreshP0Session = true,
        ),
        RmgPayloadEntry(
            payloadId = "e1s-S921BXXSFDZE1",
            displayName = "Galaxy S24 | Kernel 6.1.157",
            models = listOf("SM-S921B"),
            kernelVersions = listOf("6.1.157"),
            exploitUrl = "https://raw.githubusercontent.com/BuSung-dev/Root-My-Galaxy-Payloads/main/artifacts/e1s-S921BXXSFDZE1/cve-2026-43499-app.so",
            exploitSize = 104128L,
            kernelsuUrl = "https://raw.githubusercontent.com/BuSung-dev/Root-My-Galaxy-Payloads/main/kernelsu/ksud-e1s-S921BXXSFDZE1-kdp",
            kernelsuSize = 4920520L,
            requiresFreshP0Session = true,
        ),
        RmgPayloadEntry(
            payloadId = "essi-A566EXXSCCZG6",
            displayName = "Galaxy A56 5G | Kernel 6.6.102",
            models = listOf("SM-A5660", "SM-A566B", "SM-A566E", "SM-A566S", "SM-A566U1", "SM-A566W"),
            kernelVersions = listOf("6.6.102"),
            exploitUrl = "https://raw.githubusercontent.com/BuSung-dev/Root-My-Galaxy-Payloads/main/artifacts/essi-A566EXXSCCZG6/cve-2026-43499-app.so",
            exploitSize = 104128L,
            kernelsuUrl = "https://raw.githubusercontent.com/BuSung-dev/Root-My-Galaxy-Payloads/main/kernelsu/ksud-A566EXXSCCZG6-kdp",
            kernelsuSize = 4765336L,
        ),
        RmgPayloadEntry(
            payloadId = "a36xq-A366WVLS3AYG1",
            displayName = "Galaxy A36 5G | Kernel 6.6.46",
            models = listOf("SM-A366W"),
            kernelVersions = listOf("6.6.46"),
            exploitUrl = "https://raw.githubusercontent.com/BuSung-dev/Root-My-Galaxy-Payloads/main/artifacts/a36xq-A366WVLS3AYG1/cve-2026-43499-app.so",
            exploitSize = 128688L,
            kernelsuUrl = "https://raw.githubusercontent.com/BuSung-dev/Root-My-Galaxy-Payloads/main/kernelsu/ksud-A366WVLS3AYG1-kdp",
            kernelsuSize = 4889120L,
        ),
        RmgPayloadEntry(
            payloadId = "dm3q-S918BXXSAFZF5",
            displayName = "Galaxy S23 Ultra SM-S918B | Kernel 5.15.189 (S918BXXSAFZF5)",
            models = listOf("SM-S918B"),
            kernelVersions = listOf("5.15.189"),
            exploitUrl = "https://raw.githubusercontent.com/BuSung-dev/Root-My-Galaxy-Payloads/main/artifacts/dm3q-S918BXXSAFZF5/cve-2026-43499-app.so",
            exploitSize = 133344L,
            kernelsuUrl = "https://raw.githubusercontent.com/BuSung-dev/Root-My-Galaxy-Payloads/main/kernelsu/ksud-dm3q-S918BXXSAFZF5-kdp",
            kernelsuSize = 4879560L,
            requiresFreshP0Session = true,
        ),
        RmgPayloadEntry(
            payloadId = "a53x-A536EXXSNGZG3",
            displayName = "Galaxy A53 5G | Kernel 5.10.237",
            models = listOf("SM-A536E"),
            kernelVersions = listOf("5.10.237"),
            exploitUrl = "https://raw.githubusercontent.com/BuSung-dev/Root-My-Galaxy-Payloads/main/artifacts/a53x-A536EXXSNGZG3/cve-2026-43499-app.so",
            exploitSize = 104128L,
            kernelsuUrl = "https://raw.githubusercontent.com/BuSung-dev/Root-My-Galaxy-Payloads/main/kernelsu/ksud-A536EXXSNGZG3-kdp",
            kernelsuSize = 4870752L,
            requiresFreshP0Session = true,
        ),
        RmgPayloadEntry(
            payloadId = "dm3q-S9180ZHS8FZF5",
            displayName = "Galaxy S23 Ultra | Kernel 5.15.189",
            models = listOf("SM-S9180", "SM-S918N", "SM-S918Q", "SM-S918U", "SM-S918W", "SM-S918U1"),
            kernelVersions = listOf("5.15.189"),
            exploitUrl = "https://raw.githubusercontent.com/BuSung-dev/Root-My-Galaxy-Payloads/main/artifacts/dm3q-S9180ZHS8FZF5/cve-2026-43499-app.so",
            exploitSize = 137728L,
            kernelsuUrl = "https://raw.githubusercontent.com/BuSung-dev/Root-My-Galaxy-Payloads/main/kernelsu/ksud-dm3q-S9180ZHS8FZF5-kdp",
            kernelsuSize = 4556352L,
        ),
        RmgPayloadEntry(
            payloadId = "e1s-S921NKSSFDZF3",
            displayName = "Galaxy S24 | Kernel 6.1.157",
            models = listOf("SM-S921N"),
            kernelVersions = listOf("6.1.157"),
            exploitUrl = "https://raw.githubusercontent.com/BuSung-dev/Root-My-Galaxy-Payloads/main/artifacts/e1s-S921NKSSFDZF3/cve-2026-43499-app.so",
            exploitSize = 104128L,
            kernelsuUrl = "https://raw.githubusercontent.com/BuSung-dev/Root-My-Galaxy-Payloads/main/kernelsu/ksud-e1s-S921NKSSFDZF3-kdp",
            kernelsuSize = 4758872L,
            requiresFreshP0Session = true,
        ),
        RmgPayloadEntry(
            payloadId = "dm1q-S911U1UES6DYI3",
            displayName = "Galaxy S23 (base) | Kernel 5.15.153 | tracefs/MCAST route",
            models = listOf("SM-S911U1"),
            kernelVersions = listOf("5.15.153"),
            exploitUrl = "https://raw.githubusercontent.com/BuSung-dev/Root-My-Galaxy-Payloads/main/artifacts/dm1q-S911U1UES6DYI3/cve-2026-43499-app.so",
            exploitSize = 130168L,
            kernelsuUrl = "https://raw.githubusercontent.com/BuSung-dev/Root-My-Galaxy-Payloads/main/kernelsu/ksud-dm1q-S911U1UES6DYI3-kdp",
            kernelsuSize = 4758792L,
        ),
        RmgPayloadEntry(
            payloadId = "r12s-S721BXXSCDZF3",
            displayName = "Galaxy S24 FE | Kernel 6.1.157",
            models = listOf("SM-S721B"),
            kernelVersions = listOf("6.1.157"),
            exploitUrl = "https://raw.githubusercontent.com/BuSung-dev/Root-My-Galaxy-Payloads/main/artifacts/r12s-S721BXXSCDZF3/cve-2026-43499-app.so",
            exploitSize = 104128L,
            kernelsuUrl = "https://raw.githubusercontent.com/BuSung-dev/Root-My-Galaxy-Payloads/main/kernelsu/ksud-r12s-S721BXXSCDZF3-kdp",
            kernelsuSize = 6419768L,
            requiresFreshP0Session = true,
        ),
        RmgPayloadEntry(
            payloadId = "gts9-X710XXS6EZF1",
            displayName = "Galaxy Tab S9 SM-X710 | Kernel 5.15.189 (X710XXS6EZF1)",
            models = listOf("SM-X710"),
            kernelVersions = listOf("5.15.189"),
            exploitUrl = "https://raw.githubusercontent.com/BuSung-dev/Root-My-Galaxy-Payloads/main/artifacts/gts9-X710XXS6EZF1/cve-2026-43499-app.so",
            exploitSize = 132688L,
            kernelsuUrl = "https://raw.githubusercontent.com/BuSung-dev/Root-My-Galaxy-Payloads/main/kernelsu/ksud-gts9-X710XXS6EZF1-kdp",
            kernelsuSize = 4783256L,
            requiresFreshP0Session = true,
        ),
        RmgPayloadEntry(
            payloadId = "e3q-S9280ZCS6DZF2",
            displayName = "Galaxy S24 Ultra China | Kernel 6.1.145 (S9280ZCS6DZF2)",
            models = listOf("SM-S9280"),
            kernelVersions = listOf("6.1.145"),
            exploitUrl = "https://raw.githubusercontent.com/BuSung-dev/Root-My-Galaxy-Payloads/main/artifacts/e3q-S9280ZCS6DZF2/cve-2026-43499-app.so",
            exploitSize = 104128L,
            kernelsuUrl = "https://raw.githubusercontent.com/BuSung-dev/Root-My-Galaxy-Payloads/main/kernelsu/ksud-e3q-S9280ZCS6DZF2-kdp",
            kernelsuSize = 4879856L,
            requiresFreshP0Session = true,
        ),
    )

    /**
     * 已修复 CVE-2026-43499 的固件（机型 → 固件后缀集合）：
     * DZJ3 已修复 43499（RMS24 README 与 DZJ3 定标报告都写明，
     * DZJ3 只能走 98260 per-target 行），所以 GhostLock/43499 引擎
     * 在这些固件上不可用。RMG 的 exploit 只有 43499 这一条，
     * [matchRmg]/[recommendTool] 必须把这类固件上的 RMG 行排除，
     * 不能只在注释里写。后缀取固件构建号的尾部子串比对，
     * 与本文件其他固件匹配的口径一致。
     */
    val PATCHED_43499_FIRMWARE: Map<String, Set<String>> = mapOf(
        "SM-S9280" to setOf("DZJ3"),
    )

    /** [model] 的当前固件是否已修复 CVE-2026-43499（GhostLock 不可用）。 */
    fun isGhostLockPatched(model: String, display: String, incremental: String): Boolean {
        val suffixes = activeTables().patched43499[model.trim().uppercase()] ?: return false
        val haystack = "${display.trim().uppercase()} ${incremental.trim().uppercase()}"
        return suffixes.any { it.uppercase() in haystack }
    }

    val TESTED: List<FirmwareEntry> get() = activeTables().entries.filter { it.status == FirmwareStatus.TESTED }
    val CALIBRATED: List<FirmwareEntry> get() = activeTables().entries.filter { it.status == FirmwareStatus.CALIBRATED }

    const val PORTED_SETTING_NOTE = "移植载荷运行时按 KASLR 定标，需在 RootMyS24 设置里打开「启用未经测试的载荷」才显示。"
    const val DZF2_COVERAGE_NOTE = "国行 DZF2 载荷自 v3.5.0 起覆盖 DZE2–DZH3（同一条 6.1.145 内核线）"
    const val US_S24_NOTE = "美版 S24 系列（SM-S921U / S926U1 / S928U / S928U1）暂不支持，美版 S24+（S926U1/XAA）DZE2 已定标但因缺真机实测未随包发布"

    /** 移植载荷表（第三张表）：系列 + 内核 + 固件后缀，未经本 App 真机实测。 */
    val PORTED_PAYLOADS: List<PortedPayloadEntry> = listOf(
        PortedPayloadEntry(
            seriesLabel = "S23", modelPrefixes = listOf("SM-S9110", "SM-S9160", "SM-S9180"),
            kernels = listOf("5.15"), kernelLabel = "5.15",
            firmwareSuffixes = listOf("FZE3", "FZG1"), regions = "国行 FZE3 / FZG1，港版 FZE3+",
        ),
        PortedPayloadEntry(
            seriesLabel = "S24", modelPrefixes = listOf("SM-S9210", "SM-S9260", "SM-S9280"),
            kernels = listOf("6.1"), kernelLabel = "6.1.145",
            firmwareSuffixes = listOf("DZE2", "DZF2", "DZG1"), regions = "国行 DZE2 / DZF2 / DZG1，港台 DZG1",
        ),
        PortedPayloadEntry(
            seriesLabel = "S25", modelPrefixes = listOf("SM-S9310", "SM-S9360", "SM-S9370", "SM-S9380"),
            kernels = listOf("6.6"), kernelLabel = "6.6",
            firmwareSuffixes = listOf("BZA1", "BZC1", "CZF1", "CZG1"), regions = "国行 BZA1 / BZC1 / CZF1 / CZG1，港版 CZG1",
        ),
        PortedPayloadEntry(
            seriesLabel = "S26", modelPrefixes = listOf("SM-S9420", "SM-S9470", "SM-S9480"),
            kernels = listOf("6.12"), kernelLabel = "6.12",
            firmwareSuffixes = listOf("AZG1"), regions = "国行 AZG1",
        ),
        PortedPayloadEntry(
            // Z Flip5/6/7、Z Fold5/6/7：Flip5 SM-F731、Flip6 SM-F741、Flip7 SM-F761、
            // Fold5 SM-F946、Fold6 SM-F956、Fold7 SM-F966；另含 F956U 美版、F966B 欧版。
            seriesLabel = "折叠屏",
            modelPrefixes = listOf("SM-F73", "SM-F74", "SM-F76", "SM-F94", "SM-F95", "SM-F96"),
            kernels = listOf("5.15", "6.1", "6.6"), kernelLabel = "5.15 / 6.1 / 6.6",
            firmwareSuffixes = emptyList(),
            regions = "Z Flip5/6/7、Z Fold5/6/7 国行，F956U 美版，F966B 欧版",
        ),
        PortedPayloadEntry(
            // W25 的 Build.MODEL 在本应用实测表里记为 W9025，这里两种写法都接受。
            seriesLabel = "心系天下",
            modelPrefixes = listOf("W9025", "SM-W9025", "W9026", "SM-W9026"),
            kernels = listOf("6.1", "6.6"), kernelLabel = "6.1 / 6.6",
            firmwareSuffixes = listOf("DZF2", "DZG3", "BZG3", "BZF1"), regions = "W25 DZF2/DZG3、W26 BZG3/BZF1",
        ),
    )

    /**
     * 按机型前缀 + 内核大版本（major.minor）+ 固件后缀匹配移植载荷表。
     * 内核读不到时返回 null（不命中）。
     */
    fun matchPorted(model: String, display: String, incremental: String, kernelVersion: String): PortedPayloadEntry? {
        val kernel = SystemFont.parseKernelVersion(kernelVersion) ?: return null
        val kernelKey = "${kernel.first}.${kernel.second}"
        val normModel = model.trim().uppercase()
        val haystack = "${display.trim().uppercase()} ${incremental.trim().uppercase()}"
        return activeTables().portedPayloads.firstOrNull { entry ->
            entry.modelPrefixes.any { normModel.startsWith(it.uppercase()) } &&
                kernelKey in entry.kernels &&
                (entry.firmwareSuffixes.isEmpty() || entry.firmwareSuffixes.any { it.uppercase() in haystack })
        }
    }

    /** [entry] 的固件后缀里命中的那一个；后缀表为空（折叠屏行）时返回 null。 */
    fun matchedSuffix(entry: PortedPayloadEntry, display: String, incremental: String): String? {
        val haystack = "${display.trim().uppercase()} ${incremental.trim().uppercase()}"
        return entry.firmwareSuffixes.firstOrNull { it.uppercase() in haystack }
    }

    /**
     * 跨机型候选：只在 SM-S9260 上出现——本机固件构建号的后缀（如 DZF2/DZG1/DZJ3）
     * 在 S9280 的 6.1.145 行里存在，且本机内核也在 6.1.x 线上。
     * 已实测的行优先于待验证（已定标）的行；后者作候选时，调用方必须按待验证
     * 对待（文案写“待验证（已定标）载荷”，绝不能写“已实测”）。
     * 后缀取固件构建号最后 4 位（如 "S9280ZCS6DZF2" → "DZF2"）。
     */
    fun crossModelCandidate(model: String, display: String, incremental: String, kernelVersion: String): CrossModelCandidate? {
        if (!model.trim().equals("SM-S9260", ignoreCase = true)) return null
        val kernel = SystemFont.parseKernelVersion(kernelVersion) ?: return null
        if (kernel.first != 6 || kernel.second != 1) return null
        val haystack = "${display.trim().uppercase()} ${incremental.trim().uppercase()}"
        // 已实测优先，再找待验证（已定标）行：例如 DZJ3 只有 S9280 的定标行，
        // S9260 上仍可作候选一试，但绝不能按已实测放行。
        val row = activeTables().entries.firstOrNull { entry ->
            entry.model == "SM-S9280" && entry.status == FirmwareStatus.TESTED &&
                SystemFont.parseKernelVersion(entry.kernel) == (6 to 1) &&
                entry.firmwareBuild.takeLast(4).uppercase() in haystack
        } ?: activeTables().entries.firstOrNull { entry ->
            entry.model == "SM-S9280" && entry.status == FirmwareStatus.CALIBRATED &&
                SystemFont.parseKernelVersion(entry.kernel) == (6 to 1) &&
                entry.firmwareBuild.takeLast(4).uppercase() in haystack
        } ?: return null
        return CrossModelCandidate("SM-S9260", "S24 Ultra", row, row.firmwareBuild.takeLast(4).uppercase())
    }

    /**
     * 按机型 + 三段式内核版本匹配 Root-My-Galaxy 载荷（上游规则）：
     * 机型精确比对（大小写不敏感），内核取 uname -r 的前三段
     * （如 "6.1.145-android14-11-00001" → "6.1.145"）精确比对；
     * 固件构建号不参与匹配（feed 里没有）。
     * 但有一条硬排除：如果当前固件已修复 CVE-2026-43499
     * （见 [PATCHED_43499_FIRMWARE]，如 SM-S9280 DZJ3），
     * RMG 的 GhostLock 载荷一律返回 null——RMG 只有 43499 这一条引擎，
     * 修了就不可用，哪怕机型 + 内核都对得上。
     * 内核读不到三段时返回 null（不命中）。
     */
    fun matchRmg(model: String, display: String, incremental: String, kernelVersion: String): RmgPayloadEntry? {
        if (isGhostLockPatched(model, display, incremental)) return null
        return matchRmgUnpatched(model, kernelVersion)
    }

    /** 不含 43499 已修复排除的 RMG 匹配（仅供 [recommendTool] 判断“是否被补丁挡住”）。 */
    private fun matchRmgUnpatched(model: String, kernelVersion: String): RmgPayloadEntry? {
        val kernelFull = SystemFont.parseKernelFullVersion(kernelVersion) ?: return null
        val normModel = model.trim().uppercase()
        return activeTables().rmgPayloads.firstOrNull { entry ->
            entry.models.any { it.equals(normModel, ignoreCase = true) } &&
                kernelFull in entry.kernelVersions
        }
    }

    /**
     * 双工具推荐（安装页第 2 步直接用）：
     * 输入机型 + display + incremental + 内核，输出首选工具 + 命中的行 + 状态。
     * 等级（数字越小越优先，与 [SystemFont.checkDevice] 的主等级口径一致）：
     * 0. RMS24 精确行·已实测；1. RMS24 精确行·待验证；
     * 2. RMG 载荷（机型 + 三段内核精确匹配，移植）；
     * 3. RMS24 移植载荷表（系列 + 内核 + 固件后缀，移植）；
     * 4. RMS24 跨机型候选（只在同一工具内生效：S9260 用 S9280 同构建号行，
     *    绝不串到 RMG 去）。
     * 两工具都支持时优先已实测的；一侧已实测、另一侧待验证/移植时选已实测；
     * 都没有已实测时按上述等级走。DZJ3 这类已修复 43499 的固件上，
     * RMG 行在第 2 级就被排除，只剩 RMS24 的 98260 行。
     * [ToolRecommendation.supportsCurrentFirmware] 为 true 仅当首选是
     * RMS24 精确行或 RMG 精确匹配；移植载荷表 / 跨机型候选是回退，记 false。
     * 注意：本函数不改变 [SystemFont.checkDevice] 的等级——RMG 命中
     * 不会把一台设备从 UNSUPPORTED 变成 SUPPORTED，只是推荐层面的补充。
     */
    fun recommendTool(model: String, display: String, incremental: String, kernelVersion: String): ToolRecommendation {
        val exact = matchFirmware(model, display, incremental)
        val exactEntry = if (exact.kind == FirmwareMatchKind.EXACT_TESTED ||
            exact.kind == FirmwareMatchKind.EXACT_CALIBRATED
        ) exact.entry else null
        val rmg = matchRmg(model, display, incremental, kernelVersion)
        val rmgBlockedByPatch = rmg == null &&
            isGhostLockPatched(model, display, incremental) &&
            matchRmgUnpatched(model, kernelVersion) != null
        val patchNote = if (rmgBlockedByPatch) {
            "（Root-My-Galaxy 的 GhostLock 载荷因本固件已修复 CVE-2026-43499 而不可用）"
        } else {
            ""
        }
        if (exactEntry != null) {
            val tools = buildSet {
                add(RootTool.ROOT_MY_S24)
                if (rmg != null) add(RootTool.ROOT_MY_GALAXY)
            }
            val testedText = if (exactEntry.status == FirmwareStatus.TESTED) "已实测" else "已定标、未实测"
            return ToolRecommendation(
                recommendedTool = RootTool.ROOT_MY_S24,
                supportedTools = tools,
                rms24Entry = exactEntry,
                rmgPayload = rmg,
                ported = null,
                crossModel = null,
                status = exactEntry.status,
                engine = exactEntry.engine,
                driver = exactEntry.driver,
                supportsCurrentFirmware = true,
                reason = "$testedText：${exactEntry.modelLabel} + ${exactEntry.firmwareBuild}" +
                    "（${exactEntry.engine}，驱动 ${exactEntry.driver}），推荐 root-my-s24" +
                    if (rmg != null) "；Root-My-Galaxy 也有对应载荷（${rmg.payloadId}）" else "" +
                    patchNote,
            )
        }
        if (rmg != null) {
            val ported = matchPorted(model, display, incremental, kernelVersion)
            val tools = buildSet {
                add(RootTool.ROOT_MY_GALAXY)
                if (ported != null) add(RootTool.ROOT_MY_S24)
            }
            return ToolRecommendation(
                recommendedTool = RootTool.ROOT_MY_GALAXY,
                supportedTools = tools,
                rms24Entry = null,
                rmgPayload = rmg,
                ported = ported,
                crossModel = null,
                status = FirmwareStatus.PORTED,
                engine = rmg.engine,
                driver = rmg.driver,
                supportsCurrentFirmware = true,
                reason = "移植载荷：${rmg.displayName}（机型 + 内核 ${rmg.kernelVersions.joinToString("/")} 精确匹配），" +
                    "引擎 ${rmg.engine}，驱动${rmg.driver}，推荐 root-my-galaxy" +
                    if (rmg.requiresFreshP0Session) "，需 fresh P0 会话" else "",
            )
        }
        val ported = matchPorted(model, display, incremental, kernelVersion)
        if (ported != null) {
            val cross = crossModelCandidate(model, display, incremental, kernelVersion)
            val suffix = matchedSuffix(ported, display, incremental)
            val suffixText = suffix?.let { " + $it" } ?: ""
            return ToolRecommendation(
                recommendedTool = RootTool.ROOT_MY_S24,
                supportedTools = setOf(RootTool.ROOT_MY_S24),
                rms24Entry = null,
                rmgPayload = null,
                ported = ported,
                crossModel = cross,
                status = FirmwareStatus.PORTED,
                engine = null,
                driver = null,
                supportsCurrentFirmware = false,
                reason = "移植载荷，未实测：${ported.seriesLabel}（$model）$suffixText，" +
                    "引擎与驱动以 RootMyS24 内实际载荷为准，需在设置里打开「启用未经测试的载荷」" +
                    patchNote,
            )
        }
        val cross = crossModelCandidate(model, display, incremental, kernelVersion)
        if (cross != null) {
            val sourceText = if (cross.entry.status == FirmwareStatus.TESTED) "已实测载荷" else "待验证（已定标）载荷"
            return ToolRecommendation(
                recommendedTool = RootTool.ROOT_MY_S24,
                supportedTools = setOf(RootTool.ROOT_MY_S24),
                rms24Entry = cross.entry,
                rmgPayload = null,
                ported = null,
                crossModel = cross,
                status = cross.entry.status,
                engine = cross.entry.engine,
                driver = cross.entry.driver,
                supportsCurrentFirmware = false,
                reason = "跨机型候选（仅 root-my-s24 内）：可用 S24 Ultra 同构建号 ${cross.suffix} 的$sourceText" +
                    "（${cross.entry.firmwareBuild}，${cross.entry.engine}，驱动 ${cross.entry.driver}），需点确认才尝试" +
                    patchNote,
            )
        }
        return ToolRecommendation(
            recommendedTool = null,
            supportedTools = emptySet(),
            rms24Entry = null,
            rmgPayload = null,
            ported = null,
            crossModel = null,
            status = null,
            engine = null,
            driver = null,
            supportsCurrentFirmware = false,
            reason = "两个工具都不支持该机型 / 固件 / 内核组合" + patchNote,
        )
    }

    /**
     * 按机型 + 固件号匹配。
     * [display] 是 Build.DISPLAY（如 "UP1A.231005.007.S9280ZCS6DZF2"），
     * [incremental] 是 Build.VERSION.INCREMENTAL（如 "S9280ZCS6DZF2"），
     * 两者都按子串比对——三星固件号嵌在 DISPLAY 里，整串相等反而匹配不上。
     * 机型先精确比对（大小写不敏感）；固件号只在该机型的行里找。
     * 已实测的行优先于待验证的行。
     */
    fun matchFirmware(model: String, display: String, incremental: String): FirmwareMatchResult {
        val normModel = model.trim().uppercase()
        val rows = activeTables().entries.filter { it.model.equals(normModel, ignoreCase = true) }
        if (rows.isEmpty()) return FirmwareMatchResult(FirmwareMatchKind.UNKNOWN_MODEL, null, emptyList())
        val haystack = "${display.trim().uppercase()} ${incremental.trim().uppercase()}"
        val exact = rows.sortedBy { it.status }.firstOrNull { entry ->
            entry.matchKeys.any { key -> key.isNotBlank() && key.uppercase() in haystack }
        }
        if (exact != null) {
            val kind = if (exact.status == FirmwareStatus.TESTED) FirmwareMatchKind.EXACT_TESTED else FirmwareMatchKind.EXACT_CALIBRATED
            return FirmwareMatchResult(kind, exact, rows)
        }
        return FirmwareMatchResult(FirmwareMatchKind.MODEL_ONLY, null, rows)
    }

    // ---- 每日 feed 换表（内置表 / feed 新表二选一）----
    /** 内置快照表（与本文件手写常量一致）。 */
    val BUILTIN_TABLES: FirmwareTables
        get() = FirmwareTables(ENTRIES, RMG_PAYLOADS, PORTED_PAYLOADS, PATCHED_43499_FIRMWARE)

    @Volatile
    private var activeTablesState: FirmwareTables? = null

    /** 当前生效的表（默认内置表；feed 更新成功后换成新表）。 */
    fun activeTables(): FirmwareTables = activeTablesState ?: BUILTIN_TABLES

    /** 当前是否还在用内置表（feed 没更新成功 / 还没更新时为 true）。 */
    fun usingBuiltinTables(): Boolean = activeTablesState == null

    /** 换成 feed 校验通过的新表（调用前必须已过 [FirmwareFeed.validateFeed]）。 */
    fun useTables(tables: FirmwareTables) {
        activeTablesState = tables
    }

    /** 回到内置表（feed 校验失败 / 缓存损坏时调用）。 */
    fun resetTables() {
        activeTablesState = null
    }
}
