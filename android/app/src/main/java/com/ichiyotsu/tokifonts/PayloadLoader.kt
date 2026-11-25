package com.ichiyotsu.tokifonts

/**
 * 98260 载荷加载器：预检 → staging → 执行 → 校验 → 失败回滚 的完整链。
 *
 * 本文件是纯逻辑（无 Android 依赖，可单测）：真正碰特权进程的操作都经过
 * [PrivilegedExecutor] 接口；真机上由 [UserServiceGlue] 的
 * `UserServiceExecutor`（Shizuku UserService，见 [TokiFontsRootService]）
 * 实现，单测里用假执行器覆盖。
 *
 * 链路事实（来自 `samtimer.md` §2.4 + 预编译 `umh_action.sh` 原文，
 * 不是推测——脚本与二进制都不要动）：
 * - staging 目标是写死的：libtimer / uhexec / umh_action.sh →
 *   `/data/local/tmp/w/`，ksud → `/data/local/tmp/ksud-s25u-kdp`
 *  （`umh_action.sh` 里 `mount --bind /data/local/tmp/ksud-s25u-kdp
 *   /system/bin/logcat` 写死这个路径，换名就挂不上）。
 * - `umh_action.sh` 里 `late-load` 的参数也是写死的：
 *   `--ephemeral --allow-shell --package-name me.weishu.kernelsu`。
 *   所以 [KSU_PACKAGE_NAME] 必须保持上游默认，见下。
 * - 执行是 `/data/local/tmp/w/libtimer umhpipe`；跑之前先写
 *   `/sys/kernel/tracing/buffer_size_kb` = 65536。
 * - 校验不碰 `/data/adb`：`--ephemeral` 的 late-load 明确跳过 ksud 安装
 *   与 `/data/adb`（ksud-32525 内有原文 "ephemeral late-load complete;
 *   skipping ksud installation and /data/adb"），所以只验两件事：
 *   `su -c id` 出 uid=0，且 /proc/modules 里有 kernelsu。
 *
 * 驱动版本映射（本轮定夺，见 `docs/source-integration.md` §5 第 4 项）：
 * - 98260 行继续用 **3.2.5（ksud-32525）**，不换 3.3.1。
 *   源码编出的 `ksud-331-src`（v3.3.1，UAPI 5）**不接入**：
 *   实测它内嵌的 `.ko` 是零个（`bin/aarch64/` 里编前只放了
 *   bootctl/busybox/waitsys，没放任何 `*_kernelsu.ko`），late-load 会
 *   直接报 "No kernel module found"；且 UAPI 5 的 daemon 对 DZJ3 的
 *   UAPI 2 时代 `.ko` 会触发版本不匹配拒绝；3.3.0 在 6.1.162 上
 *   让 system_server 崩溃循环的前车之鉴在前，没有真机证据不换代。
 *   `ksud-331-src` 不进包：`build-ksud.sh` 编出后放在
 *   `third-party/kernelsu/out/`（可随时重编），不随包暂存（见
 *   [KernelSuDriver] 与 [ksudAssetForDriver] 的注释）。
 * - [KSU_PACKAGE_NAME] **不改**，保持上游默认 `me.weishu.kernelsu`：
 *   预编译 `umh_action.sh` 写死 `--package-name me.weishu.kernelsu`，
 *   预编译 `.ko`（ksud-32525 内嵌的 android14-6.1 版）也是按官方
 *   Manager 身份绑定的；改包名要重编 `.ko` 绑定，需要三星内核源码，
 *   不在本轮范围。`build-ksud.sh` 同样留默认，两处一致。
 *
 * 保守规则（与 [EmbeddedRoot.kt] 同款，缺一不可）：
 * - 任一步失败立即停、清 staging、状态复位、只报失败原因；
 * - 98260 失败绝不重试：失败由调用方记进 [EmbeddedRootAttemptGuard]，
 *   同一次开机内不再跑；界面只给“重启手机后重试”，没有“立即重试”按钮；
 * - 跑起来后没有中途暂停；不承诺成功率；绝不写 /system。
 */
