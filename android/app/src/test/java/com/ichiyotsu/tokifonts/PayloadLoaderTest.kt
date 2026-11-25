package com.ichiyotsu.tokifonts

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 载荷加载器：计划构造 + 状态机（假执行器覆盖，真机路径不进单测）。 */
class PayloadLoaderTest {

    /** 假特权执行器：按开关决定每一步成败，并记录 stage / 命令 / 清理。 */
    private class FakeExecutor(
        var failStageAsset: String? = null,
        var libtimerExit: Int = 0,
        var libtimerTimeout: Boolean = false,
        var suOk: Boolean = true,
        var moduleCount: Int = 1,
        var failRemove: Boolean = false,
    ) : PrivilegedExecutor {
        val staged = mutableMapOf<String, ByteArray>()
        val removed = mutableListOf<String>()
        val commands = mutableListOf<String>()

        override suspend fun stageFile(assetPath: String, destPath: String, executable: Boolean): Boolean {
            if (assetPath == failStageAsset) return false
            staged[destPath] = byteArrayOf(1)
            return true
        }

        override suspend fun runCommand(command: String, timeoutMs: Int): ShellResult {
            commands += command
            return when (command) {
                PayloadLoader.LIBTIMER_COMMAND ->
                    if (libtimerTimeout) ShellResult(-1, "", "", timedOut = true)
                    else ShellResult(libtimerExit, "", if (libtimerExit != 0) "boom" else "")
                PayloadLoader.VERIFY_SU_COMMAND ->
                    if (suOk) ShellResult(0, "uid=0(root) gid=0", "") else ShellResult(0, "uid=2000(shell)", "")
                PayloadLoader.VERIFY_MODULE_COMMAND ->
                    ShellResult(0, moduleCount.toString(), "")
                else -> ShellResult(0, "", "")
            }
        }

        override suspend fun readFile(path: String, maxBytes: Int): String? = null

        override suspend fun removePath(path: String): Boolean {
            removed += path
            staged.remove(path)
            return !failRemove
        }
    }

    private fun assetsPresent(): (String) -> ByteArray? = { byteArrayOf(1) }

    private fun dzj3Plan(): LoadPlan {
        val outcome = buildLoadPlan("98260 UAF", "6.1.145", "S9280ZCS6DZJ3")
        assertTrue(outcome is LoadPlanOutcome.Ready)
        return (outcome as LoadPlanOutcome.Ready).plan
    }

    private fun runPlan(
        plan: LoadPlan,
        executor: FakeExecutor,
        readAsset: (String) -> ByteArray? = assetsPresent(),
    ): Triple<LoadOutcome, List<EmbeddedRootState>, Int> {
        val states = mutableListOf<EmbeddedRootState>()
        var exploitStarts = 0
        val outcome = runBlocking {
            runLoadPlan(plan, executor, readAsset, onState = { states += it }) {
                exploitStarts++
            }
        }
        return Triple(outcome, states, exploitStarts)
    }

    @Test fun planDzj3UsesPerTargetLibtimerAnd32525() {
        val plan = dzj3Plan()
        assertEquals(KsudDriver.V32525, plan.driver)
        assertEquals("embed/samtimer/ksud-32525", plan.ksudAsset)
        assertEquals("embed/samtimer/targets/S9280ZCS6DZJ3/libtimer", plan.libtimerAsset)
        assertEquals(4, plan.stages.size)
        assertEquals(PayloadLoader.KSUD_STAGE_PATH, plan.stages.last().destPath)
        assertEquals("embed/samtimer/ksud-32525", plan.stages.last().assetPath)
        assertTrue(plan.stages.all { it.executable })
    }

    @Test fun planCommonFirmwareFallsBackToCommonLibtimer() {
        val outcome = buildLoadPlan("98260 UAF", "6.1.162", "S928BXXU6EZIF")
        assertTrue(outcome is LoadPlanOutcome.Ready)
        val plan = (outcome as LoadPlanOutcome.Ready).plan
        assertEquals("embed/samtimer/libtimer", plan.libtimerAsset)
        assertEquals(KsudDriver.V32525, plan.driver)
    }

    @Test fun planS721UsesItsOwnTargetLibtimer() {
        val outcome = buildLoadPlan("98260 UAF", "6.1.162", "S721BXXUEEZIF")
        assertTrue(outcome is LoadPlanOutcome.Ready)
        assertEquals(
            "embed/samtimer/targets/S721BXXUEEZIF/libtimer",
            (outcome as LoadPlanOutcome.Ready).plan.libtimerAsset,
        )
    }

    @Test fun planBlockedWhenDriverCannotBeSelected() {
        // 6.1.162 上没有 GhostLock 行。
        val ghost = buildLoadPlan("GhostLock", "6.1.162", "S9280ZCS6DZJ3")
        assertTrue(ghost is LoadPlanOutcome.Blocked)
        // 没引擎的移植表不替它选驱动。
        val noEngine = buildLoadPlan(null, "6.1.145", "S9280ZCS6DZJ3")
        assertTrue(noEngine is LoadPlanOutcome.Blocked)
    }

    @Test fun ksud331SrcIsNeverSelectedForAnyDriver() {
        // 源码编出的 ksud-331-src 内嵌零个 .ko，接进来 late-load 只会报
        // "No kernel module found"——映射里绝不能出现它。
        for (driver in KsudDriver.entries) {
            assertNotEquals("embed/samtimer/ksud-331-src", PayloadLoader.ksudAssetForDriver(driver))
        }
        assertEquals(null, PayloadLoader.ksudAssetForDriver(KsudDriver.V331_KDP))
    }

