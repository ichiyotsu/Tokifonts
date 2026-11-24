package com.ichiyotsu.tokifonts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray

// 注意：这个类必须是 public（不能是 internal），因为 FontsViewModel.loadToolReleases
// 是 public 函数，参数里用了它；Kotlin 不允许 public 函数暴露 internal 类型。
data class InstallerTool(
    val id: String,
    val name: String,
    val summary: String,
    val downloadLabel: String,
    val downloadUrl: String,
    val officialLabel: String,
    val officialUrl: String,
    /** 读 Release 用的仓库（owner/repo），安装页的“查看版本”按它取最近 5 个版本。 */
    val repo: String,
    val steps: List<String>,
    val caution: String,
)

internal val InstallerTools = listOf(
    InstallerTool(
        id = "samfonts",
        name = "SamFonts",
        summary = "三星 One UI 上用来生成并安装字体包的应用。仓库说明写它可以用 Shizuku，也可以走不需要 root 的方式，具体以你手机上的系统版本为准。",
        downloadLabel = "下载安装包",
        downloadUrl = "https://github.com/fahadalijaved/SamFonts/releases/download/v4.3.3/SamFonts-4.3.3-universal.apk",
        officialLabel = "GitHub 仓库",
        officialUrl = "https://github.com/fahadalijaved/SamFonts",
        repo = "fahadalijaved/SamFonts",
        steps = listOf(
            "点上面的「下载安装包」安装 SamFonts。三星可能会弹出 Knox 提示，按你自己的判断处理。",
            "在 TokiFontsPro 里把要用的字体保存到「下载」，或点「用其他应用打开」。",
            "打开 SamFonts，按应用里的字体方式来。没有 root 时先试它提供的普通方式；已经跑着 Shizuku 时，再在应用里改用 Shizuku 并授权。",
            "选中字体并安装。然后到系统设置的显示、字体大小和样式里切换。",
        ),
        caution = "只在部分三星 One UI 上有效。系统更新可能让这种方法失效。不要给不认识的字体包超出需要的权限。",
    ),
    InstallerTool(
        id = "samfonts-pro",
        name = "SamFonts Pro",
        summary = "和 SamFonts 同一个 GitHub 仓库里的另一份安装包，文件名以 SamFontsPro 开头。没有单独的官网。步骤和 SamFonts 相同，多出来的功能以应用里的说明为准。",
        downloadLabel = "下载 Pro 安装包",
        downloadUrl = "https://github.com/fahadalijaved/SamFonts/releases/download/v4.3.3/SamFontsPro-4.3.3-universal.apk",
        officialLabel = "同一个 GitHub 仓库",
        officialUrl = "https://github.com/fahadalijaved/SamFonts",
        repo = "fahadalijaved/SamFonts",
        steps = listOf(
            "点上面的「下载 Pro 安装包」，安装文件名带 SamFontsPro 的那一份，不要和普通版搞混。",
            "之后的用法和 SamFonts 一样：从 TokiFontsPro 导出字体文件，在 SamFonts Pro 里安装，再去系统字体设置里选用。",
        ),
        caution = "第三方网站上的「SamFonts Pro」安装包没有拿来当作官方来源。这里只用该仓库 Release 里的文件。",
    ),
    InstallerTool(
        id = "zfont",
        name = "zFont 3（GitHub 版）",
        summary = "zFont 的换字体应用。这里给的是官网页面上的 GitHub 直装包，不是应用商店里的那一版。",
        downloadLabel = "下载 GitHub 版",
        downloadUrl = "https://github.com/zFont/Host/releases/download/zFont/zfont_latest.apk",
        officialLabel = "官方页面",
        officialUrl = "https://zfont.github.io/",
        repo = "zFont/Host",
        steps = listOf(
            "点上面的「下载 GitHub 版」安装。官网也写了应用商店地址，那是另一条分发渠道。",
            "在 zFont 里导入 TokiFontsPro 保存的 ttf 或 otf，或从它自己的字库里选。",
            "按手机品牌选对应的应用方式。zFont 的说明把 Shizuku 写成 Android 12 以后比较稳的一种文件处理方式。",
            "先预览，再应用。应用之后到系统的字体设置里确认。",
        ),
        caution = "不同品牌的步骤不一样，系统更新后可能失效。不要从官网和这个 GitHub 仓库以外的镜像站下载 zFont。",
    ),
    InstallerTool(
        id = "root-my-s24",
        name = "root-my-s24",
        summary = "面向部分三星 Galaxy S24 固件的工具，项目说明是在不解锁 bootloader 的情况下加载 KernelSU。它本身不是字体应用。",
        downloadLabel = "打开 Releases",
        downloadUrl = "https://github.com/nanoturtle1145/root-my-s24/releases",
        officialLabel = "GitHub 仓库",
        officialUrl = "https://github.com/nanoturtle1145/root-my-s24",
        repo = "nanoturtle1145/root-my-s24",
        steps = listOf(
            "先读仓库 README，确认你的机型和固件在它列出的范围内。不匹配就不要安装。",
            "点上面的「打开 Releases」：Release 里有好几份安装包，按项目说明选对应机型的那一份。",
            "这个工具的目的是取得 KernelSU。取得之后，重启可能会失效，细节以项目自己的说明为准。",
            "字体仍然要交给 SamFonts 或 zFont 这类安装器。TokiFontsPro 只负责把字体文件下载到本机。",
        ),
        caution = "可能变砖、影响 Knox、丢数据，重启后也可能失效。本页不写利用细节。只在你自己的设备上、读完官方说明后再决定。",
    ),
    InstallerTool(
        id = "rootmygalaxy",
        name = "rootmygalaxy",
        summary = "仓库名是 Root My Galaxy。它是支持列表里的三星机型上的 KernelSU 安装器，同样不是字体应用本身。",
        downloadLabel = "打开 Releases",
        downloadUrl = "https://github.com/BuSung-dev/Root-My-Galaxy/releases",
        officialLabel = "GitHub 仓库",
        officialUrl = "https://github.com/BuSung-dev/Root-My-Galaxy",
        repo = "BuSung-dev/Root-My-Galaxy",
        steps = listOf(
            "打开仓库，看它支持哪些机型和内核版本。应用会按机型和内核去选载荷，不在列表里就不要试。",
            "点上面的「打开 Releases」，从 Releases 安装它发布的包。项目写了可以用 Shizuku 作为其中一种运行方式。",
            "如果它报告 KernelSU 已经可用，再给换字体应用或 Shizuku 授权。",
            "然后用 TokiFontsPro 下载的字体，在 SamFonts 或 zFont 里安装。",
        ),
        caution = "这是改内核权限的工具，有变砖和保修风险。不要按照来源不明的帖子去改命令。以这个仓库的 README 为准。",
    ),
    InstallerTool(
        id = "shizuku",
        name = "Shizuku",
        summary = "Shizuku 是一款开源工具，让普通应用无需 root 也能以 ADB（shell）权限调用系统接口，运行速度比反复执行 shell 命令更快。字体安装类应用通过 Shizuku 获得写入系统字体目录所需的权限，从而在不 root 的手机上安装字体。应用每次使用 Shizuku 权限都需要经过用户授权，只能在 Shizuku 里逐个应用开启。",
        downloadLabel = "打开 Releases",
        downloadUrl = "https://github.com/RikkaApps/Shizuku/releases",
        officialLabel = "官方网站",
        officialUrl = "https://shizuku.rikka.app/",
        repo = "RikkaApps/Shizuku",
        steps = listOf(
            "安装 Shizuku：从 Google Play（开发者 Rikka，包名 moe.shizuku.privileged.api）安装，或从 GitHub 仓库 RikkaApps/Shizuku 的 Releases 下载官方 APK。",
            "开启开发者选项：进入「设置 → 关于手机」，连续点按「版本号」7 次；再进入「开发者选项」，开启「USB 调试」。",
            "开启无线调试（Android 11 及以上，无需电脑）：在「开发者选项」里开启「无线调试」；打开 Shizuku，点「通过无线调试启动」→「配对」，再在「无线调试」页点「使用配对码配对设备」，把 6 位配对码填入 Shizuku 弹出的通知中。",
            "启动 Shizuku：回到 Shizuku 主界面点「启动」，看到「Shizuku 正在运行」即表示成功。",
            "授权字体应用：打开字体安装应用（如 SamFonts / zFont），按提示向 Shizuku 请求授权，在 Shizuku 的「已授权应用」中确认该应用已开启，然后即可在字体应用内安装字体。",
            "电脑启动方式（Android 10 及以下）：用 USB 连接电脑，开启 USB 调试后执行 adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh，再在 Shizuku 内确认已启动。",
        ),
        caution = "Shizuku 会把较高权限交给被你授权的应用。只授权你信任的换字体应用。ADB 权限不等于完整 root，有些机型光有 Shizuku 仍然换不了字体。无线调试启动的 Shizuku 在每次重启手机后都要重新启动一次（配对只需做一次），并请允许 Shizuku 在后台运行，否则服务可能被系统停止。",
    ),
    InstallerTool(
        id = "kernelsu",
        name = "KernelSU",
        summary = "KernelSU 是一款开源 root 方案，root 权限由 Linux 内核本身直接授予，不修改系统分区、不依赖传统的 su 文件。它只支持 GKI（通用内核映像）设备，即出厂搭载 Android 12、内核 5.10 及以上版本的大多数机型；配套的 KernelSU Manager 应用负责安装、授权管理和模块管理。获得 root 后，字体安装应用可以直接向系统字体目录写入字体文件。",
        downloadLabel = "打开 Releases",
        downloadUrl = "https://github.com/tiann/KernelSU/releases/latest",
        officialLabel = "官方网站",
        officialUrl = "https://kernelsu.org/",
        repo = "tiann/KernelSU",
        steps = listOf(
            "确认设备支持：从 GitHub 仓库 tiann/KernelSU 的 Releases 下载并安装 KernelSU Manager，打开后查看状态——显示「不支持」表示设备不在官方支持范围，显示「未安装」表示可以继续安装。",
            "解锁 Bootloader 并备份：按官方文档先解锁 Bootloader（解锁通常会清空手机数据，请先备份），并备份好原厂的 boot.img（Android 13 及以上出厂机型备份 init_boot.img），以便出问题时恢复。",
            "打补丁并刷入（当前官方推荐 LKM 模式）：在 Manager 右上角点安装图标，选择「选择文件」，选取原厂镜像文件，Manager 会自动打入与设备 KMI 匹配的模块；然后用 fastboot 把打好补丁的镜像刷回同一分区并重启。已 root 的设备可直接用「直接安装」。",
            "验证安装：重启后打开 Manager，确认显示「已安装」及内核版本；自 v3.0 起官方只提供 LKM 模式，不再发布可直接刷入的 GKI 内核镜像。",
            "授权字体应用：打开字体安装应用（如 SamFonts / zFont）并执行安装字体操作，首次会弹出 KernelSU 的 root 授权请求，点「允许」后即可安装；也可在 Manager 的「超级用户」页中查看和管理已授权的应用。",
            "按需精细控制：如需限制某个应用的 root 权限范围，可在 Manager 的「应用配置（App Profile）」中为其单独设置 UID、用户组、Linux capabilities 与 SELinux 上下文。",
        ),
        caution = "解锁 Bootloader 会清空数据、刷入与设备 KMI 或安全补丁级别不匹配的镜像可能导致无法开机，且部分银行、支付类应用可能无法在已 root 的设备上正常使用，操作前请务必备份并确认镜像与机型完全对应。",
    ),
)