object PayloadLoader {

    /** 特权进程里 staging 的目录（SamTimer / root-my-s24 的约定路径）。 */
    const val STAGE_DIR: String = "/data/local/tmp/w"

    /** ksud 的 staging 路径：`umh_action.sh` 写死 mount 这个文件。 */
    const val KSUD_STAGE_PATH: String = "/data/local/tmp/ksud-s25u-kdp"

    /** KernelSU Manager 包名：预编译脚本与 `.ko` 绑定的身份，不改（见文件头）。 */
    const val KSU_PACKAGE_NAME: String = "me.weishu.kernelsu"

    /** libtimer 执行超时：exploit 是概率性的，跑起来要等；真机未验证，先给 180 秒。 */
    const val LIBTIMER_TIMEOUT_MS: Int = 180_000

    /** 普通命令超时（staging 校验、buffer 写、校验命令）。 */
    const val COMMAND_TIMEOUT_MS: Int = 15_000

    /** libtimer 的执行命令（SamTimer 链路原文）。 */
    const val LIBTIMER_COMMAND: String = "/data/local/tmp/w/libtimer umhpipe"

    /** 跑 libtimer 前先把 tracing buffer 写大（SamTimer 链路原文）。 */
    const val TRACING_BUFFER_COMMAND: String =
        "echo 65536 > /sys/kernel/tracing/buffer_size_kb"

    /** 校验命令 1：su 能拿到 uid=0。 */
    const val VERIFY_SU_COMMAND: String = "su -c id"

    /** 校验命令 2：内核模块已加载。 */
    const val VERIFY_MODULE_COMMAND: String = "grep -c kernelsu /proc/modules"

    /** per-target libtimer：固件构建号 → assets 里的 per-target 载荷。 */
    private val TARGET_LIBTIMER_ASSETS: Map<String, String> = mapOf(
        "S9280ZCS6DZJ3" to "embed/samtimer/targets/S9280ZCS6DZJ3/libtimer",
        "S721BXXUEEZIF" to "embed/samtimer/targets/S721BXXUEEZIF/libtimer",
    )

    /**
     * 按固件构建号选 libtimer：per-target 目录里有的用 per-target，
     * 没有的回退公共载荷（与 `EmbeddedAssets` 的口径一致）。
     */
    fun libtimerAssetFor(firmwareBuild: String): String {
        val key = firmwareBuild.trim().uppercase()
        TARGET_LIBTIMER_ASSETS.entries.firstOrNull { (suffix, _) -> suffix in key }
            ?.let { return it.value }
        return "embed/samtimer/libtimer"
    }

    /**
     * 驱动 → 内嵌 ksud 产物（assets 路径）。
     *
     * 只有 [KsudDriver.V32525] 与 [KsudDriver.V32601] 有映射；
     * [KsudDriver.V331_KDP] 返回 null（实验驱动，[selectKsudDriver]
     * 本来就禁它，这里再挡一道）；**没有任何分支返回 `ksud-331-src`**——
     * 源码编出的 v3.3.1 daemon 内嵌零个 `.ko`，接进来只会让 late-load
     * 报 "No kernel module found"，见文件头。
     */
    fun ksudAssetForDriver(driver: KsudDriver): String? = when (driver) {
        KsudDriver.V32525 -> "embed/samtimer/ksud-32525"
        KsudDriver.V32601 -> "embed/samtimer/ksud-32601"
        KsudDriver.V331_KDP -> null
    }
}

/** 一次加载要 stage 的一项：assets 里的产物 → 特权进程里的目标路径。 */
data class StageEntry(
    val assetPath: String,
    val destPath: String,
    val executable: Boolean = true,
)

/** 加载计划：驱动已选定、staging 清单已定，只等执行。 */
data class LoadPlan(
    val driver: KsudDriver,
    val ksudAsset: String,
    val libtimerAsset: String,
    val stages: List<StageEntry>,
    val note: String,
)

/** 建计划的结果：能跑 / 被拦（写清原因）。 */
sealed interface LoadPlanOutcome {
    data class Ready(val plan: LoadPlan) : LoadPlanOutcome
    data class Blocked(val reason: String) : LoadPlanOutcome
}

