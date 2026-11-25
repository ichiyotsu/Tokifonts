package com.ichiyotsu.tokifonts

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku

/**
 * Shizuku UserService 胶水：binder 怎么来、service 怎么绑、特权执行怎么做。
 *
 * ## binder 走哪条路：provider，不走 Sui（本轮定夺）
 * - Shizuku 的标准通道是 `ShizukuProvider`（ContentProvider，server 在
 *   应用启动时把 binder 发过来；见 `third-party/shizuku/provider/`）。
 *   本应用已依赖 `:third-party:shizuku:provider` 并在 manifest 里注册
 *   `rikka.shizuku.ShizukuProvider`（authorities
 *   `com.ichiyotsu.tokifontspro.shizuku`），所以 Shizuku manager 一启动，
 *   binder 就到应用进程，`Shizuku.pingBinder()` 才能为真。
 * - Sui 不走：Sui 是 Magisk 模块提供的 binder 通道（`Sui.init()` 去
 *   activity service 里取 binder），要手机上已经装了 Sui——而 Sui 本身
 *   要 root/Magisk 才能装；本流程的目标用户恰恰是还没 root、靠无线调试
 *   启动 Shizuku 的那台手机，Sui 在那里根本不存在。`ShizukuProvider`
 *   的 `onCreate()` 里上游还会顺手试一次 `Sui.init()`，失败只记 false，
 *   不影响 provider 通道，两条路不混用、不冲突。
 * - 之前的状态是两条路都没接：代码里既没注册 provider，也没调过
 *   `Sui.init()`，`pingBinder()` 只能为 false——settings 注释里写的
 *   “现走 Sui 取 binder”是不准确的说法，本轮已随 provider 注册纠正。
 *
 * ## UserService 怎么绑
 * - service 类是 [TokiFontsRootService]（Pro 自己的业务 AIDL
 *   `ITokiFontsRootService` 的 Stub 实现），`UserServiceArgs.tag`
 *   必须显式设（[USER_SERVICE_TAG]）：R8 后类名不稳定，默认 tag
 *   （类名）会在混淆后对不上 server 端的记录。`daemon(false)`：
 *   一次性 exploit 跑完就走，不留常驻特权进程；`version(1)`，
 *   service 代码改了就加一，让 server 重建旧 service。
 * - 销毁：[ShizukuUserServiceConnector.unbind] 用 `remove=true` 调
 *   `unbindUserService`，server 发 destroy transaction（16777115），
 *   service 侧 [TokiFontsRootService.onTransact] 接住后自调
 *   `System.exit()`，进程才会被杀；光 unbind 不杀进程。
 * - 授权：`Shizuku.checkSelfPermission()` 不是 Android 运行时权限，
 *   是 server 端的授权记录；没授权时调 `requestPermission()`，server
 *   弹确认框，结果经 `OnRequestPermissionResultListener` 回来。
 *   用户拒绝只报“去 Shizuku 里授权”，不反复弹。
 */
object UserServiceGlue {

    /** UserService 的显式 tag：R8 后类名不稳定，绝不能用默认类名。 */
    const val USER_SERVICE_TAG: String = "com.ichiyotsu.tokifontspro.root"

    /** UserService 的版本：service 代码改了就加一，让 server 重建旧 service。 */
    const val USER_SERVICE_VERSION: Int = 1

    /** Shizuku 授权请求的 requestCode（应用内自定，只用于匹配回调）。 */
    const val PERMISSION_REQUEST_CODE: Int = 1001

    /** binder 的来源：本轮只走 provider（见文件头）。 */
    enum class BinderSource {
        PROVIDER,
        SUI,
    }

    /** binder 只走 provider——Sui 要预装 Magisk 模块，未 root 的目标机没有。 */
    fun chooseBinderSource(): BinderSource = BinderSource.PROVIDER

    /** 建 UserService 参数：显式 tag + 版本 + 非 daemon（一次性跑完就走）。 */
    fun buildUserServiceArgs(packageName: String): Shizuku.UserServiceArgs {
        val component = ComponentName(packageName, TokiFontsRootService::class.java.name)
        return Shizuku.UserServiceArgs(component)
            .tag(USER_SERVICE_TAG)
            .version(USER_SERVICE_VERSION)
            .daemon(false)
    }

    /** `Shizuku.pingBinder()` 的安全版：任何异常都当作“没在运行”。 */
    fun isShizukuBinderAlive(): Boolean =
        runCatching { Shizuku.pingBinder() }.getOrDefault(false)
}

/** 绑 UserService 的结果。 */
sealed interface UserServiceBindResult {
    data class Bound(val service: ITokiFontsRootService) : UserServiceBindResult
    data class Failed(val reason: String, val needsPermission: Boolean = false) : UserServiceBindResult
}

/**
 * Shizuku UserService 的一次绑定：检查 binder → 检查/请求授权 → 绑 service。
 *
 * 用完必须调 [unbind]（`remove=true`，让 server 销毁 service 进程）；
 * 失败时不留半截连接。
 */
