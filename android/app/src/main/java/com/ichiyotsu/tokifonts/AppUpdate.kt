package com.ichiyotsu.tokifonts

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
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
        val now = stable(current) ?: return false
        for (index in 0..2) if (next[index] != now[index]) return next[index] > now[index]
        return false
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
            val version = json.optString("tag_name")
            if (!VersionNames.isNewer(version, current)) return null
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
            return UpdateOffer(
                version = version.removePrefix("v").removePrefix("V"),
                notes = json.optString("body"),
                url = url,
                assetName = asset.optString("name").ifBlank { "tokifonts-update.apk" },
                sha256 = asset.optString("digest").substringAfter("sha256:", "").lowercase(),
            )
        }
    }

    fun download(offer: UpdateOffer, dest: File, onProgress: (Float) -> Unit) {
        dest.parentFile?.mkdirs()
        val request = Request.Builder().url(offer.url).header("User-Agent", "tokifonts/${offer.version}").build()
        http.newCall(request).execute().use { response ->
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
