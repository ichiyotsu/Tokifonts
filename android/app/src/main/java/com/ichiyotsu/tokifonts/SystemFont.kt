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
import kotlin.math.abs
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * 换系统字体：纯逻辑（无 Android 依赖，可单测）。
 *
 * 锁定决策：
 * - 只做 KSU magic mount（bind 覆盖），绝不写 /system 或任何系统分区；
 *   回滚 = 删 /data/adb/modules/tokifontspro-font + 重启 zygote（或重启手机）。
 * - 组合字体 = 全字重覆盖，每语种按字重一个文件；“组合” = 字体文件 +
 *   设备上生成的 fonts.xml，不是合成一个 TTF。
 * - 生成规则：解析设备原 fonts.xml，把 sans-serif 系列 family 里每一条
 *   常规（normal）字重条目，按所选语种顺序换成对应字重的组合字体文件；
 *   系统默认字重 400 映射各语种的常规文件（不是粗体），粗体仍在 700，
 *   通过系统设置里的粗细切换可用；其他 family、别名、斜体条目原样保留；
 *   生成后必须重新解析验证，解析失败则拒绝安装。
 * - 最近字重回退：某语种没有设备字重对应的文件时，映射到最近的可用字重，
 *   距离相同取更粗的一档（见 [SystemFontScript.fileForWeight]）。
 *   斜体（italic）条目没有斜体替换文件，原样保留——这是已知缺口：
 *   斜体文字仍会走三星原厂字体，见 [SystemFont.rewriteFontsXml]。
 * - 可变字体说明：内置 res/font/inter.ttf 是 Inter 可变字体（wght 100–900），
 *   但 Android 15 起可变字体配置在 font_fallback.xml（supportedAxes），
 *   不在 fonts.xml 里；本模块只改写 fonts.xml，且三星 One UI 的
 *   fonts.xml 解析是否认 variationSettings 没有实测证据，所以拉丁文
 *   按静态字重文件做（Inter 4.1 官方包 extras/otf，各字重与内置
 *   Inter-Bold.otf 同一版本，字节与 SHA-256 已逐个核对）。
 */
data class ScriptWeightFile(
    /** 字重，如 400。 */
    val weight: Int,
    /** 模块内 / 设备 fonts.xml 里引用的文件名。 */
    val fileName: String,
    /** 期望字节数（下载/复制后校验）。 */
    val expectedBytes: Long,
    /** 期望 SHA-256（小写 hex；没有公开校验值时留空，只按字节数 + 文件头校验，与苹方旧做法一致）。 */
    val expectedSha256: String = "",
    /** 苹方：catalog.json 里的 face id（如 "2001"）；其他语种为 null。 */
    val catalogFaceId: String? = null,
)

