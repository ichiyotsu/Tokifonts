package com.ichiyotsu.tokifonts

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 已装字体模块的最小卸载入口（0.1.8/0.1.9 换字体功能已移除后保留）：
 * 只会删 /data/adb/modules/tokifonts-font + 软重启 zygote，恢复原厂字体，
 * 不含任何字体生成 / 下载逻辑。root 来自用户自己的 root 环境，
 * 这里只执行 `su -c ...`。所有失败都返回中文错误，不抛异常、不崩溃。
 * 模块只做 KSU magic mount（bind 覆盖），绝不写 /system 或任何系统分区。
 */
data class ModuleCommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val timedOut: Boolean = false,
) {
    val ok: Boolean get() = !timedOut && exitCode == 0
}

/** 卸载结果：模块是否已删、zygote 是否确认重启——删了但没重启成功时要让用户去重启手机。 */
data class ModuleUninstallOutcome(
    val moduleRemoved: Boolean,
    val zygoteOk: Boolean,
    val error: String?,
)

object InstalledModule {
    private const val TIMEOUT_MS = 15_000L
    /** 0.1.8/0.1.9 换字体功能装的模块目录（组合字体包名 Inter，id tokifonts-font）。 */
    const val MODULE_DIR = "/data/adb/modules/tokifonts-font"

    /** 在 Dispatchers.IO 上跑 `su -c <cmd>`，15 秒超时，读 stdout/stderr + exit code。 */
    suspend fun runAsRoot(cmd: String): ModuleCommandResult = withContext(Dispatchers.IO) {
        val process = runCatching { Runtime.getRuntime().exec(arrayOf("su", "-c", cmd)) }
            .getOrElse { return@withContext ModuleCommandResult(-1, "", "无法执行 su：${it.message ?: "未知错误"}") }
        val out = StringBuilder()
        val err = StringBuilder()
        val outThread = Thread { process.inputStream.bufferedReader().use { reader -> reader.forEachLine { out.appendLine(it) } } }
        val errThread = Thread { process.errorStream.bufferedReader().use { reader -> reader.forEachLine { err.appendLine(it) } } }
        outThread.start()
        errThread.start()
        val finished = process.waitFor(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        if (!finished) {
            process.destroyForcibly()
            outThread.join(1000)
            errThread.join(1000)
            return@withContext ModuleCommandResult(-1, out.toString(), err.toString(), timedOut = true)
        }
        outThread.join(2000)
        errThread.join(2000)
        ModuleCommandResult(process.exitValue(), out.toString().trim(), err.toString().trim())
    }

    /** 模块是否已装（目录存在，且 module.prop 在——只剩空目录不算装好）。 */
    suspend fun isInstalled(): Boolean {
        val result = runAsRoot("test -d $MODULE_DIR && test -f $MODULE_DIR/module.prop")
        return result.ok
    }

    /**
     * 卸载：删模块目录 + 重启 zygote，恢复原厂字体。
     * 模块删了但 zygote 重启没确认时也要如实返回（moduleRemoved=true, zygoteOk=false），
     * 让界面把“重启手机”按钮顶到前面，而不是报成“卸载失败”。
     */
    suspend fun uninstall(): ModuleUninstallOutcome {
        val removed = runAsRoot("rm -rf $MODULE_DIR")
        if (!removed.ok) return ModuleUninstallOutcome(false, false, "删除模块失败：${removed.errorText()}")
        if (isInstalled()) return ModuleUninstallOutcome(false, false, "模块目录还在，请检查 root 授权后重试")
        val restart = runAsRoot("setprop ctl.restart zygote")
        return if (restart.ok) {
            ModuleUninstallOutcome(true, true, null)
        } else {
            ModuleUninstallOutcome(
                moduleRemoved = true,
                zygoteOk = false,
                error = "模块已删，但软重启 zygote 没确认：${restart.errorText()}，请点“重启手机”",
            )
        }
    }

    /** 重启手机（备选：软重启后字体未生效，说明 magic mount 未在当前挂载命名空间生效，需重启）。 */
    suspend fun reboot(): String? {
        val result = runAsRoot("reboot")
        if (!result.ok) return "重启失败：${result.errorText()}"
        return null
    }

    private fun ModuleCommandResult.errorText(): String {
        val detail = stderr.ifBlank { stdout }.trim()
        return detail.ifBlank { "无输出" }.take(300)
    }
}
