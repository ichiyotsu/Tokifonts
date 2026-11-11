package com.ichiyotsu.tokifonts

import android.app.Application
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext

const val DEFAULT_PREVIEW = "天地玄黄，宇宙洪荒\nThe quick brown fox jumps over the lazy dog\n0123456789"
const val AUTO_PREVIEW_BYTES = 4_500_000L

sealed interface FaceStatus {
    data object Absent : FaceStatus
    data object Queued : FaceStatus
    data class Downloading(val progress: Float) : FaceStatus
    data class Ready(val file: File) : FaceStatus
    data class Failed(val message: String) : FaceStatus
}

class FontsViewModel(app: Application, private val savedState: SavedStateHandle = SavedStateHandle()) : AndroidViewModel(app) {
    private val store = FontStore(app)
    private val prefs = app.getSharedPreferences(FontStore.PREFS, Application.MODE_PRIVATE)
    private val main = Handler(Looper.getMainLooper())
    private val jobs = java.util.concurrent.ConcurrentHashMap<String, Job>()
    private val slots = Semaphore(2)
    private val typefaces = LruCache<String, Typeface>(8)

    var catalog by mutableStateOf<Catalog?>(null)
        private set
    var loadError by mutableStateOf<String?>(null)
        private set
    var downloads by mutableStateOf<Map<String, FaceStatus>>(emptyMap())
        private set
    var themeMode by mutableIntStateOf(0)
    var dynamicColor by mutableStateOf(true)
    var previewText by mutableStateOf(DEFAULT_PREVIEW)
    var previewOverrides by mutableStateOf<Map<String, String>>(emptyMap())
    var query by mutableStateOf(savedState["query"] ?: "")
    var searchOpen by mutableStateOf(savedState["searchOpen"] ?: false)
    var groupId by mutableStateOf(savedState["groupId"] ?: "all")
    var sectionId by mutableStateOf(savedState["sectionId"] ?: "all")
    var tab by mutableIntStateOf(savedState["tab"] ?: 0)
    var openFamilyId by mutableStateOf<String?>(savedState["openFamilyId"])
    var installPage by mutableStateOf(savedState["installPage"] ?: "fonts")
    var checkOnLaunch by mutableStateOf(true)
    var updateOffer by mutableStateOf<UpdateOffer?>(null)
    var updateChecking by mutableStateOf(false)
    var updateDownloading by mutableStateOf(false)
    var updateProgress by mutableFloatStateOf(0f)
    var pendingUpdate by mutableStateOf<File?>(null)
        private set
    private var updateDismissed = false
    var message by mutableStateOf<String?>(null)
    /** 每次 flash 自增：同一条文案连发两次也不会被 LaunchedEffect 当成“没变”丢掉。 */
    var messageSeq by mutableIntStateOf(0)
        private set
    var cacheBytes by mutableLongStateOf(0L)
        private set
    var exported by mutableStateOf<Set<String>>(emptySet())
        private set

    init {
        themeMode = prefs.getInt(KEY_THEME, 0)
        dynamicColor = prefs.getBoolean(KEY_DYNAMIC, true)
        previewText = prefs.getString(KEY_PREVIEW, DEFAULT_PREVIEW) ?: DEFAULT_PREVIEW
        previewOverrides = decodeOverrides(prefs.getString(KEY_PREVIEW_OVERRIDES, null))
        checkOnLaunch = prefs.getBoolean(KEY_UPDATE_LAUNCH, true)
        exported = prefs.getStringSet(KEY_EXPORTED, emptySet()).orEmpty().toSet()
        viewModelScope.launch {
            // 导航状态写进 SavedStateHandle：进程被杀后回来，tab、详情页、搜索、筛选都还在。
            snapshotFlow { listOf(tab, openFamilyId, installPage, query, groupId, sectionId, searchOpen) }.collect {
                savedState["tab"] = tab
                savedState["openFamilyId"] = openFamilyId
                savedState["installPage"] = installPage
                savedState["query"] = query
                savedState["groupId"] = groupId
                savedState["sectionId"] = sectionId
                savedState["searchOpen"] = searchOpen
            }
        }
        loadCatalog()
    }