@Composable
fun InstallTutorialIndex(vm: FontsViewModel, padding: PaddingValues) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding.asPagePadding()),
        verticalArrangement = Arrangement.spacedBy(TokiSpacing.lg),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(TokiSpacing.sm)) {
            Text(
                "内容由 Grok 生成，按各项目公开说明整理的使用顺序。不含利用细节。root 和改内核可能变砖、影响 Knox 或丢失数据。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        InstallerTools.forEach { tool ->
            Surface(
                onClick = { vm.installPage = "tutorial:${tool.id}" },
                shape = TokiCardShape,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = TokiSpacing.xl),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(tool.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable
fun InstallTutorial(vm: FontsViewModel, id: String, padding: PaddingValues) {
    val context = LocalContext.current
    val tool = InstallerTools.firstOrNull { it.id == id }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding.asPagePadding()),
        verticalArrangement = Arrangement.spacedBy(TokiSpacing.lg),
    ) {
        if (tool == null) {
            Text("没有这个教程", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(TokiSpacing.sm)) {
                // 步骤第一步就要用到这个工具的下载/官方入口：直接放在本页顶部，和安装页同一份
                // InstallerTools 数据、同一组链接，不用再返回两级去安装页找（文案里的“上面的”指的就是这里）。
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { if (!openLink(context, tool.downloadUrl)) vm.flash("没有可以打开链接的应用") }) { Text(tool.downloadLabel) }
                    OutlinedButton(onClick = { if (!openLink(context, tool.officialUrl)) vm.flash("没有可以打开链接的应用") }) { Text(tool.officialLabel) }
                }
            }
            tool.steps.forEachIndexed { index, step ->
                Text("${index + 1}. $step", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
            }
            Surface(shape = TokiCardShape, color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.fillMaxWidth()) {
                Text(tool.caution, modifier = Modifier.padding(TokiSpacing.xl), color = MaterialTheme.colorScheme.onTertiaryContainer, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** 一个工具的仓库 Release 里的一个文件。 */
data class ToolReleaseAsset(val name: String, val url: String, val size: Long)

/** 一个工具的仓库 Release（最近 5 个之一）。 */
data class ToolRelease(
    val tag: String,
    val name: String,
    val htmlUrl: String,
    val assets: List<ToolReleaseAsset>,
)

private val releasesHttp = OkHttpClient.Builder()
    .callTimeout(30, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .build()

/** 读一个公开仓库的最近 5 个 Release（不带 token；草稿跳过）。 */
fun fetchToolReleases(repo: String): List<ToolRelease> {
    val request = Request.Builder()
        .url("https://api.github.com/repos/$repo/releases?per_page=5")
        .header("User-Agent", "tokifontspro (https://github.com/ichiyotsu/Tokifonts)")
        .header("Accept", "application/vnd.github+json")
        .build()
    releasesHttp.newCall(request).execute().use { response ->
        if (!response.isSuccessful) error("读取 Release 失败 HTTP ${response.code}")
        val array = JSONArray(response.body?.string().orEmpty())
        return buildList {
            for (index in 0 until array.length()) {
                val json = array.getJSONObject(index)
                if (json.optBoolean("draft")) continue
                val assets = buildList {
                    val list = json.optJSONArray("assets") ?: JSONArray()
                    for (j in 0 until list.length()) {
                        val asset = list.getJSONObject(j)
                        val name = asset.optString("name")
                        val url = asset.optString("browser_download_url")
                        if (name.isNotBlank() && url.startsWith("https://")) {
                            add(ToolReleaseAsset(name, url, asset.optLong("size")))
                        }
                    }
                }
                add(
                    ToolRelease(
                        tag = json.optString("tag_name").ifBlank { "未命名版本" },
                        name = json.optString("name"),
                        htmlUrl = json.optString("html_url"),
                        assets = assets,
                    ),
                )
            }
        }
    }
}

/**
 * 安装器工具的二级页面：读它仓库最近 5 个 Release。
 * 有 apk 文件的直接给下载按钮（点一下走系统下载）；
 * 没有 apk 的给打开版本页的入口，不猜文件名。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun InstallToolReleases(vm: FontsViewModel, id: String, padding: PaddingValues) {
    val context = LocalContext.current
    val tool = InstallerTools.firstOrNull { it.id == id }
    LaunchedEffect(id) { tool?.let { vm.loadToolReleases(it) } }
    if (tool == null) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding.asPagePadding()),
            verticalArrangement = Arrangement.spacedBy(TokiSpacing.lg),
        ) {
            Text("没有这个工具", style = MaterialTheme.typography.titleLarge)
        }
        return
    }
    val loading = id in vm.toolReleasesLoading
    val error = vm.toolReleasesError[id]
    val releases = vm.toolReleases[id].orEmpty()
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = padding.asPagePadding(),
        verticalArrangement = Arrangement.spacedBy(TokiSpacing.lg),
    ) {
        item {
            Text(tool.summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.loadToolReleases(tool, force = true) }) { Text("刷新") }
                OutlinedButton(onClick = { if (!openLink(context, tool.officialUrl)) vm.flash("没有可以打开链接的应用") }) { Text(tool.officialLabel) }
            }
        }
        if (loading) {
            item {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { LoadingIndicator() }
            }
        } else if (error != null) {
            item {
                Text("读取失败：$error", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
            item {
                Button(onClick = { vm.loadToolReleases(tool, force = true) }) { Text("重试") }
            }
        } else if (releases.isEmpty()) {
            item {
                Text("这个仓库最近没有发布版本。", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            items(releases, key = { it.tag }) { release ->
                Surface(shape = TokiCardShape, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(TokiSpacing.xl), verticalArrangement = Arrangement.spacedBy(TokiSpacing.sm)) {
                        Text(release.tag, style = MaterialTheme.typography.titleLarge)
                        if (release.name.isNotBlank() && release.name != release.tag) {
                            Text(release.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        val apks = release.assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
                        val others = release.assets.filter { !it.name.endsWith(".apk", ignoreCase = true) }
                        apks.forEach { asset ->
                            Button(onClick = { if (!openLink(context, asset.url)) vm.flash("没有可以打开链接的应用") }) { Text(asset.name) }
                        }
                        others.forEach { asset ->
                            OutlinedButton(onClick = { if (!openLink(context, asset.url)) vm.flash("没有可以打开链接的应用") }) { Text(asset.name) }
                        }
                        if (release.assets.isEmpty()) {
                            OutlinedButton(onClick = { if (!openLink(context, release.htmlUrl)) vm.flash("没有可以打开链接的应用") }) { Text("打开此版本") }
                        }
                    }
                }
            }
        }
    }
}
