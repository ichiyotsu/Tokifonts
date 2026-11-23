package com.ichiyotsu.tokifonts

import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.StringWriter
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * 换系统字体：纯逻辑（无 Android 依赖，可单测）。
 *
 * 锁定决策：
 * - 只做 KSU magic mount（bind 覆盖），绝不写 /system 或任何系统分区；
 *   回滚 = 删 /data/adb/modules/tokifonts-font + 重启 zygote（或重启手机）。
 * - 组合字体 = Bold（700）only，每语种一个文件；“组合” = 字体文件 +
 *   设备上生成的 fonts.xml，不是合成一个 TTF。
 * - 生成规则：解析设备原 fonts.xml，只替换 sans-serif 系列 family 里
 *   weight=700 的 <font> 条目为所选语种的组合字体文件，其他条目原样保留；
 *   生成后必须重新解析验证，解析失败则拒绝安装。
 */
enum class SystemFontScript(
    val displayName: String,
    /** 模块内 / 设备 fonts.xml 里引用的文件名。 */
    val moduleFileName: String,
    /** 期望字节数（下载/复制后校验）。 */
    val expectedBytes: Long,
    /** 期望 SHA-256（小写 hex；苹方两档无公开校验值，留空表示只按字节数 + 文件头校验）。 */
    val expectedSha256: String,
    val sourceNote: String,
    val defaultEnabled: Boolean = true,
) {
    LATIN(
        displayName = "拉丁（Inter Bold）",
        moduleFileName = "Inter-Bold.otf",
        expectedBytes = 631_880L,
        expectedSha256 = "9fc6261e817d0b1b2bf1953d8799bb1c837d11aeebbf83ae352e7b4d6de6425d",
        sourceNote = "应用 assets 内置（OFL 允许再分发），安装时直接取用",
    ),
    SC(
        displayName = "简体中文（苹方 Bold）",
        moduleFileName = "2001.ttf",
        expectedBytes = 10_923_212L,
        expectedSha256 = "",
        sourceNote = "走现有 catalog + FontStore 下载（face 2001）；苹方为 Apple internal-use-only，绝不打包进仓库",
    ),
    TC(
        displayName = "繁体中文（苹方 Semibold）",
        moduleFileName = "2010.ttf",
        expectedBytes = 12_590_380L,
        expectedSha256 = "",
        sourceNote = "走现有 catalog + FontStore 下载（face 2010，weight 600，库里最粗的 HK 档，没有 HK Bold）",
    ),
    JP(
        displayName = "日文（Noto Sans JP Bold）",
        moduleFileName = "NotoSansCJKjp-Bold.otf",
        expectedBytes = 17_032_620L,
        expectedSha256 = "e53dcb0dcb2922e45d01aae1ebd2f382bb81d4229b18b6b883bd170678af1f76",
        sourceNote = "安装时按需从官方 raw URL 下载（notofonts/noto-cjk），不打包进 APK",
    ),
    KR(
        displayName = "韩文（Source Han Sans KR Bold）",
        moduleFileName = "SourceHanSansK-Bold.otf",
        expectedBytes = 16_959_020L,
        expectedSha256 = "b15ca0f485b4a055f2fb7b4055a11cbe4cddd18a73a380f49aeb01b11dc58201",
        sourceNote = "安装时按需从官方 raw URL 下载（adobe-fonts/source-han-sans），不打包进 APK",
    ),
}

/** 设备支持情况：内核 build 号精确表未知时给 [UNKNOWN]，只警告、不放行安装。 */
enum class DeviceSupport {
    /** 固件精确命中已实测行，可以安装。 */
    SUPPORTED,
    /** 机型不在表里。 */
    UNSUPPORTED,
    /** 无法确认（固件不在表里、内核读不到或与已知内核不一致），只警告、不放行安装。 */
    UNKNOWN,
    /** 固件精确命中待验证行：只在用户额外确认风险后放行。 */
    CALIBRATED,
    /** 命中移植载荷表（未经真机实测）：与 [CALIBRATED] 同级处理，只在用户额外确认后放行。 */
    PORTED,
    /** 有跨机型候选载荷可试（S9260 用 S9280 同构建号载荷）：只在用户额外确认后放行。 */
    CANDIDATE,
}

