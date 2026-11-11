package com.ichiyotsu.tokifonts

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

data class UpdateOffer(
    val version: String,
    val notes: String,
    val url: String,
    val assetName: String,
    val sha256: String,
)

object VersionNames {
    fun isNewer(remote: String, current: String): Boolean {
        val next = stable(remote) ?: return false
        // 远端带 - 后缀（beta）时不提示；但当前装的就是 beta 包时，要按去掉后缀的底版比，
        // 且同底版的正式版也算更新，否则 beta 用户会永远收不到正式版。
        val currentBase = current.trim().removePrefix("v").removePrefix("V")
        val currentHadSuffix = '-' in currentBase
        val now = stable(currentBase) ?: stable(currentBase.substringBefore('-')) ?: return false
        for (index in 0..2) if (next[index] != now[index]) return next[index] > now[index]
        return currentHadSuffix
    }

    /** 从资产文件名里抠版本号，如 tokifonts-0.1.2-android.apk → 0.1.2。 */
    fun fromFileName(name: String): String? {
        val match = Regex("""(\d+)\.(\d+)\.(\d+)""").find(name) ?: return null
        return match.value
    }

    private fun stable(raw: String): IntArray? {
        val version = raw.trim().removePrefix("v").removePrefix("V")
        if ('-' in version) return null
        val parts = version.split('.')
        if (parts.size != 3) return null
        return IntArray(3) { parts[it].toIntOrNull() ?: return null }
    }
}

object AppUpdate {
    private val http = OkHttpClient.Builder()
        .followRedirects(true)
        .callTimeout(3, TimeUnit.MINUTES)
        .readTimeout(2, TimeUnit.MINUTES)
        .build()

    // 更新下载在任一时刻最多一个：留住正在跑的 Call，取消时直接掐断连接。
    // 只取消协程掐不断 execute() 的阻塞读，必须从 Call 这一层动手（与 FontStore.calls 同款）。
    @Volatile
    private var currentCall: Call? = null

    /**
     * 取消正在进行的更新下载：掐断连接后，下载会以 CancellationException 收尾，
     * 残包由 [download] 的失败清理删掉。没有下载在进行时是无操作；
     * 已下载完、待安装的包不经过这里，不受任何影响。
     */
    fun cancelDownload() {
        currentCall?.cancel()
    }

    fun latest(current: String): UpdateOffer? {
        val request = Request.Builder()
            .url("https://api.github.com/repos/ichiyotsu/Tokifonts/releases/latest")
            .header("User-Agent", "tokifonts/$current (https://github.com/ichiyotsu/Tokifonts)")
            .header("Accept", "application/vnd.github+json")
            .build()
        http.newCall(request).execute().use { response ->
            if (response.code == 404) return null
            if (!response.isSuccessful) error("检查更新失败 HTTP ${response.code}")
            val json = JSONObject(response.body?.string().orEmpty())
            if (json.optBoolean("draft") || json.optBoolean("prerelease")) return null
            val tagVersion = json.optString("tag_name")
            val assets = json.optJSONArray("assets") ?: return null
            var chosen: JSONObject? = null
            for (index in 0 until assets.length()) {
                val asset = assets.getJSONObject(index)
                val name = asset.optString("name")
                if (name.endsWith("-android.apk")) {
                    chosen = asset
                    break
                }
                if (chosen == null && name.startsWith("tokifonts") && name.endsWith(".apk")) chosen = asset
            }
            val asset = chosen ?: return null
            val url = asset.optString("browser_download_url")
            if (!url.startsWith("https://")) error("更新地址无效")
            // 只信 tag 会误报：tag 与包内版本脱节时（如 tag v1.3.1 却装了 1.3.0 的包），
            // 装完版本号仍小于 tag，会反复弹窗。资产文件名里的版本优先，取不到才回退到 tag。
            val assetName = asset.optString("name").ifBlank { "tokifonts-update.apk" }
            val fileVersion = VersionNames.fromFileName(assetName)
            val version = fileVersion ?: tagVersion
            if (!VersionNames.isNewer(version, current)) return null
            return UpdateOffer(
                version = version.removePrefix("v").removePrefix("V"),
                notes = json.optString("body"),
                url = url,
                assetName = assetName,
                sha256 = asset.optString("digest").substringAfter("sha256:", "").lowercase(),
            )
        }
    }

    fun download(offer: UpdateOffer, dest: File, onProgress: (Float) -> Unit) {
        dest.parentFile?.mkdirs()
        try {
            downloadInto(offer, dest, onProgress)
        } catch (error: Throwable) {
            // 下载中途失败、被取消或校验不一致时都删掉残包，不让不完整的 APK 一直占着 cache。
            dest.delete()
            throw error
        }
    }

    private fun downloadInto(offer: UpdateOffer, dest: File, onProgress: (Float) -> Unit) {
        val request = Request.Builder().url(offer.url).header("User-Agent", "tokifonts/${offer.version}").build()
        val call = http.newCall(request)
        currentCall = call
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) error("下载更新失败 HTTP ${response.code}")
                val body = response.body ?: error("更新包是空的")
                val total = body.contentLength()
                val digest = MessageDigest.getInstance("SHA-256")
                body.byteStream().use { input ->
                    dest.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var read = 0L
                        var last = -1f
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            digest.update(buffer, 0, count)
                            read += count
                            if (total > 0) {
                                val progress = read.toFloat() / total.toFloat()
                                if (progress - last >= 0.02f || progress >= 1f) {
                                    last = progress
                                    onProgress(progress.coerceIn(0f, 1f))
                                }
                            }
                        }
                    }
                }
                val actual = digest.digest().joinToString("") { "%02x".format(it) }
                if (offer.sha256.isNotBlank() && offer.sha256 != actual) error("更新包校验不一致")
            }
        } catch (error: IOException) {
            // 被 cancelDownload() 掐断时，OkHttp 抛的是 IOException：翻译成取消语义，
            // 让调用方能把“用户取消”和“真的下载失败”分开处理。
            if (call.isCanceled()) throw CancellationException("更新下载已取消")
            throw error
        } finally {
            // 按值清：只清自己登记的这个 Call。一次新下载只会在上一次彻底结束后才开始，
            // 这里再防一手，绝不抹掉后来者的句柄。
            if (currentCall === call) currentCall = null
        }
    }

    fun install(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