/**
 * 按引擎 + 内核 + 固件建加载计划。
 *
 * 驱动选择复用 [selectKsudDriver]（同一套硬规则：3.3.0 在 6.1.162 上禁、
 * 3.3.1 KDP 绝不默认）；选不出来就 Blocked，不编驱动、不猜产物。
 */
fun buildLoadPlan(
    engine: String?,
    kernelFull: String?,
    firmwareBuild: String,
): LoadPlanOutcome {
    return when (val decision = selectKsudDriver(engine, kernelFull)) {
        is DriverDecision.Forbidden -> LoadPlanOutcome.Blocked(decision.reason)
        is DriverDecision.Bundled -> LoadPlanOutcome.Blocked(
            "驱动随载荷提供（见 feed），本 App 不替它选 ksud 产物：${decision.note}",
        )
        is DriverDecision.Allowed -> {
            val ksudAsset = PayloadLoader.ksudAssetForDriver(decision.driver)
                ?: return LoadPlanOutcome.Blocked(
                    "驱动 ${decision.driver.versionLabel} 没有可加载的内嵌产物，不执行。",
                )
            val libtimerAsset = PayloadLoader.libtimerAssetFor(firmwareBuild)
            val stages = listOf(
                StageEntry(libtimerAsset, "${PayloadLoader.STAGE_DIR}/libtimer"),
                StageEntry("embed/samtimer/uhexec", "${PayloadLoader.STAGE_DIR}/uhexec"),
                StageEntry("embed/samtimer/umh_action.sh", "${PayloadLoader.STAGE_DIR}/umh_action.sh"),
                StageEntry(ksudAsset, PayloadLoader.KSUD_STAGE_PATH),
            )
            LoadPlanOutcome.Ready(
                LoadPlan(
                    driver = decision.driver,
                    ksudAsset = ksudAsset,
                    libtimerAsset = libtimerAsset,
                    stages = stages,
                    note = "驱动 ${decision.driver.versionLabel}（${decision.note}）",
                ),
            )
        }
    }
}

/**
 * 特权执行接口：[PayloadLoader] 只认这个接口，不认 binder。
 *
 * 真机实现是 [UserServiceGlue] 的 `UserServiceExecutor`（经 Shizuku
 * UserService 调 [TokiFontsRootService]）；单测用假执行器。
 * [stageFile] 的 [assetPath] 由实现方读 assets（真机）或从表里取（假执行器）。
 */
interface PrivilegedExecutor {
    /** 把 assets 里的 [assetPath] 写到特权进程的 [destPath]。 */
    suspend fun stageFile(assetPath: String, destPath: String, executable: Boolean): Boolean

    /** 以特权身份跑 `sh -c [command]`（[timeoutMs] 毫秒超时）。 */
    suspend fun runCommand(command: String, timeoutMs: Int): ShellResult

    /** 读特权进程可见的文件（最多 [maxBytes] 字节），读不到返回 null。 */
    suspend fun readFile(path: String, maxBytes: Int): String?

    /** 删文件或目录（rm -rf 语义）。 */
    suspend fun removePath(path: String): Boolean
}

/** 加载结果：成功 / 失败（失败带原因、是否记 98260 失败、staging 是否已清）。 */
sealed interface LoadOutcome {
    data class Success(val note: String, val log: List<String>) : LoadOutcome
    data class Failure(
        val reason: String,
        /** true = 这次是 98260 失败，调用方必须记进 guard，同开机内不再跑。 */
        val markFailed98260: Boolean,
        /** 失败后 staging 是否已清干净。 */
        val cleanedUp: Boolean,
        val log: List<String>,
    ) : LoadOutcome
}

/**
 * 跑一次加载计划：LOADING → RUNNING → VERIFYING → SUCCEEDED / FAILED。
 *
 * [readAsset] 读应用进程 assets（真机）或假表（单测）；
 * [onState] 收到每次状态变迁（调用方同步到界面状态机）。
 * 任一步失败：立即停、清 staging（尽力而为，逐项删）、报 FAILED，
 * 不重试、不重启——重试与否由 guard + 界面文案管，不在这里决定。
 */