data class DeviceCheck(
    val manufacturer: String,
    val model: String,
    val kernelVersion: String,
    val support: DeviceSupport,
    /** 给用户看的原因 / 警告文案。 */
    val reason: String,
    /** 固件自动匹配结果（旧的三参入口拿不到固件号时为 null）。 */
    val firmware: FirmwareMatchResult? = null,
    /** 读到的固件字符串（Build.DISPLAY / Build.VERSION.INCREMENTAL），界面展示用。 */
    val firmwareDisplay: String = "",
    val firmwareIncremental: String = "",
)

object SystemFont {
    const val MODULE_ID = "tokifonts-font"
    /** 组合字体包的名字就叫 Inter（id 保持 tokifonts-font 不变）。 */
    const val MODULE_NAME = "Inter"
    const val MODULE_DIR = "/data/adb/modules/tokifonts-font"

    /** 支持机型白名单（Build.MODEL，大写比对）。 */
    val SUPPORTED_MODELS: Set<String> = setOf(
        // Galaxy S24
        "SM-S9210", "SM-S921B", "SM-S921N",
        // Galaxy S24+
        "SM-S9260", "SM-S926B", "SM-S926N",
        // Galaxy S24 Ultra
        "SM-S9280", "SM-S928B", "SM-S928N", "SM-S928U", "SM-S928W",
        // Galaxy Z Fold6
        "SM-F9560", "SM-F956N",
        // Galaxy Z Flip6
        "SM-F741N",
        // W 系列（S24 Ultra 国行定制版）
        "W9025",
    )

