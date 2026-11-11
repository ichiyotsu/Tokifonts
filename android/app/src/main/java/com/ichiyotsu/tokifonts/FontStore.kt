package com.ichiyotsu.tokifonts

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile
import kotlin.coroutines.cancellation.CancellationException
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.archivers.sevenz.SevenZFile

class FontStore(private val context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val calls = ConcurrentHashMap<String, Call>()
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.MINUTES)
        .build()

    val dir: File = File(context.filesDir, "fonts").apply { mkdirs() }

    fun file(face: FontFace): File = File(dir, "${face.id}.${face.usableExt()}")

    fun part(face: FontFace): File = File(dir, "${face.id}.part")

    fun seedIfFirstLaunch() {
        // 缺哪个补哪个：按文件存在性判断，不再只认一次性的 KEY_SEEDED 标志。
        // 这样“清除下载”后，下次启动会自动把内置的三档 Google Sans 补回来。
        val bundled = listOf(
            "3001" to R.font.google_sans_400,
            "3003" to R.font.google_sans_500,
            "3007" to R.font.google_sans_700,
        )
        bundled.forEach { (id, res) ->
            val dest = File(dir, "$id.ttf")
            if (!dest.exists() || dest.length() == 0L) {
                context.resources.openRawResource(res).use { input ->
                    dest.outputStream().use { input.copyTo(it) }
                }
            }
        }
        prefs.edit().putBoolean(KEY_SEEDED, true).apply()
    }

    fun storedBytes(): Long = dir.listFiles().orEmpty().filter { it.isFile && !it.name.endsWith(".part") }.sumOf { it.length() }

    fun cancel(faceId: String) {
        calls.remove(faceId)?.cancel()
    }

    fun download(face: FontFace, onProgress: (Float) -> Unit) {
        val dest = file(face)
        val partial = part(face)
        partial.delete()
        var lastError: Throwable? = null
        for (url in face.urls()) {
            try {
                fetch(face.id, url, partial, onProgress)
                if (!looksValid(partial, face.format)) error("下载到的不是字体文件")
                when (face.format) {
                    "zip" -> {
                        onProgress(-1f)
                        extractZip(partial, dest, face.innerName)
                        partial.delete()
                    }
                    "7z" -> {
                        onProgress(-1f)
                        extract7z(partial, dest, face.innerName)
                        partial.delete()
                    }
                    else -> {
                        if (!partial.renameTo(dest)) {
                            partial.copyTo(dest, overwrite = true)
                            partial.delete()
                        }
                    }
                }
                if (!dest.exists() || dest.length() == 0L || !looksValid(dest, dest.extension)) error("字体文件为空")
                return
            } catch (cancelled: CancellationException) {
                partial.delete()
                throw cancelled
            } catch (error: Throwable) {
                partial.delete()
                if (dest.exists() && !looksValid(dest, dest.extension)) dest.delete()
                lastError = error
            }
        }
        throw lastError ?: IllegalStateException("下载失败")
    }

    fun delete(face: FontFace) {
        cancel(face.id)
        file(face).delete()
        part(face).delete()
    }

    fun clear() {
        calls.values.forEach { it.cancel() }
        calls.clear()
        dir.listFiles()?.forEach { it.delete() }
    }

    fun export(face: FontFace): String {
        val source = file(face)
        if (!source.exists()) error("还没有下载这个字体")
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, face.exportName)
            put(MediaStore.Downloads.MIME_TYPE, mime(face))
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("无法写入下载目录")
        resolver.openOutputStream(uri)?.use { out -> source.inputStream().use { it.copyTo(out) } } ?: error("无法写入下载目录")
        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return face.exportName
    }

    fun share(face: FontFace) = send(face, Intent.ACTION_SEND, "分享字体文件")

    fun openWith(face: FontFace) = send(face, Intent.ACTION_VIEW, "用其他应用打开")

    private fun send(face: FontFace, action: String, title: String) {
        val source = file(face)
        if (!source.exists()) error("还没有下载这个字体")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", source)
        val intent = Intent(action).apply {
            if (action == Intent.ACTION_SEND) {
                type = mime(face)
                putExtra(Intent.EXTRA_STREAM, uri)
            } else {
                setDataAndType(uri, mime(face))
            }
            clipData = ClipData.newRawUri(face.exportName, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun fetch(faceId: String, url: String, dest: File, onProgress: (Float) -> Unit) {
        val request = Request.Builder().url(url).header("User-Agent", "tokifonts/${BuildConfig.VERSION_NAME}").build()
        val call = client.newCall(request)
        calls[faceId] = call
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
                val body = response.body ?: error("空响应")
                val total = body.contentLength()
                body.byteStream().use { input ->
                    dest.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var read = 0L
                        var last = -1f
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            output.write(buffer, 0, n)
                            read += n
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
            }
        } catch (error: IOException) {
            if (call.isCanceled()) throw CancellationException("已取消")
            throw error
        } finally {
            calls.remove(faceId, call)
        }
    }

    /**
     * 解包时优先取 innerName 指定的那一个字体；innerName 为空时才取第一个字体文件，
     * 找不到指定文件时报错，而不是悄悄装上压缩包里的“第一个”。
     */
    private fun extractZip(archive: File, dest: File, innerName: String) {
        ZipFile(archive).use { zip ->
            val wanted = innerName.substringAfterLast("/")
            val entry = zip.entries().asSequence().firstOrNull { item ->
                val name = item.name.substringAfterLast("/")
                !item.isDirectory && (name.endsWith(".ttf") || name.endsWith(".otf")) &&
                    (wanted.isBlank() || name.equals(wanted, ignoreCase = true))
            } ?: if (wanted.isBlank()) {
                error("压缩包里没有字体")
            } else {
                error("压缩包里没有 $wanted")
            }
            zip.getInputStream(entry).use { input -> dest.outputStream().use { input.copyTo(it) } }
        }
    }

    private fun extract7z(archive: File, dest: File, innerName: String) {
        SevenZFile(archive).use { seven ->
            val wanted = innerName.substringAfterLast("/")
            var entry = seven.nextEntry
            while (entry != null) {
                val name = entry.name.substringAfterLast("/")
                if (!entry.isDirectory && (name.endsWith(".ttf") || name.endsWith(".otf"))) {
                    if (wanted.isBlank() || name.equals(wanted, ignoreCase = true)) {
                        dest.outputStream().use { output ->
                            val buffer = ByteArray(8192)
                            while (true) {
                                val n = seven.read(buffer)
                                if (n < 0) break
                                output.write(buffer, 0, n)
                            }
                        }
                        return
                    }
                }
                entry = seven.nextEntry
            }
            // 7z 是流式读，命中目标前已跳过前面的条目：innerName 为空时上面已在第一个字体处返回；
            // 到这里说明 innerName 指定了但包里没有。
            if (wanted.isBlank()) error("压缩包里没有字体") else error("压缩包里没有 $wanted")
        }
    }

    private fun mime(face: FontFace): String = if (face.usableExt() == "otf") "font/otf" else "font/ttf"

    companion object {
        const val PREFS = "tokifonts"
        const val KEY_SEEDED = "seeded_ui_fonts"

        fun looksValid(file: File, format: String): Boolean {
            if (!file.exists() || file.length() < 12) return false
            val head = ByteArray(12)
            file.inputStream().use { stream ->
                if (stream.read(head) < 12) return false
            }
            val zip = head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte()
            val seven = head[0] == 0x37.toByte() && head[1] == 0x7A.toByte() && head[2] == 0xBC.toByte()
            val otto = head[0] == 'O'.code.toByte() && head[1] == 'T'.code.toByte() && head[2] == 'T'.code.toByte() && head[3] == 'O'.code.toByte()
            val trueType = head[0] == 0.toByte() && head[1] == 1.toByte() && head[2] == 0.toByte() && head[3] == 0.toByte()
            val trueTag = head[0] == 't'.code.toByte() && head[1] == 'r'.code.toByte() && head[2] == 'u'.code.toByte() && head[3] == 'e'.code.toByte()
            val font = otto || trueType || trueTag
            return when (format.lowercase()) {
                "zip" -> zip
                "7z" -> seven
                else -> font
            }
        }
    }
}

fun openLink(context: Context, url: String): Boolean {
    // 设备上没有能打开链接的应用时，startActivity 会抛 ActivityNotFoundException：
    // 返回 false 让调用方提示，而不是直接崩溃。
    return runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    }.getOrDefault(false)
}

/** 当前是否连着 Wi-Fi：详情页自动下载预览只在 Wi-Fi 下触发，移动数据下不静默耗流量。 */
fun isWifiConnected(context: Context): Boolean {
    val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
        ?: return false
    val network = manager.activeNetwork ?: return false
    val capabilities = manager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
}
