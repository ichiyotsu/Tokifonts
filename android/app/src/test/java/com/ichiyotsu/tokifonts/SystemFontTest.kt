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

    @Test fun rewriteReplacesEveryWeightPerScript() {
        val rewritten = SystemFont.rewriteFontsXml(sampleXml, listOf(SystemFontScript.LATIN, SystemFontScript.SC))
        val fonts = SystemFont.sansSerifFonts(rewritten)
        // 每档字重各占一条：第一个语种替换原条目，第二个语种追加在该字重组内。
        assertEquals(
            listOf(
                400 to "Inter-Regular.otf", 400 to "2006 PingFang Regular.ttf",
                500 to "Inter-Medium.otf", 500 to "2005 PingFang Medium.ttf",
                700 to "Inter-Bold.otf", 700 to "2001 PingFang Bold.ttf",
            ),
            fonts,
        )
    }

    @Test fun rewriteMaps400ToRegularNotBold() {
        // 默认映射：400 映射常规文件，绝不能映射到 Bold；700 仍映射粗体文件。
        val rewritten = SystemFont.rewriteFontsXml(sampleXml, listOf(SystemFontScript.LATIN, SystemFontScript.JP))
        val fonts = SystemFont.sansSerifFonts(rewritten)
        assertEquals("Inter-Regular.otf", fonts.first { it.first == 400 }.second)
        assertEquals("NotoSansCJKjp-Regular.otf", fonts.filter { it.first == 400 }[1].second)
        assertEquals("Inter-Bold.otf", fonts.first { it.first == 700 }.second)
        assertEquals("NotoSansCJKjp-Bold.otf", fonts.filter { it.first == 700 }[1].second)
        assertTrue(fonts.none { it.second == "Inter-Bold.otf" && it.first != 700 })
    }

    @Test fun rewriteKeepsOtherFamiliesUntouched() {
        val rewritten = SystemFont.rewriteFontsXml(sampleXml, listOf(SystemFontScript.LATIN))
        assertTrue(rewritten.contains("NotoSansCJK-Bold.ttc"))
        assertTrue(rewritten.contains("Serif-Bold.ttf"))
        assertTrue(rewritten.contains("NotoSansCJK-Regular.ttc"))
        assertTrue(rewritten.contains("sans-serif-cjk"))
    }

    /** 斜体条目没有斜体替换文件，原样保留（已知缺口），且不影响结构校验。 */
    private val sampleXmlWithItalic = """
        <?xml version="1.0" encoding="utf-8"?>
        <familyset version="23">
            <family name="sans-serif">
                <font weight="400" style="normal">SamsungOneUI-Regular.ttf</font>
                <font weight="400" style="italic">SamsungOneUI-Italic.ttf</font>
                <font weight="700" style="normal">SamsungOneUI-Bold.ttf</font>
            </family>
            <alias name="sans-serif" to="sans-serif" weight="400" />
        </familyset>
    """.trimIndent()

    @Test fun rewriteKeepsItalicEntriesUntouched() {
        val rewritten = SystemFont.rewriteFontsXml(sampleXmlWithItalic, listOf(SystemFontScript.LATIN))
        assertTrue(rewritten.contains("SamsungOneUI-Italic.ttf"))
        assertEquals(
            listOf(400 to "Inter-Regular.otf", 400 to "SamsungOneUI-Italic.ttf", 700 to "Inter-Bold.otf"),
            SystemFont.sansSerifFonts(rewritten),
        )
        val problems = SystemFont.validateRewrite(
            sampleXmlWithItalic, rewritten,
            listOf(SystemFontScript.LATIN), setOf("Inter-Regular.otf", "Inter-Bold.otf"),
        )
        assertTrue(problems.isEmpty())
    }

    @Test fun rewrittenXmlParsesAgain() {
        val rewritten = SystemFont.rewriteFontsXml(
            sampleXml,
            listOf(SystemFontScript.LATIN, SystemFontScript.SC, SystemFontScript.TC, SystemFontScript.JP, SystemFontScript.KR),
        )
        assertTrue(SystemFont.isValidFontsXml(rewritten))
        // 五档全开 × 三档字重：每档字重 5 条，共 15 条。
        assertEquals(15, SystemFont.sansSerifFonts(rewritten).size)
        assertEquals(
            listOf("Inter-Bold.otf", "2001 PingFang Bold.ttf", "2010 PingFangHK-Semibold.ttf", "NotoSansCJKjp-Bold.otf", "SourceHanSansK-Bold.otf"),
            SystemFont.sansSerifFonts(rewritten).filter { it.first == 700 }.map { it.second },
        )
        assertEquals(
            listOf("Inter-Regular.otf", "2006 PingFang Regular.ttf", "2009 PingFangHK-Regular.ttf", "NotoSansCJKjp-Regular.otf", "SourceHanSansK-Regular.otf"),
            SystemFont.sansSerifFonts(rewritten).filter { it.first == 400 }.map { it.second },
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
        assertTrue(prop.contains("id=tokifontspro-font"))
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
                "system/fonts/Inter-Thin.otf", "system/fonts/Inter-ExtraLight.otf",
                "system/fonts/Inter-Light.otf", "system/fonts/Inter-Regular.otf",
                "system/fonts/Inter-Medium.otf", "system/fonts/Inter-SemiBold.otf",
                "system/fonts/Inter-Bold.otf", "system/fonts/Inter-ExtraBold.otf",
                "system/fonts/Inter-Black.otf",
                "system/fonts/NotoSansCJKjp-Thin.otf", "system/fonts/NotoSansCJKjp-Light.otf",
                "system/fonts/NotoSansCJKjp-DemiLight.otf", "system/fonts/NotoSansCJKjp-Regular.otf",
                "system/fonts/NotoSansCJKjp-Medium.otf", "system/fonts/NotoSansCJKjp-Bold.otf",
                "system/fonts/NotoSansCJKjp-Black.otf",
            ),
            layout,
        )
    }

    @Test fun moduleLayoutForDeviceWeightsOnlyListsNeededFiles() {
        // 设备只有 400/700 两档时，清单只列这两档映射到的文件（HK 700 回退到 600 档）。
        val layout = SystemFont.moduleLayout(
            listOf(SystemFontScript.TC),
            setOf(400, 700),
        )
        assertEquals(
            listOf(
                "module.prop", "system/etc/fonts.xml", "fonts.xml.original",
                "system/fonts/2009 PingFangHK-Regular.ttf", "system/fonts/2010 PingFangHK-Semibold.ttf",
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
        assertEquals(31, FirmwareSupport.ENTRIES.size)
        assertEquals(20, FirmwareSupport.TESTED.size)
        assertEquals(11, FirmwareSupport.CALIBRATED.size)
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
            File(staging, "module.prop").writeText("id=tokifontspro-font\n")
            File(staging, "system/etc/fonts.xml").apply { parentFile.mkdirs() }.writeText("<familyset/>")
            File(staging, "system/fonts/Inter-Bold.otf").apply { parentFile.mkdirs() }.writeText("fake-font-bytes")
            val zip = File(staging.parentFile, "tokifontspro-font.zip")
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
        // 三档字重 × 两语种：每档 1 条替换 + 1 条追加，共 6 条。
        assertEquals(6, changes.size)
        assertEquals("SamsungOneUI-Regular.ttf", changes[0].oldFile)
        assertEquals("Inter-Regular.otf", changes[0].newFile)
        assertEquals(400, changes[0].weight)
        assertFalse(changes[0].added)
        assertNull(changes[1].oldFile)
        assertEquals("2006 PingFang Regular.ttf", changes[1].newFile)
        assertEquals(400, changes[1].weight)
        assertTrue(changes[1].added)
        assertEquals(SystemFontScript.SC, changes[1].script)
        assertEquals("SamsungOneUI-Bold.ttf", changes[4].oldFile)
        assertEquals("Inter-Bold.otf", changes[4].newFile)
        assertEquals(700, changes[4].weight)
        assertEquals("2001 PingFang Bold.ttf", changes[5].newFile)
        assertTrue(changes[5].added)
    }

    @Test fun changeListAllScriptsThreeWeightsFifteenChanges() {
        val changes = SystemFont.computeChangeList(
            sampleXml,
            listOf(
                SystemFontScript.LATIN, SystemFontScript.SC, SystemFontScript.TC,
                SystemFontScript.JP, SystemFontScript.KR,
            ),
        )
        assertEquals(15, changes.size)
        // 每档字重：首条替换，其余 4 条追加。
        assertEquals(listOf(400, 400, 400, 400, 400, 500, 500, 500, 500, 500, 700, 700, 700, 700, 700), changes.map { it.weight })
        assertFalse(changes[0].added)
        assertTrue(changes.subList(1, 5).all { it.added })
        assertFalse(changes[5].added)
        assertTrue(changes.subList(6, 10).all { it.added })
        assertFalse(changes[10].added)
        assertTrue(changes.subList(11, 15).all { it.added })
        assertEquals(
            listOf("Inter-Bold.otf", "2001 PingFang Bold.ttf", "2010 PingFangHK-Semibold.ttf", "NotoSansCJKjp-Bold.otf", "SourceHanSansK-Bold.otf"),
            changes.filter { it.weight == 700 }.map { it.newFile },
        )
    }

    @Test fun changeListGroupsByWeight() {
        val changes = SystemFont.computeChangeList(
            sampleXml,
            listOf(SystemFontScript.LATIN, SystemFontScript.SC),
        )
        val groups = SystemFont.groupChangesByWeight(changes)
        assertEquals(listOf(400, 500, 700), groups.map { it.weight })
        assertTrue(groups.all { it.changes.size == 2 })
        assertEquals(
            listOf("Inter-Regular.otf", "2006 PingFang Regular.ttf"),
            groups[0].changes.map { it.newFile },
        )
    }

    private fun rewrittenTwoScripts() =
        SystemFont.rewriteFontsXml(sampleXml, listOf(SystemFontScript.LATIN, SystemFontScript.SC))

    private val stagingTwoScripts = setOf(
        "Inter-Regular.otf", "Inter-Medium.otf", "Inter-Bold.otf",
        "2006 PingFang Regular.ttf", "2005 PingFang Medium.ttf", "2001 PingFang Bold.ttf",
    )

    @Test fun validateRewriteAcceptsCleanRewrite() {
        val problems = SystemFont.validateRewrite(
            sampleXml, rewrittenTwoScripts(),
            listOf(SystemFontScript.LATIN, SystemFontScript.SC), stagingTwoScripts,
        )
        assertTrue(problems.isEmpty())
    }

    @Test fun validateRewriteRejectsTamperedMediumWeight() {
        val tampered = rewrittenTwoScripts().replace("Inter-Medium.otf", "Evil.otf")
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
        val tampered = rewrittenTwoScripts().replace("Inter-Medium.otf", "")
        val problems = SystemFont.validateRewrite(
            sampleXml, tampered,
            listOf(SystemFontScript.LATIN, SystemFontScript.SC), stagingTwoScripts,
        )
        assertTrue(problems.isNotEmpty())
    }

    @Test fun validateRewriteRejectsLeftoverSamsungFile() {
        // 常规字重条目里不许留三星原厂文件：把 400 档换回三星原文件也必须拒绝。
        val tampered = rewrittenTwoScripts().replace("Inter-Regular.otf", "SamsungOneUI-Regular.ttf")
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
            File(staging, "module.prop").writeText("id=tokifontspro-font\n")
            File(staging, "system/etc/fonts.xml").apply { parentFile.mkdirs() }.writeText("<familyset/>")
            File(staging, "system/fonts/Inter-Bold.otf").apply { parentFile.mkdirs() }.writeText("fake-font-bytes")
            val zip = File(staging.parentFile, "tokifontspro-font.zip")
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

    @Test fun requiredDownloadBytesCoversAllWeightsWithMargin() {
        // JP 7 档 + KR 7 档全量：(115,614,852 + 115,502,372) × 2 + 16MB。
        val needed = SystemFont.requiredDownloadBytes(listOf(SystemFontScript.JP, SystemFontScript.KR))
        assertEquals(479_011_664L, needed)
        assertTrue(SystemFont.hasEnoughSpace(needed, needed))
        assertFalse(SystemFont.hasEnoughSpace(needed - 1, needed))
        // LATIN 只有 Inter 4.1 zip 一个下载物：33,707,794 × 2 + 16MB。
        assertEquals(84_192_804L, SystemFont.requiredDownloadBytes(listOf(SystemFontScript.LATIN)))
        // 苹方只用本地副本，不计下载量。
        assertEquals(16L * 1024 * 1024, SystemFont.requiredDownloadBytes(listOf(SystemFontScript.SC)))
    }

    @Test fun requiredDownloadBytesForDeviceWeightsIsSmaller() {
        // 设备只有 400/700 两档时，JP/KR 各只下 2 个文件（去重后），比全量小得多。
        val needed = SystemFont.requiredDownloadBytes(
            listOf(SystemFontScript.JP, SystemFontScript.KR),
            setOf(400, 700),
        )
        assertEquals(150_746_824L, needed)
        assertTrue(needed < SystemFont.requiredDownloadBytes(listOf(SystemFontScript.JP, SystemFontScript.KR)))
        // 两档设备字重映射到同一文件时只算一次：JP 600 回退到 700 档，只下一个 Bold。
        val jpFallback = SystemFont.requiredDownloadBytes(listOf(SystemFontScript.JP), setOf(600, 700))
        assertEquals(17_032_620L * 2 + 16L * 1024 * 1024, jpFallback)
    }

    @Test fun requiredDownloadBytesAllScriptsAllWeights() {
        // 五语种全开全量：(zip 33,707,794 + JP 115,614,852 + KR 115,502,372) × 2 + 16MB。
        val needed = SystemFont.requiredDownloadBytes(SystemFontScript.entries)
        assertEquals(546_427_252L, needed)
    }

    // ---- S9260 DZJ3 跨机型候选（S9280 DZJ3 待验证定标行）----

    @Test fun s9260Dzj3CrossModelCandidateIsCalibrated() {
        // S9260 国行 DZJ3：S9260 行里没有，移植载荷 S24 行里也没有 DZJ3，
        // 但 S9280 有同构建号 DZJ3 的待验证（已定标）行 → 候选，不走移植载荷表。
        val check = SystemFont.checkDevice(
            "samsung", "SM-S9260",
            "UP1A.231005.007.S9260ZCS6DZJ3", "S9260ZCS6DZJ3", "6.1.145-android14-11",
        )
        assertEquals(DeviceSupport.CANDIDATE, check.support)
        assertEquals(FirmwareMatchKind.MODEL_ONLY, check.firmware?.kind)
        assertNull(check.firmware?.ported)
        val cross = check.firmware?.crossModel
        assertEquals("SM-S9260", cross?.fromModel)
        assertEquals("S24 Ultra", cross?.toModelLabel)
        assertEquals("DZJ3", cross?.suffix)
        assertEquals("S9280ZCS6DZJ3", cross?.entry?.firmwareBuild)
        assertEquals(FirmwareStatus.CALIBRATED, cross?.entry?.status)
        assertEquals("98260 UAF", cross?.entry?.engine)
        assertTrue(check.reason.contains("待验证（已定标）载荷"))
        assertFalse(check.reason.contains("已实测载荷"))
    }

    @Test fun s9280Dzj3ExactCalibrated() {
        val check = SystemFont.checkDevice(
            "samsung", "SM-S9280",
            "UP1A.231005.007.S9280ZCS6DZJ3", "S9280ZCS6DZJ3", "6.1.145-android14-11",
        )
        assertEquals(DeviceSupport.CALIBRATED, check.support)
        assertEquals(FirmwareMatchKind.EXACT_CALIBRATED, check.firmware?.kind)
        assertEquals("S9280ZCS6DZJ3", check.firmware?.entry?.firmwareBuild)
        assertEquals("98260 UAF", check.firmware?.entry?.engine)
        assertTrue(check.reason.contains("未实测"))
    }

    @Test fun crossModelCandidatePrefersTestedOverCalibrated() {
        // DZF2 在 S9280 有已实测行：候选必须取已实测行，不取待验证行。
        val dzf2 = SystemFont.checkDevice(
            "samsung", "SM-S9260",
            "UP1A.231005.007.S9260ZCS6DZF2", "S9260ZCS6DZF2", "6.1.145-android14-11",
        )
        assertEquals(FirmwareStatus.TESTED, dzf2.firmware?.crossModel?.entry?.status)
        assertEquals("S9280ZCS6DZF2", dzf2.firmware?.crossModel?.entry?.firmwareBuild)
        assertEquals("GhostLock", dzf2.firmware?.crossModel?.entry?.engine)
        // DZH3 同理：已实测行优先。
        val dzh3 = SystemFont.checkDevice(
            "samsung", "SM-S9260",
            "UP1A.231005.007.S9260ZCS6DZH3", "S9260ZCS6DZH3", "6.1.145-android14-11",
        )
        assertEquals(DeviceSupport.CANDIDATE, dzh3.support)
        assertEquals(FirmwareStatus.TESTED, dzh3.firmware?.crossModel?.entry?.status)
        assertTrue(dzh3.reason.contains("已实测载荷"))
    }

    // ---- 全字重映射（最近字重回退：距离相同取更粗档）----

    @Test fun latinFileForWeightIsExactForAllWeights() {
        for (weight in listOf(100, 200, 300, 400, 500, 600, 700, 800, 900)) {
            assertEquals(weight, SystemFontScript.LATIN.fileForWeight(weight).weight)
        }
        assertEquals("Inter-Regular.otf", SystemFontScript.LATIN.fileForWeight(400).fileName)
        assertEquals("Inter-Bold.otf", SystemFontScript.LATIN.fileForWeight(700).fileName)
        assertEquals(9, SystemFontScript.LATIN.weightFiles.size)
    }

    @Test fun pingFangFileForWeightFallsBackToNearest() {
        // SC：100 → 200；600 距 500/700 相等 → 取更粗的 700；900 → 800。
        assertEquals(200, SystemFontScript.SC.fileForWeight(100).weight)
        assertEquals("2002", SystemFontScript.SC.fileForWeight(100).catalogFaceId)
        assertEquals(700, SystemFontScript.SC.fileForWeight(600).weight)
        assertEquals(800, SystemFontScript.SC.fileForWeight(900).weight)
        assertEquals(400, SystemFontScript.SC.fileForWeight(400).weight)
        assertEquals("2006", SystemFontScript.SC.fileForWeight(400).catalogFaceId)
        // HK：700/800/900 都没有 → 最近的 600（库里最粗档）；350 → 300/400 相等 → 取 400。
        assertEquals(600, SystemFontScript.TC.fileForWeight(700).weight)
        assertEquals(600, SystemFontScript.TC.fileForWeight(800).weight)
        assertEquals(600, SystemFontScript.TC.fileForWeight(900).weight)
        assertEquals("2010", SystemFontScript.TC.fileForWeight(700).catalogFaceId)
        assertEquals(400, SystemFontScript.TC.fileForWeight(350).weight)
    }

    @Test fun notoAndSourceHanFileForWeightFallsBackToNearest() {
        // JP：200 距 100/300 相等 → 取 300；600 距 500/700 相等 → 取 700；
        // 800 距 700/900 相等 → 取 900；350 是 DemiLight 精确档。
        assertEquals(300, SystemFontScript.JP.fileForWeight(200).weight)
        assertEquals(700, SystemFontScript.JP.fileForWeight(600).weight)
        assertEquals(900, SystemFontScript.JP.fileForWeight(800).weight)
        assertEquals(350, SystemFontScript.JP.fileForWeight(350).weight)
        assertEquals("NotoSansCJKjp-DemiLight.otf", SystemFontScript.JP.fileForWeight(350).fileName)
        // KR：100 → 200；600 距 500/700 相等 → 取 700；900 → 800；
        // 350 是 Normal 精确档（Normal 是 350，不是 400）。
        assertEquals(200, SystemFontScript.KR.fileForWeight(100).weight)
        assertEquals(700, SystemFontScript.KR.fileForWeight(600).weight)
        assertEquals(800, SystemFontScript.KR.fileForWeight(900).weight)
        assertEquals(350, SystemFontScript.KR.fileForWeight(350).weight)
        assertEquals("SourceHanSansK-Normal.otf", SystemFontScript.KR.fileForWeight(350).fileName)
        assertEquals(400, SystemFontScript.KR.fileForWeight(400).weight)
        assertEquals("SourceHanSansK-Regular.otf", SystemFontScript.KR.fileForWeight(400).fileName)
    }

    // ---- 按字重的远程下载 face ----

    @Test fun remoteFacesJpCoverAllSevenWeights() {
        val faces = SystemFont.remoteFaces(SystemFontScript.JP)
        assertEquals(listOf(100, 300, 350, 400, 500, 700, 900), faces.map { it.weight })
        assertEquals("NotoSansCJKjp-Regular.otf", faces.first { it.weight == 400 }.fileName)
        assertEquals(16_467_736L, faces.first { it.weight == 400 }.bytes)
        assertEquals("notofonts/noto-cjk", faces.first().repo)
        assertEquals("main", faces.first().ref)
        assertTrue(faces.all { it.urls().size == 2 })
        assertEquals(
            "https://cdn.jsdelivr.net/gh/notofonts/noto-cjk@main/Sans/OTF/Japanese/NotoSansCJKjp-Regular.otf",
            faces.first { it.weight == 400 }.urls()[1],
        )
        // 700 档与旧的单档入口完全一致。
        val bold = SystemFont.remoteFace(SystemFontScript.JP)
        assertEquals("NotoSansCJKjp-Bold.otf", bold.fileName)
        assertEquals(17_032_620L, bold.bytes)
    }

    @Test fun remoteFacesKrCoverAllSevenWeights() {
        val faces = SystemFont.remoteFaces(SystemFontScript.KR)
        assertEquals(listOf(200, 300, 350, 400, 500, 700, 800), faces.map { it.weight })
        assertEquals("SourceHanSansK-Normal.otf", faces.first { it.weight == 350 }.fileName)
        assertEquals("SourceHanSansK-Regular.otf", faces.first { it.weight == 400 }.fileName)
        assertEquals("adobe-fonts/source-han-sans", faces.first().repo)
        assertEquals("release", faces.first().ref)
        assertTrue(faces.all { it.urls().size == 2 })
        assertEquals(
            "https://cdn.jsdelivr.net/gh/adobe-fonts/source-han-sans@release/OTF/Korean/SourceHanSansK-Bold.otf",
            faces.first { it.weight == 700 }.urls()[1],
        )
    }

    @Test fun latinZipFacesShareOneReleaseZip() {
        val faces = SystemFont.remoteFaces(SystemFontScript.LATIN)
        assertEquals(9, faces.size)
        assertTrue(faces.all { it.format == "zip" })
        assertTrue(faces.all { it.bytes == SystemFont.LATIN_ZIP_BYTES })
        assertEquals("https://github.com/rsms/inter/releases/download/v4.1/Inter-4.1.zip", faces.first().extraUrls.first())
        assertEquals("extras/otf/Inter-Regular.otf", faces.first { it.weight == 400 }.innerName)
        assertEquals("extras/otf/Inter-Bold.otf", faces.first { it.weight == 700 }.innerName)
        // 700 档仍是内置的 Inter-Bold.otf（字节与 SHA 与 4.1 包内一致）。
        assertEquals("Inter-Bold.otf", SystemFont.remoteFace(SystemFontScript.LATIN).fileName)
    }

    @Test fun modulePropDescriptionMentionsAllWeights() {
        val prop = SystemFont.buildModuleProp("0.1.7", 8)
        assertTrue(prop.contains("全部字重"))
        assertTrue(prop.contains("常规 400"))
        assertTrue(prop.contains("粗体 700"))
    }

    @Test fun sansSerifWeightsReadFromXml() {
        assertEquals(listOf(400, 500, 700), SystemFont.sansSerifWeights(sampleXml))
        assertEquals(listOf(400, 700), SystemFont.sansSerifWeights(sampleXmlWithItalic))
    }

    @Test fun verifyFontFileChecksPerWeightSize() {
        val dir = Files.createTempDirectory("sysfont-verify").toFile()
        try {
            // 400 档文件按 700 档校验会报大小不对；按 400 档校验（只有文件头 + 大小，
            // Regular 有 SHA-256，这里用错内容凑不出 SHA，所以只断言大小口径不同）。
            val fake = File(dir, "Inter-Regular.otf")
            fake.writeBytes(ByteArray(609_600) { 0 })
            val wrongWeight = SystemFont.verifyFontFile(fake, SystemFontScript.LATIN, 700)
            assertTrue(wrongWeight != null && wrongWeight.contains("大小不对"))
            val missing = SystemFont.verifyFontFile(File(dir, "nope.otf"), SystemFontScript.LATIN, 400)
            assertTrue(missing != null && missing.contains("不存在"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun buildModuleZipRoundTripsMultiWeightFiles() {
        val staging = Files.createTempDirectory("sysfont-staging-multi").toFile()
        try {
            File(staging, "module.prop").writeText("id=tokifontspro-font\n")
            File(staging, "system/etc/fonts.xml").apply { parentFile.mkdirs() }.writeText("<familyset/>")
            File(staging, "system/fonts/Inter-Regular.otf").apply { parentFile.mkdirs() }.writeText("fake-regular")
            File(staging, "system/fonts/Inter-Bold.otf").writeText("fake-bold")
            File(staging, "system/fonts/2006 PingFang Regular.ttf").writeText("fake-pingfang")
            val zip = File(staging.parentFile, "tokifontspro-font-multi.zip")
            try {
                SystemFont.buildModuleZip(staging, zip)
                val entries = mutableSetOf<String>()
                ZipFile(zip).use { z ->
                    z.entries().asSequence().forEach { entries.add(it.name) }
                }
                assertEquals(
                    setOf(
                        "module.prop", "system/etc/fonts.xml",
                        "system/fonts/Inter-Regular.otf", "system/fonts/Inter-Bold.otf",
                        "system/fonts/2006 PingFang Regular.ttf",
                    ),
                    entries,
                )
            } finally {
                zip.delete()
            }
        } finally {
            staging.deleteRecursively()
        }
    }

    // ---- Root-My-Galaxy 合并（targets-v3.json，20 个 payload）----

    @Test fun rmgTableHasTwentyPayloadsFromFeed() {
        assertEquals(20, FirmwareSupport.RMG_PAYLOADS.size)
        assertTrue(FirmwareSupport.RMG_TABLE_SOURCE.contains("targets-v3.json"))
        // 全部只有 GhostLock/43499 这一条引擎；feed 里没有 KSU 版本号，
        // 驱动只记随载荷提供的工件，不编版本号。
        for (payload in FirmwareSupport.RMG_PAYLOADS) {
            assertEquals(FirmwareSupport.RMG_ENGINE, payload.engine)
            assertEquals(RootTool.ROOT_MY_GALAXY, payload.tool)
            assertEquals(FirmwareStatus.PORTED, payload.status)
            assertEquals(FirmwareSupport.RMG_DRIVER_NOTE, payload.driver)
            assertTrue(payload.exploitUrl.contains("cve-2026-43499-app.so"))
            assertTrue(payload.exploitSize > 0)
            assertTrue(payload.kernelsuUrl.contains("kernelsu/ksud-"))
            assertTrue(payload.kernelsuSize > 0)
            assertTrue(payload.models.isNotEmpty())
            assertTrue(payload.kernelVersions.isNotEmpty())
        }
        // 每条 payloadId 唯一。
        assertEquals(20, FirmwareSupport.RMG_PAYLOADS.map { it.payloadId }.toSet().size)
    }

    @Test fun rms24RowsCarryToolAndDriver() {
        // 本表 31 行不变，全部是 root-my-s24；驱动按 README 驱动版本表。
        assertEquals(31, FirmwareSupport.ENTRIES.size)
        assertTrue(FirmwareSupport.ENTRIES.all { it.tool == RootTool.ROOT_MY_S24 })
        val ezif = FirmwareSupport.ENTRIES.first { it.firmwareBuild == "S9280ZHS6EZJ1" }
        assertEquals("98260 UAF", ezif.engine)
        assertTrue(ezif.driver.contains("3.2.5"))
        val dzf2 = FirmwareSupport.ENTRIES.first { it.firmwareBuild == "S9280ZCS6DZF2" }
        assertEquals("GhostLock", dzf2.engine)
        assertTrue(dzf2.driver.contains("3.3.0"))
        val dzj3 = FirmwareSupport.ENTRIES.first { it.firmwareBuild == "S9280ZCS6DZJ3" }
        assertEquals("98260 UAF", dzj3.engine)
        assertTrue(dzj3.driver.contains("3.2.5"))
        assertTrue(FirmwareSupport.DRIVER_KDP_EXPERIMENTAL.contains("3.3.1"))
    }

    @Test fun kernelFullVersionParsing() {
        assertEquals("6.1.145", SystemFont.parseKernelFullVersion("6.1.145-android14-11-00001"))
        assertEquals("6.6.98", SystemFont.parseKernelFullVersion("6.6.98-android16"))
        assertEquals("5.15.189", SystemFont.parseKernelFullVersion("  5.15.189-android13 "))
        assertNull(SystemFont.parseKernelFullVersion("6.1-android14"))
        assertNull(SystemFont.parseKernelFullVersion(""))
    }

    @Test fun rmgMatchS926BWith6157() {
        // SM-S926B + 6.1.157 → root-my-galaxy（RMS24 表里没有 S926B）。
        val payload = FirmwareSupport.matchRmg(
            "SM-S926B", "S926BXXUEDZDR", "S926BXXUEDZDR", "6.1.157-android14-11-00001",
        )
        assertEquals("e2s-S926BXXUEDZDR", payload?.payloadId)
        assertEquals(true, payload?.requiresFreshP0Session)
        val rec = FirmwareSupport.recommendTool(
            "SM-S926B", "S926BXXUEDZDR", "S926BXXUEDZDR", "6.1.157-android14-11-00001",
        )
        assertEquals(RootTool.ROOT_MY_GALAXY, rec.recommendedTool)
        assertEquals(setOf(RootTool.ROOT_MY_GALAXY), rec.supportedTools)
        assertEquals("e2s-S926BXXUEDZDR", rec.rmgPayload?.payloadId)
        assertEquals(FirmwareStatus.PORTED, rec.status)
        assertTrue(rec.engine?.contains("43499") == true)
        assertEquals(FirmwareSupport.RMG_DRIVER_NOTE, rec.driver)
        assertTrue(rec.supportsCurrentFirmware)
        assertEquals("root-my-galaxy", rec.recommendedTool?.label)
    }

    @Test fun rmgMatchNeedsExactThreePartKernel() {
        // 同机型、内核三段不对 → 不命中；两段 major.minor 也不够。
        assertNull(
            FirmwareSupport.matchRmg("SM-S926B", "", "", "6.1.145-android14-11-00001"),
        )
        assertNull(
            FirmwareSupport.matchRmg("SM-S926B", "", "", "6.1-android14"),
        )
        assertNull(FirmwareSupport.matchRmg("SM-S926B", "", "", ""))
        // 机型不对也不命中。
        assertNull(
            FirmwareSupport.matchRmg("SM-S926C", "", "", "6.1.157-android14-11-00001"),
        )
        // 固件构建号不参与匹配：display/incremental 为空也能命中。
        assertEquals(
            "e2s-S926BXXUEDZDR",
            FirmwareSupport.matchRmg("SM-S926B", "", "", "6.1.157-android14-11-00001")?.payloadId,
        )
    }

    @Test fun rmgS25RowMatchesByModelAndFullKernel() {
        // S25 系列行：SM-S931B 只在 RMG feed 里，不在 RMS24 表里。
        val payload = FirmwareSupport.matchRmg(
            "SM-S931B", "S931BXXU1CZG1", "S931BXXU1CZG1", "6.6.98-android16",
        )
        assertEquals("galaxy-s25-series-2026-06-07", payload?.payloadId)
        val rec = FirmwareSupport.recommendTool(
            "SM-S931B", "S931BXXU1CZG1", "S931BXXU1CZG1", "6.6.98-android16",
        )
        assertEquals(RootTool.ROOT_MY_GALAXY, rec.recommendedTool)
        assertTrue(rec.supportsCurrentFirmware)
    }

    @Test fun bothToolsSupportS9280Dzf2() {
        // SM-S9280 DZF2 + 6.1.145 → 两者都支持：RMS24 已实测行 + RMG 载荷行；
        // 已实测优先，首选仍是 root-my-s24。
        val rec = FirmwareSupport.recommendTool(
            "SM-S9280",
            "UP1A.231005.007.S9280ZCS6DZF2", "S9280ZCS6DZF2", "6.1.145-android14-11",
        )
        assertEquals(RootTool.ROOT_MY_S24, rec.recommendedTool)
        assertEquals(setOf(RootTool.ROOT_MY_S24, RootTool.ROOT_MY_GALAXY), rec.supportedTools)
        assertEquals("两者都支持", rec.toolsLabel)
        assertEquals("S9280ZCS6DZF2", rec.rms24Entry?.firmwareBuild)
        assertEquals(FirmwareStatus.TESTED, rec.rms24Entry?.status)
        assertEquals("e3q-S9280ZCS6DZF2", rec.rmgPayload?.payloadId)
        assertEquals(FirmwareStatus.TESTED, rec.status)
        assertEquals("GhostLock", rec.engine)
        assertTrue(rec.driver?.contains("3.3.0") == true)
        assertTrue(rec.supportsCurrentFirmware)
    }

    @Test fun dzj3ExcludesRmgGhostLock() {
        // 锁死陷阱：SM-S9280 DZJ3 已修复 43499，RMG 的 e3q-S9280ZCS6DZF2 行
        // （机型 + 6.1.145 都对得上）绝不能被推荐；只剩 RMS24 的 98260 行。
        assertTrue(
            FirmwareSupport.isGhostLockPatched(
                "SM-S9280", "UP1A.231005.007.S9280ZCS6DZJ3", "S9280ZCS6DZJ3",
            ),
        )
        assertFalse(
            FirmwareSupport.isGhostLockPatched(
                "SM-S9280", "UP1A.231005.007.S9280ZCS6DZF2", "S9280ZCS6DZF2",
            ),
        )
        assertNull(
            FirmwareSupport.matchRmg(
                "SM-S9280", "UP1A.231005.007.S9280ZCS6DZJ3", "S9280ZCS6DZJ3",
                "6.1.145-android14-11",
            ),
        )
        // 同机型同内核、固件是 DZF2 时 RMG 正常命中——排除只针对 DZJ3。
        assertEquals(
            "e3q-S9280ZCS6DZF2",
            FirmwareSupport.matchRmg(
                "SM-S9280", "UP1A.231005.007.S9280ZCS6DZF2", "S9280ZCS6DZF2",
                "6.1.145-android14-11",
            )?.payloadId,
        )
        val rec = FirmwareSupport.recommendTool(
            "SM-S9280",
            "UP1A.231005.007.S9280ZCS6DZJ3", "S9280ZCS6DZJ3", "6.1.145-android14-11",
        )
        assertEquals(RootTool.ROOT_MY_S24, rec.recommendedTool)
        assertEquals(setOf(RootTool.ROOT_MY_S24), rec.supportedTools)
        assertEquals("root-my-s24", rec.toolsLabel)
        assertNull(rec.rmgPayload)
        assertEquals("S9280ZCS6DZJ3", rec.rms24Entry?.firmwareBuild)
        assertEquals(FirmwareStatus.CALIBRATED, rec.status)
        assertEquals("98260 UAF", rec.engine)
        assertTrue(rec.driver?.contains("3.2.5") == true)
        assertTrue(rec.supportsCurrentFirmware)
        assertTrue(rec.reason.contains("43499"))
    }

    @Test fun s9260Dzj3CandidateStaysInsideRms24() {
        // S9260 DZJ3 的跨机型候选只能是 RMS24 的 S9280 DZJ3 行，不能串到 RMG 去：
        // RMG 里根本没有 S9260，且 DZJ3 已修复 43499。
        val rec = FirmwareSupport.recommendTool(
            "SM-S9260",
            "UP1A.231005.007.S9260ZCS6DZJ3", "S9260ZCS6DZJ3", "6.1.145-android14-11",
        )
        assertEquals(RootTool.ROOT_MY_S24, rec.recommendedTool)
        assertEquals(setOf(RootTool.ROOT_MY_S24), rec.supportedTools)
        assertNull(rec.rmgPayload)
        assertNull(rec.ported)
        val cross = rec.crossModel
        assertEquals("S9280ZCS6DZJ3", cross?.entry?.firmwareBuild)
        assertEquals("DZJ3", cross?.suffix)
        assertEquals(RootTool.ROOT_MY_S24, cross?.entry?.tool)
        assertEquals("98260 UAF", cross?.entry?.engine)
        assertEquals(FirmwareStatus.CALIBRATED, rec.status)
        assertEquals("98260 UAF", rec.engine)
        // 候选是回退，不是本固件精确支持。
        assertFalse(rec.supportsCurrentFirmware)
        assertTrue(rec.reason.contains("跨机型候选"))
    }

    @Test fun recommendPrefersTestedOverCalibratedAndPorted() {
        // 一侧已实测、另一侧待验证/移植时选已实测（DZF2 已覆盖）；
        // 都没有已实测时，待验证胜过移植：S9360 CZG1 上 RMS24 是待验证行，
        // RMG 是移植行 → 首选 root-my-s24（待验证）。
        val s9360 = FirmwareSupport.recommendTool(
            "SM-S9360",
            "UP1A.231005.007.S9360ZCSCCZG1", "S9360ZCSCCZG1", "6.6.98-android16",
        )
        assertEquals(RootTool.ROOT_MY_S24, s9360.recommendedTool)
        assertEquals(setOf(RootTool.ROOT_MY_S24, RootTool.ROOT_MY_GALAXY), s9360.supportedTools)
        assertEquals(FirmwareStatus.CALIBRATED, s9360.status)
        assertEquals("S9360ZCSCCZG1", s9360.rms24Entry?.firmwareBuild)
        assertEquals("pa2q-S9360ZCSCCZG1", s9360.rmgPayload?.payloadId)
        assertTrue(s9360.supportsCurrentFirmware)
        // 两边都没有时返回均不支持。
        val none = FirmwareSupport.recommendTool(
            "SM-A5560", "A5560ZCS6DZF2", "A5560ZCS6DZF2", "6.1.118-android14",
        )
        assertNull(none.recommendedTool)
        assertTrue(none.supportedTools.isEmpty())
        assertEquals("均不支持", none.toolsLabel)
        assertNull(none.status)
        assertFalse(none.supportsCurrentFirmware)
    }

    @Test fun rmgDoesNotChangeCheckDeviceLevels() {
        // RMG 只进推荐层，不进 checkDevice：SM-S926B 在 RMS24 表里没有，
        // 哪怕 RMG 有它的载荷，checkDevice 仍是 UNSUPPORTED（行为保持不变）。
        val s926b = SystemFont.checkDevice(
            "samsung", "SM-S926B",
            "UP1A.231005.007.S926BXXUEDZDR", "S926BXXUEDZDR", "6.1.157-android14-11",
        )
        assertEquals(DeviceSupport.UNSUPPORTED, s926b.support)
        assertEquals(FirmwareMatchKind.UNKNOWN_MODEL, s926b.firmware?.kind)
        // SM-S9280 DZJ3 仍是 RMS24 的待验证行，不受 RMG 排除规则以外的任何影响。
        val dzj3 = SystemFont.checkDevice(
            "samsung", "SM-S9280",
            "UP1A.231005.007.S9280ZCS6DZJ3", "S9280ZCS6DZJ3", "6.1.145-android14-11",
        )
        assertEquals(DeviceSupport.CALIBRATED, dzj3.support)
        assertEquals(FirmwareMatchKind.EXACT_CALIBRATED, dzj3.firmware?.kind)
        assertEquals("98260 UAF", dzj3.firmware?.entry?.engine)
    }
}