    /**
     * 设备检查（主入口，固件表优先）：
     * 先按机型 + 固件号查 [FirmwareSupport] 的实测表，再用内核做二次校验。
     * - 固件精确命中已实测行 + 内核 major.minor 与该行一致 → [DeviceSupport.SUPPORTED]；
     * - 固件精确命中待验证行 → [DeviceSupport.CALIBRATED]（只在用户额外确认后放行）；
     * - 固件表没命中，但命中移植载荷表（系列 + 内核 + 固件后缀）→ [DeviceSupport.PORTED]，
     *   与待验证行同级处理（琥珀警告 + 额外确认），且提示需在 RootMyS24 设置里开启未经测试的载荷；
     * - 机型在表里但固件号没命中 → [DeviceSupport.UNKNOWN]，并把该机型已知固件列进原因；
     * - 机型不在表里 → [DeviceSupport.UNSUPPORTED]；
     * - 内核读不到，或与匹配行的已知内核 major.minor 不一致 → [DeviceSupport.UNKNOWN]。
     * [firmwareDisplay] 是 Build.DISPLAY，[firmwareIncremental] 是 Build.VERSION.INCREMENTAL，
     * 两者都按子串比对（三星固件号嵌在 DISPLAY 里）。
     */
    fun checkDevice(
        manufacturer: String,
        model: String,
        firmwareDisplay: String,
        firmwareIncremental: String,
        kernelVersion: String,
    ): DeviceCheck {
        if (!manufacturer.trim().equals("samsung", ignoreCase = true)) {
            return DeviceCheck(
                manufacturer, model, kernelVersion, DeviceSupport.UNSUPPORTED,
                "只支持三星设备，当前厂商是 $manufacturer",
                firmwareDisplay = firmwareDisplay, firmwareIncremental = firmwareIncremental,
            )
        }
        val match = FirmwareSupport.matchFirmware(model, firmwareDisplay, firmwareIncremental)
        // 精确命中已实测 / 待验证行时走固件表；其他情况（机型在表里但固件未命中、
        // 机型根本不在表里）再查移植载荷表——已实测 / 待验证行永远优先于移植载荷。
        // 跨机型候选（S9260 用 S9280 同构建号载荷）只是一条附加建议，不改变主匹配等级。
        var crossModel: CrossModelCandidate? = null
        if (match.kind != FirmwareMatchKind.EXACT_TESTED && match.kind != FirmwareMatchKind.EXACT_CALIBRATED) {
            crossModel = FirmwareSupport.crossModelCandidate(model, firmwareDisplay, firmwareIncremental, kernelVersion)
            val ported = FirmwareSupport.matchPorted(model, firmwareDisplay, firmwareIncremental, kernelVersion)
            if (ported != null) {
                val suffix = FirmwareSupport.matchedSuffix(ported, firmwareDisplay, firmwareIncremental)
                val suffixText = suffix?.let { " + $it" } ?: ""
                val kernel = SystemFont.parseKernelVersion(kernelVersion)
                val kernelText = kernel?.let { "${it.first}.${it.second}" } ?: kernelVersion.ifBlank { "未知" }
                return DeviceCheck(
                    manufacturer, model, kernelVersion, DeviceSupport.PORTED,
                    "移植载荷，未实测：${ported.seriesLabel}（$model）$suffixText，内核 $kernelText；需在 RootMyS24 设置里开启未经测试的载荷，root 可能不成功",
                    firmware = FirmwareMatchResult(FirmwareMatchKind.PORTED, null, match.knownBuilds, ported = ported, crossModel = crossModel),
                    firmwareDisplay = firmwareDisplay, firmwareIncremental = firmwareIncremental,
                )
            }
        }
        when (match.kind) {
            FirmwareMatchKind.UNKNOWN_MODEL -> return DeviceCheck(
                manufacturer, model, kernelVersion, DeviceSupport.UNSUPPORTED,
                "机型 $model 不在支持列表（见“支持设备”，含 S23/S24/S25/S26、折叠屏与心系天下）",
                firmware = match.copy(crossModel = crossModel), firmwareDisplay = firmwareDisplay, firmwareIncremental = firmwareIncremental,
            )
            FirmwareMatchKind.MODEL_ONLY -> {
                val current = firmwareDisplay.ifBlank { firmwareIncremental }.ifBlank { "未知" }
                val known = match.knownBuilds.joinToString("、") { it.firmwareBuild }
                if (crossModel != null) {
                    return DeviceCheck(
                        manufacturer, model, kernelVersion, DeviceSupport.CANDIDATE,
                        "该固件（$current）未在 S24+ 支持列表里，但 S24 Ultra 有同构建号 ${crossModel.suffix} 的已实测载荷（${crossModel.entry.firmwareBuild}），你以前用这条路子通过过；需点确认才尝试，不默认走",
                        firmware = match.copy(crossModel = crossModel), firmwareDisplay = firmwareDisplay, firmwareIncremental = firmwareIncremental,
                    )
                }
                return DeviceCheck(
                    manufacturer, model, kernelVersion, DeviceSupport.UNKNOWN,
                    "机型 $model 在支持列表里，但当前固件（$current）未在支持列表里；该机型已知固件：$known",
                    firmware = match, firmwareDisplay = firmwareDisplay, firmwareIncremental = firmwareIncremental,
                )
            }
            FirmwareMatchKind.PORTED -> return DeviceCheck(
                manufacturer, model, kernelVersion, DeviceSupport.UNKNOWN,
                "固件匹配结果异常（移植载荷不应走到这里），请重新检查",
                firmware = match, firmwareDisplay = firmwareDisplay, firmwareIncremental = firmwareIncremental,
            )
            FirmwareMatchKind.EXACT_TESTED, FirmwareMatchKind.EXACT_CALIBRATED -> {
                val entry = match.entry ?: return DeviceCheck(
                    manufacturer, model, kernelVersion, DeviceSupport.UNKNOWN,
                    "固件匹配结果异常，请重新检查",
                    firmware = match, firmwareDisplay = firmwareDisplay, firmwareIncremental = firmwareIncremental,
                )
                val kernel = parseKernelVersion(kernelVersion)
                    ?: return DeviceCheck(
                        manufacturer, model, kernelVersion, DeviceSupport.UNKNOWN,
                        "无法读取内核版本号，无法确认该固件是否支持，请先确认机型与固件后再试",
                        firmware = match, firmwareDisplay = firmwareDisplay, firmwareIncremental = firmwareIncremental,
                    )
                val entryKernel = parseKernelVersion(entry.kernel)
                if (entryKernel == null || kernel.first != entryKernel.first || kernel.second != entryKernel.second) {
                    return DeviceCheck(
                        manufacturer, model, kernelVersion, DeviceSupport.UNKNOWN,
                        "固件 ${entry.firmwareBuild} 已匹配，但内核 ${kernel.first}.${kernel.second} 与该固件已知内核 ${entry.kernel} 不一致，请先确认该固件可用后再试",
                        firmware = match, firmwareDisplay = firmwareDisplay, firmwareIncremental = firmwareIncremental,
                    )
                }
                return if (match.kind == FirmwareMatchKind.EXACT_TESTED) {
                    DeviceCheck(
                        manufacturer, model, kernelVersion, DeviceSupport.SUPPORTED,
                        "支持：${entry.modelLabel} + ${entry.firmwareBuild}（${entry.engine}）",
                        firmware = match, firmwareDisplay = firmwareDisplay, firmwareIncremental = firmwareIncremental,
                    )
                } else {
                    DeviceCheck(
                        manufacturer, model, kernelVersion, DeviceSupport.CALIBRATED,
                        "已定标、未实测：${entry.modelLabel} + ${entry.firmwareBuild}（${entry.engine}），root 可能不成功",
                        firmware = match, firmwareDisplay = firmwareDisplay, firmwareIncremental = firmwareIncremental,
                    )
                }
            }
        }
    }

