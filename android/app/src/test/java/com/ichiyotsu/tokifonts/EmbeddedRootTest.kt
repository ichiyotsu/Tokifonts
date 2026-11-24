package com.ichiyotsu.tokifonts

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** 保守内嵌一键 root 的预检规则（纯逻辑）：任何一项不过就停在向导里。 */
class EmbeddedRootTest {
    @Before fun useBuiltinTables() {
        FirmwareSupport.resetTables()
    }

    @After fun resetTables() {
        FirmwareSupport.resetTables()
    }

    private fun allAssetsPresent(): Map<String, Boolean> =
        EmbeddedAssets.ALL.associateWith { true }

    private fun recommendationForDzj3(): ToolRecommendation =
        FirmwareSupport.recommendTool(
            "SM-S9280",
            "S9280ZCS6DZJ3",
            "S9280ZCS6DZJ3",
            "6.1.145-android14-11-00001",
        )

    @Test fun precheckNeedsDeviceCheckFirst() {
        val checks = embeddedRootPrecheck(
            recommendation = null,
            shizukuRunning = true,
            calibratedConfirmed = true,
            guard = EmbeddedRootAttemptGuard(),
            embeddedAssetsPresent = allAssetsPresent(),
        )
        assertFalse(checks.allPassed())
        assertTrue(checks.any { !it.passed && it.label == "设备已检查" })
    }

    @Test fun precheckGhostLockBlockedByMissingPerTagPayload() {
        // S9280 DZF2 是 GhostLock 已实测行，但按固件分的 cve-2026-43499-<TAG>
        // 不在上游公开仓库里，本版取不到——预检必须拦截，不能放行。
        val rec = FirmwareSupport.recommendTool(
            "SM-S9280",
            "S9280ZCS6DZF2",
            "S9280ZCS6DZF2",
            "6.1.145-android14-11-00001",
        )
        val checks = embeddedRootPrecheck(
            recommendation = rec,
            shizukuRunning = true,
            calibratedConfirmed = true,
            guard = EmbeddedRootAttemptGuard(),
            embeddedAssetsPresent = allAssetsPresent(),
        )
        assertFalse(checks.allPassed())
        val ghostLock = checks.first { it.label == "GhostLock 载荷可用" }
        assertFalse(ghostLock.passed)
        assertTrue(ghostLock.reason.contains("cve-2026-43499-<TAG>"))
    }

    @Test fun precheck98260Dzj3PassesWhenConfirmed() {
        // DZJ3：98260 待验证行，用户已额外确认 + Shizuku 在运行 + 产物齐全 → 全过。
        val rec = recommendationForDzj3()
        assertTrue(rec.rms24Entry?.engine == "98260 UAF")
        val checks = embeddedRootPrecheck(
            recommendation = rec,
            shizukuRunning = true,
            calibratedConfirmed = true,
            guard = EmbeddedRootAttemptGuard(),
            embeddedAssetsPresent = allAssetsPresent(),
        )
        assertTrue(checks.allPassed())
    }

    @Test fun precheckCalibratedNeedsExplicitConfirmation() {
        val rec = recommendationForDzj3()
        val checks = embeddedRootPrecheck(
            recommendation = rec,
            shizukuRunning = true,
            calibratedConfirmed = false,
            guard = EmbeddedRootAttemptGuard(),
            embeddedAssetsPresent = allAssetsPresent(),
        )
        assertFalse(checks.allPassed())
        assertTrue(checks.any { !it.passed && it.label.contains("待验证") })
    }

    @Test fun precheckRequiresShizukuRunning() {
        val rec = recommendationForDzj3()
        val checks = embeddedRootPrecheck(
            recommendation = rec,
            shizukuRunning = false,
            calibratedConfirmed = true,
            guard = EmbeddedRootAttemptGuard(),
            embeddedAssetsPresent = allAssetsPresent(),
        )
        assertFalse(checks.allPassed())
        assertTrue(checks.any { !it.passed && it.label == "Shizuku 正在运行" })
    }