    /** 目录加载失败后，错误页的“重试”按钮走这里。 */
    fun retryLoad() {
        if (catalog != null) return
        loadError = null
        loadCatalog()
    }

    private fun loadCatalog() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val json = getApplication<Application>().assets.open("catalog.json").bufferedReader().use { it.readText() }
                val parsed = Catalog.parse(json)
                store.seedIfFirstLaunch()
                onMain {
                    catalog = parsed
                    refreshLocal()
                    if (parsed.skipped > 0) flash("目录里有 ${parsed.skipped} 条损坏记录，已跳过")
                }
            } catch (error: Throwable) {
                onMain { loadError = error.message ?: "字库索引读取失败" }
            }
        }
    }

    fun prepareForTest() {
        val json = getApplication<Application>().assets.open("catalog.json").bufferedReader().use { it.readText() }
        catalog = Catalog.parse(json)
        store.seedIfFirstLaunch()
        refreshLocal()
        catalog?.face("3001")?.let { face ->
            val file = store.file(face)
            if (file.exists()) typefaces.put(typeKey(face, null), Typeface.createFromFile(file))
        }
    }

    fun status(id: String): FaceStatus = downloads[id] ?: FaceStatus.Absent

    fun setGroup(id: String) {
        groupId = id
        val section = catalog?.sections?.firstOrNull { it.id == sectionId }
        if (id != "all" && section != null && section.group != id) sectionId = "all"
    }

    fun persistTheme(mode: Int) {
        themeMode = mode
        prefs.edit().putInt(KEY_THEME, mode).apply()
    }

    fun toggleDynamic() {
        dynamicColor = !dynamicColor
        prefs.edit().putBoolean(KEY_DYNAMIC, dynamicColor).apply()
        flash(if (dynamicColor) "已开启动态取色" else "已关闭动态取色")
    }

    fun setDynamic(enabled: Boolean) {
        dynamicColor = enabled
        prefs.edit().putBoolean(KEY_DYNAMIC, enabled).apply()
    }

    fun updatePreview(text: String) {
        previewText = text
        savePreviewSoon()
    }

    fun previewFor(familyId: String): String = previewOverrides[familyId] ?: previewText

    fun hasFamilyPreview(familyId: String) = familyId in previewOverrides

    fun setFamilyPreview(familyId: String, text: String) {
        previewOverrides = if (text == previewText) previewOverrides - familyId else previewOverrides + (familyId to text)
        savePreviewSoon()
    }

    fun clearFamilyPreview(familyId: String) {
        previewOverrides = previewOverrides - familyId
        savePreviewSoon()
    }

    /** 预览文字每敲一键只改内存，停 300ms 后再写盘，避免输入时反复写 SharedPreferences。 */
    private var previewSaveJob: Job? = null

    private fun savePreviewSoon() {
        previewSaveJob?.cancel()
        previewSaveJob = viewModelScope.launch {
            delay(300)
            prefs.edit().putString(KEY_PREVIEW, previewText).apply()
            saveOverrides()
        }
    }

    private fun saveOverrides() {
        val json = org.json.JSONObject()
        previewOverrides.forEach { (id, text) -> json.put(id, text) }
        prefs.edit().putString(KEY_PREVIEW_OVERRIDES, json.toString()).apply()
    }

    fun resetPreview() {
        updatePreview(DEFAULT_PREVIEW)
    }

    fun persistCheckOnLaunch(enabled: Boolean) {
        checkOnLaunch = enabled
        prefs.edit().putBoolean(KEY_UPDATE_LAUNCH, enabled).apply()
    }

    fun checkUpdate(manual: Boolean) {
        if (updateChecking || updateDownloading) return
        // 已有下载好待安装的更新时不再重查：旋转重建会重跑启动检查，成功回调会清掉
        // pendingUpdate，用户就得把同一个 APK 再下载一遍。
        if (pendingUpdate != null) return
        if (!manual && updateDismissed) return
        if (manual) updateDismissed = false
        updateChecking = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val offer = AppUpdate.latest(BuildConfig.VERSION_NAME)
                onMain {
                    updateChecking = false
                    pendingUpdate = null
                    if (offer == null) {
                        updateOffer = null
                        if (manual) flash("已是最新版本")
                    } else {
                        updateOffer = offer
                    }
                }
            } catch (error: Throwable) {
                onMain {
                    updateChecking = false
                    if (manual) flash(error.message ?: "检查更新失败")
                }
            }
        }
    }

    fun dismissUpdate() {
        updateDismissed = true
        updateOffer = null
    }

    fun downloadUpdate() {
        val offer = updateOffer ?: return
        if (updateDownloading) return
        // 必须同步置位（和 checkUpdate 一样）：置位若放进协程的 onMain 里，连点两次时
        // 第二次的入口检查会在标志生效前通过，两个协程会并发写同一个 APK 文件。
        updateDownloading = true
        updateProgress = 0f
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = File(getApplication<Application>().cacheDir, "updates/${offer.assetName}")
                AppUpdate.download(offer, file) { progress -> onMain { updateProgress = progress } }
                onMain { pendingUpdate = file; updateDownloading = false }
            } catch (error: Throwable) {
                onMain {
                    updateDownloading = false
                    flash(error.message ?: "下载更新失败")
                }
            }
        }
    }

    fun refreshLocal() {
        val parsed = catalog ?: return
        // 进程被杀遗留的 .part 没有任何代码路径会删（storedBytes 也不统计它）：刷新时顺手清掉。
        store.cleanOrphanParts(jobs.keys.toSet())
        val next = mutableMapOf<String, FaceStatus>()
        for ((id, state) in downloads) {
            if (state is FaceStatus.Downloading || state is FaceStatus.Queued || state is FaceStatus.Failed) next[id] = state
        }
        for (face in parsed.families.flatMap { it.faces }) {
            val file = store.file(face)
            // 只判存在不够：解压/下载失败残留的坏文件至少要过一次文件头校验才标 Ready，
            // 与 download() 落盘后的最终校验口径一致（按 dest 扩展名判格式）。
            if (file.exists() && file.length() > 0L && FontStore.looksValid(file, file.extension)) {
                next[face.id] = FaceStatus.Ready(file)
            }
        }
        downloads = next
        cacheBytes = store.storedBytes()
    }

    fun download(face: FontFace) {
        val state = status(face.id)
        if (state is FaceStatus.Ready || state is FaceStatus.Downloading || state is FaceStatus.Queued) return
        if (jobs.containsKey(face.id)) return
        setStatus(face.id, FaceStatus.Queued)
        // LAZY 启动 + jobRef 回填：先把 job 登记进 jobs 再 start，保证 finally 按值删除时
        // 引用必然已赋值、且协程不可能在登记前就跑完（原先是先 launch 后赋值，有极小竞态窗口）。
        var jobRef: Job? = null
        val job = viewModelScope.launch(Dispatchers.IO, start = kotlinx.coroutines.CoroutineStart.LAZY) {
            var acquired = false
            try {
                slots.acquire()
                acquired = true
                if (!isActive) return@launch
                setStatus(face.id, FaceStatus.Downloading(0f))
                store.download(face) { progress -> setStatus(face.id, FaceStatus.Downloading(progress)) }
                onMain {
                    // 解压是阻塞调用，取消打不断：取消/清除全部之后任务仍会走到这里。
                    // 已取消的 job 不许把状态写回 Ready，否则界面上“复活”为已下载。
                    if (jobRef?.isCancelled == false) {
                        downloads = downloads + (face.id to FaceStatus.Ready(store.file(face)))
                        cacheBytes = store.storedBytes()
                    }
                }
            } catch (cancelled: CancellationException) {
                store.part(face).delete()
                setStatus(face.id, FaceStatus.Absent)
                throw cancelled
            } catch (error: Throwable) {
                store.part(face).delete()
                setStatus(face.id, FaceStatus.Failed(error.message ?: "下载失败"))
            } finally {
                if (acquired) slots.release()
                // 按值删除：取消后立刻重下同一字体时，旧任务的 finally 不能抹掉新任务的登记。
                jobRef?.let { jobs.remove(face.id, it) }
            }
        }
        jobRef = job
        jobs[face.id] = job
        job.start()
    }

    fun downloadAll(family: FontFamily) {
        family.faces.forEach { download(it) }
    }

    fun cancel(face: FontFace) {
        store.cancel(face.id)
        jobs.remove(face.id)?.cancel()
        store.part(face).delete()
        setStatus(face.id, FaceStatus.Absent)
    }

    fun delete(face: FontFace) {
        cancel(face)
        store.delete(face)
        exported = exported - face.id
        prefs.edit().putStringSet(KEY_EXPORTED, HashSet(exported)).apply()
        typefaces.evictAll()
        refreshLocal()
    }

    fun clearAll() {
        jobs.values.toList().forEach { it.cancel() }
        jobs.clear()
        store.clear()
        exported = emptySet()
        prefs.edit().putStringSet(KEY_EXPORTED, emptySet()).apply()
        downloads = emptyMap()
        cacheBytes = 0L
        typefaces.evictAll()
        flash("已清除本机字体")
    }

    fun export(face: FontFace) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val name = store.export(face)
                onMain {
                    exported = exported + face.id
                    prefs.edit().putStringSet(KEY_EXPORTED, HashSet(exported)).apply()
                    flash("已保存到下载目录：$name")
                }
            } catch (error: Throwable) {
                onMain { flash(error.message ?: "保存失败") }
            }
        }
    }

    fun share(face: FontFace) = runCatching { store.share(face) }.onFailure { flash(it.message ?: "无法分享") }

    fun openWith(face: FontFace) = runCatching { store.openWith(face) }.onFailure { flash(it.message ?: "没有可以打开字体的应用") }

    fun cachedTypeface(face: FontFace, wght: Int?): Typeface? = typefaces.get(typeKey(face, wght))

    suspend fun typefaceFor(face: FontFace, wght: Int?): Typeface? = withContext(Dispatchers.IO) {
        val file = store.file(face)
        if (!file.exists()) return@withContext null
        val key = typeKey(face, wght)
        typefaces.get(key)?.let { return@withContext it }
        // 文件头校验只看前 12 字节，截断/损坏的字体到这里才可能暴露：创建失败就标为失败，
        // 让用户能重新下载，而不是每次打开列表/详情都走到无保护的 Typeface 创建。
        val built = runCatching {
            if (face.variable && wght != null) {
                Typeface.Builder(file).setFontVariationSettings("'wght' ${wght.coerceIn(1, 1000)}").build()
            } else {
                Typeface.createFromFile(file)
            }
        }.getOrNull()
        if (built == null) {
            setStatus(face.id, FaceStatus.Failed("字体文件损坏，请重新下载"))
            return@withContext null
        }
        typefaces.put(key, built)
        built
    }

    fun flash(text: String) {
        message = text
        messageSeq++
    }

    fun consumeMessage() {
        message = null
    }

    private fun typeKey(face: FontFace, wght: Int?) = face.id + ":" + if (face.variable) (wght ?: face.weight) else 0

    private fun setStatus(id: String, status: FaceStatus) = onMain { downloads = downloads + (id to status) }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }

    companion object {
        private const val KEY_THEME = "theme"
        private const val KEY_DYNAMIC = "dynamic"
        private const val KEY_PREVIEW = "preview"
        private const val KEY_PREVIEW_OVERRIDES = "preview_overrides"
        private const val KEY_EXPORTED = "exported"
        private const val KEY_UPDATE_LAUNCH = "update_on_launch"

        private fun decodeOverrides(raw: String?): Map<String, String> {
            if (raw.isNullOrBlank()) return emptyMap()
            val json = org.json.JSONObject(raw)
            return buildMap {
                json.keys().forEach { key -> put(key, json.optString(key)) }
            }
        }
    }
}