    /**
     * 设备检查（兼容入口）：拿不到固件号时只做机型白名单 + 内核 6.1.x 检查（旧行为）。
     * 界面一律走五参入口；这个入口只留给单测与没有固件号的调用方。
     */
    fun checkDevice(manufacturer: String, model: String, kernelVersion: String): DeviceCheck {
        val normModel = model.trim().uppercase()
        if (!manufacturer.trim().equals("samsung", ignoreCase = true)) {
            return DeviceCheck(manufacturer, model, kernelVersion, DeviceSupport.UNSUPPORTED, "只支持三星设备，当前厂商是 $manufacturer")
        }
        if (normModel !in SUPPORTED_MODELS) {
            return DeviceCheck(
                manufacturer, model, kernelVersion, DeviceSupport.UNSUPPORTED,
                "机型 $model 不在支持列表（仅三星 S24 系列 / Z Fold6 / Z Flip6）",
            )
        }
        val kernel = parseKernelVersion(kernelVersion)
            ?: return DeviceCheck(
                manufacturer, model, kernelVersion, DeviceSupport.UNKNOWN,
                "无法读取内核版本号，无法确认该固件是否支持，请先确认机型与固件后再试",
            )
        // S24 系列 One UI 6/7 的内核是 6.1.x；其他大版本（例如 One UI 8 的 6.6）
        // 不在已知支持表里，给警告、不放行。
        return if (kernel.first == 6 && kernel.second == 1) {
            DeviceCheck(manufacturer, model, kernelVersion, DeviceSupport.SUPPORTED, "机型与内核（$kernelVersion）均在支持范围")
        } else {
            DeviceCheck(
                manufacturer, model, kernelVersion, DeviceSupport.UNKNOWN,
                "内核 ${kernel.first}.${kernel.second} 不在已知支持表（已知：6.1.x），请先确认该固件可用后再试",
            )
        }
    }

    /** 失败自动取消的清理方案（纯逻辑，供单测与 ViewModel 共用）。 */
    data class CancelPlan(
        val clearStaging: Boolean,
        val deleteModuleDir: Boolean,
        val restartZygote: Boolean,
    )

    /**
     * 失败取消决策（纯逻辑，硬规则）：
     * - 任何一步失败都清 staging；
     * - 本次操作写过模块目录（安装步跑过，无论成败），或装后验证失败 → 删模块目录，
     *   绝不留半成品模块；
     * - 只有“模块已装上但装后验证失败”才重启 zygote（自动回滚）；其他取消不重启，
     *   因为新字体根本没挂上。
     */
    fun planCancel(moduleWrittenByThisRun: Boolean, postVerifyFailed: Boolean): CancelPlan =
        CancelPlan(
            clearStaging = true,
            deleteModuleDir = moduleWrittenByThisRun || postVerifyFailed,
            restartZygote = postVerifyFailed,
        )

    /** 清 staging：删模块 staging 目录与打包出来的 zip，半成品不留。 */
    fun clearStaging(stagingDir: File, zipFile: File) {
        stagingDir.deleteRecursively()
        zipFile.delete()
    }

    /** 从 os.version（如 "6.1.118-android14-..."）取 (major, minor)；取不到返回 null。 */
    fun parseKernelVersion(kernelVersion: String): Pair<Int, Int>? {
        val match = Regex("""(\d+)\.(\d+)""").find(kernelVersion.trim()) ?: return null
        val major = match.groupValues[1].toIntOrNull() ?: return null
        val minor = match.groupValues[2].toIntOrNull() ?: return null
        return major to minor
    }

