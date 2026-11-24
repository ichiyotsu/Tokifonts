package com.ichiyotsu.tokifonts

import android.app.Application
import android.graphics.Typeface
import android.os.Build
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
import java.io.IOException
import java.util.zip.ZipFile
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
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

/** 换系统字体模块的状态。 */
enum class SystemFontModuleState { NOT_GENERATED, GENERATED, INSTALLED, FAILED }

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
    /** 种子色下标（语义 token 的 seeds 顺序）；点选种子即关动态取色，见 [setSeed]。 */
    var seedIndex by mutableIntStateOf(0)
    var reduceMotion by mutableStateOf(false)
    var backdropBlur by mutableStateOf(true)
    var previewText by mutableStateOf(DEFAULT_PREVIEW)
    var previewOverrides by mutableStateOf<Map<String, String>>(emptyMap())
    var query by mutableStateOf(savedState["query"] ?: "")
    var searchOpen by mutableStateOf(savedState["searchOpen"] ?: false)
    var groupId by mutableStateOf(savedState["groupId"] ?: "all")
    var sectionId by mutableStateOf(savedState["sectionId"] ?: "all")
    var tab by mutableIntStateOf(savedState["tab"] ?: 0)
    var openFamilyId by mutableStateOf<String?>(savedState["openFamilyId"])
    var installPage by mutableStateOf(savedState["installPage"] ?: "fonts")
    /** 设置页的二级页面："main" 主页 / "files" 字体文件管理。 */
    var settingsPage by mutableStateOf(savedState["settingsPage"] ?: "main")
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

    /** 一键下载全部的聚合进度（非 null = 进行中；状态在 ViewModel，切标签页不丢失）。 */
    var bulk by mutableStateOf<BulkProgress?>(null)
        private set

    // ---- 换系统字体（安装页默认内容）----
    /** 模块状态：未生成 / 已生成 / 已安装 / 失败。 */
    var sysFontModuleState by mutableStateOf(SystemFontModuleState.NOT_GENERATED)
        private set
    var sysFontDevice by mutableStateOf<DeviceCheck?>(null)
        private set
    /** root 检测结果：null = 还没测过。 */
    var sysFontRoot by mutableStateOf<Boolean?>(null)
        private set
    var sysFontRootChecking by mutableStateOf(false)
        private set
    // ---- 安装页四步向导（Shizuku → root 工具 → KernelSU → 模块）----
    /** Shizuku 是否已装（按包名 + 启动 intent 检测）：null = 还没检查。 */
    var sysFontShizukuInstalled by mutableStateOf<Boolean?>(null)
        private set
    /** KernelSU Manager 是否已装：null = 还没检查。 */
    var sysFontKsuManagerInstalled by mutableStateOf<Boolean?>(null)
        private set
    /** 已装的 root 工具（按包名检测到、有启动 intent 的）。 */
    var sysFontInstalledRootTools by mutableStateOf<Set<RootTool>>(emptySet())
        private set
    /**
     * 用户手动确认“已装好”的 root 工具：极少数情况下包名检测不到
     * （如 root 工具换了包名），装好后点“我已装好”也可以放行第 2 步；
     * 重新检查设备时不清零（是用户自己的确认，不是检测结果）。
     */
    var sysFontConfirmedRootTools by mutableStateOf<Set<RootTool>>(emptySet())
        private set

    fun confirmRootToolInstalled(tool: RootTool) {
        sysFontConfirmedRootTools = sysFontConfirmedRootTools + tool
    }

    /**
     * 第 2 步的工具推荐：按当前设备（机型 + 固件 + 内核）算，
     * 设备还没检查时为 null。推荐本身不改变设备是否支持换字体，
     * 只决定第 2 步用哪个工具（见 [FirmwareSupport.recommendTool]）。
     */
    val sysFontToolRecommendation: ToolRecommendation?
        get() = sysFontDevice?.let { device ->
            FirmwareSupport.recommendTool(
                device.model,
                device.firmwareDisplay,
                device.firmwareIncremental,
                device.kernelVersion,
            )
        }

    /** 四步向导的外部应用是否已装（Shizuku / KernelSU Manager / 已知包名的 root 工具）。 */
    fun checkWizardApps() {
        val packageManager = getApplication<Application>().packageManager
        fun isInstalled(packageName: String): Boolean =
            runCatching { packageManager.getLaunchIntentForPackage(packageName) != null }
                .getOrDefault(false)
        sysFontShizukuInstalled = isInstalled(WizardApps.SHIZUKU_PACKAGE)
        sysFontKsuManagerInstalled = isInstalled(WizardApps.KERNELSU_MANAGER_PACKAGE)
        sysFontInstalledRootTools = RootTool.entries.filter { tool ->
            val packageName = WizardApps.launchPackageFor(tool) ?: return@filter false
            isInstalled(packageName)
        }.toSet()
        // Shizuku 是否正在运行：pingBinder（Shizuku API，本地 AAR）。Shizuku 没起来
        // 时返回 false；任何异常都当作“没查到”，不崩溃、不谎报已运行。
        sysFontShizukuRunning = runCatching { rikka.shizuku.Shizuku.pingBinder() }.getOrNull()
    }

    // ---- 每日固件支持 feed（安装页顶栏更新按钮）----
    /** 安装页顶栏下的 feed 状态行（界面直接展示）：用的哪份表、更新到哪天、失败原因。 */
    var feedStatus by mutableStateOf("设备列表：内置（$BUILTIN_FEED_DATE）")
        private set
    /** 正在拉取 feed（顶栏更新按钮转圈）。 */
    var feedUpdating by mutableStateOf(false)
        private set
    /** 当前生效的 feed 的 updatedAt（null = 内置表）。 */
    var feedUpdatedAt by mutableStateOf<String?>(null)
        private set

    private fun feedCacheFile(): File =
        File(getApplication<Application>().filesDir, FirmwareFeed.CACHE_FILE_NAME)

    private fun activeFeedEntryCount(): Int {
        val tables = FirmwareSupport.activeTables()
        return tables.entries.size + tables.rmgPayloads.size + tables.portedPayloads.size
    }

    /**
     * 启动时读本地缓存的 feed：校验通过就换成缓存表并重新匹配（不联网）；
     * 缓存缺失 / 校验失败就沿用内置表，并在状态行明示。
     */
    fun loadCachedFeed() {
        val cache = feedCacheFile()
        if (!cache.exists()) return
        val text = runCatching { cache.readText() }.getOrNull() ?: return
        when (val result = FirmwareFeed.validateFeed(text, null)) {
            is FirmwareFeed.FeedValidation.Valid -> {
                FirmwareSupport.useTables(FirmwareFeed.feedToTables(result.json))
                feedUpdatedAt = FirmwareFeed.updatedAtOf(result.json)
                feedStatus = "设备列表：已更新到 ${feedUpdatedAt ?: "未知日期"}（本地缓存）"
                if (sysFontDevice != null) checkSystemFontDevice()
            }
            is FirmwareFeed.FeedValidation.Invalid -> {
                FirmwareSupport.resetTables()
                feedStatus = "设备列表：内置（$BUILTIN_FEED_DATE），本地缓存损坏已弃用：${result.reason}"
            }
        }
    }

    /**
     * 点顶栏更新按钮：从 Pro 分支拉 docs/firmware-support.json，按 SCHEMA 校验，
     * 通过后缓存到本地并用新表重新匹配；无网或拉取/校验失败时保留内置表
     * （或上一次好的表），并在状态行明示失败原因。拉取中有转圈，成功/失败与
     * “已是最新/已更新到某日”都有明确反馈。
     */
    fun refreshFeed() {
        if (feedUpdating) return
        feedUpdating = true
        feedStatus = "设备列表：正在拉取最新列表…"
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val client = okhttp3.OkHttpClient.Builder()
                    .callTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                val request = okhttp3.Request.Builder()
                    .url(FirmwareFeed.FEED_URL)
                    .header("User-Agent", "tokifontspro/${BuildConfig.VERSION_NAME}")
                    .build()
                val text = client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) error("拉取设备列表失败 HTTP ${response.code}")
                    response.body?.string() ?: error("设备列表是空的")
                }
                val previousCount = activeFeedEntryCount()
                when (val result = FirmwareFeed.validateFeed(text, previousCount)) {
                    is FirmwareFeed.FeedValidation.Valid -> {
                        val updatedAt = FirmwareFeed.updatedAtOf(result.json)
                        val alreadyLatest = updatedAt != null && updatedAt == feedUpdatedAt
                        FirmwareSupport.useTables(FirmwareFeed.feedToTables(result.json))
                        runCatching { feedCacheFile().writeText(text) }
                        onMain {
                            feedUpdating = false
                            feedUpdatedAt = updatedAt
                            feedStatus = if (alreadyLatest) {
                                "设备列表：已是最新（${updatedAt}）"
                            } else {
                                "设备列表：已更新到 ${updatedAt ?: "未知日期"}"
                            }
                            checkSystemFontDevice()
                            flash(feedStatus.removePrefix("设备列表："))
                        }
                    }
                    is FirmwareFeed.FeedValidation.Invalid -> {
                        onMain {
                            feedUpdating = false
                            feedStatus = "设备列表更新失败，用的${if (FirmwareSupport.usingBuiltinTables()) "内置列表" else "上一次好的列表"}（${feedUpdatedAt ?: BUILTIN_FEED_DATE}）：${result.reason}"
                            flash("更新失败，用的还是原来的设备列表")
                        }
                    }
                }
            } catch (error: Throwable) {
                onMain {
                    feedUpdating = false
                    feedStatus = "设备列表更新失败，用的${if (FirmwareSupport.usingBuiltinTables()) "内置列表" else "上一次好的列表"}（${feedUpdatedAt ?: BUILTIN_FEED_DATE}）：${error.message ?: "无网络"}"
                    flash("更新失败：${error.message ?: "无网络"}，用的还是原来的设备列表")
                }
            }
        }
    }

    // ---- 保守内嵌一键 root（第 2 步主路；执行链未接通前只做预检）----
    /** Shizuku 是否正在运行（pingBinder 结果）：null = 还没检查。 */
    var sysFontShizukuRunning by mutableStateOf<Boolean?>(null)
        private set
    /** 内嵌一键 root 的运行状态（见 [EmbeddedRootState]：只有三种诚实状态）。 */
    var embeddedRootState by mutableStateOf(EmbeddedRootState.IDLE)
        private set
    /** 最近一次预检的逐项结果（界面直接展示）。 */
    var embeddedRootChecks by mutableStateOf<List<EmbeddedRootCheck>>(emptyList())
        private set
    /** 内嵌产物是否都在（assets/embed/ 逐个试读的结果）。 */
    var embeddedRootAssetsPresent by mutableStateOf<Map<String, Boolean>>(emptyMap())
        private set
    /** 一次启动只跑一次的守卫（98260 的硬规则）。 */
    val embeddedRootGuard = EmbeddedRootAttemptGuard()

    /** 试读 assets/embed/ 下的内嵌产物，缺哪个就记哪个（不读内容，只确认在位）。 */
    fun checkEmbeddedAssets() {
        val assets = getApplication<Application>().assets
        embeddedRootAssetsPresent = EmbeddedAssets.ALL.associateWith { path ->
            runCatching { assets.open(path).close() }.isSuccess
        }
    }

    /**
     * 内嵌一键 root：只跑预检，不执行 exploit。
     * 预检全过 → [EmbeddedRootState.READY_NOT_WIRED]：执行链（Shizuku UserService
     * 胶水 + 载荷加载器）尚未接通，本版停在这里，绝不假装能跑；
     * 预检不过 → [EmbeddedRootState.FAILED]，只报失败原因；失败后没有“立即重试”
     * 按钮（98260 失败要完整重启手机才能再试，见预检项）。
     */
    fun runEmbeddedRootPrecheck() {
        checkWizardApps()
        embeddedRootState = EmbeddedRootState.CHECKING
        if (embeddedRootAssetsPresent.isEmpty()) checkEmbeddedAssets()
        val device = sysFontDevice
        if (device == null) checkSystemFontDevice()
        val checks = embeddedRootPrecheck(
            recommendation = sysFontToolRecommendation,
            shizukuRunning = sysFontShizukuRunning,
            calibratedConfirmed = sysFontCalibratedConfirmed,
            guard = embeddedRootGuard,
            embeddedAssetsPresent = embeddedRootAssetsPresent,
        )
        embeddedRootChecks = checks
        embeddedRootState = if (checks.allPassed()) {
            EmbeddedRootState.READY_NOT_WIRED
        } else {
            EmbeddedRootState.FAILED
        }
    }

    /**
     * 第 4 步可视化管理之“调整并重新安装”：改脚本开关或粗细后，
     * 重新生成模块 + 重装 + 重启 zygote，一次点完。字体文件不重下
     * （已下载且校验通过的直接复用）；任何一步失败都走自动取消
     * （停、清理、状态复位、只报失败原因），与一键流程同款。
     */
    fun adjustAndReinstallModule() {
        if (sysFontWorking) return
        val selected = enabledSystemFontScripts()
        if (selected.isEmpty()) {
            viewModelScope.launch(Dispatchers.IO) { autoCancelSystemFont("至少选一个语种") }
            return
        }
        if (!sysFontInstallAllowed()) {
            viewModelScope.launch(Dispatchers.IO) { autoCancelSystemFont("设备未通过检查，或待验证固件还没勾选风险确认，无法重新安装") }
            return
        }
        sysFontWorking = true
        sysFontError = null
        sysFontVerifyWarning = null
        sysFontStep = "正在重新生成模块…"
        viewModelScope.launch(Dispatchers.IO) {
            generateSystemFontModuleInternal(selected)?.let { autoCancelSystemFont(it); return@launch }
            onMain { sysFontStep = "正在重新安装并软重启…" }
            val installed = installSystemFontModuleInternal()
            if (installed.error != null) {
                autoCancelSystemFont(
                    installed.error,
                    moduleWritten = installed.moduleWritten,
                    postVerifyFailed = installed.postVerifyFailed,
                )
                return@launch
            }
            onMain {
                sysFontWorking = false
                sysFontStep = null
                sysFontProgress = 1f
                sysFontModuleState = SystemFontModuleState.INSTALLED
                flash("已按新的开关与粗细重新安装，系统字体已更新")
            }
        }
    }

    /** 五档语种开关（默认全开，SharedPreferences 持久化）。 */
    var sysFontScripts by mutableStateOf<Map<SystemFontScript, Boolean>>(
        SystemFontScript.entries.associateWith { it.defaultEnabled },
    )
        private set
    var sysFontWorking by mutableStateOf(false)
        private set
    var sysFontProgress by mutableFloatStateOf(0f)
        private set
    /** 最近一步的错误文案（null = 无错误），界面直接展示。 */
    var sysFontError by mutableStateOf<String?>(null)
        private set
    /** 本次安装的改动清单（安装前确认弹窗原样展示）。 */
    var sysFontChangeList by mutableStateOf<List<SystemFont.FontChange>>(emptyList())
        private set
    /** 生成时 fonts.xml 结构校验是否通过（安装门禁之一）。 */
    var sysFontXmlValidated by mutableStateOf(false)
        private set
    /** 生成时全部组合字体文件是否通过校验（安装门禁之一）。 */
    var sysFontFilesVerified by mutableStateOf(false)
        private set
    /** 安装走的是哪条路（"ksud" / "复制"，安装后告诉用户）。 */
    var sysFontInstallMethod by mutableStateOf<String?>(null)
        private set
    /** 安装后验证未通过时的警告文案（null = 验证通过或还没验证）。 */
    var sysFontVerifyWarning by mutableStateOf<String?>(null)
        private set
    /** 回滚删了模块但 zygote 重启没确认时为 true：界面把“重启手机”顶到前面。 */
    var sysFontNeedsReboot by mutableStateOf(false)
        private set
    /** 下载前空间检查的结果（null = 还没检查过）。 */
    var sysFontSpaceOk by mutableStateOf<Boolean?>(null)
        private set
    /** 一键流程当前步骤文案（null = 没在跑一键）。 */
    var sysFontStep by mutableStateOf<String?>(null)
        private set
    /** 组合字体文件目录（应用私有目录，不进 FontStore 的 fonts/ 目录）。 */
    val sysFontDir: File = File(app.filesDir, "system-font").apply { mkdirs() }
    /** 模块 staging 目录（生成后由此经 su 安装到 /data/adb/modules/tokifontspro-font）。 */
    val sysFontStagingDir: File = File(app.filesDir, "system-font-module")
    /** ksud 安装用的模块 zip（staging 打包后放这里）。 */
    val sysFontZipFile: File = File(sysFontDir, "tokifontspro-font.zip")
    private var sysFontJob: Job? = null

    /** 安装器仓库的 Release 列表（按工具 id 缓存，只取最近 5 个版本）。 */
    var toolReleases by mutableStateOf<Map<String, List<ToolRelease>>>(emptyMap())
        private set
    var toolReleasesLoading by mutableStateOf<Set<String>>(emptySet())
        private set
    var toolReleasesError by mutableStateOf<Map<String, String>>(emptyMap())
        private set
    private var bulkJob: Job? = null
    private var bulkFaces: List<FontFace> = emptyList()
    private var bulkSettled = false

    init {
        themeMode = prefs.getInt(KEY_THEME, 0)
        dynamicColor = prefs.getBoolean(KEY_DYNAMIC, true)
        seedIndex = prefs.getInt(KEY_SEED, 0)
        reduceMotion = prefs.getBoolean(KEY_REDUCE_MOTION, false)
        backdropBlur = prefs.getBoolean(KEY_BACKDROP_BLUR, true)
        previewText = prefs.getString(KEY_PREVIEW, DEFAULT_PREVIEW) ?: DEFAULT_PREVIEW
        previewOverrides = decodeOverrides(prefs.getString(KEY_PREVIEW_OVERRIDES, null))
        exported = prefs.getStringSet(KEY_EXPORTED, emptySet()).orEmpty().toSet()
        sysFontScripts = SystemFontScript.entries.associateWith { script ->
            prefs.getBoolean(KEY_SYSFONT_SCRIPT_PREFIX + script.name, script.defaultEnabled)
        }
        viewModelScope.launch {
            // 导航状态写进 SavedStateHandle：进程被杀后回来，tab、详情页、搜索、筛选都还在。
            snapshotFlow { listOf(tab, openFamilyId, installPage, settingsPage, query, groupId, sectionId, searchOpen) }.collect {
                savedState["tab"] = tab
                savedState["openFamilyId"] = openFamilyId
                savedState["installPage"] = installPage
                savedState["settingsPage"] = settingsPage
                savedState["query"] = query
                savedState["groupId"] = groupId
                savedState["sectionId"] = sectionId
                savedState["searchOpen"] = searchOpen
            }
        }
        loadCatalog()
        loadCachedFeed()
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

    /**
     * 预览渲染实际用的 face：已下载的优先（规则见 [FontFamily.previewFace]）。
     * 读 [downloads] 状态，下载完成/删除时调用方组合会重算，预览随即换成（或撤下）真实字形。
     */
    fun previewFace(family: FontFamily): FontFace? =
        family.previewFace(downloads.filterValues { it is FaceStatus.Ready }.keys)

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

    /** 点选种子色即切到种子方案并关动态取色（TokiStudio 外观页同款行为）。 */
    fun setSeed(index: Int) {
        seedIndex = index
        dynamicColor = false
        prefs.edit().putInt(KEY_SEED, index).putBoolean(KEY_DYNAMIC, false).apply()
    }

    fun persistReduceMotion(enabled: Boolean) {
        reduceMotion = enabled
        prefs.edit().putBoolean(KEY_REDUCE_MOTION, enabled).apply()
    }

    fun persistBackdropBlur(enabled: Boolean) {
        backdropBlur = enabled
        prefs.edit().putBoolean(KEY_BACKDROP_BLUR, enabled).apply()
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
        // 下载进行中关弹窗（点“以后再说”或点弹窗外）＝顺带取消下载：不停的话连接还在，
        // 后台会继续把更新包下完，白耗流量。状态复位由下载协程的取消分支完成。
        // 已下完待安装时 updateDownloading 已是 false，这里不会碰 pendingUpdate，
        // 那个包该留着等用户回来点“安装”，不能因为关一次弹窗就删掉。
        if (updateDownloading) AppUpdate.cancelDownload()
        updateDismissed = true
        updateOffer = null
    }

    /** 弹窗里点“取消下载”：只掐下载，弹窗保持打开，取消完成后可立刻重新下载。不弹任何提示。 */
    fun cancelUpdateDownload() {
        if (!updateDownloading) return
        AppUpdate.cancelDownload()
    }

    fun downloadUpdate() {
        val offer = updateOffer ?: return
        if (updateDownloading) return
        // 必须同步置位（和 checkUpdate 一样）：置位若放进协程的 onMain 里，连点两次时
        // 第二次的入口检查会在标志生效前通过，两个协程会并发写同一个 APK 文件。
        updateDownloading = true
        updateProgress = 0f
        // LAZY + jobRef 回填（与字体下载同款）：先登记再 start，
        // 这样取消之后，迟到的成功/失败回调都能凭 jobRef 认出“已经取消了”，不会再把状态写回去。
        var jobRef: Job? = null
        val job = viewModelScope.launch(Dispatchers.IO, start = kotlinx.coroutines.CoroutineStart.LAZY) {
            try {
                val file = File(getApplication<Application>().cacheDir, "updates/${offer.assetName}")
                AppUpdate.download(offer, file) { progress -> onMain { updateProgress = progress } }
                onMain {
                    if (jobRef?.isCancelled == false) {
                        pendingUpdate = file
                        updateDownloading = false
                    }
                }
            } catch (cancelled: CancellationException) {
                // 用户取消：AppUpdate 已把连接掐断并删掉残包，这里只复位状态，不弹“下载失败”。
                onMain {
                    updateDownloading = false
                    updateProgress = 0f
                }
                throw cancelled
            } catch (error: Throwable) {
                onMain {
                    updateDownloading = false
                    if (jobRef?.isCancelled == false) flash(error.message ?: "下载更新失败")
                }
            }
        }
        jobRef = job
        job.start()
    }

    /**
     * 安装器仓库的最近 5 个 Release：读 GitHub API，不带 token（公开仓库）。
     * 取过的用缓存；[force] 时重新取。
     */
    fun loadToolReleases(tool: InstallerTool, force: Boolean = false) {
        if (tool.id in toolReleasesLoading) return
        if (!force && tool.id in toolReleases) return
        toolReleasesLoading = toolReleasesLoading + tool.id
        toolReleasesError = toolReleasesError - tool.id
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val list = fetchToolReleases(tool.repo)
                onMain {
                    toolReleasesLoading = toolReleasesLoading - tool.id
                    toolReleases = toolReleases + (tool.id to list)
                }
            } catch (error: Throwable) {
                onMain {
                    toolReleasesLoading = toolReleasesLoading - tool.id
                    toolReleasesError = toolReleasesError + (tool.id to (error.message ?: "读取失败"))
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
        // 程序构造的 JP/KR/LATIN 各字重 face 不在 catalog 里，上面扫不到：只要本地文件存在且
        // 通过大小 + 文件头 + SHA-256 全套校验（无 SHA 的档只按大小 + 文件头），就标 Ready，
        // 免得每次点下载都重下。LATIN 的文件在 sysFontDir，其余在 FontStore 的 fonts/ 目录。
        for (script in SystemFontScript.entries) {
            for (face in SystemFont.remoteFaces(script)) {
                val existing = next[face.id]
                if (existing is FaceStatus.Downloading || existing is FaceStatus.Queued) continue
                val file = when (script) {
                    SystemFontScript.LATIN -> File(sysFontDir, face.fileName)
                    else -> store.file(face)
                }
                if (SystemFont.verifyFontFile(file, script, face.weight) == null) {
                    next[face.id] = FaceStatus.Ready(file)
                }
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

    /**
     * 一键下载全部：把全部未 Ready 的 face 入队，复用 [download] 的守卫
     * （已下载/下载中/排队中跳过）与 slots 并发信号量。聚合进度写进 [bulk]，
     * 由收集 downloads 状态的协程推进；全部 face 到达终态（Ready/Failed，
     * 或被手动取消）时弹汇总。预览联动不需要额外处理：列表与详情的预览读
     * readyIds，face 变 Ready 后 LaunchedEffect 会重新取 Typeface；
     * typefaces 缓存只存成功建出的 Typeface，从不缓存“文件不存在”的结果。
     */
    fun startBulkDownload() {
        if (bulkJob?.isActive == true) return
        val parsed = catalog ?: return
        val readyIds = downloads.filterValues { it is FaceStatus.Ready }.keys
        val pending = pendingDownloadFaces(parsed.families, readyIds)
        if (pending.isEmpty()) {
            flash("全部字体已下载")
            return
        }
        bulkFaces = pending
        bulkSettled = false
        bulk = BulkProgress(
            totalFaces = pending.size,
            doneFaces = 0,
            failedFaces = 0,
            totalBytes = pending.sumOf { it.bytes },
            doneBytes = 0L,
            currentLabel = pending.first().fileName,
        )
        pending.forEach { download(it) }
        bulkJob = viewModelScope.launch {
            snapshotFlow { downloads }.collect { map ->
                updateBulkProgress(map)
                if (bulkFaces.none { val s = map[it.id]; s is FaceStatus.Downloading || s is FaceStatus.Queued }) {
                    finishBulk()
                }
            }
        }
    }

    /**
     * 取消批量下载：只取消未完成的任务，已落盘的文件保留、不删除；
     * 失败的 face 保持 Failed 状态（可逐个重试）。结束时弹汇总。
     */
    fun cancelBulkDownload() {
        if (bulkJob == null) return
        // 先停收集器，避免它在取消过程中误判“全部结束”再弹一次汇总。
        val job = bulkJob
        bulkJob = null
        job?.cancel()
        bulkFaces.forEach { face ->
            val s = downloads[face.id]
            if (s is FaceStatus.Downloading || s is FaceStatus.Queued) cancel(face)
        }
        finishBulk()
    }

    private fun updateBulkProgress(map: Map<String, FaceStatus>) {
        val faces = bulkFaces
        if (faces.isEmpty()) return
        val done = faces.count { map[it.id] is FaceStatus.Ready }
        val failed = faces.count { map[it.id] is FaceStatus.Failed }
        var doneBytes = 0L
        for (face in faces) {
            when (val s = map[face.id]) {
                is FaceStatus.Ready -> doneBytes += face.bytes
                // 解压中（progress < 0）时文件已下完，按整文件计；下载中按进度折算。
                is FaceStatus.Downloading -> doneBytes += if (s.progress < 0f) face.bytes else (face.bytes * s.progress).toLong()
                else -> Unit
            }
        }
        val current = faces.firstOrNull { val s = map[it.id]; s is FaceStatus.Downloading || s is FaceStatus.Queued }
        bulk = BulkProgress(
            totalFaces = faces.size,
            doneFaces = done,
            failedFaces = failed,
            totalBytes = faces.sumOf { it.bytes },
            doneBytes = doneBytes,
            currentLabel = current?.fileName.orEmpty(),
        )
    }

    private fun finishBulk() {
        if (bulkSettled) return
        bulkSettled = true
        bulkJob?.cancel()
        bulkJob = null
        val map = downloads
        val done = bulkFaces.count { map[it.id] is FaceStatus.Ready }
        val failed = bulkFaces.count { map[it.id] is FaceStatus.Failed }
        bulkFaces = emptyList()
        bulk = null
        flash("已下载 $done 个，失败 $failed 个")
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

    // ---- 换系统字体 ----
    /** 待验证固件的用户额外确认：勾选后才放行一键 / 分步安装；每次重新检查设备时清零。 */
    var sysFontCalibratedConfirmed by mutableStateOf(false)

    fun setSystemFontCalibratedConfirmed(confirmed: Boolean) {
        sysFontCalibratedConfirmed = confirmed
    }

    /** 设备检查：固件表优先（机型 + 固件号 + 内核二次校验）。 */
    fun checkSystemFontDevice() {
        sysFontCalibratedConfirmed = false
        sysFontDevice = SystemFont.checkDevice(
            Build.MANUFACTURER ?: "",
            Build.MODEL ?: "",
            Build.DISPLAY ?: "",
            Build.VERSION.INCREMENTAL ?: "",
            System.getProperty("os.version") ?: "",
        )
    }

    /** root 检查：执行 `su -c id` 看到 uid=0。 */
    fun checkRoot() {
        if (sysFontRootChecking) return
        sysFontRootChecking = true
        viewModelScope.launch {
            val rooted = runCatching { SystemFontShell.checkRoot() }.getOrDefault(false)
            sysFontRoot = rooted
            sysFontRootChecking = false
            if (!rooted) flash("没有检测到 root，请先在第 2 步用内嵌一键 root 或 RootMyS24 取得 root")
        }
    }

    fun setSystemFontScript(script: SystemFontScript, enabled: Boolean) {
        sysFontScripts = sysFontScripts + (script to enabled)
        prefs.edit().putBoolean(KEY_SYSFONT_SCRIPT_PREFIX + script.name, enabled).apply()
    }

    fun enabledSystemFontScripts(): List<SystemFontScript> =
        SystemFontScript.entries.filter { sysFontScripts[it] == true }

    /** 该语种组合字体在本机的来源文件（LATIN 在 sysFontDir，其余在 FontStore 的 fonts/ 目录）。 */
    private fun systemFontSourceFile(script: SystemFontScript, weight: Int): File = when (script) {
        SystemFontScript.LATIN -> File(sysFontDir, script.fileForWeight(weight).fileName)
        SystemFontScript.SC, SystemFontScript.TC -> {
            val parsed = catalog ?: error("字库目录还没加载，请先回字体页等目录加载完成")
            val face = parsed.systemFontFace(script, weight)
                ?: error("字库目录里没有${script.displayName}${weight} 的字体")
            store.file(face)
        }
        SystemFontScript.JP, SystemFontScript.KR -> store.file(SystemFont.remoteFace(script, weight))
    }

    /** 设备 sans-serif 实际字重（读 /system/etc/fonts.xml；读不到或为空就按全量算）。结果缓存，本次会话内设备 fonts.xml 不会变。 */
    private var cachedDeviceWeights: Set<Int>? = null

    private fun deviceFontWeights(): Set<Int> {
        cachedDeviceWeights?.let { return it }
        val weights = runCatching {
            SystemFont.sansSerifWeights(File("/system/etc/fonts.xml").readText())
        }.getOrNull()?.takeIf { it.isNotEmpty() }?.toSet() ?: SystemFont.ALL_WEIGHTS
        cachedDeviceWeights = weights
        return weights
    }

    /** 本次安装某语种实际要用的文件（设备字重 → 最近档，去重），与 [SystemFont.moduleLayout] 的按字重版同一口径。 */
    private fun neededWeightFiles(script: SystemFontScript, weights: Set<Int>): List<ScriptWeightFile> =
        weights.map { script.fileForWeight(it) }.distinctBy { it.fileName }

    /**
     * 拉丁文：只下载一次 Inter 4.1 zip，解出本次需要的各档 OTF。
     * FontStore.download 一次只解一个 innerName、且下完就删 .part，所以 9 档不能走它
     * （会下 9 次 zip）；这里直接用 HttpURLConnection 按 face.urls() 顺序试地址
     * （raw 主地址注定 404，靠 extraUrls 里的 release 直链），落到
     * sysFontDir/Inter-4.1.zip，已下好且大小对得上的直接复用。
     * 全部需要的档解出并校验通过后删掉 zip；失败时删掉 zip（下次重试重新下载），
     * 避免损坏的包被反复复用。返回 null 表示成功，否则为中文错误。
     * （在 IO 协程里调用；取消时掐断连接并删掉 .part。）
     */
    private suspend fun downloadLatinZip(needed: List<ScriptWeightFile>): String? {
        val missing = needed.filter {
            SystemFont.verifyFontFile(File(sysFontDir, it.fileName), SystemFontScript.LATIN, it.weight) != null
        }
        if (missing.isEmpty()) return null
        val zipFile = File(sysFontDir, SystemFont.LATIN_ZIP_FILE)
        val zipPart = File(sysFontDir, "${SystemFont.LATIN_ZIP_FILE}.part")
        return try {
            if (!zipFile.exists() || zipFile.length() != SystemFont.LATIN_ZIP_BYTES) {
                zipFile.delete()
                val face = SystemFont.remoteFace(SystemFontScript.LATIN, 700)
                var lastError: String? = null
                var downloaded = false
                for (url in face.urls()) {
                    try {
                        downloadUrlToFile(url, zipPart)
                        if (zipPart.length() != SystemFont.LATIN_ZIP_BYTES) {
                            throw IOException("下载不完整（${zipPart.length()}/${SystemFont.LATIN_ZIP_BYTES}）")
                        }
                        if (!zipPart.renameTo(zipFile)) {
                            zipPart.copyTo(zipFile, overwrite = true)
                            zipPart.delete()
                        }
                        downloaded = true
                        break
                    } catch (cancelled: CancellationException) {
                        zipPart.delete()
                        throw cancelled
                    } catch (error: Throwable) {
                        zipPart.delete()
                        lastError = error.message ?: "下载失败"
                    }
                }
                if (!downloaded) return "Inter 字体包下载失败：${lastError ?: "未知错误"}"
            }
            val entriesOk = runCatching {
                ZipFile(zipFile).use { zip ->
                    missing.all { zip.getEntry(SystemFont.LATIN_ZIP_INNER_DIR + it.fileName) != null }
                }
            }.getOrDefault(false)
            if (!entriesOk) {
                zipFile.delete()
                return "Inter 字体包损坏或不完整，已删除，请重试下载"
            }
            try {
                ZipFile(zipFile).use { zip ->
                    for (wanted in missing) {
                        val entry = zip.getEntry(SystemFont.LATIN_ZIP_INNER_DIR + wanted.fileName)
                            ?: return "Inter 字体包里没有 ${wanted.fileName}，请重试下载"
                        val dest = File(sysFontDir, wanted.fileName)
                        zip.getInputStream(entry).use { input ->
                            dest.outputStream().use { input.copyTo(it) }
                        }
                    }
                }
            } catch (error: Throwable) {
                zipFile.delete()
                return "Inter 字体包解压失败：${error.message ?: "未知错误"}，已删除损坏的安装包，请重试"
            }
            for (wanted in missing) {
                val problem = SystemFont.verifyFontFile(File(sysFontDir, wanted.fileName), SystemFontScript.LATIN, wanted.weight)
                if (problem != null) return problem
            }
            zipFile.delete()
            null
        } catch (cancelled: CancellationException) {
            zipPart.delete()
            throw cancelled
        } catch (error: Throwable) {
            error.message ?: "下载 Inter 字体失败"
        }
    }

    /**
     * 用 HttpURLConnection 下一个文件（跟随重定向；读循环里响应取消）。
     * 调用方须在 IO 线程；进度通过 [sysFontProgress] 回主线程。
     */
    private suspend fun downloadUrlToFile(url: String, dest: File) {
        val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        connection.connectTimeout = 30_000
        connection.readTimeout = 120_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "tokifontspro/${BuildConfig.VERSION_NAME}")
        try {
            connection.connect()
            if (connection.responseCode != java.net.HttpURLConnection.HTTP_OK) {
                error("HTTP ${connection.responseCode}")
            }
            dest.parentFile?.mkdirs()
            connection.inputStream.use { input ->
                dest.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var read = 0L
                    var last = 0f
                    val total = connection.contentLengthLong
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        read += n
                        if (total > 0) {
                            val progress = (read.toFloat() / total).coerceIn(0f, 1f)
                            if (progress - last >= 0.02f || progress >= 1f) {
                                last = progress
                                onMain { sysFontProgress = progress }
                            }
                        }
                    }
                    if (total > 0 && read != total) throw IOException("下载不完整（$read/$total）")
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * 一键换字体：从已取得临时 root 开始，点一下自动顺序跑完
     * 下载 Inter → 生成模块 → 安装 → 软重启 zygote，全程显示当前步骤，
     * 出错停在当步并报中文原因。root 本身仍在独立的 RootMyS24 应用里取得，不在本应用内。
     * 分步按钮（下载 / 生成 / 安装）是同一批内部步骤的手动入口。
     */
    fun oneClickChangeFont() {
        if (sysFontWorking) return
        val selected = enabledSystemFontScripts()
        if (selected.isEmpty()) {
            viewModelScope.launch(Dispatchers.IO) { autoCancelSystemFont("至少选一个语种") }
            return
        }
        if (!sysFontInstallAllowed()) {
            val error = when (sysFontDevice?.support) {
                DeviceSupport.CALIBRATED -> "该固件已定标但未实测，请先勾选风险确认再点一键换字体"
                DeviceSupport.PORTED -> "该固件是移植载荷，需先在 RootMyS24 设置里开启未经测试的载荷，并勾选风险确认再点一键换字体"
                DeviceSupport.CANDIDATE -> "该固件需用 S24 Ultra 同构建号载荷跨机型尝试，请先勾选风险确认再点一键换字体"
                else -> "设备未通过检查，无法一键换字体"
            }
            viewModelScope.launch(Dispatchers.IO) { autoCancelSystemFont(error) }
            return
        }
        sysFontWorking = true
        sysFontError = null
        sysFontVerifyWarning = null
        sysFontStep = "正在检查 root…"
        sysFontJob = viewModelScope.launch(Dispatchers.IO) {
            // 还没测过 root 就先测：没 root 直接停，引导用户去 RootMyS24 取得 root。
            if (sysFontRoot != true) {
                val rooted = runCatching { SystemFontShell.checkRoot() }.getOrDefault(false)
                onMain { sysFontRoot = rooted }
                if (!rooted) {
                    autoCancelSystemFont("没有检测到 root，请先在第 2 步用内嵌一键 root 或 RootMyS24 取得 root 再点一键换字体")
                    return@launch
                }
            }
            onMain { sysFontStep = "正在下载 Inter 字体文件…" }
            downloadSystemFontFilesInternal(selected)?.let { autoCancelSystemFont(it); return@launch }
            onMain { sysFontStep = "正在生成模块…" }
            generateSystemFontModuleInternal(selected)?.let { autoCancelSystemFont(it); return@launch }
            onMain { sysFontStep = "正在安装模块并软重启…" }
            val installed = installSystemFontModuleInternal()
            if (installed.error != null) {
                autoCancelSystemFont(
                    installed.error,
                    moduleWritten = installed.moduleWritten,
                    postVerifyFailed = installed.postVerifyFailed,
                )
                return@launch
            }
            onMain {
                sysFontWorking = false
                sysFontStep = null
                sysFontProgress = 1f
                sysFontModuleState = SystemFontModuleState.INSTALLED
                flash("一键换字体完成，系统全部字重已换成 Inter")
            }
        }
    }

    /**
     * 失败自动取消（硬规则，决策见 [SystemFont.planCancel]）：
     * 一键 / 分步流程里任何一步不通过，整个操作自动取消——立刻停止、清理 staging、
     * 状态复位到未生成，界面只显示失败原因，绝不出现“装了一半卡住”的状态。
     * [moduleWritten]：本次操作已把模块写到 /data/adb/modules/tokifontspro-font
     * （安装步跑过，无论成败）→ 先删模块目录再报错。
     * [postVerifyFailed]：模块已装上但装后验证失败 → 自动回滚（删模块 + 重启 zygote）再报错。
     * 除回滚外不重启 zygote（新字体根本没挂上）。
     * （在 IO 协程里调用；调用后调用方应立即 return。）
     */
    private suspend fun autoCancelSystemFont(
        error: String,
        moduleWritten: Boolean = false,
        postVerifyFailed: Boolean = false,
    ) {
        val plan = SystemFont.planCancel(moduleWritten, postVerifyFailed)
        var extraNote: String? = null
        var needsReboot = false
        if (plan.deleteModuleDir) {
            if (plan.restartZygote) {
                val outcome = runCatching { SystemFontShell.rollback() }.getOrNull()
                when {
                    outcome == null -> extraNote = "自动回滚执行失败，请手动进 KernelSU 管理器关掉「Inter」模块，或执行 adb shell rm -rf /data/adb/modules/tokifontspro-font 后重启"
                    !outcome.moduleRemoved -> extraNote = "自动回滚也没能删掉模块：${outcome.error ?: "未知错误"}，请手动进 KernelSU 管理器关掉「Inter」模块，或执行 adb shell rm -rf /data/adb/modules/tokifontspro-font 后重启"
                    !outcome.zygoteOk -> {
                        needsReboot = true
                        extraNote = outcome.error ?: "模块已删，但软重启 zygote 没确认，请重启手机"
                    }
                }
            } else {
                // 安装步跑过但没完成：删掉可能已写入一半的模块目录，不重启 zygote。
                runCatching { SystemFontShell.runAsRoot("rm -rf ${SystemFont.MODULE_DIR}") }
            }
        }
        if (plan.clearStaging) {
            SystemFont.clearStaging(sysFontStagingDir, sysFontZipFile)
        }
        onMain {
            sysFontWorking = false
            sysFontStep = null
            sysFontProgress = 0f
            sysFontModuleState = SystemFontModuleState.NOT_GENERATED
            sysFontChangeList = emptyList()
            sysFontXmlValidated = false
            sysFontFilesVerified = false
            sysFontSpaceOk = null
            sysFontVerifyWarning = null
            sysFontInstallMethod = null
            if (needsReboot) sysFontNeedsReboot = true
            sysFontError = listOfNotNull(error, extraNote).joinToString("；")
        }
    }

    /** 安装步的结果：[error] 为 null 表示成功；失败时 [moduleWritten] 表示安装步是否已对模块目录动过手，[postVerifyFailed] 表示模块已装上但装后验证失败（须自动回滚）。 */
    private data class InstallResult(
        val error: String?,
        val moduleWritten: Boolean = false,
        val postVerifyFailed: Boolean = false,
    )

    /**
     * 安装放行条件：已实测固件直接放行；待验证固件、移植载荷与跨机型候选只在用户额外确认
     * 风险后放行；其他情况一律不放行。界面的一键 / 下载 / 生成按钮与 [sysFontInstallBlockers] 共用它。
     */
    fun sysFontInstallAllowed(): Boolean =
        sysFontDevice?.support == DeviceSupport.SUPPORTED ||
            ((sysFontDevice?.support == DeviceSupport.CALIBRATED ||
                sysFontDevice?.support == DeviceSupport.PORTED ||
                sysFontDevice?.support == DeviceSupport.CANDIDATE) &&
                sysFontCalibratedConfirmed)

    /**
     * 安装门禁：任一条成立就不能点安装，界面把原因逐条展示。
     * （staging 完整性用 File.exists 快速判断，几十个文件，开销可忽略。）
     */
    fun sysFontInstallBlockers(): List<String> = buildList {
        val device = sysFontDevice
        when (device?.support) {
            DeviceSupport.SUPPORTED -> {}
            DeviceSupport.CALIBRATED -> if (!sysFontCalibratedConfirmed) {
                add("该固件已定标但未实测，root 可能不成功；确认风险后勾选“我已知晓，仍要继续”才能安装")
            }
            DeviceSupport.PORTED -> if (!sysFontCalibratedConfirmed) {
                add("该固件是移植载荷（未实测），需先在 RootMyS24 设置里开启未经测试的载荷；确认风险后勾选“我已知晓，仍要继续”才能安装")
            }
            DeviceSupport.CANDIDATE -> if (!sysFontCalibratedConfirmed) {
                add("该固件不在 S24+ 支持列表里，但有 S24 Ultra 同构建号载荷可试（你以前用过这条路子）；确认风险后勾选“我已知晓，仍要继续”才能安装")
            }
            else -> add("设备未通过检查" + (device?.reason?.let { "：$it" } ?: ""))
        }
        if (sysFontRoot != true) add("没有检测到 root")
        if (sysFontModuleState != SystemFontModuleState.GENERATED) add("请先生成模块")
        if (!sysFontXmlValidated) add("fonts.xml 结构校验未通过")
        if (!sysFontFilesVerified) add("Inter 字体文件未通过校验")
        if (sysFontChangeList.isEmpty()) add("没有可安装的改动")
        if (sysFontSpaceOk == false) add("空间不足")
        if (sysFontModuleState == SystemFontModuleState.GENERATED) {
            val weights = cachedDeviceWeights ?: SystemFont.ALL_WEIGHTS
            val missing = SystemFont.moduleLayout(enabledSystemFontScripts(), weights)
                .filter { !File(sysFontStagingDir, it).exists() }
            if (missing.isNotEmpty()) add("模块文件不完整，请重新生成")
        }
    }

    /** 下载 Inter 字体文件（分步入口，内部步骤与一键流程共用）。失败时同样自动取消。 */
    fun downloadSystemFontFiles() {
        if (sysFontWorking) return
        val selected = enabledSystemFontScripts()
        if (selected.isEmpty()) {
            viewModelScope.launch(Dispatchers.IO) { autoCancelSystemFont("至少选一个语种") }
            return
        }
        sysFontWorking = true
        sysFontProgress = 0f
        sysFontError = null
        sysFontJob = viewModelScope.launch(Dispatchers.IO) {
            val error = downloadSystemFontFilesInternal(selected)
            if (error != null) {
                autoCancelSystemFont(error)
            } else {
                onMain {
                    sysFontWorking = false
                    sysFontProgress = 1f
                    flash("Inter 字体文件已就绪")
                }
            }
        }
    }

    /**
     * 下载内部步骤（在 IO 协程里调用，返回 null 表示成功，否则为中文错误）：
     * 先按设备实际字重算剩余空间，再按语种取文件——苹方各档走现有 catalog +
     * FontStore（只用用户已下载的本地副本，缺了就现下）；拉丁文只下载一次
     * Inter 4.1 zip、解出本次需要的各档；Noto / Source Han 按字重走构造的
     * FontFace + store.download（主地址 + jsDelivr 备用）。下载后按期望大小 /
     * 文件头 / SHA-256 校验，失败就报中文错误。已下载且校验通过的文件直接跳过，不重下。
     */
    private suspend fun downloadSystemFontFilesInternal(selected: List<SystemFontScript>): String? {
        val weights = deviceFontWeights()
        val needed = SystemFont.requiredDownloadBytes(selected, weights)
        val usable = getApplication<Application>().filesDir.usableSpace
        val enough = SystemFont.hasEnoughSpace(usable, needed)
        onMain { sysFontSpaceOk = enough }
        if (!enough) {
            return "空间不足：下载 Inter 需要约 ${needed / 1024 / 1024}MB 剩余空间，当前剩余约 ${usable / 1024 / 1024}MB，请清理后再试"
        }
        return try {
            selected.forEachIndexed { index, script ->
                onMain { sysFontProgress = index.toFloat() / selected.size }
                val files = neededWeightFiles(script, weights)
                when (script) {
                    SystemFontScript.LATIN -> {
                        downloadLatinZip(files)?.let { return it }
                    }
                    SystemFontScript.SC, SystemFontScript.TC -> {
                        val parsed = catalog ?: return "字库目录还没加载，请先回字体页等目录加载完成"
                        for (wanted in files) {
                            val face = parsed.systemFontFace(script, wanted.weight)
                                ?: return "字库目录里没有${script.displayName}${wanted.weight} 的字体"
                            if (status(face.id) !is FaceStatus.Ready) {
                                store.download(face) { progress -> onMain { sysFontProgress = progress } }
                                onMain { refreshLocal() }
                            }
                        }
                    }
                    SystemFontScript.JP, SystemFontScript.KR -> {
                        for (wanted in files) {
                            val face = SystemFont.remoteFace(script, wanted.weight)
                            if (status(face.id) !is FaceStatus.Ready) {
                                store.download(face) { progress -> onMain { sysFontProgress = progress } }
                                onMain { refreshLocal() }
                            }
                        }
                    }
                }
                for (wanted in files) {
                    val problem = SystemFont.verifyFontFile(systemFontSourceFile(script, wanted.weight), script, wanted.weight)
                    if (problem != null) return problem
                }
            }
            null
        } catch (error: Throwable) {
            error.message ?: "下载 Inter 字体失败"
        }
    }

    /** 生成模块（分步入口，内部步骤与一键流程共用）。失败时同样自动取消。 */
    fun generateSystemFontModule() {
        if (sysFontWorking) return
        val selected = enabledSystemFontScripts()
        if (selected.isEmpty()) {
            viewModelScope.launch(Dispatchers.IO) { autoCancelSystemFont("至少选一个语种") }
            return
        }
        if (!sysFontInstallAllowed()) {
            val error = when (sysFontDevice?.support) {
                DeviceSupport.CALIBRATED -> "该固件已定标但未实测，请先勾选风险确认再生成模块"
                DeviceSupport.PORTED -> "该固件是移植载荷，需先在 RootMyS24 设置里开启未经测试的载荷，并勾选风险确认再生成模块"
                DeviceSupport.CANDIDATE -> "该固件需用 S24 Ultra 同构建号载荷跨机型尝试，请先勾选风险确认再生成模块"
                else -> "设备未通过检查，无法生成模块"
            }
            viewModelScope.launch(Dispatchers.IO) { autoCancelSystemFont(error) }
            return
        }
        sysFontWorking = true
        sysFontError = null
        viewModelScope.launch(Dispatchers.IO) {
            val error = generateSystemFontModuleInternal(selected)
            if (error != null) {
                autoCancelSystemFont(error)
            } else {
                onMain {
                    sysFontWorking = false
                    flash("模块已生成，可以安装了")
                }
            }
        }
    }

    /**
     * 生成内部步骤（在 IO 协程里调用，返回 null 表示成功，否则为中文错误）：
     * 读设备 /system/etc/fonts.xml（读不到或解析失败直接拒绝）→ 算改动清单 →
     * 按设备实际字重替换 sans-serif 各字重条目 → 重解析 + 结构校验（只有预期改动才通过，
     * staging 清单按设备字重传）→ 先校验全部字重的字体文件，再写 staging
     * （module.prop + system/etc/fonts.xml + 各字重字体文件），同时把原 fonts.xml
     * 备份到模块内与应用私有目录 → 最后复核 staging 清单齐全才算生成成功。
     * 斜体条目没有斜体替换文件，原样保留，不进清单、也不拦安装。
     */
    private suspend fun generateSystemFontModuleInternal(selected: List<SystemFontScript>): String? {
        return try {
            val deviceXmlFile = File("/system/etc/fonts.xml")
            if (!deviceXmlFile.exists()) return "读不到设备上的 /system/etc/fonts.xml"
            val original = deviceXmlFile.readText()
            if (!SystemFont.isValidFontsXml(original)) return "设备上的 fonts.xml 解析失败，已拒绝安装"
            val weights = SystemFont.sansSerifWeights(original).ifEmpty { SystemFont.ALL_WEIGHTS }.toSet()
            cachedDeviceWeights = weights
            val changeList = SystemFont.computeChangeList(original, selected)
            val rewritten = SystemFont.rewriteFontsXml(original, selected)
            if (!SystemFont.isValidFontsXml(rewritten)) return "生成的 fonts.xml 解析失败，已拒绝安装"
            val stagingNames = selected.flatMap { script ->
                neededWeightFiles(script, weights).map { it.fileName }
            }.toSet()
            val problems = SystemFont.validateRewrite(original, rewritten, selected, stagingNames)
            if (problems.isNotEmpty()) return "fonts.xml 结构校验未通过：${problems.first()}"
            for (script in selected) {
                for (wanted in neededWeightFiles(script, weights)) {
                    val problem = SystemFont.verifyFontFile(systemFontSourceFile(script, wanted.weight), script, wanted.weight)
                    if (problem != null) return "请先下载 Inter 字体文件：$problem"
                }
            }
            val staging = sysFontStagingDir
            staging.deleteRecursively()
            File(staging, "system/etc").mkdirs()
            File(staging, "system/fonts").mkdirs()
            File(staging, "module.prop").writeText(
                SystemFont.buildModuleProp(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
            )
            File(staging, "system/etc/fonts.xml").writeText(rewritten)
            // 原文件双备份：模块内一份（随模块走），应用私有目录一份（删模块后还在）。
            File(staging, "fonts.xml.original").writeText(original)
            File(sysFontDir, "fonts.xml.bak").writeText(original)
            for (script in selected) {
                for (wanted in neededWeightFiles(script, weights)) {
                    val source = systemFontSourceFile(script, wanted.weight)
                    source.copyTo(File(staging, "system/fonts/${wanted.fileName}"), overwrite = true)
                }
            }
            val missing = SystemFont.moduleLayout(selected, weights).filter { !File(staging, it).exists() }
            if (missing.isNotEmpty()) return "模块文件不完整，缺少：${missing.joinToString("、")}"
            onMain {
                sysFontChangeList = changeList
                sysFontXmlValidated = true
                sysFontFilesVerified = true
                sysFontModuleState = SystemFontModuleState.GENERATED
            }
            null
        } catch (error: Throwable) {
            error.message ?: "生成模块失败"
        }
    }

    /** 安装模块（分步入口，内部步骤与一键流程共用）。失败时同样自动取消。 */
    fun installSystemFontModule() {
        if (sysFontWorking) return
        val blockers = sysFontInstallBlockers()
        if (blockers.isNotEmpty()) {
            sysFontError = blockers.first()
            return
        }
        sysFontWorking = true
        sysFontError = null
        sysFontVerifyWarning = null
        viewModelScope.launch(Dispatchers.IO) {
            val result = installSystemFontModuleInternal()
            if (result.error != null) {
                autoCancelSystemFont(
                    result.error,
                    moduleWritten = result.moduleWritten,
                    postVerifyFailed = result.postVerifyFailed,
                )
            } else {
                onMain {
                    sysFontWorking = false
                    sysFontModuleState = SystemFontModuleState.INSTALLED
                    flash("已安装并软重启，系统全部字重已换成 Inter")
                }
            }
        }
    }

    /**
     * 安装内部步骤（调用方须先过 [sysFontInstallBlockers] 门禁）：
     * 打包 zip → ksud 安装，失败回退复制安装 → 校验模块在位才重启 zygote →
     * 重启后验证挂载出来的字体文件。
     * 装后验证失败是硬失败（不再只是警告）：返回 [InstallResult] 标 postVerifyFailed，
     * 调用方自动回滚（删模块 + 重启 zygote）再报错。
     */
    private suspend fun installSystemFontModuleInternal(): InstallResult {
        return when (val outcome = SystemFontShell.installModule(sysFontStagingDir, sysFontZipFile)) {
            is InstallOutcome.Failure -> InstallResult(outcome.message, moduleWritten = outcome.moduleWritten)
            is InstallOutcome.Success -> {
                onMain { sysFontInstallMethod = outcome.method }
                val restartError = SystemFontShell.restartZygote()
                if (restartError != null) return InstallResult(restartError, moduleWritten = true)
                val selected = enabledSystemFontScripts()
                val stagingXml = File(sysFontStagingDir, "system/etc/fonts.xml")
                val warning = SystemFontShell.verifyInstalled(
                    selected,
                    weights = deviceFontWeights(),
                    expectedFontsXmlBytes = if (stagingXml.exists()) stagingXml.length() else -1,
                )
                if (warning != null) {
                    return InstallResult("装后验证失败：$warning", moduleWritten = true, postVerifyFailed = true)
                }
                InstallResult(null)
            }
        }
    }

    /** 回滚：删模块目录 + 重启 zygote，恢复原厂字体。 */
    fun rollbackSystemFont() {
        if (sysFontWorking) return
        sysFontWorking = true
        sysFontError = null
        sysFontNeedsReboot = false
        viewModelScope.launch {
            val outcome = SystemFontShell.rollback()
            sysFontWorking = false
            if (!outcome.moduleRemoved) {
                sysFontModuleState = SystemFontModuleState.FAILED
                sysFontError = outcome.error ?: "回滚失败"
            } else {
                sysFontModuleState = SystemFontModuleState.NOT_GENERATED
                sysFontStagingDir.deleteRecursively()
                sysFontChangeList = emptyList()
                sysFontXmlValidated = false
                sysFontFilesVerified = false
                sysFontInstallMethod = null
                sysFontVerifyWarning = null
                if (outcome.zygoteOk) {
                    flash("已回滚：模块已删除并软重启，系统字体已恢复")
                } else {
                    sysFontNeedsReboot = true
                    sysFontError = outcome.error ?: "模块已删，但软重启没确认，请重启手机"
                }
            }
        }
    }

    /** 备选：软重启后字体未生效时重启手机（magic mount 未在当前挂载命名空间生效）。 */
    fun rebootSystemFontPhone() {
        if (sysFontWorking) return
        viewModelScope.launch {
            SystemFontShell.reboot().let { error ->
                if (error != null) flash(error)
            }
        }
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
        /** 内置 feed 表的日期（docs/firmware-support.json 的 updatedAt，种子转录日）。 */
        const val BUILTIN_FEED_DATE = "2026-11-24"

        private const val KEY_THEME = "theme"
        private const val KEY_DYNAMIC = "dynamic"
        private const val KEY_SEED = "seed"
        private const val KEY_REDUCE_MOTION = "reduce_motion"
        private const val KEY_BACKDROP_BLUR = "backdrop_blur"
        private const val KEY_PREVIEW = "preview"
        private const val KEY_PREVIEW_OVERRIDES = "preview_overrides"
        private const val KEY_EXPORTED = "exported"
        private const val KEY_SYSFONT_SCRIPT_PREFIX = "sysfont_script_"

        private fun decodeOverrides(raw: String?): Map<String, String> {
            if (raw.isNullOrBlank()) return emptyMap()
            val json = org.json.JSONObject(raw)
            return buildMap {
                json.keys().forEach { key -> put(key, json.optString(key)) }
            }
        }
    }
}
