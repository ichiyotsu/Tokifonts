package com.ichiyotsu.tokifonts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

internal data class InstallerTool(
    val id: String,
    val name: String,
    val summary: String,
    val downloadLabel: String,
    val downloadUrl: String,
    val officialLabel: String,
    val officialUrl: String,
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
        steps = listOf(
            "点上面的「下载安装包」安装 SamFonts。三星可能会弹出 Knox 提示，按你自己的判断处理。",
            "在 tokifonts 里把要用的字体保存到「下载」，或点「用其他应用打开」。",
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
        steps = listOf(
            "点上面的「下载 Pro 安装包」，安装文件名带 SamFontsPro 的那一份，不要和普通版搞混。",
            "之后的用法和 SamFonts 一样：从 tokifonts 导出字体文件，在 SamFonts Pro 里安装，再去系统字体设置里选用。",
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
        steps = listOf(
            "点上面的「下载 GitHub 版」安装。官网也写了应用商店地址，那是另一条分发渠道。",
            "在 zFont 里导入 tokifonts 保存的 ttf 或 otf，或从它自己的字库里选。",
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
        steps = listOf(
            "先读仓库 README，确认你的机型和固件在它列出的范围内。不匹配就不要安装。",
            "点上面的「打开 Releases」：Release 里有好几份安装包，按项目说明选对应机型的那一份。",
            "这个工具的目的是取得 KernelSU。取得之后，重启可能会失效，细节以项目自己的说明为准。",
            "字体仍然要交给 SamFonts 或 zFont 这类安装器。tokifonts 只负责把字体文件下载到本机。",
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
        steps = listOf(
            "打开仓库，看它支持哪些机型和内核版本。应用会按机型和内核去选载荷，不在列表里就不要试。",
            "点上面的「打开 Releases」，从 Releases 安装它发布的包。项目写了可以用 Shizuku 作为其中一种运行方式。",
            "如果它报告 KernelSU 已经可用，再给换字体应用或 Shizuku 授权。",
            "然后用 tokifonts 下载的字体，在 SamFonts 或 zFont 里安装。",
        ),
        caution = "这是改内核权限的工具，有变砖和保修风险。不要按照来源不明的帖子去改命令。以这个仓库的 README 为准。",
    ),
    InstallerTool(
        id = "shizuku",
        name = "Shizuku",
        summary = "名字按官方拼写是 Shizuku，不是 shikuzu。它让普通应用通过 ADB 或 root 使用更高权限的系统接口。SamFonts 和 zFont 都会用到它。",
        downloadLabel = "打开 Releases",
        downloadUrl = "https://github.com/RikkaApps/Shizuku/releases",
        officialLabel = "官方网站",
        officialUrl = "https://shizuku.rikka.app/",
        steps = listOf(
            "点上面的「打开 Releases」从 GitHub Releases 安装，或去 Play 商店安装。Play 商店包名是 moe.shizuku.privileged.api。",
            "按官网说明打开开发者选项，用无线调试或电脑上的 adb 把 Shizuku 启动起来。",
            "看到它在运行之后，打开 SamFonts 或 zFont，选择 Shizuku，并在弹出的授权里允许。",
            "回到 tokifonts，把字体保存或分享给那个安装器。",
        ),
        caution = "Shizuku 会把较高权限交给被你授权的应用。只授权你信任的换字体应用。ADB 权限不等于完整 root，有些机型光有 Shizuku 仍然换不了字体。",
    ),
    InstallerTool(
        id = "kernelsu",
        name = "KernelSU",
        summary = "名单里的 kernel 按这些换字体工具的用法，对应的是 KernelSU：一种做在内核里的 root 方案。root-my-s24 和 Root My Galaxy 的说明都是在加载它。",
        downloadLabel = "打开 Releases",
        downloadUrl = "https://github.com/tiann/KernelSU/releases/latest",
        officialLabel = "官方网站",
        officialUrl = "https://kernelsu.org/",
        steps = listOf(
            "只有设备已经按 KernelSU 官方文档装好管理器，应用里才会出现 root 授权。不要安装签名来源不明的管理器。",
            "在超级用户列表里，只给 Shizuku 或你准备使用的换字体应用授权。",
            "授权之后，再用那个应用安装 tokifonts 下载的字体。",
        ),
        caution = "刷入或加载内核不属于普通换字体。弄错可能无法开机。官方安装说明在 kernelsu.org，不要用来路不明的内核包。",
    ),
)

@Composable
fun InstallTutorialIndex(vm: FontsViewModel, padding: PaddingValues) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding.asPagePadding()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextButton(onClick = { vm.installPage = "fonts" }) { Text("返回安装") }
        Text("教程", style = MaterialTheme.typography.displaySmallEmphasized, color = MaterialTheme.colorScheme.onBackground)
        Text("由 Grok 生成", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        Text(
            "按各项目公开说明整理的使用顺序。不含利用细节。root 和改内核可能变砖、影响 Knox 或丢失数据。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        InstallerTools.forEach { tool ->
            Surface(
                onClick = { vm.installPage = "tutorial:${tool.id}" },
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(tool.name, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
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
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = { vm.installPage = "tutorials" }) { Text("返回教程") }
        if (tool == null) {
            Text("没有这个教程", style = MaterialTheme.typography.titleLarge)
            return@Column
        }
        Text(tool.name, style = MaterialTheme.typography.displaySmallEmphasized)
        Text("由 Grok 生成", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        // 步骤第一步就要用到这个工具的下载/官方入口：直接放在本页顶部，和安装页同一份
        // InstallerTools 数据、同一组链接，不用再返回两级去安装页找（文案里的“上面的”指的就是这里）。
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { if (!openLink(context, tool.downloadUrl)) vm.flash("没有可以打开链接的应用") }) { Text(tool.downloadLabel) }
            OutlinedButton(onClick = { if (!openLink(context, tool.officialUrl)) vm.flash("没有可以打开链接的应用") }) { Text(tool.officialLabel) }
        }
        tool.steps.forEachIndexed { index, step ->
            Text("${index + 1}. $step", style = MaterialTheme.typography.bodyLarge)
        }
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.fillMaxWidth()) {
            Text(tool.caution, modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onTertiaryContainer, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