    @Test fun ksuPackageNameStaysUpstreamDefault() {
        // 预编译 umh_action.sh 写死 --package-name me.weishu.kernelsu，
        // 预编译 .ko 也是按官方 Manager 身份绑定的，改了就断链。
        assertEquals("me.weishu.kernelsu", PayloadLoader.KSU_PACKAGE_NAME)
    }

    @Test fun happyPathRunsAllStatesAndVerifies() {
        val executor = FakeExecutor()
        var exploitStarts = 0
        val states = mutableListOf<EmbeddedRootState>()
        val outcome = runBlocking {
            runLoadPlan(
                dzj3Plan(), executor, assetsPresent(),
                onState = { states += it },
                onExploitStart = { exploitStarts++ },
            )
        }
        assertTrue(outcome is LoadOutcome.Success)
        assertEquals(
            listOf(
                EmbeddedRootState.LOADING,
                EmbeddedRootState.RUNNING,
                EmbeddedRootState.VERIFYING,
                EmbeddedRootState.SUCCEEDED,
            ),
            states,
        )
        assertEquals(1, exploitStarts)
        assertEquals(4, executor.staged.size)
        assertTrue(executor.commands.contains(PayloadLoader.LIBTIMER_COMMAND))
        assertTrue(executor.commands.contains(PayloadLoader.VERIFY_SU_COMMAND))
        assertTrue(executor.commands.contains(PayloadLoader.VERIFY_MODULE_COMMAND))
    }

    @Test fun stagingFailureIsNotA98260FailureAndCleansUp() {
        val executor = FakeExecutor(failStageAsset = "embed/samtimer/uhexec")
        var exploitStarts = 0
        val states = mutableListOf<EmbeddedRootState>()
        val outcome = runBlocking {
            runLoadPlan(
                dzj3Plan(), executor, assetsPresent(),
                onState = { states += it },
                onExploitStart = { exploitStarts++ },
            )
        }
        assertTrue(outcome is LoadOutcome.Failure)
        val failure = outcome as LoadOutcome.Failure
        assertFalse(failure.markFailed98260)
        assertTrue(failure.cleanedUp)
        assertEquals(0, exploitStarts)
        assertEquals(listOf(EmbeddedRootState.LOADING, EmbeddedRootState.FAILED), states)
        // 已 stage 的第一项也被清掉。
        assertTrue(executor.removed.contains("${PayloadLoader.STAGE_DIR}/libtimer"))
        assertTrue(executor.staged.isEmpty())
    }

    @Test fun missingAssetIsNotA98260Failure() {
        val executor = FakeExecutor()
        val outcome = runBlocking {
            runLoadPlan(dzj3Plan(), executor, readAsset = { null }, onState = {})
        }
        assertTrue(outcome is LoadOutcome.Failure)
        assertFalse((outcome as LoadOutcome.Failure).markFailed98260)
    }

    @Test fun libtimerNonZeroExitIsA98260Failure() {
        val executor = FakeExecutor(libtimerExit = 3)
        val (outcome, states, _) = runPlan(dzj3Plan(), executor)
        assertTrue(outcome is LoadOutcome.Failure)
        val failure = outcome as LoadOutcome.Failure
        assertTrue(failure.markFailed98260)
        assertTrue(failure.cleanedUp)
        assertTrue(failure.reason.contains("libtimer"))
        assertEquals(
            listOf(EmbeddedRootState.LOADING, EmbeddedRootState.RUNNING, EmbeddedRootState.FAILED),
            states,
        )
        assertTrue(executor.staged.isEmpty())
    }

    @Test fun libtimerTimeoutIsA98260Failure() {
        val executor = FakeExecutor(libtimerTimeout = true)
        val (outcome, _, _) = runPlan(dzj3Plan(), executor)
        assertTrue(outcome is LoadOutcome.Failure)
        assertTrue((outcome as LoadOutcome.Failure).markFailed98260)
    }

    @Test fun verifySuFailureIsA98260Failure() {
        val executor = FakeExecutor(suOk = false)
        val (outcome, states, _) = runPlan(dzj3Plan(), executor)
        assertTrue(outcome is LoadOutcome.Failure)
        assertTrue((outcome as LoadOutcome.Failure).markFailed98260)
        assertEquals(
            listOf(
                EmbeddedRootState.LOADING,
                EmbeddedRootState.RUNNING,
                EmbeddedRootState.VERIFYING,
                EmbeddedRootState.FAILED,
            ),
            states,
        )
    }

    @Test fun verifyModuleMissingIsA98260Failure() {
        val executor = FakeExecutor(moduleCount = 0)
        val (outcome, _, _) = runPlan(dzj3Plan(), executor)
        assertTrue(outcome is LoadOutcome.Failure)
        assertTrue((outcome as LoadOutcome.Failure).markFailed98260)
    }

    @Test fun cleanupFailureIsReported() {
        val executor = FakeExecutor(libtimerExit = 1, failRemove = true)
        val (outcome, _, _) = runPlan(dzj3Plan(), executor)
        assertTrue(outcome is LoadOutcome.Failure)
        assertFalse((outcome as LoadOutcome.Failure).cleanedUp)
    }
}
