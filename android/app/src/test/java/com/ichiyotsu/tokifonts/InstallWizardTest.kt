package com.ichiyotsu.tokifonts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InstallWizardTest {
    private fun state(
        shizukuInstalled: Boolean? = null,
        recommendation: ToolRecommendation? = null,
        installedRootTools: Set<RootTool> = emptySet(),
        root: Boolean? = null,
        moduleState: SystemFontModuleState = SystemFontModuleState.NOT_GENERATED,
        installAllowed: Boolean = false,
        working: Boolean = false,
        confirmedRootTools: Set<RootTool> = emptySet(),
    ) = installWizardState(
        shizukuInstalled, recommendation, installedRootTools, root,
        moduleState, installAllowed, working, confirmedRootTools,
    )

    private fun recommend(model: String, display: String, kernel: String) =
        FirmwareSupport.recommendTool(model, display, display, kernel)

    // ---- 包名常量 ----

    @Test fun shizukuPackageName() {
        // 来源：本仓库 InstallSolutions.kt 里 Shizuku 教程第 1 步原文；
        // rms24-readme.md 里没有这个包名，别以它为据。
        assertEquals("moe.shizuku.privileged.api", WizardApps.SHIZUKU_PACKAGE)
    }

    @Test fun kernelsuManagerPackageName() {
        assertEquals("me.weishu.kernelsu", WizardApps.KERNELSU_MANAGER_PACKAGE)
    }

    @Test fun rootMyS24PackageNameIsUpstreamSourcePath() {
        // 按上游仓库源码包路径 cn/nanoturtle/rootmys9280 推断，未经真机核对。
        assertEquals("cn.nanoturtle.rootmys9280", WizardApps.ROOT_MY_S24_PACKAGE)
        assertEquals(
            WizardApps.ROOT_MY_S24_PACKAGE,
            WizardApps.launchPackageFor(RootTool.ROOT_MY_S24),
        )
    }

    @Test fun rootMyGalaxyPackageNameIsVerifiedFromUpstreamBuildFile() {
        // 已从上游 app/build.gradle.kts 核实（v0.2.65，applicationId 与 namespace 一致）；
        // 只剩真机启动未核对，启动失败时界面一律回落到“去下载”。
        assertEquals("dev.busung.s25uroot", WizardApps.ROOT_MY_GALAXY_PACKAGE)
        assertEquals(
            WizardApps.ROOT_MY_GALAXY_PACKAGE,
            WizardApps.launchPackageFor(RootTool.ROOT_MY_GALAXY),
        )
    }

    // ---- 步骤状态机：前置与锁定 ----

    @Test fun allUnknownLocksLaterSteps() {
        val s = state()
        assertEquals(WizardStepState.IN_PROGRESS, s.shizuku.state)
        assertEquals(WizardStepState.TODO, s.rootTool.state)
        assertEquals(WizardStepState.TODO, s.kernelSu.state)
        assertEquals(WizardStepState.TODO, s.module.state)
    }

    @Test fun shizukuMissingBlocksStep1() {
        val s = state(shizukuInstalled = false)
        assertEquals(WizardStepState.BLOCKED, s.shizuku.state)
        assertTrue(s.shizuku.note.contains("还没装 Shizuku"))
        assertEquals(WizardStepState.TODO, s.rootTool.state)
    }

    @Test fun shizukuDoneUnlocksStep2ButWaitsForDeviceCheck() {
        val s = state(shizukuInstalled = true)
        assertEquals(WizardStepState.DONE, s.shizuku.state)
        assertEquals(WizardStepState.TODO, s.rootTool.state)
        assertTrue(s.rootTool.note.contains("设备检查"))
    }

    @Test fun step2BlockedWhenNeitherToolSupports() {
        val rec = recommend("SM-S9999", "S9999ZCS6DZF2", "6.1.145-android14-11-00001")
        assertNull(rec.recommendedTool)
        val s = state(shizukuInstalled = true, recommendation = rec)
        assertEquals(WizardStepState.BLOCKED, s.rootTool.state)
        assertEquals(WizardStepState.TODO, s.kernelSu.state)
    }

    @Test fun step2InProgressUntilRecommendedToolInstalled() {
        val rec = recommend("SM-S9280", "UP1A.231005.007.S9280ZCS6DZF2", "6.1.145-android14-11-00001")
        assertEquals(RootTool.ROOT_MY_S24, rec.recommendedTool)
        val s = state(shizukuInstalled = true, recommendation = rec)
        assertEquals(WizardStepState.IN_PROGRESS, s.rootTool.state)
        assertTrue(s.rootTool.note.contains("root-my-s24"))
    }

    @Test fun step2DoneWhenRecommendedToolInstalled() {
        val rec = recommend("SM-S9280", "UP1A.231005.007.S9280ZCS6DZF2", "6.1.145-android14-11-00001")
        val s = state(
            shizukuInstalled = true, recommendation = rec,
            installedRootTools = setOf(RootTool.ROOT_MY_S24),
        )
        assertEquals(WizardStepState.DONE, s.rootTool.state)
        assertEquals(WizardStepState.IN_PROGRESS, s.kernelSu.state)
    }

    @Test fun step2DoneWhenOtherSupportedToolInstalled() {
        // 两者都支持时，装了另一个工具也能继续。
        val rec = recommend("SM-S9280", "UP1A.231005.007.S9280ZCS6DZF2", "6.1.145-android14-11-00001")
        assertEquals("两者都支持", rec.toolsLabel)
        val s = state(
            shizukuInstalled = true, recommendation = rec,
            installedRootTools = setOf(RootTool.ROOT_MY_GALAXY),
        )
        assertEquals(WizardStepState.DONE, s.rootTool.state)
        assertTrue(s.rootTool.note.contains("root-my-galaxy"))
    }

    @Test fun step2DoneByUserConfirmationSaysSo() {
        // 包名查不到的工具靠用户手动确认放行，说明里写明是用户确认的。
        val rec = recommend("SM-S928B", "S928BXXS6DZF2", "6.1.145-android14-11-00001")
        assertEquals(RootTool.ROOT_MY_GALAXY, rec.recommendedTool)
        val s = state(
            shizukuInstalled = true, recommendation = rec,
            confirmedRootTools = setOf(RootTool.ROOT_MY_GALAXY),
        )
        assertEquals(WizardStepState.DONE, s.rootTool.state)
        assertTrue(s.rootTool.note.contains("你确认的"))
    }

    @Test fun step3LockedEvenWhenRootTrueIfStep2NotDone() {
        val rec = recommend("SM-S9280", "UP1A.231005.007.S9280ZCS6DZF2", "6.1.145-android14-11-00001")
        val s = state(shizukuInstalled = true, recommendation = rec, root = true)
        assertEquals(WizardStepState.IN_PROGRESS, s.rootTool.state)
        assertEquals(WizardStepState.TODO, s.kernelSu.state)
        assertEquals(WizardStepState.TODO, s.module.state)
    }

    @Test fun step3BlockedWhenRootMissing() {
        val rec = recommend("SM-S9280", "UP1A.231005.007.S9280ZCS6DZF2", "6.1.145-android14-11-00001")
        val s = state(
            shizukuInstalled = true, recommendation = rec,
            installedRootTools = setOf(RootTool.ROOT_MY_S24), root = false,
        )
        assertEquals(WizardStepState.DONE, s.rootTool.state)
        assertEquals(WizardStepState.BLOCKED, s.kernelSu.state)
        assertTrue(s.kernelSu.note.contains("没有检测到 root"))
    }

    @Test fun step4BlockedWhenInstallNotAllowed() {
        val rec = recommend("SM-S9280", "UP1A.231005.007.S9280ZCS6DZF2", "6.1.145-android14-11-00001")
        val s = state(
            shizukuInstalled = true, recommendation = rec,
            installedRootTools = setOf(RootTool.ROOT_MY_S24), root = true,
            installAllowed = false,
        )
        assertEquals(WizardStepState.DONE, s.kernelSu.state)
        assertEquals(WizardStepState.BLOCKED, s.module.state)
    }

    @Test fun step4InProgressWhenReadyAndWorkingWhenRunning() {
        val rec = recommend("SM-S9280", "UP1A.231005.007.S9280ZCS6DZF2", "6.1.145-android14-11-00001")
        val ready = state(
            shizukuInstalled = true, recommendation = rec,
            installedRootTools = setOf(RootTool.ROOT_MY_S24), root = true,
            installAllowed = true,
        )
        assertEquals(WizardStepState.IN_PROGRESS, ready.module.state)
        val running = state(
            shizukuInstalled = true, recommendation = rec,
            installedRootTools = setOf(RootTool.ROOT_MY_S24), root = true,
            installAllowed = true, working = true,
        )
        assertEquals(WizardStepState.IN_PROGRESS, running.module.state)
        assertTrue(running.module.note.contains("正在换字体"))
    }

    @Test fun step4DoneWhenModuleInstalled() {
        val rec = recommend("SM-S9280", "UP1A.231005.007.S9280ZCS6DZF2", "6.1.145-android14-11-00001")
        val s = state(
            shizukuInstalled = true, recommendation = rec,
            installedRootTools = setOf(RootTool.ROOT_MY_S24), root = true,
            installAllowed = true, moduleState = SystemFontModuleState.INSTALLED,
        )
        assertEquals(WizardStepState.DONE, s.module.state)
    }

    // ---- 第 2 步展示映射 ----

    @Test fun level0ExactTestedShowsEngineAndDriver() {
        val rec = recommend("SM-S9280", "UP1A.231005.007.S9280ZCS6DZF2", "6.1.145-android14-11-00001")
        val display = rootToolStepDisplay(rec)!!
        assertEquals(RootTool.ROOT_MY_S24, display.recommendedTool)
        assertEquals("已实测", display.statusText)
        assertEquals("GhostLock", display.engineText)
        assertEquals("KernelSU 3.3.0（实验）", display.driverText)
        assertTrue(display.supportsCurrentFirmware)
        assertEquals("两者都支持", display.toolsLabel)
        assertNotNull(display.otherTool)
        assertEquals(RootTool.ROOT_MY_GALAXY, display.otherTool)
        assertFalse(display.requiresFreshP0Session)
    }

    @Test fun level2RmgShowsEngineAndDriver() {
        val rec = recommend("SM-S928B", "S928BXXS6DZF2", "6.1.145-android14-11-00001")
        val display = rootToolStepDisplay(rec)!!
        assertEquals(RootTool.ROOT_MY_GALAXY, display.recommendedTool)
        assertEquals("移植载荷、未实测", display.statusText)
        assertEquals(FirmwareSupport.RMG_ENGINE, display.engineText)
        assertEquals(FirmwareSupport.RMG_DRIVER_NOTE, display.driverText)
        assertTrue(display.supportsCurrentFirmware)
    }

    @Test fun level3PortedTableNeverInventsEngineOrDriver() {
        // 第 3 级（RMS24 移植载荷表）：engine/driver 为 null，界面只写兜底文案，
        // 绝不编造引擎/驱动值。
        val rec = recommend("SM-S9210", "S9210ZCS6DZE2", "6.1.145-android14-11-00001")
        assertNull(rec.engine)
        assertNull(rec.driver)
        assertEquals(RootTool.ROOT_MY_S24, rec.recommendedTool)
        assertFalse(rec.supportsCurrentFirmware)
        val display = rootToolStepDisplay(rec)!!
        assertEquals("以工具内实际载荷为准", display.engineText)
        assertEquals("以工具内实际载荷为准", display.driverText)
        assertFalse(display.engineText.contains("GhostLock"))
        assertFalse(display.engineText.contains("98260"))
        assertFalse(display.driverText.contains("KernelSU 3"))
        assertFalse(display.driverText.contains("GhostLock"))
        assertEquals("移植载荷、未实测", display.statusText)
    }

    @Test fun level4CrossModelStaysInsideRms24() {
        val rec = recommend("SM-S9260", "S9260ZHS6DZH3", "6.1.145-android14-11-00001")
        assertNotNull(rec.crossModel)
        assertEquals(RootTool.ROOT_MY_S24, rec.recommendedTool)
        assertEquals("GhostLock", rec.engine)
        assertFalse(rec.supportsCurrentFirmware)
        val display = rootToolStepDisplay(rec)!!
        assertEquals("GhostLock", display.engineText)
        assertNull(display.otherTool)
    }

    @Test fun dzj3ExcludesRmgLeavingOnlyRms24() {
        // DZJ3 已修复 CVE-2026-43499：RMG 的 GhostLock 载荷不可用，只剩 RMS24 的 98260 行。
        val rec = recommend("SM-S9280", "S9280ZCS6DZJ3", "6.1.145-android14-11-00001")
        assertEquals(RootTool.ROOT_MY_S24, rec.recommendedTool)
        assertEquals(setOf(RootTool.ROOT_MY_S24), rec.supportedTools)
        assertEquals("98260 UAF", rec.engine)
        val display = rootToolStepDisplay(rec)!!
        assertNull(display.otherTool)
        assertTrue(display.reason.contains("43499"))
    }

    @Test fun noRecommendationMapsToNull() {
        assertNull(rootToolStepDisplay(null))
    }

    @Test fun stepStateLabels() {
        assertEquals("已完成", WizardStepState.DONE.label)
        assertEquals("进行中", WizardStepState.IN_PROGRESS.label)
        assertEquals("待办", WizardStepState.TODO.label)
        assertEquals("被拦住", WizardStepState.BLOCKED.label)
    }
}
