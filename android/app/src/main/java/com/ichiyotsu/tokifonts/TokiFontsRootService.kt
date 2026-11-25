package com.ichiyotsu.tokifonts

import android.content.Context
import android.os.Parcel
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Pro 自己的 Shizuku UserService：特权进程里 staging + 跑命令的执行端。
 *
 * 机制（`shizuku.md` §5）：Shizuku server 经 `app_process` 以 root（uid 0）
 * 或 shell（uid 2000，无线调试启动）身份拉起本类，binder 经
 * `attachUserService`（AIDL transaction 101）交回应用进程；应用进程侧
 * 用 [UserServiceGlue] 的 `ShizukuUserServiceConnector` 拿 `ITokiFontsRootService`。
 *
 * 注意点：
 * - 本类**不在 AndroidManifest 里注册**：它不是系统拉起的普通 Service，
 *   是 server 用 `app_process` 直接起的类，manifest 里没有它的位置。
 * - 两个构造器都要有：Shizuku v13 起优先试带 `Context` 的构造器，
 *   找不到才回退无参构造器。Context 在这里基本不能用（非常规应用进程，
 *   `registerReceiver` / `getContentResolver` 等受限），只留着不用。
 * - destroy：server 用 transaction `16777115`（`ShizukuApiConstants.
 *   USER_SERVICE_TRANSACTION_destroy`，AIDL 里写 16777114 的那个是笔误
 *   方向——以常量类为准）通知销毁；本类在 [onTransact] 里拦截它，
 *   做完清理后必须自调 `System.exit()`，否则 user service 进程不会被杀
 *   （`Shizuku.unbindUserService` 的文档原话）。
 * - 本类只做“写文件 / 跑命令 / 读文件 / 删路径”四件机械事，不做任何
 *   业务判断：跑什么、验什么、失败怎么办都在应用进程的 [PayloadLoader] 里。
 */
class TokiFontsRootService : ITokiFontsRootService.Stub {

    @Suppress("unused")
    private val context: Context?

    constructor() : super() {
        context = null
    }

    constructor(context: Context) : super() {
        this.context = context
    }

    companion object {
        /** Shizuku user service 的 destroy transaction（见上游常量类）。 */
        const val TRANSACTION_DESTROY: Int = 16777115

        /** `runCommand` 的 stdout/stderr 各截断到这么多字符，防 log 刷爆。 */
        const val MAX_OUTPUT_CHARS: Int = 65536
    }

    @Volatile
    private var lastStdout: String = ""

    @Volatile
    private var lastStderr: String = ""

    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        if (code == TRANSACTION_DESTROY) {
            // server 要求销毁：user service 进程必须自己退出，不会自动被杀。
            runCatching { System.exit(0) }
            return true
        }
        return super.onTransact(code, data, reply, flags)
    }

    override fun stageFile(destPath: String?, content: ByteArray?, executable: Boolean): Int {
        if (destPath.isNullOrBlank() || content == null) return -1
        return runCatching {
            val file = File(destPath)
            file.parentFile?.mkdirs()
            file.outputStream().use { it.write(content) }
            file.setReadable(true, false)
            file.setExecutable(executable, false)
            if (executable && !file.canExecute()) return -1
            0
        }.getOrDefault(-1)
    }

    override fun runCommand(command: String?, timeoutMs: Int): Int {
        if (command.isNullOrBlank()) return -1
        val process = runCatching {
            Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
        }.getOrNull() ?: return -1
        val out = StringBuilder()
        val err = StringBuilder()
        val outThread = Thread {
            runCatching {
                process.inputStream.bufferedReader().use { reader ->
                    reader.forEachLine { line ->
                        if (out.length < MAX_OUTPUT_CHARS) out.appendLine(line)
                    }
                }
            }
        }
        val errThread = Thread {
            runCatching {
                process.errorStream.bufferedReader().use { reader ->
                    reader.forEachLine { line ->
                        if (err.length < MAX_OUTPUT_CHARS) err.appendLine(line)
                    }
                }
            }
        }
        outThread.start()
        errThread.start()
        val finished = process.waitFor(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
        if (!finished) {
            process.destroyForcibly()
            outThread.join(1000)
            errThread.join(1000)
            lastStdout = out.toString().trim()
            lastStderr = err.toString().trim()
            return -2
        }
        outThread.join(2000)
        errThread.join(2000)
        lastStdout = out.toString().trim()
        lastStderr = err.toString().trim()
        return process.exitValue()
    }

    override fun getLastStdout(): String = lastStdout

    override fun getLastStderr(): String = lastStderr

    override fun readFile(path: String?, maxBytes: Int): String? {
        if (path.isNullOrBlank() || maxBytes <= 0) return null
        return runCatching {
            val file = File(path)
            if (!file.isFile || !file.canRead()) return null
            val bytes = file.inputStream().use { it.readNBytes(maxBytes) }
            String(bytes, Charsets.UTF_8)
        }.getOrNull()
    }

    override fun removePath(path: String?): Int {
        if (path.isNullOrBlank()) return -1
        return runCatching {
            val file = File(path)
            if (!file.exists()) return 0
            if (file.deleteRecursively()) 0 else -1
        }.getOrDefault(-1)
    }
}
