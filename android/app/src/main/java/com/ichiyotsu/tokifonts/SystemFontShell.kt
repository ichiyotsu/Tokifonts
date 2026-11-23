package com.ichiyotsu.tokifonts

import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 换系统字体的 shell 层：root 来自独立的 RootMyS24 应用（绝不嵌入 Tokifonts），
 * 这里只执行 `su -c ...`。所有失败都返回中文错误，不抛异常、不崩溃。
 * 模块只做 KSU magic mount（bind 覆盖），绝不写 /system 或任何系统分区。
 */
data class ShellResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val timedOut: Boolean = false,
) {
    val ok: Boolean get() = !timedOut && exitCode == 0
}

/** 模块安装结果：成功时 [method] 说明走的是哪条路（"ksud" 或 "复制"），界面要告诉用户。 */
sealed interface InstallOutcome {
    data class Success(val method: String) : InstallOutcome
    /**
     * [moduleWritten]：安装步是否已对 /data/adb/modules/tokifonts-font 动过手
     * （ksud 安装或复制安装跑过，无论成败都可能留下半成品）；打包失败、staging
     * 缺失这类还没动目录的失败为 false。失败自动取消时按它决定要不要删模块目录。
     */
    data class Failure(val message: String, val moduleWritten: Boolean = false) : InstallOutcome
}

/** 回滚结果：模块是否已删、zygote 是否确认重启——删了但没重启成功时要让用户去重启手机。 */
data class RollbackOutcome(
    val moduleRemoved: Boolean,
    val zygoteOk: Boolean,
    val error: String?,
)

object SystemFontShell {
    private const val TIMEOUT_MS = 15_000L
    private const val MODULE_DIR = "/data/adb/modules/tokifonts-font"

