package com.ichiyotsu.tokifonts

import java.io.File
import java.nio.file.Files
import java.util.zip.ZipFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemFontTest {
    /** 一段仿真三星 fonts.xml：sans-serif 含 400/500/700 三档 + 别名 + CJK family。 */
    private val sampleXml = """
        <?xml version="1.0" encoding="utf-8"?>
        <familyset version="23">
            <family name="sans-serif">
                <font weight="400" style="normal">SamsungOneUI-Regular.ttf</font>
                <font weight="500" style="normal">SamsungOneUI-Medium.ttf</font>
                <font weight="700" style="normal">SamsungOneUI-Bold.ttf</font>
            </family>
            <alias name="sans-serif" to="sans-serif" weight="400" />
            <family name="sans-serif-cjk">
                <font weight="400" style="normal">NotoSansCJK-Regular.ttc</font>
                <font weight="700" style="normal">NotoSansCJK-Bold.ttc</font>
            </family>
            <family name="serif">
                <font weight="700" style="normal">Serif-Bold.ttf</font>
            </family>
        </familyset>
    """.trimIndent()

    @Test fun rewriteOnlyChangesSansSerif700Entries() {
        val rewritten = SystemFont.rewriteFontsXml(sampleXml, listOf(SystemFontScript.LATIN, SystemFontScript.SC))
        val fonts = SystemFont.sansSerifFonts(rewritten)
        // 只有一个 700 条目：第一个语种占它，第二个语种追加。
        assertEquals(
            listOf(400 to "SamsungOneUI-Regular.ttf", 500 to "SamsungOneUI-Medium.ttf", 700 to "Inter-Bold.otf", 700 to "2001.ttf"),
            fonts,
        )
    }

    @Test fun rewriteKeepsOtherFamiliesUntouched() {
        val rewritten = SystemFont.rewriteFontsXml(sampleXml, listOf(SystemFontScript.LATIN))
        assertTrue(rewritten.contains("NotoSansCJK-Bold.ttc"))
        assertTrue(rewritten.contains("Serif-Bold.ttf"))
        assertTrue(rewritten.contains("NotoSansCJK-Regular.ttc"))
        assertTrue(rewritten.contains("sans-serif-cjk"))
    }

    @Test fun rewriteKeepsNonBoldEntriesUntouched() {
        val rewritten = SystemFont.rewriteFontsXml(sampleXml, listOf(SystemFontScript.JP))
        assertTrue(rewritten.contains("SamsungOneUI-Regular.ttf"))
        assertTrue(rewritten.contains("SamsungOneUI-Medium.ttf"))
        assertTrue(rewritten.contains("sans-serif\" to=\"sans-serif\""))
    }

    @Test fun rewrittenXmlParsesAgain() {
        val rewritten = SystemFont.rewriteFontsXml(
            sampleXml,
            listOf(SystemFontScript.LATIN, SystemFontScript.SC, SystemFontScript.TC, SystemFontScript.JP, SystemFontScript.KR),
        )
        assertTrue(SystemFont.isValidFontsXml(rewritten))
        // 五档全开：原 1 个 700 条目被替换，其余 4 个追加。
        assertEquals(7, SystemFont.sansSerifFonts(rewritten).size)
        assertEquals(
            listOf("Inter-Bold.otf", "2001.ttf", "2010.ttf", "NotoSansCJKjp-Bold.otf", "SourceHanSansK-Bold.otf"),
            SystemFont.sansSerifFonts(rewritten).filter { it.first == 700 }.map { it.second },
        )
    }

    @Test fun invalidXmlIsRejected() {
        assertFalse(SystemFont.isValidFontsXml("<familyset><family>"))
        assertFalse(SystemFont.isValidFontsXml("not xml at all"))
    }

    @Test fun rewriteRejectsXmlWithoutSansSerif() {
        val xml = """<?xml version="1.0"?><familyset><family name="serif"><font weight="400">A.ttf</font></family></familyset>"""
        runCatching { SystemFont.rewriteFontsXml(xml, listOf(SystemFontScript.LATIN)) }
            .onSuccess { throw AssertionError("没有 sans-serif family 时应拒绝") }
    }

    @Test fun modulePropHasRequiredFields() {
        val prop = SystemFont.buildModuleProp("0.1.7", 8)
        assertTrue(prop.contains("id=tokifonts-font"))
        assertTrue(prop.contains("name=Inter"))
        assertTrue(prop.contains("version=0.1.7"))
        assertTrue(prop.contains("versionCode=8"))
        assertTrue(prop.contains("author=TokiFonts"))
        assertTrue(prop.contains("description="))
    }

    @Test fun moduleLayoutListsPropFontsXmlBackupAndFonts() {
        val layout = SystemFont.moduleLayout(listOf(SystemFontScript.LATIN, SystemFontScript.JP))
        assertEquals(
            listOf(
                "module.prop", "system/etc/fonts.xml", "fonts.xml.original",
                "system/fonts/Inter-Bold.otf", "system/fonts/NotoSansCJKjp-Bold.otf",
            ),
            layout,
        )
    }

    @Test fun supportedModelWith61KernelIsSupported() {
        val check = SystemFont.checkDevice("samsung", "SM-S9280", "6.1.118-android14-11-00001")
        assertEquals(DeviceSupport.SUPPORTED, check.support)
    }

    @Test fun foldAndFlipModelsAreSupported() {
        assertEquals(DeviceSupport.SUPPORTED, SystemFont.checkDevice("samsung", "SM-F9560", "6.1.75-android14").support)
        assertEquals(DeviceSupport.SUPPORTED, SystemFont.checkDevice("samsung", "SM-F741N", "6.1.75-android14").support)
        assertEquals(DeviceSupport.SUPPORTED, SystemFont.checkDevice("SAMSUNG", "sm-s921b", "6.1.118-android14").support)
    }

    @Test fun otherModelsAreUnsupported() {
        val check = SystemFont.checkDevice("samsung", "SM-A5560", "6.1.118-android14")
        assertEquals(DeviceSupport.UNSUPPORTED, check.support)
        assertTrue(check.reason.contains("SM-A5560"))
    }

    @Test fun nonSamsungIsUnsupported() {
        assertEquals(DeviceSupport.UNSUPPORTED, SystemFont.checkDevice("Google", "Pixel 8", "6.1.118-android14").support)
    }

    @Test fun unreadableKernelIsUnknownNotSupported() {
        val check = SystemFont.checkDevice("samsung", "SM-S9280", "")
        assertEquals(DeviceSupport.UNKNOWN, check.support)
        assertNotEquals(DeviceSupport.SUPPORTED, check.support)
    }

    @Test fun unknownKernelMajorIsUnknownNotSupported() {
        val check = SystemFont.checkDevice("samsung", "SM-S9280", "6.6.66-android15")
        assertEquals(DeviceSupport.UNKNOWN, check.support)
    }

    @Test fun allScriptsDefaultEnabled() {
        assertEquals(5, SystemFontScript.entries.size)
        assertTrue(SystemFontScript.entries.all { it.defaultEnabled })
    }

    @Test fun bundledAndRemoteShaConstantsArePresent() {
        assertTrue(SystemFontScript.LATIN.expectedSha256.isNotBlank())
        assertTrue(SystemFontScript.JP.expectedSha256.isNotBlank())
        assertTrue(SystemFontScript.KR.expectedSha256.isNotBlank())
        assertEquals(631_880L, SystemFontScript.LATIN.expectedBytes)
        assertEquals(17_032_620L, SystemFontScript.JP.expectedBytes)
        assertEquals(16_959_020L, SystemFontScript.KR.expectedBytes)
        assertEquals(10_923_212L, SystemFontScript.SC.expectedBytes)
        assertEquals(12_590_380L, SystemFontScript.TC.expectedBytes)
    }

    @Test fun remoteFacesPointAtOfficialRawUrls() {
        val jp = SystemFont.remoteFace(SystemFontScript.JP)
        assertEquals("notofonts/noto-cjk", jp.repo)
        assertEquals("main", jp.ref)
        assertTrue(jp.urls().first().contains("raw.githubusercontent.com/notofonts/noto-cjk/main/Sans/OTF/Japanese/NotoSansCJKjp-Bold.otf"))
        val kr = SystemFont.remoteFace(SystemFontScript.KR)
        assertEquals("adobe-fonts/source-han-sans", kr.repo)
        assertEquals("release", kr.ref)
        assertTrue(kr.urls().first().contains("raw.githubusercontent.com/adobe-fonts/source-han-sans/release/OTF/Korean/SourceHanSansK-Bold.otf"))
    }

    @Test fun remoteFacesHaveJsdelivrFallback() {
        val jp = SystemFont.remoteFace(SystemFontScript.JP)
        assertEquals(2, jp.urls().size)
        assertEquals(
            "https://cdn.jsdelivr.net/gh/notofonts/noto-cjk@main/Sans/OTF/Japanese/NotoSansCJKjp-Bold.otf",
            jp.urls()[1],
        )
        val kr = SystemFont.remoteFace(SystemFontScript.KR)
        assertEquals(2, kr.urls().size)
        assertEquals(
            "https://cdn.jsdelivr.net/gh/adobe-fonts/source-han-sans@release/OTF/Korean/SourceHanSansK-Bold.otf",
            kr.urls()[1],
        )
    }

    // ---- 固件自动匹配（FirmwareSupport 表优先，内核二次校验）----

    @Test fun firmwareTableHasAllRows() {
        assertEquals(30, FirmwareSupport.ENTRIES.size)
        assertEquals(20, FirmwareSupport.TESTED.size)
        assertEquals(10, FirmwareSupport.CALIBRATED.size)
        assertTrue(FirmwareSupport.TABLE_SOURCE.contains("root-my-s24 README v3.7.0-alpha.1"))
    }

    @Test fun exactTestedFirmwareMatchIsSupported() {
        // DISPLAY 风格：固件号嵌在整串里；INCREMENTAL 风格：就是固件号本身。
        val viaDisplay = SystemFont.checkDevice(
            "samsung", "SM-S9280",
            "UP1A.231005.007.S9280ZCS6DZF2", "S9280ZCS6DZF2", "6.1.145-android14-11",
        )
        assertEquals(DeviceSupport.SUPPORTED, viaDisplay.support)
        assertEquals("S9280ZCS6DZF2", viaDisplay.firmware?.entry?.firmwareBuild)
        assertEquals(FirmwareMatchKind.EXACT_TESTED, viaDisplay.firmware?.kind)
        assertTrue(viaDisplay.reason.contains("GhostLock"))
    }

    @Test fun firmwareMatchWorksFromIncrementalAlone() {
        val check = SystemFont.checkDevice(
            "samsung", "SM-S9280", "", "S9280ZCS6DZH3", "6.1.145-android14-11",
        )
        assertEquals(DeviceSupport.SUPPORTED, check.support)
        assertEquals("S9280ZCS6DZH3", check.firmware?.entry?.firmwareBuild)
    }

    @Test fun calibratedFirmwareMatchIsCalibratedNotSupported() {
        val check = SystemFont.checkDevice(
            "samsung", "SM-S9280",
            "UP1A.231005.007.S9280ZCS4CZA1", "S9280ZCS4CZA1", "6.1.128-android14-11",
        )
        assertEquals(DeviceSupport.CALIBRATED, check.support)
        assertEquals(FirmwareMatchKind.EXACT_CALIBRATED, check.firmware?.kind)
        assertTrue(check.reason.contains("未实测"))
    }

    @Test fun s25CalibratedFirmwareUses66Kernel() {
        val check = SystemFont.checkDevice(
            "samsung", "SM-S9360", "S9360ZCSCCZG1", "S9360ZCSCCZG1", "6.6.98-android16",
        )
        assertEquals(DeviceSupport.CALIBRATED, check.support)
        assertEquals("S9360ZCSCCZG1", check.firmware?.entry?.firmwareBuild)
    }

    @Test fun modelOnlyFirmwareIsUnknownWithKnownBuilds() {
        val check = SystemFont.checkDevice(
            "samsung", "SM-S9280",
            "UP1A.231005.007.S9280ZCS6DZZ9", "S9280ZCS6DZZ9", "6.1.145-android14-11",
        )
        assertEquals(DeviceSupport.UNKNOWN, check.support)
        assertEquals(FirmwareMatchKind.MODEL_ONLY, check.firmware?.kind)
        assertNull(check.firmware?.entry)
        assertTrue(check.reason.contains("未在支持列表里"))
        // 该机型已知固件要列出来，方便用户对照。
        assertTrue(check.firmware?.knownBuilds?.any { it.firmwareBuild == "S9280ZCS6DZF2" } == true)
        assertTrue(check.reason.contains("S9280ZCS6DZF2"))
    }

    @Test fun unknownModelIsUnsupported() {
        val check = SystemFont.checkDevice(
            "samsung", "SM-A5560",
            "UP1A.231005.007.A5560ZCS6DZF2", "A5560ZCS6DZF2", "6.1.118-android14",
        )
        assertEquals(DeviceSupport.UNSUPPORTED, check.support)
        assertEquals(FirmwareMatchKind.UNKNOWN_MODEL, check.firmware?.kind)
    }

    @Test fun exactFirmwareButWrongKernelIsUnknown() {
        val check = SystemFont.checkDevice(
            "samsung", "SM-S9280",
            "UP1A.231005.007.S9280ZCS6DZF2", "S9280ZCS6DZF2", "6.6.66-android15",
        )
        assertEquals(DeviceSupport.UNKNOWN, check.support)
        assertTrue(check.reason.contains("内核"))
    }

    @Test fun exactFirmwareButUnreadableKernelIsUnknown() {
        val check = SystemFont.checkDevice(
            "samsung", "SM-S9280",
            "UP1A.231005.007.S9280ZCS6DZF2", "S9280ZCS6DZF2", "",
        )
        assertEquals(DeviceSupport.UNKNOWN, check.support)
    }

    @Test fun sharedPayloadFold6MatchesByModelPlusDzf2() {
        val check = SystemFont.checkDevice(
            "samsung", "SM-F9580",
            "UP1A.231005.007.S9280ZCS6DZF2", "S9280ZCS6DZF2", "6.1.145-android14-11",
        )
        assertEquals(DeviceSupport.SUPPORTED, check.support)
        assertEquals("SM-F9580", check.firmware?.entry?.model)
        // 同机型但不是 DZF2 载荷的固件：已实测行不命中，但折叠屏移植载荷行覆盖
        // Z Fold6 国行（机型前缀 + 内核 6.1）→ 移植载荷，未实测。
        val other = SystemFont.checkDevice(
            "samsung", "SM-F9580",
            "UP1A.231005.007.S9280ZCS6DZH3", "S9280ZCS6DZH3", "6.1.145-android14-11",
        )
        assertEquals(DeviceSupport.PORTED, other.support)
        assertEquals(FirmwareMatchKind.PORTED, other.firmware?.kind)
        assertEquals("折叠屏", other.firmware?.ported?.seriesLabel)
        // 内核不在移植载荷行里时才真正不命中。
        val wrongKernel = SystemFont.checkDevice(
            "samsung", "SM-F9580",
            "UP1A.231005.007.S9280ZCS6DZH3", "S9280ZCS6DZH3", "5.10.100-android13",
        )
        assertEquals(DeviceSupport.UNKNOWN, wrongKernel.support)
        assertEquals(FirmwareMatchKind.MODEL_ONLY, wrongKernel.firmware?.kind)
    }

    @Test fun firmwareSubstringMatchingIsCaseInsensitive() {
        val match = FirmwareSupport.matchFirmware("sm-s928b", "up1a.231005.007.s928bxxu6ezjb", "")
        assertEquals(FirmwareMatchKind.EXACT_TESTED, match.kind)
        assertEquals("S928BXXU6EZJB", match.entry?.firmwareBuild)
        // EZJ1 与 EZJB/EZJD 是不同固件，不能串。
        val ezj1 = FirmwareSupport.matchFirmware("SM-S928B", "S928BXXS6EZJ1", "S928BXXS6EZJ1")
        assertEquals("S928BXXS6EZJ1", ezj1.entry?.firmwareBuild)
    }

    @Test fun kernelVersionParsing() {
        assertEquals(6 to 1, SystemFont.parseKernelVersion("6.1.145-android14-11-00001"))
        assertEquals(6 to 6, SystemFont.parseKernelVersion("6.6.98-android16"))
        assertEquals(6 to 1, SystemFont.parseKernelVersion("  6.1.75-android14 "))
        assertNull(SystemFont.parseKernelVersion(""))
        assertNull(SystemFont.parseKernelVersion("no-version-here"))
    }

    @Test fun nonSamsungWithKnownFirmwareIsStillUnsupported() {
        val check = SystemFont.checkDevice(
            "Google", "SM-S9280",
            "UP1A.231005.007.S9280ZCS6DZF2", "S9280ZCS6DZF2", "6.1.145-android14",
        )
        assertEquals(DeviceSupport.UNSUPPORTED, check.support)
    }

    @Test fun firmwareTableSourceAndProbabilisticNoteArePresent() {
        assertTrue(FirmwareSupport.PROBABILISTIC_NOTE.contains("不是 100%"))
        assertTrue(FirmwareSupport.PROBABILISTIC_NOTE.contains("只运行一次"))
    }

    // ---- 移植载荷表（系列 + 内核 + 固件后缀，未经真机实测）----

    @Test fun portedTableHasSixRows() {
        assertEquals(6, FirmwareSupport.PORTED_PAYLOADS.size)
        assertTrue(FirmwareSupport.PORTED_SETTING_NOTE.contains("启用未经测试的载荷"))
        assertTrue(FirmwareSupport.DZF2_COVERAGE_NOTE.contains("v3.5.0"))
        assertTrue(FirmwareSupport.US_S24_NOTE.contains("SM-S921U"))
    }

    @Test fun portedS23MatchIsPortedNotSupported() {
        val check = SystemFont.checkDevice(
            "samsung", "SM-S9110",
            "UP1A.231005.007.S9110ZCS5FZE3", "S9110ZCS5FZE3", "5.15.100-android13",
        )
        assertEquals(DeviceSupport.PORTED, check.support)
        assertEquals(FirmwareMatchKind.PORTED, check.firmware?.kind)
        assertEquals("S23", check.firmware?.ported?.seriesLabel)
        assertNull(check.firmware?.entry)
        assertTrue(check.reason.contains("移植载荷"))
        assertTrue(check.reason.contains("需在 RootMyS24 设置里开启未经测试的载荷"))
    }

    @Test fun portedS24ViaUntestedSuffixIsPorted() {
        // S9210 在已实测表里只有 DZI1；DZE2 只在移植载荷表里。
        val check = SystemFont.checkDevice(
            "samsung", "SM-S9210",
            "UP1A.231005.007.S9210ZCS6DZE2", "S9210ZCS6DZE2", "6.1.145-android14-11",
        )
        assertEquals(DeviceSupport.PORTED, check.support)
        assertEquals("S24", check.firmware?.ported?.seriesLabel)
    }

    @Test fun portedS26AndW26Match() {
        val s26 = SystemFont.checkDevice(
            "samsung", "SM-S9420", "S9420ZCS6AZG1", "S9420ZCS6AZG1", "6.12.10-android16",
        )
        assertEquals(DeviceSupport.PORTED, s26.support)
        assertEquals("S26", s26.firmware?.ported?.seriesLabel)
        val w26 = SystemFont.checkDevice(
            "samsung", "W9026", "W9026ZCS6BZG3", "W9026ZCS6BZG3", "6.6.50-android16",
        )
        assertEquals(DeviceSupport.PORTED, w26.support)
        assertEquals("心系天下", w26.firmware?.ported?.seriesLabel)
    }

    @Test fun portedFoldableMatchesByModelPrefixAndKernel() {
        // F956U 美版 Fold6 不在已实测表里，折叠屏行只按机型前缀 + 内核匹配。
        val check = SystemFont.checkDevice(
            "samsung", "SM-F956U",
            "UP1A.231005.007.F956USQU6DZG1", "F956USQU6DZG1", "6.1.75-android14",
        )
        assertEquals(DeviceSupport.PORTED, check.support)
        assertEquals("折叠屏", check.firmware?.ported?.seriesLabel)
    }

    @Test fun portedMissOnWrongKernelIsNotPorted() {
        // S9110 + FZE3 但内核是 6.1：移植载荷 S23 行要求 5.15，不命中。
        val check = SystemFont.checkDevice(
            "samsung", "SM-S9110",
            "UP1A.231005.007.S9110ZCS5FZE3", "S9110ZCS5FZE3", "6.1.100-android14",
        )
        assertEquals(DeviceSupport.UNSUPPORTED, check.support)
        assertEquals(FirmwareMatchKind.UNKNOWN_MODEL, check.firmware?.kind)
        assertNull(check.firmware?.ported)
    }

    @Test fun portedMissOnUnknownSuffixIsNotPorted() {
        // S9110 + 5.15 内核，但固件后缀不在移植载荷表里。
        val check = SystemFont.checkDevice(
            "samsung", "SM-S9110",
            "UP1A.231005.007.S9110ZCS5FZG9", "S9110ZCS5FZG9", "5.15.100-android13",
        )
        assertEquals(DeviceSupport.UNSUPPORTED, check.support)
        assertNull(check.firmware?.ported)
    }

    @Test fun testedRowWinsOverPortedRow() {
        // S9280 国行 DZG1 同时在已实测表（完整固件号）和移植载荷 S24 行（DZG1 后缀）里，
        // 以已实测为准，放绿灯。
        val dzg1 = SystemFont.checkDevice(
            "samsung", "SM-S9280",
            "UP1A.231005.007.S9280ZCS6DZG1", "S9280ZCS6DZG1", "6.1.145-android14-11",
        )
        assertEquals(DeviceSupport.SUPPORTED, dzg1.support)
        assertEquals(FirmwareMatchKind.EXACT_TESTED, dzg1.firmware?.kind)
        val dzf2 = SystemFont.checkDevice(
            "samsung", "SM-S9280",
            "UP1A.231005.007.S9280ZCS6DZF2", "S9280ZCS6DZF2", "6.1.145-android14-11",
        )
        assertEquals(DeviceSupport.SUPPORTED, dzf2.support)
        // Flip6 DZE1 已实测，折叠屏移植载荷行不能把它降级。
        val flip6 = SystemFont.checkDevice(
            "samsung", "SM-F741N",
            "UP1A.231005.007.F741NKSS4DZE1", "F741NKSS4DZE1", "6.1.145-android14-11",
        )
        assertEquals(DeviceSupport.SUPPORTED, flip6.support)
    }

    @Test fun calibratedRowWinsOverPortedRow() {
        // S9310 CZG1 同时在待验证表（完整固件号）和移植载荷 S25 行（CZG1 后缀）里，
        // 以待验证为准（更精确的匹配优先于通用载荷）。
        val check = SystemFont.checkDevice(
            "samsung", "SM-S9310",
            "UP1A.231005.007.S9310ZCSCCZG1", "S9310ZCSCCZG1", "6.6.98-android16",
        )
        assertEquals(DeviceSupport.CALIBRATED, check.support)
        assertEquals(FirmwareMatchKind.EXACT_CALIBRATED, check.firmware?.kind)
        assertNull(check.firmware?.ported)
    }

    @Test fun portedKernel612DoesNotMatch61() {
        // 6.12 与 6.1 是不同大版本，不能串：S9420 配 6.1 内核不在任何表里。
        val check = SystemFont.checkDevice(
            "samsung", "SM-S9420", "S9420ZCS6AZG1", "S9420ZCS6AZG1", "6.1.100-android14",
        )
        assertEquals(DeviceSupport.UNSUPPORTED, check.support)
        assertNull(check.firmware?.ported)
    }

    // ---- 跨机型候选（S9260 用 S9280 同构建号载荷，机主验证过的路子）----

    @Test fun crossModelCandidateOnS9260Dzh3IsCandidate() {
        // S9260 国行 DZH3：S9260 行里没有，移植载荷 S24 行里也没有 DZH3，
        // 但 S9280 有同构建号 DZH3 的已实测行 → 候选，不改变主匹配等级（MODEL_ONLY）。
        val check = SystemFont.checkDevice(
            "samsung", "SM-S9260",
            "UP1A.231005.007.S9260ZCS6DZH3", "S9260ZCS6DZH3", "6.1.145-android14-11",
        )
        assertEquals(DeviceSupport.CANDIDATE, check.support)
        assertEquals(FirmwareMatchKind.MODEL_ONLY, check.firmware?.kind)
        val cross = check.firmware?.crossModel
        assertEquals("SM-S9260", cross?.fromModel)
        assertEquals("S24 Ultra", cross?.toModelLabel)
        assertEquals("DZH3", cross?.suffix)
        assertEquals("S9280ZCS6DZH3", cross?.entry?.firmwareBuild)
        assertTrue(check.reason.contains("S24 Ultra"))
        assertTrue(check.reason.contains("你以前用这条路子通过过"))
    }

    @Test fun crossModelCandidateAttachedToPortedMatch() {
        // S9260 国行 DZF2：主匹配是移植载荷（S24 行），同时附带跨机型候选；
        // 主等级不变（PORTED），候选只是附加建议。
        val check = SystemFont.checkDevice(
            "samsung", "SM-S9260",
            "UP1A.231005.007.S9260ZCS6DZF2", "S9260ZCS6DZF2", "6.1.145-android14-11",
        )
        assertEquals(DeviceSupport.PORTED, check.support)
        assertEquals(FirmwareMatchKind.PORTED, check.firmware?.kind)
        assertEquals("S9280ZCS6DZF2", check.firmware?.crossModel?.entry?.firmwareBuild)
        assertEquals("DZF2", check.firmware?.crossModel?.suffix)
    }

    @Test fun crossModelCandidateAbsentWithoutMatchingSuffix() {
        // S9260 DZI1：S9280 的 6.1.145 行里没有 DZI1，没有候选。
        val check = SystemFont.checkDevice(
            "samsung", "SM-S9260",
            "UP1A.231005.007.S9260ZCS6DZI1", "S9260ZCS6DZI1", "6.1.145-android14-11",
        )
        assertEquals(DeviceSupport.UNKNOWN, check.support)
        assertEquals(FirmwareMatchKind.MODEL_ONLY, check.firmware?.kind)
        assertNull(check.firmware?.crossModel)
    }

    @Test fun crossModelCandidateOnlyOnS9260() {
        // S9280 本机不需要候选；S9210 也不需要。
        val s9280 = SystemFont.checkDevice(
            "samsung", "SM-S9280",
            "UP1A.231005.007.S9280ZCS6DZF2", "S9280ZCS6DZF2", "6.1.145-android14-11",
        )
        assertEquals(DeviceSupport.SUPPORTED, s9280.support)
        assertNull(s9280.firmware?.crossModel)
        val s9210 = SystemFont.checkDevice(
            "samsung", "SM-S9210",
            "UP1A.231005.007.S9210ZCS6DZE2", "S9210ZCS6DZE2", "6.1.145-android14-11",
        )
        assertEquals(DeviceSupport.PORTED, s9210.support)
        assertNull(s9210.firmware?.crossModel)
    }

    @Test fun crossModelCandidateNeeds61Kernel() {
        // S9260 DZF2 但内核是 6.6：不在同一条 6.1.145 内核线上，没有候选。
        val check = SystemFont.checkDevice(
            "samsung", "SM-S9260",
            "UP1A.231005.007.S9260ZCS6DZF2", "S9260ZCS6DZF2", "6.6.66-android15",
        )
        assertEquals(DeviceSupport.UNKNOWN, check.support)
        assertNull(check.firmware?.crossModel)
    }

    @Test fun crossModelCandidateNotShownWhenAlreadyTested() {
        // S9260 台版 DZG1 已实测，放绿灯，不需要候选。
        val check = SystemFont.checkDevice(
            "samsung", "SM-S9260",
            "UP1A.231005.007.S9260ZHS6DZG1", "S9260ZHS6DZG1", "6.1.145-android14-11",
        )
        assertEquals(DeviceSupport.SUPPORTED, check.support)
        assertNull(check.firmware?.crossModel)
    }

    // ---- 失败自动取消（硬规则：任何一步不通过，整个操作取消，不留半成品）----

    @Test fun planCancelEarlyFailureOnlyClearsStaging() {
        // 下载 / 生成失败（安装步没跑过）：只清 staging，不删模块目录、不重启 zygote。
        val plan = SystemFont.planCancel(moduleWrittenByThisRun = false, postVerifyFailed = false)
        assertTrue(plan.clearStaging)
        assertFalse(plan.deleteModuleDir)
        assertFalse(plan.restartZygote)
    }

    @Test fun planCancelInstallFailureDeletesModuleWithoutRestart() {
        // 安装步跑过但没完成：删掉可能已写入一半的模块目录，不重启 zygote（新字体没挂上）。
        val plan = SystemFont.planCancel(moduleWrittenByThisRun = true, postVerifyFailed = false)
        assertTrue(plan.clearStaging)
        assertTrue(plan.deleteModuleDir)
        assertFalse(plan.restartZygote)
    }

    @Test fun planCancelPostVerifyFailureRollsBackWithRestart() {
        // 模块已装上但装后验证失败：删模块 + 重启 zygote（自动回滚）。
        val plan = SystemFont.planCancel(moduleWrittenByThisRun = true, postVerifyFailed = true)
        assertTrue(plan.clearStaging)
        assertTrue(plan.deleteModuleDir)
        assertTrue(plan.restartZygote)
        // 装后验证失败本身就意味着模块已装上，即使没传 moduleWritten 也要删 + 回滚。
        val plan2 = SystemFont.planCancel(moduleWrittenByThisRun = false, postVerifyFailed = true)
        assertTrue(plan2.deleteModuleDir)
        assertTrue(plan2.restartZygote)
    }

    @Test fun clearStagingRemovesStagingDirAndZip() {
        val staging = Files.createTempDirectory("sysfont-staging").toFile()
        try {
            File(staging, "module.prop").writeText("id=tokifonts-font\n")
            File(staging, "system/etc/fonts.xml").apply { parentFile.mkdirs() }.writeText("<familyset/>")
            File(staging, "system/fonts/Inter-Bold.otf").apply { parentFile.mkdirs() }.writeText("fake-font-bytes")
            val zip = File(staging.parentFile, "tokifonts-font.zip")
            try {
                zip.writeText("fake-zip")
                assertTrue(staging.exists())
                assertTrue(zip.exists())
                SystemFont.clearStaging(staging, zip)
                // 某一步失败后：staging 已清理、zip 已删，不留半成品。
                assertFalse(staging.exists())
                assertFalse(zip.exists())
            } finally {
                zip.delete()
            }
        } finally {
            staging.deleteRecursively()
        }
    }

    // ---- 改动清单与结构校验（加固）----

    @Test fun changeListShowsReplaceAndAdded() {
        val changes = SystemFont.computeChangeList(
            sampleXml,
            listOf(SystemFontScript.LATIN, SystemFontScript.SC),
        )
        assertEquals(2, changes.size)
        assertEquals("SamsungOneUI-Bold.ttf", changes[0].oldFile)
        assertEquals("Inter-Bold.otf", changes[0].newFile)
        assertFalse(changes[0].added)
        assertNull(changes[1].oldFile)
        assertEquals("2001.ttf", changes[1].newFile)
        assertTrue(changes[1].added)
        assertEquals(SystemFontScript.SC, changes[1].script)
    }

    @Test fun changeListAllScriptsOneReplaceFourAdded() {
        val changes = SystemFont.computeChangeList(
            sampleXml,
            listOf(
                SystemFontScript.LATIN, SystemFontScript.SC, SystemFontScript.TC,
                SystemFontScript.JP, SystemFontScript.KR,
            ),
        )
        assertEquals(5, changes.size)
        assertFalse(changes[0].added)
        assertTrue(changes.subList(1, 5).all { it.added })
        assertEquals(
            listOf("Inter-Bold.otf", "2001.ttf", "2010.ttf", "NotoSansCJKjp-Bold.otf", "SourceHanSansK-Bold.otf"),
            changes.map { it.newFile },
        )
    }

    private fun rewrittenTwoScripts() =
        SystemFont.rewriteFontsXml(sampleXml, listOf(SystemFontScript.LATIN, SystemFontScript.SC))

    private val stagingTwoScripts = setOf("Inter-Bold.otf", "2001.ttf")

    @Test fun validateRewriteAcceptsCleanRewrite() {
        val problems = SystemFont.validateRewrite(
            sampleXml, rewrittenTwoScripts(),
            listOf(SystemFontScript.LATIN, SystemFontScript.SC), stagingTwoScripts,
        )
        assertTrue(problems.isEmpty())
    }

    @Test fun validateRewriteRejectsNonBoldChange() {
        val tampered = rewrittenTwoScripts().replace("SamsungOneUI-Regular.ttf", "Evil.ttf")
        val problems = SystemFont.validateRewrite(
            sampleXml, tampered,
            listOf(SystemFontScript.LATIN, SystemFontScript.SC), stagingTwoScripts,
        )
        assertTrue(problems.isNotEmpty())
    }

    @Test fun validateRewriteRejectsOtherFamilyChange() {
        val tampered = rewrittenTwoScripts().replace("Serif-Bold.ttf", "Evil.ttf")
        val problems = SystemFont.validateRewrite(
            sampleXml, tampered,
            listOf(SystemFontScript.LATIN, SystemFontScript.SC), stagingTwoScripts,
        )
        assertTrue(problems.isNotEmpty())
    }

    @Test fun validateRewriteRejectsEmptiedEntry() {
        val tampered = rewrittenTwoScripts().replace("SamsungOneUI-Medium.ttf", "")
        val problems = SystemFont.validateRewrite(
            sampleXml, tampered,
            listOf(SystemFontScript.LATIN, SystemFontScript.SC), stagingTwoScripts,
        )
        assertTrue(problems.isNotEmpty())
    }

    @Test fun validateRewriteRejectsUnexpectedBoldFile() {
        val tampered = rewrittenTwoScripts().replace("Inter-Bold.otf", "Evil.otf")
        val problems = SystemFont.validateRewrite(
            sampleXml, tampered,
            listOf(SystemFontScript.LATIN, SystemFontScript.SC), stagingTwoScripts,
        )
        assertTrue(problems.isNotEmpty())
    }

    @Test fun validateRewriteRejectsFileMissingFromStaging() {
        val problems = SystemFont.validateRewrite(
            sampleXml, rewrittenTwoScripts(),
            listOf(SystemFontScript.LATIN, SystemFontScript.SC), setOf("Inter-Bold.otf"),
        )
        assertTrue(problems.isNotEmpty())
    }

    @Test fun validateRewriteRejectsAliasChange() {
        val tampered = rewrittenTwoScripts().replace("to=\"sans-serif\"", "to=\"serif\"")
        val problems = SystemFont.validateRewrite(
            sampleXml, tampered,
            listOf(SystemFontScript.LATIN, SystemFontScript.SC), stagingTwoScripts,
        )
        assertTrue(problems.isNotEmpty())
    }

    @Test fun validateRewriteRejectsInvalidXml() {
        val problems = SystemFont.validateRewrite(
            sampleXml, "<familyset><family>",
            listOf(SystemFontScript.LATIN), setOf("Inter-Bold.otf"),
        )
        assertTrue(problems.isNotEmpty())
    }

    // ---- 模块打包与空间检查（加固）----

    @Test fun buildModuleZipRoundTrips() {
        val staging = Files.createTempDirectory("sysfont-staging").toFile()
        try {
            File(staging, "module.prop").writeText("id=tokifonts-font\n")
            File(staging, "system/etc/fonts.xml").apply { parentFile.mkdirs() }.writeText("<familyset/>")
            File(staging, "system/fonts/Inter-Bold.otf").apply { parentFile.mkdirs() }.writeText("fake-font-bytes")
            val zip = File(staging.parentFile, "tokifonts-font.zip")
            try {
                SystemFont.buildModuleZip(staging, zip)
                val entries = mutableMapOf<String, String>()
                ZipFile(zip).use { z ->
                    z.entries().asSequence().forEach { e ->
                        entries[e.name] = z.getInputStream(e).bufferedReader().readText()
                    }
                }
                assertEquals(
                    setOf("module.prop", "system/etc/fonts.xml", "system/fonts/Inter-Bold.otf"),
                    entries.keys,
                )
                assertEquals("fake-font-bytes", entries["system/fonts/Inter-Bold.otf"])
            } finally {
                zip.delete()
            }
        } finally {
            staging.deleteRecursively()
        }
    }

    @Test fun requiredDownloadBytesCoversJpKrWithMargin() {
        val needed = SystemFont.requiredDownloadBytes(listOf(SystemFontScript.JP, SystemFontScript.KR))
        assertEquals((17_032_620L + 16_959_020L) * 2 + 16L * 1024 * 1024, needed)
        assertTrue(SystemFont.hasEnoughSpace(needed, needed))
        assertFalse(SystemFont.hasEnoughSpace(needed - 1, needed))
        assertEquals(16L * 1024 * 1024, SystemFont.requiredDownloadBytes(listOf(SystemFontScript.LATIN)))
    }
}