    /** Noto / Source Han 按需下载用的 FontFace（复用 FontStore.download，走 raw.githubusercontent.com）。 */
    fun remoteFace(script: SystemFontScript): FontFace = when (script) {
        SystemFontScript.JP -> FontFace(
            id = "sysfont-noto-jp-bold",
            fileName = "NotoSansCJKjp-Bold.otf",
            exportName = "NotoSansCJKjp-Bold.otf",
            weight = 700, weightName = "Bold", weightLabel = "粗体",
            italic = false, optical = "", opticalLabel = "",
            variable = false, format = "otf", innerName = "",
            bytes = SystemFontScript.JP.expectedBytes,
            repo = "notofonts/noto-cjk", ref = "main",
            path = "Sans/OTF/Japanese/NotoSansCJKjp-Bold.otf",
            mirrorRepo = "", mirrorRef = "", mirrorPath = "",
            // 备用：jsDelivr 的 GitHub 镜像（raw.githubusercontent.com 曾返回 503）。
            // 两个地址都已实测：文件大小与 SHA-256 与期望值一致。
            extraUrls = listOf(
                FontUrls.jsdelivr("notofonts/noto-cjk", "main", "Sans/OTF/Japanese/NotoSansCJKjp-Bold.otf"),
            ),
        )
        SystemFontScript.KR -> FontFace(
            id = "sysfont-sourcehan-kr-bold",
            fileName = "SourceHanSansK-Bold.otf",
            exportName = "SourceHanSansK-Bold.otf",
            weight = 700, weightName = "Bold", weightLabel = "粗体",
            italic = false, optical = "", opticalLabel = "",
            variable = false, format = "otf", innerName = "",
            bytes = SystemFontScript.KR.expectedBytes,
            repo = "adobe-fonts/source-han-sans", ref = "release",
            path = "OTF/Korean/SourceHanSansK-Bold.otf",
            mirrorRepo = "", mirrorRef = "", mirrorPath = "",
            extraUrls = listOf(
                FontUrls.jsdelivr("adobe-fonts/source-han-sans", "release", "OTF/Korean/SourceHanSansK-Bold.otf"),
            ),
        )
        else -> error("只有 JP / KR 走远程下载：$script")
    }

    fun sha256Hex(file: File): String = sha256Hex(file.readBytes())

    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    /** 校验组合字体文件：大小 + 文件头（OTTO/\0\1\0\0/true）+ SHA-256（有期望值时）。 */
    fun verifyFontFile(file: File, script: SystemFontScript): String? {
        if (!file.exists()) return "${script.displayName}文件不存在"
        if (file.length() != script.expectedBytes) {
            return "${script.displayName}大小不对（${file.length()}，期望 ${script.expectedBytes}），可能没下完"
        }
        if (!isFontHeaderValid(file)) return "${script.displayName}文件头校验失败，可能已损坏"
        if (script.expectedSha256.isNotBlank() && sha256Hex(file) != script.expectedSha256) {
            return "${script.displayName}校验和不对，可能已损坏或被替换"
        }
        return null
    }

    /** 字体文件头校验（OTTO / \0\1\0\0 / true），与 FontStore.looksValid 同口径，避免纯逻辑文件依赖 Android 类。 */
    fun isFontHeaderValid(file: File): Boolean {
        if (!file.exists() || file.length() < 12) return false
        val head = ByteArray(12)
        file.inputStream().use { if (it.read(head) < 12) return false }
        val otto = head[0] == 'O'.code.toByte() && head[1] == 'T'.code.toByte() && head[2] == 'T'.code.toByte() && head[3] == 'O'.code.toByte()
        val trueType = head[0] == 0.toByte() && head[1] == 1.toByte() && head[2] == 0.toByte() && head[3] == 0.toByte()
        val trueTag = head[0] == 't'.code.toByte() && head[1] == 'r'.code.toByte() && head[2] == 'u'.code.toByte() && head[3] == 'e'.code.toByte()
        return otto || trueType || trueTag
    }

    /** 解析 fonts.xml 是否合法（生成后必须调一次，失败则拒绝安装）。 */
    fun isValidFontsXml(xml: String): Boolean = runCatching { parseFontsXml(xml); true }.getOrDefault(false)