suspend fun runLoadPlan(
    plan: LoadPlan,
    executor: PrivilegedExecutor,
    readAsset: (String) -> ByteArray?,
    onState: (EmbeddedRootState) -> Unit,
    /** libtimer 即将执行时调一次（调用方在这里记“本次开机已跑过”；staging 失败不调）。 */
    onExploitStart: () -> Unit = {},
): LoadOutcome {
    val log = mutableListOf<String>()
    suspend fun fail(reason: String, markFailed98260: Boolean): LoadOutcome.Failure {
        log += "失败：$reason"
        var cleaned = true
        for (stage in plan.stages) {
            if (!executor.removePath(stage.destPath)) {
                cleaned = false
                log += "清理失败：${stage.destPath}"
            }
        }
        onState(EmbeddedRootState.FAILED)
        return LoadOutcome.Failure(reason, markFailed98260, cleaned, log.toList())
    }

    onState(EmbeddedRootState.LOADING)
    log += "开始 staging：驱动 ${plan.driver.versionLabel}，libtimer ${plan.libtimerAsset}"
    for (stage in plan.stages) {
        if (readAsset(stage.assetPath) == null) {
            return fail("内嵌产物读不到：${stage.assetPath}", markFailed98260 = false)
        }
        if (!executor.stageFile(stage.assetPath, stage.destPath, stage.executable)) {
            return fail("staging 失败：${stage.assetPath} → ${stage.destPath}", markFailed98260 = false)
        }
        log += "已 stage：${stage.destPath}"
    }

    onState(EmbeddedRootState.RUNNING)
    val buffer = executor.runCommand(PayloadLoader.TRACING_BUFFER_COMMAND, PayloadLoader.COMMAND_TIMEOUT_MS)
    if (!buffer.ok) {
        // 写 tracing buffer 失败不直接判死刑：记一笔，继续跑 libtimer，
        // 成败由后面的校验说了算。
        log += "写 tracing buffer 没成功（${buffer.exitCode}），继续跑 libtimer"
    }
    val exploit = run {
        // 到这里才真正跑 exploit：调用方在这里记一次开机已跑过。
        onExploitStart()
        executor.runCommand(PayloadLoader.LIBTIMER_COMMAND, PayloadLoader.LIBTIMER_TIMEOUT_MS)
    }
    if (exploit.timedOut) {
        return fail("libtimer 执行超时（${PayloadLoader.LIBTIMER_TIMEOUT_MS / 1000} 秒没返回）", markFailed98260 = true)
    }
    if (!exploit.ok) {
        val detail = exploit.stderr.ifBlank { exploit.stdout }.trim().take(200)
        return fail(
            "libtimer 退出码 ${exploit.exitCode}${if (detail.isNotBlank()) "：$detail" else ""}",
            markFailed98260 = true,
        )
    }
    log += "libtimer 已返回（exit 0）"

    onState(EmbeddedRootState.VERIFYING)
    val su = executor.runCommand(PayloadLoader.VERIFY_SU_COMMAND, PayloadLoader.COMMAND_TIMEOUT_MS)
    if (!su.ok || "uid=0" !in su.stdout) {
        return fail("校验失败：`su -c id` 没拿到 uid=0，root 没起来", markFailed98260 = true)
    }
    val module = executor.runCommand(PayloadLoader.VERIFY_MODULE_COMMAND, PayloadLoader.COMMAND_TIMEOUT_MS)
    val moduleCount = module.stdout.trim().toIntOrNull()
    if (!module.ok || moduleCount == null || moduleCount <= 0) {
        return fail("校验失败：/proc/modules 里没找到 kernelsu", markFailed98260 = true)
    }
    log += "校验通过：uid=0，kernelsu 模块已加载"
    onState(EmbeddedRootState.SUCCEEDED)
    return LoadOutcome.Success("已取得 root（驱动 ${plan.driver.versionLabel}）", log.toList())
}