    /** 在 Dispatchers.IO 上跑 `su -c <cmd>`，15 秒超时，读 stdout/stderr + exit code。 */
    suspend fun runAsRoot(cmd: String): ShellResult = withContext(Dispatchers.IO) {
        val process = runCatching { Runtime.getRuntime().exec(arrayOf("su", "-c", cmd)) }
            .getOrElse { return@withContext ShellResult(-1, "", "无法执行 su：${it.message ?: "未知错误"}") }
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
            return@withContext ShellResult(-1, out.toString(), err.toString(), timedOut = true)
        }
        outThread.join(2000)
        errThread.join(2000)
        ShellResult(process.exitValue(), out.toString().trim(), err.toString().trim())
    }

    /** root 检测：`su -c id` 输出含 uid=0。 */
    suspend fun checkRoot(): Boolean {
        val result = runAsRoot("id")
        return result.ok && "uid=0" in result.stdout
    }

    /** ksud 是否可用（KernelSU 的命令行工具，有则优先用它装模块）。 */
    suspend fun hasKsud(): Boolean {
        val result = runAsRoot("command -v ksud")
        return result.ok && result.stdout.isNotBlank()
    }

    /** 模块是否已装（目录存在，且 module.prop 在——只剩空目录不算装好）。 */
    suspend fun isModuleInstalled(): Boolean {
        val result = runAsRoot("test -d $MODULE_DIR && test -f $MODULE_DIR/module.prop")
        return result.ok
    }

    /**
     * 安装模块：先把 staging 打成 zip，再按顺序尝试两条路——
     * (1) ksud 可用时 `ksud module install <zip>`（ksud 吃 zip 不吃目录）；
     * (2) 失败时回退到 `su cp -r` staging → /data/adb/modules/tokifonts-font。
     * 任何一条路走完都要校验模块目录确实在位才算成功；两条都失败才报错。
     * zygote 绝不在模块未验证在位时重启（调用方只在 Success 后才重启）。
     */
    suspend fun installModule(stagingDir: File, zipFile: File): InstallOutcome = withContext(Dispatchers.IO) {
        if (!stagingDir.exists()) return@withContext InstallOutcome.Failure("模块还没生成，请先点“生成模块”")
        runCatching { SystemFont.buildModuleZip(stagingDir, zipFile) }.onFailure {
            return@withContext InstallOutcome.Failure("打包模块失败：${it.message ?: "未知错误"}")
        }
        var ksudError: String? = null
        if (hasKsud()) {
            val installed = runAsRoot("ksud module install ${zipFile.absolutePath}")
            if (installed.ok && isModuleInstalled()) {
                return@withContext InstallOutcome.Success("ksud")
            }
            ksudError = "ksud 安装没成功：${installed.errorText()}（${if (installed.timedOut) "命令超时" else "exit ${installed.exitCode}"}），已改走复制安装"
        }
        val copied = runAsRoot("rm -rf $MODULE_DIR && cp -r ${stagingDir.absolutePath} $MODULE_DIR")
        if (!copied.ok) {
            val detail = "复制安装失败：${copied.errorText()}（${if (copied.timedOut) "命令超时" else "exit ${copied.exitCode}"}）"
            return@withContext InstallOutcome.Failure(listOfNotNull(ksudError, detail).joinToString("；"), moduleWritten = true)
        }
        if (!isModuleInstalled()) {
            return@withContext InstallOutcome.Failure(
                listOfNotNull(ksudError, "安装后找不到模块目录，请检查 root 授权后重试").joinToString("；"),
                moduleWritten = true,
            )
        }
        InstallOutcome.Success("复制")
    }

    /**
     * 安装后验证（尽力而为）：模块目录还在，且挂载出来的每个新字体文件大小
     * 都对得上（读的是 /system/fonts 下经过挂载的路径）；[expectedFontsXmlBytes] > 0 时
     * 也核对 /system/etc/fonts.xml 的大小。返回 null 表示通过，否则为中文问题。
     */
    suspend fun verifyInstalled(selected: List<SystemFontScript>, expectedFontsXmlBytes: Long = -1): String? {
        if (!isModuleInstalled()) return "安装后找不到模块目录"
        if (expectedFontsXmlBytes > 0) {
            val xml = runAsRoot("stat -c %s /system/etc/fonts.xml")
            if (!xml.ok) return "无法验证 /system/etc/fonts.xml（读不到，挂载可能没生效）"
            val size = xml.stdout.trim().toLongOrNull()
            if (size == null) return "无法验证 /system/etc/fonts.xml（读到的大小不是数字）"
            if (size != expectedFontsXmlBytes) {
                return "/system/etc/fonts.xml 大小不对（$size，期望 $expectedFontsXmlBytes），新字体可能没挂上"
            }
        }
        for (script in selected) {
            val path = "/system/fonts/${script.moduleFileName}"
            val result = runAsRoot("stat -c %s $path")
            if (!result.ok) return "无法验证 $path（读不到，挂载可能没生效）"
            val size = result.stdout.trim().toLongOrNull()
            if (size == null) return "无法验证 $path（读到的大小不是数字）"
            if (size != script.expectedBytes) {
                return "$path 大小不对（$size，期望 ${script.expectedBytes}），新字体可能没挂上"
            }
        }
        return null
    }

    /** 软重启 zygote，让新字体生效。返回 null 表示成功，否则为中文错误。 */
    suspend fun restartZygote(): String? {
        val result = runAsRoot("setprop ctl.restart zygote")
        if (!result.ok) return "软重启 zygote 失败：${result.errorText()}，可改点“重启手机”"
        return null
    }

    /**
     * 回滚：删模块目录 + 重启 zygote，恢复原厂字体。
     * 模块删了但 zygote 重启没确认时也要如实返回（moduleRemoved=true, zygoteOk=false），
     * 让界面把“重启手机”按钮顶到前面，而不是报成“回滚失败”。
     */
    suspend fun rollback(): RollbackOutcome {
        val removed = runAsRoot("rm -rf $MODULE_DIR")
        if (!removed.ok) return RollbackOutcome(false, false, "删除模块失败：${removed.errorText()}")
        if (isModuleInstalled()) return RollbackOutcome(false, false, "模块目录还在，请检查 root 授权后重试")
        val restart = runAsRoot("setprop ctl.restart zygote")
        return if (restart.ok) {
            RollbackOutcome(true, true, null)
        } else {
            RollbackOutcome(
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

    private fun ShellResult.errorText(): String {
        val detail = stderr.ifBlank { stdout }.trim()
        return detail.ifBlank { "无输出" }.take(300)
    }
}