    /**
     * 重写 fonts.xml：只替换 sans-serif 系列 family 里 weight=700 的 <font> 条目，
     * 按所选语种顺序填入组合字体文件名；700 条目不足时追加，多的 700 条目保留原样；
     * 其他 family、别名、其他字重条目原样保留（节点内容不变，DOM 序列化会统一格式）。
     */
    fun rewriteFontsXml(originalXml: String, selected: List<SystemFontScript>): String {
        require(selected.isNotEmpty()) { "至少选一个语种" }
        val doc = parseFontsXml(originalXml)
        val families = doc.getElementsByTagName("family")
        var replaced = false
        for (i in 0 until families.length) {
            val family = families.item(i) as? Element ?: continue
            if (family.getAttribute("name") != "sans-serif") continue
            val fonts = directFontChildren(family)
            val bold = fonts.filter { (it.getAttribute("weight").toIntOrNull() ?: 400) == 700 }
            selected.forEachIndexed { index, script ->
                if (index < bold.size) {
                    setFontText(bold[index], script.moduleFileName)
                } else {
                    val added = doc.createElement("font")
                    added.setAttribute("weight", "700")
                    added.setAttribute("style", "normal")
                    added.textContent = script.moduleFileName
                    family.appendChild(added)
                }
            }
            replaced = true
        }
        require(replaced) { "原 fonts.xml 里没有 sans-serif family" }
        return serialize(doc)
    }

    /** 读出 sans-serif family 里各 <font> 条目的 (weight, 文件名)，供测试与界面展示。 */
    fun sansSerifFonts(xml: String): List<Pair<Int, String>> {
        val doc = parseFontsXml(xml)
        val families = doc.getElementsByTagName("family")
        for (i in 0 until families.length) {
            val family = families.item(i) as? Element ?: continue
            if (family.getAttribute("name") != "sans-serif") continue
            return directFontChildren(family).map { (it.getAttribute("weight").toIntOrNull() ?: 400) to it.textContent.trim() }
        }
        return emptyList()
    }

    /**
     * 本次安装要改的每一条：原系统字体文件 → 组合字体文件。
     * [oldFile] 为 null 表示原 family 里 700 条目不够、要追加的新条目。
     * 安装前确认弹窗就按这个清单原样展示，不做任何静默改动。
     */
    data class FontChange(val oldFile: String?, val newFile: String, val script: SystemFontScript) {
        val added: Boolean get() = oldFile == null
    }

    fun computeChangeList(originalXml: String, selected: List<SystemFontScript>): List<FontChange> {
        require(selected.isNotEmpty()) { "至少选一个语种" }
        val model = parseFontsXmlModel(originalXml)
        val sans = model.families.firstOrNull { it.name == "sans-serif" }
            ?: error("原 fonts.xml 里没有 sans-serif family")
        val bold = sans.fonts.filter { it.weight == 700 }
        return selected.mapIndexed { index, script ->
            if (index < bold.size) FontChange(oldFile = bold[index].text, newFile = script.moduleFileName, script = script)
            else FontChange(oldFile = null, newFile = script.moduleFileName, script = script)
        }
    }

    /** fonts.xml 的结构快照：family 名顺序 + 每个 family 的 <font> 条目 + alias 节点（只比属性）。 */
    data class FontsXmlModel(
        val families: List<FamilyModel>,
        val aliases: List<Map<String, String>>,
    ) {
        data class FamilyModel(val name: String, val fonts: List<FontEntry>)
        data class FontEntry(val weight: Int, val style: String, val text: String)
    }