    @Test fun precheckMissingAssetNamesIt() {
        val rec = recommendationForDzj3()
        val present = allAssetsPresent() + ("embed/samtimer/ksud-32525" to false)
        val checks = embeddedRootPrecheck(
            recommendation = rec,
            shizukuRunning = true,
            calibratedConfirmed = true,
            guard = EmbeddedRootAttemptGuard(),
            embeddedAssetsPresent = present,
        )
        assertFalse(checks.allPassed())
        val assets = checks.first { it.label == "内嵌产物齐全" }
        assertTrue(assets.reason.contains("embed/samtimer/ksud-32525"))
    }

    @Test fun precheckRmgRecommendationNotEmbedded() {
        // SM-S921B + 6.1.157：只有 RMG 载荷命中，.so 能否进本 App 的加载链路未核实，
        // 内嵌不直接跑。
        val rec = FirmwareSupport.recommendTool(
            "SM-S921B",
            "",
            "",
            "6.1.157-android14-11-00001",
        )
        assertTrue(rec.recommendedTool == RootTool.ROOT_MY_GALAXY)
        val checks = embeddedRootPrecheck(
            recommendation = rec,
            shizukuRunning = true,
            calibratedConfirmed = true,
            guard = EmbeddedRootAttemptGuard(),
            embeddedAssetsPresent = allAssetsPresent(),
        )
        assertFalse(checks.allPassed())
        assertTrue(checks.any { !it.passed && it.reason.contains("Root-My-Galaxy") })
    }

    @Test fun guardBlocksSecondAttemptThisBoot() {
        val rec = recommendationForDzj3()
        val guard = EmbeddedRootAttemptGuard()
        guard.markAttempted()
        val checks = embeddedRootPrecheck(
            recommendation = rec,
            shizukuRunning = true,
            calibratedConfirmed = true,
            guard = guard,
            embeddedAssetsPresent = allAssetsPresent(),
        )
        assertFalse(checks.allPassed())
        assertTrue(checks.any { !it.passed && it.label == "本次开机还没跑过" })
    }

    @Test fun guardBlocksAfter98260Failure() {
        val rec = recommendationForDzj3()
        val guard = EmbeddedRootAttemptGuard()
        guard.markFailed98260()
        val checks = embeddedRootPrecheck(
            recommendation = rec,
            shizukuRunning = true,
            calibratedConfirmed = true,
            guard = guard,
            embeddedAssetsPresent = allAssetsPresent(),
        )
        assertFalse(checks.allPassed())
        assertTrue(checks.any { !it.passed && it.reason.contains("重启手机") })
    }

    @Test fun guardResetsAfterReboot() {
        var clock = 1000L
        val guard = EmbeddedRootAttemptGuard(nowElapsedRealtimeMs = { clock })
        guard.markAttempted()
        guard.markFailed98260()
        // 重启后 elapsedRealtime 从小开始：守卫复位。
        clock = 500L
        guard.resetIfRebooted()
        val rec = recommendationForDzj3()
        val checks = embeddedRootPrecheck(
            recommendation = rec,
            shizukuRunning = true,
            calibratedConfirmed = true,
            guard = guard,
            embeddedAssetsPresent = allAssetsPresent(),
        )
        assertTrue(checks.allPassed())
    }

    @Test fun embeddedAssetsInventoryMatchesDesignDoc() {
        // 清单与 docs/embed-inventory.md §A 一致：14 个文件。
        assertEquals(14, EmbeddedAssets.ALL.size)
        assertTrue("embed/cve-2026-43499" in EmbeddedAssets.ALL)
        assertTrue("embed/samtimer/ksud-32525" in EmbeddedAssets.ALL)
        assertTrue("embed/samtimer/targets/S9280ZCS6DZJ3/libtimer" in EmbeddedAssets.ALL)
        assertTrue("embed/licenses/gpl-3.0.txt" in EmbeddedAssets.ALL)
    }
}
