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
enum class FirmwareStatus(val label: String) {
    TESTED("已实测"),
    CALIBRATED("待验证"),
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

object FirmwareSupport {
    const val TABLE_SOURCE = "数据来自 root-my-s24 README v3.7.0-alpha.1"
    const val PROBABILISTIC_NOTE = "root 成功率不是 100%，98260 引擎每次开机只运行一次，失败请重启后重试。"

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

    val TESTED: List<FirmwareEntry> get() = ENTRIES.filter { it.status == FirmwareStatus.TESTED }
    val CALIBRATED: List<FirmwareEntry> get() = ENTRIES.filter { it.status == FirmwareStatus.CALIBRATED }

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
        return PORTED_PAYLOADS.firstOrNull { entry ->
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
        val row = ENTRIES.firstOrNull { entry ->
            entry.model == "SM-S9280" && entry.status == FirmwareStatus.TESTED &&
                SystemFont.parseKernelVersion(entry.kernel) == (6 to 1) &&
                entry.firmwareBuild.takeLast(4).uppercase() in haystack
        } ?: ENTRIES.firstOrNull { entry ->
            entry.model == "SM-S9280" && entry.status == FirmwareStatus.CALIBRATED &&
                SystemFont.parseKernelVersion(entry.kernel) == (6 to 1) &&
                entry.firmwareBuild.takeLast(4).uppercase() in haystack
        } ?: return null
        return CrossModelCandidate("SM-S9260", "S24 Ultra", row, row.firmwareBuild.takeLast(4).uppercase())
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
        val rows = ENTRIES.filter { it.model.equals(normModel, ignoreCase = true) }
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
}