    fun parseFontsXmlModel(xml: String): FontsXmlModel {
        val doc = parseFontsXml(xml)
        val families = mutableListOf<FontsXmlModel.FamilyModel>()
        val aliases = mutableListOf<Map<String, String>>()
        val root = doc.documentElement
        val children = root.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node.nodeType != Node.ELEMENT_NODE) continue
            val element = node as Element
            when (element.nodeName) {
                "family" -> {
                    val fonts = directFontChildren(element).map {
                        FontsXmlModel.FontEntry(
                            weight = it.getAttribute("weight").toIntOrNull() ?: 400,
                            style = it.getAttribute("style"),
                            text = it.textContent.trim(),
                        )
                    }
                    families.add(FontsXmlModel.FamilyModel(name = element.getAttribute("name"), fonts = fonts))
                }
                "alias" -> {
                    val attrs = element.attributes
                    val map = mutableMapOf<String, String>()
                    for (j in 0 until attrs.length) {
                        val attr = attrs.item(j)
                        map[attr.nodeName] = attr.nodeValue
                    }
                    aliases.add(map)
                }
            }
        }
        return FontsXmlModel(families = families, aliases = aliases)
    }

    /**
     * 结构校验：断言新旧 fonts.xml 之间**只有**预期的改动。
     * 逐项检查（任一项不过就返回一条中文问题，空列表 = 通过）：
     * - family 数量与名字顺序不变；alias 节点原样不变；
     * - 非 sans-serif 的 family 里每条 <font> 逐字节（weight/style/文件名）不变；
     * - sans-serif 里：非 700 条目不变；前 N 个 700 条目只换文件名（style 不变），
     *   换成所选语种的组合文件名；超出原 700 条目数的部分只允许追加 700 条目，
     *   且追加的必须是所选语种的组合文件；条目总数 = 原总数 + 追加数；
     * - 每个新文件名都必须在 [stagingFileNames] 里（即模块 staging 中确实存在该文件）；
     * - 所选每个语种的组合文件都必须出现在新 sans-serif 的 700 条目里。
     */
    fun validateRewrite(
        originalXml: String,
        rewrittenXml: String,
        selected: List<SystemFontScript>,
        stagingFileNames: Set<String>,
    ): List<String> {
        val problems = mutableListOf<String>()
        val oldModel: FontsXmlModel
        val newModel: FontsXmlModel
        try {
            oldModel = parseFontsXmlModel(originalXml)
            newModel = parseFontsXmlModel(rewrittenXml)
        } catch (error: Throwable) {
            return listOf("fonts.xml 解析失败：${error.message ?: "未知错误"}")
        }
        if (oldModel.families.size != newModel.families.size) {
            return listOf("family 数量变了（${oldModel.families.size} → ${newModel.families.size}），已拒绝")
        }
        oldModel.families.zip(newModel.families).forEachIndexed { index, (oldFamily, newFamily) ->
            if (oldFamily.name != newFamily.name) {
                problems.add("第 ${index + 1} 个 family 的名字变了（${oldFamily.name} → ${newFamily.name}），已拒绝")
            }
        }
        if (problems.isNotEmpty()) return problems
        if (oldModel.aliases != newModel.aliases) {
            problems.add("alias 节点被改动了，已拒绝")
        }
        val oldSans = oldModel.families.firstOrNull { it.name == "sans-serif" }
        val newSans = newModel.families.firstOrNull { it.name == "sans-serif" }
        if (oldSans == null || newSans == null) {
            problems.add("sans-serif family 找不到了，已拒绝")
            return problems
        }
        oldModel.families.zip(newModel.families).forEach { (oldFamily, newFamily) ->
            if (oldFamily.name == "sans-serif") return@forEach
            if (oldFamily.fonts != newFamily.fonts) {
                problems.add("family「${oldFamily.name}」里的字体条目被改动了，已拒绝")
            }
        }
        val oldFonts = oldSans.fonts
        val newFonts = newSans.fonts
        val oldBoldIndices = oldFonts.indices.filter { oldFonts[it].weight == 700 }
        val replacedCount = minOf(oldBoldIndices.size, selected.size)
        val appendedCount = selected.size - replacedCount
        if (newFonts.size != oldFonts.size + appendedCount) {
            problems.add("sans-serif 里的字体条目总数不对（${oldFonts.size} → ${newFonts.size}，预期追加 $appendedCount 条），已拒绝")
            return problems
        }
        for (i in oldFonts.indices) {
            val old = oldFonts[i]
            val new = newFonts[i]
            val boldPos = oldBoldIndices.indexOf(i)
            if (old.weight == 700 && boldPos in 0 until replacedCount) {
                val script = selected[boldPos]
                if (new.weight != 700 || new.text != script.moduleFileName) {
                    problems.add("sans-serif 第 ${i + 1} 条 700 字体被改成了预期之外的 ${new.text}，已拒绝")
                } else if (new.style != old.style) {
                    problems.add("sans-serif 第 ${i + 1} 条字体的 style 被改动了，已拒绝")
                } else if (new.text !in stagingFileNames) {
                    problems.add("新字体 ${new.text} 不在模块文件里，已拒绝")
                }
            } else if (new != old) {
                problems.add("sans-serif 第 ${i + 1} 条非粗体字体被改动了（${old.text} → ${new.text}），已拒绝")
            }
        }
        for (j in 0 until appendedCount) {
            val script = selected[replacedCount + j]
            val new = newFonts[oldFonts.size + j]
            if (new.weight != 700 || new.text != script.moduleFileName || new.style != "normal") {
                problems.add("追加的字体条目不是预期的 ${script.moduleFileName}，已拒绝")
            } else if (new.text !in stagingFileNames) {
                problems.add("新字体 ${new.text} 不在模块文件里，已拒绝")
            }
        }
        val newBoldTexts = newFonts.filter { it.weight == 700 }.map { it.text }
        for (script in selected) {
            if (script.moduleFileName !in newBoldTexts) {
                problems.add("缺少${script.displayName}的字体条目，已拒绝")
            }
        }
        return problems
    }

    /** 把 staging 目录打成 KSU 模块 zip（ksud module install 吃 zip 不吃目录）。 */
    fun buildModuleZip(stagingDir: File, zipFile: File) {
        require(stagingDir.isDirectory) { "模块目录不存在：${stagingDir.absolutePath}" }
        zipFile.parentFile?.mkdirs()
        val files = stagingDir.walkTopDown().filter { it.isFile }
            .sortedBy { it.relativeTo(stagingDir).invariantSeparatorsPath }
        ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zip ->
            for (file in files) {
                val entry = ZipEntry(file.relativeTo(stagingDir).invariantSeparatorsPath)
                entry.time = 0L
                zip.putNextEntry(entry)
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    /**
     * 下载所选语种里需联网拉取的部分（JP/KR）所需空间：
     * 文件本身 ×2（下载中的 .part 与落盘文件可能同时存在）+ 16MB 余量。
     */
    fun requiredDownloadBytes(selected: List<SystemFontScript>): Long {
        val remote = selected.filter { it == SystemFontScript.JP || it == SystemFontScript.KR }
            .sumOf { it.expectedBytes }
        return remote * 2 + 16L * 1024 * 1024
    }

    fun hasEnoughSpace(usableBytes: Long, neededBytes: Long): Boolean = usableBytes >= neededBytes

    fun buildModuleProp(versionName: String, versionCode: Int): String = buildString {
        appendLine("id=$MODULE_ID")
        appendLine("name=$MODULE_NAME")
        appendLine("version=$versionName")
        appendLine("versionCode=$versionCode")
        appendLine("author=TokiFonts")
        appendLine("description=Inter 组合字体：把系统粗体（700）替换为所选语种的 Inter 组合；只做 KSU magic mount，不写系统分区。删除本模块并重启 zygote 即可恢复原厂字体。")
    }.trimEnd() + "\n"

    /** 模块文件清单（相对 staging 根目录）：module.prop + fonts.xml + 各语种字体文件 + 原 fonts.xml 备份。 */
    fun moduleLayout(selected: List<SystemFontScript>): List<String> = buildList {
        add("module.prop")
        add("system/etc/fonts.xml")
        add("fonts.xml.original")
        selected.forEach { add("system/fonts/${it.moduleFileName}") }
    }

    private fun directFontChildren(family: Element): List<Element> {
        val out = mutableListOf<Element>()
        val children = family.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node.nodeType == Node.ELEMENT_NODE && node.nodeName == "font") out.add(node as Element)
        }
        return out
    }

    private fun setFontText(font: Element, fileName: String) {
        // 只换文本节点，不动 weight/style 等属性。
        while (font.hasChildNodes()) font.removeChild(font.firstChild)
        font.appendChild(font.ownerDocument.createTextNode(fileName))
    }

    private fun parseFontsXml(xml: String): Document {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = false
        // 外部实体一律禁掉：fonts.xml 来自设备，别让 XXE 有机可乘。
        runCatching { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        factory.isXIncludeAware = false
        factory.isExpandEntityReferences = false
        val builder = factory.newDocumentBuilder()
        return builder.parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))
    }

    private fun serialize(doc: Document): String {
        val transformer = TransformerFactory.newInstance().newTransformer()
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8")
        transformer.setOutputProperty(OutputKeys.INDENT, "no")
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no")
        val writer = StringWriter()
        transformer.transform(DOMSource(doc), StreamResult(writer))
        return writer.toString()
    }
}