class ShizukuUserServiceConnector(
    private val context: Context,
    private val bindTimeoutMs: Long = 30_000,
) {
    private var args: Shizuku.UserServiceArgs? = null
    private var connection: ServiceConnection? = null

    suspend fun bind(): UserServiceBindResult = withContext(Dispatchers.IO) {
        if (!UserServiceGlue.isShizukuBinderAlive()) {
            return@withContext UserServiceBindResult.Failed(
                "Shizuku 没在运行。先在第 1 步里按提示用无线调试启动它，再回来。",
            )
        }
        val permission = runCatching { Shizuku.checkSelfPermission() }.getOrNull()
        if (permission != PackageManager.PERMISSION_GRANTED) {
            val granted = requestShizukuPermission()
            if (!granted) {
                return@withContext UserServiceBindResult.Failed(
                    "还没在 Shizuku 里给本应用授权。请打开 Shizuku，在“已授权应用”里允许 TokiFontsPro，再回来。",
                    needsPermission = true,
                )
            }
        }
        val serviceArgs = UserServiceGlue.buildUserServiceArgs(context.packageName)
        val connected = CompletableDeferred<IBinder?>()
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                connected.complete(service)
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                if (!connected.isCompleted) connected.complete(null)
            }
        }
        val binder = try {
            Shizuku.bindUserService(serviceArgs, conn)
            withTimeoutOrNull(bindTimeoutMs) { connected.await() }
        } catch (e: Exception) {
            connected.complete(null)
            null
        }
        if (binder == null) {
            return@withContext UserServiceBindResult.Failed(
                "UserService 绑不上（Shizuku 没把特权 service 拉起来），请重启手机后重试。",
            )
        }
        args = serviceArgs
        connection = conn
        UserServiceBindResult.Bound(ITokiFontsRootService.Stub.asInterface(binder))
    }

    /** 解绑并销毁 service 进程（remove=true → server 发 destroy → service 自退出）。 */
    fun unbind() {
        val boundArgs = args
        val boundConn = connection
        args = null
        connection = null
        if (boundArgs != null) {
            runCatching { Shizuku.unbindUserService(boundArgs, boundConn, true) }
        }
    }

    /**
     * 向 Shizuku 请求授权：server 弹确认框，结果经 listener 回来；
     * 超时或被拒都返回 false（不反复弹，只报一次）。
     */
    private suspend fun requestShizukuPermission(): Boolean {
        val result = CompletableDeferred<Boolean>()
        val listener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == UserServiceGlue.PERMISSION_REQUEST_CODE) {
                result.complete(grantResult == PackageManager.PERMISSION_GRANTED)
            }
        }
        Shizuku.addRequestPermissionResultListener(listener)
        try {
            runCatching { Shizuku.requestPermission(UserServiceGlue.PERMISSION_REQUEST_CODE) }
                .onFailure { result.complete(false) }
            return withTimeoutOrNull(bindTimeoutMs) { result.await() } ?: false
        } finally {
            Shizuku.removeRequestPermissionResultListener(listener)
        }
    }
}

/**
 * [PrivilegedExecutor] 的真机实现：经 Shizuku UserService 调
 * [TokiFontsRootService] 的四个方法。
 *
 * [readAsset] 由调用方给（应用进程的 assets）；`runCommand` 的输出不走
 * AIDL `out` 参数（String 只能是 `in`），而是跑完后调
 * `getLastStdout()` / `getLastStderr()` 再取一次。
 */
class UserServiceExecutor(
    private val service: ITokiFontsRootService,
    private val readAsset: (String) -> ByteArray?,
) : PrivilegedExecutor {

    override suspend fun stageFile(assetPath: String, destPath: String, executable: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            val bytes = readAsset(assetPath) ?: return@withContext false
            runCatching { service.stageFile(destPath, bytes, executable) == 0 }.getOrDefault(false)
        }

    override suspend fun runCommand(command: String, timeoutMs: Int): ShellResult =
        withContext(Dispatchers.IO) {
            val code = runCatching { service.runCommand(command, timeoutMs) }
                .getOrNull() ?: return@withContext ShellResult(-1, "", "调用 UserService 失败")
            val stdout = runCatching { service.lastStdout }.getOrDefault("")
            val stderr = runCatching { service.lastStderr }.getOrDefault("")
            ShellResult(
                exitCode = code,
                stdout = stdout,
                stderr = stderr,
                timedOut = code == -2,
            )
        }

    override suspend fun readFile(path: String, maxBytes: Int): String? =
        withContext(Dispatchers.IO) {
            runCatching { service.readFile(path, maxBytes) }.getOrNull()
        }

    override suspend fun removePath(path: String): Boolean =
        withContext(Dispatchers.IO) {
            runCatching { service.removePath(path) == 0 }.getOrDefault(false)
        }
}
