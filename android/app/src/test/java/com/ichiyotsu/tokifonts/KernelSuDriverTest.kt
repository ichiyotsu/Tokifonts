package com.ichiyotsu.tokifonts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** KernelSU 驱动选择（纯逻辑）：硬规则必须拦截，不要只写文案提醒。 */
class KernelSuDriverTest {
    @Test fun driver98260DefaultsTo32525() {
        val decision = selectKsudDriver("98260 UAF", "6.1.162")
        assertTrue(decision is DriverDecision.Allowed)
        assertEquals(KsudDriver.V32525, (decision as DriverDecision.Allowed).driver)
    }

    @Test fun driver330ForbiddenOn6162() {
        // 3.3.0 在 6.1.162 上会让 system_server 崩溃循环——无论怎么要求都禁。
        val decision = selectKsudDriver("98260 UAF", "6.1.162", requested = KsudDriver.V32601)
        assertTrue(decision is DriverDecision.Forbidden)
        assertTrue((decision as DriverDecision.Forbidden).reason.contains("崩溃循环"))
    }

    @Test fun driverGhostLockOldFirmwareUses330() {
        val decision = selectKsudDriver("GhostLock", "6.1.145")
        assertTrue(decision is DriverDecision.Allowed)
        assertEquals(KsudDriver.V32601, (decision as DriverDecision.Allowed).driver)
    }

    @Test fun driverGhostLockForbiddenOn6162() {
        // 6.1.162 上没有 GhostLock 行（DZJ3 已修复 43499）。
        val decision = selectKsudDriver("GhostLock", "6.1.162")
        assertTrue(decision is DriverDecision.Forbidden)
    }

    @Test fun driverKdpNeverDefault() {
        val decision = selectKsudDriver("98260 UAF", "6.1.162", requested = KsudDriver.V331_KDP)
        assertTrue(decision is DriverDecision.Forbidden)
        // 没要求时也不会默认选 KDP。
        val default = selectKsudDriver("98260 UAF", "6.1.162")
        assertTrue((default as DriverDecision.Allowed).driver != KsudDriver.V331_KDP)
    }

    @Test fun driverRmgIsBundledWithoutVersion() {
        val decision = selectKsudDriver("GhostLock (CVE-2026-43499)", "6.1.145")
        assertTrue(decision is DriverDecision.Bundled)
    }

    @Test fun driverNullEngineForbidden() {
        val decision = selectKsudDriver(null, "6.1.145")
        assertTrue(decision is DriverDecision.Forbidden)
    }

    @Test fun driverNullKernelForbidden() {
        val decision = selectKsudDriver("98260 UAF", null)
        assertTrue(decision is DriverDecision.Forbidden)
    }
}