enum class SystemFontScript(
    val displayName: String,
    /**
     * 700（粗体）档的文件名：旧的单文件入口（[SystemFont.moduleLayout]、
     * [SystemFont.remoteFace]、[SystemFont.verifyFontFile] 的默认档）仍以它为准，
     * 供尚未改到按字重取文件的调用方兼容；全字重逻辑一律走 [weightFiles]。
     */
    val moduleFileName: String,
    /** 700 档期望字节数（见 [moduleFileName]）。 */
    val expectedBytes: Long,
    /** 700 档期望 SHA-256（见 [moduleFileName]）。 */
    val expectedSha256: String,
    val sourceNote: String,
    val defaultEnabled: Boolean = true,
    /** 该语种全部可用字重的文件表（按字重升序）。 */
    val weightFiles: List<ScriptWeightFile>,
) {
    LATIN(
        displayName = "拉丁（Inter）",
        moduleFileName = "Inter-Bold.otf",
        expectedBytes = 631_880L,
        expectedSha256 = "9fc6261e817d0b1b2bf1953d8799bb1c837d11aeebbf83ae352e7b4d6de6425d",
        sourceNote = "Inter 4.1 官方包（extras/otf），9 档字重；700 档随应用内置，其余 8 档安装时从官方包下载",
        weightFiles = listOf(
            ScriptWeightFile(100, "Inter-Thin.otf", 580_244L, "cac20f7a0c98f45770b098f3eea4bbf5ca020f91602ae119342198dfca14107c"),
            ScriptWeightFile(200, "Inter-ExtraLight.otf", 593_100L, "09c80155d69174316313b9b81eabacea453aa76817f8fd4be9c3c263034b67f9"),
            ScriptWeightFile(300, "Inter-Light.otf", 612_020L, "0817fd071377740a8e1cba673baf2c726ef5e5c4e6e0962c6e5bdc096329586d"),
            ScriptWeightFile(400, "Inter-Regular.otf", 609_600L, "d4f2b9e148059a15f014cb0f0b8fea8cd11bfa447dd483bedf1b0adc0e2ba799"),
            ScriptWeightFile(500, "Inter-Medium.otf", 627_988L, "6c222ed2c3660524dd0c14fb745e00fee65c5361ab28f60fba6730bf4e648763"),
            ScriptWeightFile(600, "Inter-SemiBold.otf", 629_912L, "0a4d30778fa2dc239368d90fd854b88b27e7ede737450da72686a90eca66f02c"),
            ScriptWeightFile(700, "Inter-Bold.otf", 631_880L, "9fc6261e817d0b1b2bf1953d8799bb1c837d11aeebbf83ae352e7b4d6de6425d"),
            ScriptWeightFile(800, "Inter-ExtraBold.otf", 633_580L, "db2643a54b9c2492e99cd70f73702906d1677aedde969f63bffd4cbc3789ad6d"),
            ScriptWeightFile(900, "Inter-Black.otf", 626_660L, "da79284ab25b9630e62d967d84fe1c3a1766fa2d7eb39314849ceb3b0759c362"),
        ),
    ),
    SC(
        displayName = "简体中文（苹方）",
        moduleFileName = "2001.ttf",
        expectedBytes = 10_923_212L,
        expectedSha256 = "",
        sourceNote = "走现有 catalog + FontStore 下载，只用用户已下载的本地副本；6 档字重（200/300/400/500/700/800）",
        weightFiles = listOf(
            ScriptWeightFile(200, "2002 PingFang ExtraLight.ttf", 11_035_472L, catalogFaceId = "2002"),
            ScriptWeightFile(300, "2004 PingFang Light.ttf", 11_024_840L, catalogFaceId = "2004"),
            ScriptWeightFile(400, "2006 PingFang Regular.ttf", 11_004_656L, catalogFaceId = "2006"),
            ScriptWeightFile(500, "2005 PingFang Medium.ttf", 10_968_356L, catalogFaceId = "2005"),
            ScriptWeightFile(700, "2001 PingFang Bold.ttf", 10_923_212L, catalogFaceId = "2001"),
            ScriptWeightFile(800, "2003 PingFang Heavy.ttf", 10_898_528L, catalogFaceId = "2003"),
        ),
    ),
    TC(
        displayName = "繁体中文（苹方）",
        moduleFileName = "2010.ttf",
        expectedBytes = 12_590_380L,
        expectedSha256 = "",
        sourceNote = "走现有 catalog + FontStore 下载，只用用户已下载的本地副本；6 档字重（100/200/300/400/500/600，库里最粗只有 Semibold 600，没有 HK Bold）",
        weightFiles = listOf(
            ScriptWeightFile(100, "2011 PingFangHK-Thin.ttf", 12_550_652L, catalogFaceId = "2011"),
            ScriptWeightFile(200, "2012 PingFangHK-Ultralight.ttf", 12_577_220L, catalogFaceId = "2012"),
            ScriptWeightFile(300, "2007 PingFangHK-Light.ttf", 12_552_136L, catalogFaceId = "2007"),
            ScriptWeightFile(400, "2009 PingFangHK-Regular.ttf", 12_545_048L, catalogFaceId = "2009"),
            ScriptWeightFile(500, "2008 PingFangHK-Medium.ttf", 12_575_228L, catalogFaceId = "2008"),
            ScriptWeightFile(600, "2010 PingFangHK-Semibold.ttf", 12_590_380L, catalogFaceId = "2010"),
        ),
    ),
    JP(
        displayName = "日文（Noto Sans JP）",
        moduleFileName = "NotoSansCJKjp-Bold.otf",
        expectedBytes = 17_032_620L,
        expectedSha256 = "e53dcb0dcb2922e45d01aae1ebd2f382bb81d4229b18b6b883bd170678af1f76",
        sourceNote = "安装时按需从官方 raw URL 下载（notofonts/noto-cjk），不打包进 APK；7 档字重（100/300/350/400/500/700/900）",
        weightFiles = listOf(
            ScriptWeightFile(100, "NotoSansCJKjp-Thin.otf", 14_927_060L),
            ScriptWeightFile(300, "NotoSansCJKjp-Light.otf", 16_360_020L),
            ScriptWeightFile(350, "NotoSansCJKjp-DemiLight.otf", 16_445_132L),
            ScriptWeightFile(400, "NotoSansCJKjp-Regular.otf", 16_467_736L, "68a3fc98800b2a27b371f2fb79991daf3633bd89309d4ffaa6946fd587f375b5"),
            ScriptWeightFile(500, "NotoSansCJKjp-Medium.otf", 16_554_004L),
            ScriptWeightFile(700, "NotoSansCJKjp-Bold.otf", 17_032_620L, "e53dcb0dcb2922e45d01aae1ebd2f382bb81d4229b18b6b883bd170678af1f76"),
            ScriptWeightFile(900, "NotoSansCJKjp-Black.otf", 17_828_280L),
        ),
    ),
    KR(
        displayName = "韩文（Source Han Sans KR）",
        moduleFileName = "SourceHanSansK-Bold.otf",
        expectedBytes = 16_959_020L,
        expectedSha256 = "b15ca0f485b4a055f2fb7b4055a11cbe4cddd18a73a380f49aeb01b11dc58201",
        sourceNote = "安装时按需从官方 raw URL 下载（adobe-fonts/source-han-sans），不打包进 APK；7 档字重（200/300/350/400/500/700/800，注意 Normal 是 350 不是 400）",
        weightFiles = listOf(
            ScriptWeightFile(200, "SourceHanSansK-ExtraLight.otf", 14_889_236L),
            ScriptWeightFile(300, "SourceHanSansK-Light.otf", 16_455_704L),
            ScriptWeightFile(350, "SourceHanSansK-Normal.otf", 16_520_984L),
            ScriptWeightFile(400, "SourceHanSansK-Regular.otf", 16_525_428L),
            ScriptWeightFile(500, "SourceHanSansK-Medium.otf", 16_541_920L),
            ScriptWeightFile(700, "SourceHanSansK-Bold.otf", 16_959_020L, "b15ca0f485b4a055f2fb7b4055a11cbe4cddd18a73a380f49aeb01b11dc58201"),
            ScriptWeightFile(800, "SourceHanSansK-Heavy.otf", 17_610_080L),
        ),
    );

    /**
     * 设备字重 → 该语种实际用的文件：精确档直接取，没有精确档就取最近的可用字重，
     * 距离相同取更粗的一档（例如 HK 700 → 600；SC 600 → 700；JP 600 → 700、800 → 900；
     * KR 600 → 700、900 → 800；SC 100 → 200、HK 700/800/900 → 600）。
     * 规则写死在这里，改动它必须同步改单测。
     */
    fun fileForWeight(weight: Int): ScriptWeightFile =
        weightFiles.minWith(
            compareBy<ScriptWeightFile> { abs(it.weight - weight) }.thenByDescending { it.weight },
        )

    /** 该语种全部字重的文件名（模块 staging 清单用）。 */
    fun allFileNames(): Set<String> = weightFiles.map { it.fileName }.toSet()
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
    const val MODULE_ID = "tokifontspro-font"
    /** 组合字体包的名字就叫 Inter；旧 id tokifonts-font 是 Main 轨（TokiFonts）的模块，Pro 轨改用 tokifontspro-font，两者不得混用。 */
    const val MODULE_NAME = "Inter"
    const val MODULE_DIR = "/data/adb/modules/tokifontspro-font"

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
                    // 候选来源行的状态决定文案：已实测行才写“已实测载荷”，
                    // 待验证（已定标）行（如 DZJ3）必须写“待验证（已定标）载荷”，绝不能写“已实测”。
                    val sourceText = if (crossModel.entry.status == FirmwareStatus.TESTED) {
                        "已实测载荷"
                    } else {
                        "待验证（已定标）载荷"
                    }
                    return DeviceCheck(
                        manufacturer, model, kernelVersion, DeviceSupport.CANDIDATE,
                        "该固件（$current）未在 S24+ 支持列表里，但 S24 Ultra 有同构建号 ${crossModel.suffix} 的$sourceText（${crossModel.entry.firmwareBuild}），你以前用这条路子通过过；需点确认才尝试，不默认走",
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

    /**
     * 从 os.version 取三段式内核版本（如 "6.1.145-android14-11-00001" → "6.1.145"）；
     * Root-My-Galaxy 的匹配规则要求三段精确比对，取不到返回 null。
     */
    fun parseKernelFullVersion(kernelVersion: String): String? {
        val match = Regex("""(\d+)\.(\d+)\.(\d+)""").find(kernelVersion.trim()) ?: return null
        return "${match.groupValues[1]}.${match.groupValues[2]}.${match.groupValues[3]}"
    }

    /**
     * 拉丁文各字重的来源：Inter 4.1 官方 release 包（33.7MB 的 zip，内含
     * extras/otf/ 下 9 档静态 OTF，与内置 Inter-Bold.otf 同一版本、700 档
     * 字节与 SHA-256 完全一致）。raw.githubusercontent.com 不托管 release
     * 附件，所以主地址（raw）注定 404，[FontStore.download] 会按顺序 fallback
     * 到 [extraUrls] 里的 release 直链；jsDelivr 没有 release 附件镜像。
     * 注意：9 个档共用同一个 zip，调用方只下载一次、解出全部 9 个文件，
     * 不要按档逐个调 store.download（否则会下 9 次 zip）。
     */
    const val LATIN_ZIP_REF = "v4.1"
    const val LATIN_ZIP_FILE = "Inter-4.1.zip"
    const val LATIN_ZIP_BYTES = 33_707_794L
    const val LATIN_ZIP_URL = "https://github.com/rsms/inter/releases/download/v4.1/Inter-4.1.zip"
    const val LATIN_ZIP_INNER_DIR = "extras/otf/"

    /** Noto / Source Han 按需下载用的 FontFace（复用 FontStore.download，走 raw.githubusercontent.com）。 */
    fun remoteFace(script: SystemFontScript): FontFace = remoteFace(script, 700)

    /**
     * 按字重的远程 FontFace：JP/KR 取对应字重的文件；LATIN 取 Inter 4.1
     * release 包里的对应档（format zip，innerName 指向包内路径）；
     * 苹方（SC/TC）走 catalog，不走这里。
     */
    fun remoteFace(script: SystemFontScript, weight: Int): FontFace {
        val file = script.fileForWeight(weight)
        return when (script) {
            SystemFontScript.LATIN -> FontFace(
                id = "sysfont-inter-${file.weight}",
                fileName = file.fileName,
                exportName = file.fileName,
                weight = file.weight, weightName = latinWeightName(file.weight),
                weightLabel = weightLabel(file.weight),
                italic = false, optical = "", opticalLabel = "",
                variable = false, format = "zip", innerName = LATIN_ZIP_INNER_DIR + file.fileName,
                bytes = LATIN_ZIP_BYTES,
                repo = "rsms/inter", ref = LATIN_ZIP_REF,
                path = LATIN_ZIP_FILE,
                mirrorRepo = "", mirrorRef = "", mirrorPath = "",
                // raw.githubusercontent.com 不托管 release 附件：主地址注定 404，
                // FontStore.download 会 fallback 到这条 release 直链。
                extraUrls = listOf(LATIN_ZIP_URL),
            )
            SystemFontScript.JP -> FontFace(
                id = "sysfont-noto-jp-${file.weight}",
                fileName = file.fileName,
                exportName = file.fileName,
                weight = file.weight, weightName = jpWeightName(file.weight),
                weightLabel = weightLabel(file.weight),
                italic = false, optical = "", opticalLabel = "",
                variable = false, format = "otf", innerName = "",
                bytes = file.expectedBytes,
                repo = "notofonts/noto-cjk", ref = "main",
                path = "Sans/OTF/Japanese/${file.fileName}",
                mirrorRepo = "", mirrorRef = "", mirrorPath = "",
                // 备用：jsDelivr 的 GitHub 镜像（raw.githubusercontent.com 曾返回 503）。
                extraUrls = listOf(
                    FontUrls.jsdelivr("notofonts/noto-cjk", "main", "Sans/OTF/Japanese/${file.fileName}"),
                ),
            )
            SystemFontScript.KR -> FontFace(
                id = "sysfont-sourcehan-kr-${file.weight}",
                fileName = file.fileName,
                exportName = file.fileName,
                weight = file.weight, weightName = krWeightName(file.weight),
                weightLabel = weightLabel(file.weight),
                italic = false, optical = "", opticalLabel = "",
                variable = false, format = "otf", innerName = "",
                bytes = file.expectedBytes,
                repo = "adobe-fonts/source-han-sans", ref = "release",
                path = "OTF/Korean/${file.fileName}",
                mirrorRepo = "", mirrorRef = "", mirrorPath = "",
                extraUrls = listOf(
                    FontUrls.jsdelivr("adobe-fonts/source-han-sans", "release", "OTF/Korean/${file.fileName}"),
                ),
            )
            SystemFontScript.SC, SystemFontScript.TC ->
                error("苹方走 catalog + FontStore，不走远程下载：$script")
        }
    }

    /** 该语种全部字重的远程 FontFace（JP/KR 各档 + LATIN 的 zip 各档；苹方为空，走 catalog）。 */
    fun remoteFaces(script: SystemFontScript): List<FontFace> = when (script) {
        SystemFontScript.SC, SystemFontScript.TC -> emptyList()
        else -> script.weightFiles.map { remoteFace(script, it.weight) }
    }

    private fun latinWeightName(weight: Int): String = when (weight) {
        100 -> "Thin"; 200 -> "ExtraLight"; 300 -> "Light"; 400 -> "Regular"
        500 -> "Medium"; 600 -> "SemiBold"; 700 -> "Bold"; 800 -> "ExtraBold"
        900 -> "Black"; else -> "Regular"
    }

    private fun jpWeightName(weight: Int): String = when (weight) {
        100 -> "Thin"; 300 -> "Light"; 350 -> "DemiLight"; 400 -> "Regular"
        500 -> "Medium"; 700 -> "Bold"; 900 -> "Black"; else -> "Regular"
    }

    private fun krWeightName(weight: Int): String = when (weight) {
        200 -> "ExtraLight"; 300 -> "Light"; 350 -> "Normal"; 400 -> "Regular"
        500 -> "Medium"; 700 -> "Bold"; 800 -> "Heavy"; else -> "Regular"
    }

    private fun weightLabel(weight: Int): String = when (weight) {
        100 -> "极细"; 200 -> "特细"; 300 -> "细体"; 350 -> "偏细"; 400 -> "常规"
        500 -> "中等"; 600 -> "半粗"; 700 -> "粗体"; 800 -> "特粗"; 900 -> "极粗"
        else -> "常规"
    }

    fun sha256Hex(file: File): String = sha256Hex(file.readBytes())

    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    /** 校验组合字体文件：大小 + 文件头（OTTO/\0\1\0\0/true）+ SHA-256（有期望值时）。默认 700 档。 */
    fun verifyFontFile(file: File, script: SystemFontScript): String? =
        verifyFontFile(file, script, 700)

    /** 按字重校验组合字体文件（大小与 SHA-256 取该语种对应字重档的期望值）。 */
    fun verifyFontFile(file: File, script: SystemFontScript, weight: Int): String? {
        val expected = script.fileForWeight(weight)
        if (!file.exists()) return "${script.displayName}${expected.weight} 文件不存在"
        if (file.length() != expected.expectedBytes) {
            return "${script.displayName}${expected.weight} 大小不对（${file.length()}，期望 ${expected.expectedBytes}），可能没下完"
        }
        if (!isFontHeaderValid(file)) return "${script.displayName}${expected.weight} 文件头校验失败，可能已损坏"
        if (expected.expectedSha256.isNotBlank() && sha256Hex(file) != expected.expectedSha256) {
            return "${script.displayName}${expected.weight} 校验和不对，可能已损坏或被替换"
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

    /** 该 family 里常规（normal）字重条目的 style 判定：没写 style 视同 normal。 */
    fun isNormalStyle(style: String): Boolean = style.isBlank() || style == "normal"

    /**
     * 重写 fonts.xml：把 sans-serif 系列 family 里每一条常规（normal）字重的
     * <font> 条目，按所选语种顺序换成对应字重的组合字体文件——系统默认 400
     * 映射各语种的常规文件（不是粗体），700 仍映射粗体文件。
     * 同一字重的多个条目按位置分给各语种（第 1 条 → 第 1 个语种，以此类推）；
     * 某字重的条目不够分时，在该字重的最后一条后追加（不堆到 family 末尾，
     * 这样同字重的条目在文件里仍排在一起，预览可读）；
     * 某字重的条目比语种多时，多出来的条目原样保留（三星原 fonts.xml 里
     * sans-serif 每字重只有一个条目，正常走不到这条；真遇到了也不静默删）；
     * 斜体（italic）等非常规条目没有斜体替换文件，原样保留——斜体文字仍会
     * 走三星原厂字体，这是已知缺口，见文件头注释；
     * 其他 family、别名原样保留（节点内容不变，DOM 序列化会统一格式）。
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
            // 按字重分组（保持原文件里字重第一次出现的顺序）。
            val weights = fonts.filter { isNormalStyle(it.getAttribute("style")) }
                .map { it.getAttribute("weight").toIntOrNull() ?: 400 }.distinct()
            val slotsByWeight = weights.associateWith { weight ->
                fonts.filter { isNormalStyle(it.getAttribute("style")) && (it.getAttribute("weight").toIntOrNull() ?: 400) == weight }
            }
            for (weight in weights) {
                val slots = slotsByWeight.getValue(weight)
                var anchor: Node = slots.last()
                selected.forEachIndexed { index, script ->
                    val fileName = script.fileForWeight(weight).fileName
                    if (index < slots.size) {
                        setFontText(slots[index], fileName)
                    } else {
                        val added = doc.createElement("font")
                        added.setAttribute("weight", weight.toString())
                        added.setAttribute("style", "normal")
                        added.textContent = fileName
                        // 追加在该字重的最后一条之后（其后可能有空白文本节点，
                        // insertBefore(anchor.nextSibling) 仍保证排在该字重组内）。
                        family.insertBefore(added, anchor.nextSibling)
                        anchor = added
                    }
                }
            }
            // family 里没有任何常规条目（理论上不会发生）：按 400 追加一套，
            // 不让安装走到“一个字重都没换”的状态。
            if (weights.isEmpty()) {
                selected.forEach { script ->
                    val added = doc.createElement("font")
                    added.setAttribute("weight", "400")
                    added.setAttribute("style", "normal")
                    added.textContent = script.fileForWeight(400).fileName
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

    /** 读出 sans-serif family 里常规（normal）条目的全部字重（第一次出现的顺序，去重）。 */
    fun sansSerifWeights(xml: String): List<Int> {
        val model = parseFontsXmlModel(xml)
        val sans = model.families.firstOrNull { it.name == "sans-serif" } ?: return emptyList()
        return sans.fonts.filter { isNormalStyle(it.style) }.map { it.weight }.distinct()
    }

    /**
     * 本次安装要改的每一条：原系统字体文件 → 组合字体文件。
     * [oldFile] 为 null 表示原 family 里该字重条目不够、要追加的新条目。
     * [weight] 是这条的字重；清单按字重分组（见 [groupChangesByWeight]），
     * 安装前确认弹窗就按这个清单原样展示，不做任何静默改动。
     */
    data class FontChange(val oldFile: String?, val newFile: String, val script: SystemFontScript, val weight: Int) {
        val added: Boolean get() = oldFile == null
    }

    /** 按字重分组的改动清单（安装前确认弹窗按这个分组展示，保持可读）。 */
    data class WeightGroup(val weight: Int, val changes: List<FontChange>)

    fun groupChangesByWeight(changes: List<FontChange>): List<WeightGroup> =
        changes.groupBy { it.weight }.entries
            .sortedBy { it.key }
            .map { (weight, group) -> WeightGroup(weight, group) }

    fun computeChangeList(originalXml: String, selected: List<SystemFontScript>): List<FontChange> {
        require(selected.isNotEmpty()) { "至少选一个语种" }
        val model = parseFontsXmlModel(originalXml)
        val sans = model.families.firstOrNull { it.name == "sans-serif" }
            ?: error("原 fonts.xml 里没有 sans-serif family")
        // 与 rewriteFontsXml 同一套规则：每字重按位置分条目，不够追加；
        // 多出来的同字重条目与斜体条目原样保留，不进清单。
        val weights = sans.fonts.filter { isNormalStyle(it.style) }.map { it.weight }.distinct()
        if (weights.isEmpty()) {
            return selected.map { script ->
                FontChange(oldFile = null, newFile = script.fileForWeight(400).fileName, script = script, weight = 400)
            }
        }
        return buildList {
            for (weight in weights) {
                val slots = sans.fonts.filter { isNormalStyle(it.style) && it.weight == weight }
                selected.forEachIndexed { index, script ->
                    if (index < slots.size) {
                        add(FontChange(oldFile = slots[index].text, newFile = script.fileForWeight(weight).fileName, script = script, weight = weight))
                    } else {
                        add(FontChange(oldFile = null, newFile = script.fileForWeight(weight).fileName, script = script, weight = weight))
                    }
                }
            }
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
     * - sans-serif 里：按 [rewriteFontsXml] 的同一套规则重建预期条目表，
     *   逐条比对（weight/style/文件名全等）——常规字重的每一条都必须换成
     *   所选语种对应字重的文件，不许留任何一条指向三星原厂文件；
     *   斜体等非常规条目与多出来的同字重条目必须原样不动；
     * - 每个新文件名都必须在 [stagingFileNames] 里（即模块 staging 中确实存在该文件）；
     * - 所选每个语种、每个设备字重的组合文件都必须出现在新 sans-serif 里。
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
        // 按 rewriteFontsXml 的同一套规则，从旧表重建新 sans-serif 应有的条目表，
        // 再逐条比对：任何一条对不上（包括漏换的三星原厂文件）都拒绝。
        val expectedSans = expectedSansFonts(oldSans.fonts, selected)
        val newFonts = newSans.fonts
        if (newFonts.size != expectedSans.size) {
            problems.add("sans-serif 里的字体条目总数不对（${oldSans.fonts.size} → ${newFonts.size}，预期 ${expectedSans.size} 条），已拒绝")
            return problems
        }
        for (i in expectedSans.indices) {
            val expected = expectedSans[i]
            val actual = newFonts[i]
            if (actual != expected) {
                problems.add("sans-serif 第 ${i + 1} 条字体不是预期的 ${expected.text}（${expected.weight}），而是 ${actual.text}，已拒绝")
            } else if (actual.text !in oldSans.fonts.map { it.text } && actual.text !in stagingFileNames) {
                // 新换上的文件必须在 staging 里；原样保留的条目（斜体、多余同字重条目）除外。
                problems.add("新字体 ${actual.text} 不在模块文件里，已拒绝")
            }
        }
        if (problems.isNotEmpty()) return problems
        val newTexts = newFonts.map { it.text }
        for (script in selected) {
            for (weight in sansSerifWeights(originalXml)) {
                val file = script.fileForWeight(weight).fileName
                if (file !in newTexts) {
                    problems.add("缺少${script.displayName}${weight} 的字体条目（$file），已拒绝")
                }
            }
        }
        return problems
    }

    /**
     * 按 rewriteFontsXml 的规则，从旧 sans-serif 条目表算出新表应有的样子：
     * 每字重的常规条目按位置换成各语种对应字重的文件，不够追加；
     * 斜体等非常规条目与多出来的同字重条目原样保留。
     */
    private fun expectedSansFonts(
        oldFonts: List<FontsXmlModel.FontEntry>,
        selected: List<SystemFontScript>,
    ): List<FontsXmlModel.FontEntry> {
        val weights = oldFonts.filter { isNormalStyle(it.style) }.map { it.weight }.distinct()
        if (weights.isEmpty()) {
            return oldFonts + selected.map { script ->
                FontsXmlModel.FontEntry(weight = 400, style = "normal", text = script.fileForWeight(400).fileName)
            }
        }
        val slotsByWeight = weights.associateWith { weight ->
            oldFonts.filter { isNormalStyle(it.style) && it.weight == weight }
        }
        val out = mutableListOf<FontsXmlModel.FontEntry>()
        // 记录每个字重已处理到第几条原条目：追加项插在该字重的最后一条原条目之后。
        val consumed = weights.associateWith { 0 }.toMutableMap()
        for (old in oldFonts) {
            if (!isNormalStyle(old.style)) {
                out.add(old)
                continue
            }
            val slots = slotsByWeight.getValue(old.weight)
            val pos = consumed.getValue(old.weight)
            consumed[old.weight] = pos + 1
            if (pos < selected.size) {
                val script = selected[pos]
                out.add(
                    FontsXmlModel.FontEntry(
                        weight = old.weight,
                        // style 原样保留（没写 style 的条目读出来是 ""，重写也不动它；
                        // 这里绝不能归一化成 "normal"，否则会和实际重写结果对不上）。
                        style = old.style,
                        text = script.fileForWeight(old.weight).fileName,
                    ),
                )
            } else {
                out.add(old)
            }
            if (pos == slots.size - 1) {
                // 该字重的最后一条原条目之后，追加分不到条目的语种。
                for (index in slots.size until selected.size) {
                    val script = selected[index]
                    out.add(
                        FontsXmlModel.FontEntry(
                            weight = old.weight,
                            style = "normal",
                            text = script.fileForWeight(old.weight).fileName,
                        ),
                    )
                }
            }
        }
        return out
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
     * 下载所选语种全部字重所需空间（最坏情况：设备上所有字重都要下）：
     * - JP/KR：各字重文件本身 ×2（下载中的 .part 与落盘文件可能同时存在）；
     * - LATIN：Inter 4.1 zip（33.7MB）×2（.part + 解压产物），9 档共用一个 zip；
     * - 苹方（SC/TC）：只用用户已下载的本地副本，不在这里计下载量；
     * 再加 16MB 余量。
     * 调用方拿到设备 fonts.xml 后，应改用 [requiredDownloadBytes] 的按字重版，
     * 只按设备上实际有的字重算（通常比全量小得多），界面显示也用那个数。
     */
    fun requiredDownloadBytes(selected: List<SystemFontScript>): Long =
        requiredDownloadBytes(selected, ALL_WEIGHTS)

    /** 设备上实际有的字重集合（不知道时传 [ALL_WEIGHTS]，即按全量算）。 */
    val ALL_WEIGHTS: Set<Int> = setOf(100, 200, 300, 350, 400, 500, 600, 700, 800, 900)

    /**
     * 按设备实际字重算下载所需空间：JP/KR 只算 [weights] 映射到的去重文件
     * （两档设备字重映射到同一文件时只算一次）；LATIN 仍是整个 zip ×2；
     * 苹方不计；再加 16MB 余量。
     */
    fun requiredDownloadBytes(selected: List<SystemFontScript>, weights: Set<Int>): Long {
        var remote = 0L
        for (script in selected) {
            when (script) {
                SystemFontScript.LATIN -> remote += LATIN_ZIP_BYTES
                SystemFontScript.JP, SystemFontScript.KR -> {
                    val files = weights.map { script.fileForWeight(it) }.distinctBy { it.fileName }
                    remote += files.sumOf { it.expectedBytes }
                }
                SystemFontScript.SC, SystemFontScript.TC -> {}
            }
        }
        return remote * 2 + 16L * 1024 * 1024
    }

    fun hasEnoughSpace(usableBytes: Long, neededBytes: Long): Boolean = usableBytes >= neededBytes

    fun buildModuleProp(versionName: String, versionCode: Int): String = buildString {
        appendLine("id=$MODULE_ID")
        appendLine("name=$MODULE_NAME")
        appendLine("version=$versionName")
        appendLine("versionCode=$versionCode")
        appendLine("author=TokiFonts")
        appendLine("description=Inter 组合字体：把系统全部字重替换为所选语种的 Inter 组合（常规 400 用常规文件，粗体 700 用粗体文件）；只做 KSU magic mount，不写系统分区。删除本模块并重启 zygote 即可恢复原厂字体。")
    }.trimEnd() + "\n"

    /** 模块文件清单（相对 staging 根目录）：module.prop + fonts.xml + 各语种全部字重文件 + 原 fonts.xml 备份。 */
    fun moduleLayout(selected: List<SystemFontScript>): List<String> =
        moduleLayout(selected, ALL_WEIGHTS)

    /**
     * 按设备实际字重的模块文件清单：只列 [weights] 映射到的去重文件。
     * 调用方生成模块时按这个清单 staging 与复核；不知道设备字重时用无参版（全量）。
     */
    fun moduleLayout(selected: List<SystemFontScript>, weights: Set<Int>): List<String> = buildList {
        add("module.prop")
        add("system/etc/fonts.xml")
        add("fonts.xml.original")
        selected.forEach { script ->
            val files = if (weights == ALL_WEIGHTS) {
                script.weightFiles
            } else {
                weights.map { script.fileForWeight(it) }.distinctBy { it.fileName }
            }
            files.forEach { add("system/fonts/${it.fileName}") }
        }
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
