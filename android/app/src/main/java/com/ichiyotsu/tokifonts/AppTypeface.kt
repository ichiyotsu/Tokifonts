package com.ichiyotsu.tokifonts

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight

/**
 * 全局字体：拉丁文 Inter，中文及其他汉字用思源黑体。
 *（对照 TokiStudio `ui/theme/AppTypeface.kt`，同一 commit 的 TokiUI 仓库。）
 *
 * 打包进 `res/font/` 的是官方文件，离线可用：
 * - `inter.ttf`：Inter 可变字体（The Inter Project，SIL OFL）
 * - `noto_sans_sc.ttf`：思源黑体的官方 Noto Sans SC 发行（Adobe Source Han Sans，
 *   字体名保留 Source；SIL OFL）。没有另找一套形近字体代替。
 *
 * 加载是 Compose 的可变字体写法：拉丁字体在前，Noto Sans SC 紧随其后。
 * Inter 没有中文字形，混排时拉丁走 Inter，汉字落到思源黑体，两边都没有的
 * 字形再由系统字体兜底。字重走 `wght` 轴：正文 400，标题和标签 700。
 * 两套可变字体都注册了这两档，汉字和拉丁文会落到真实的粗体轮廓，而不是系统去描粗。
 *
 * 注意：`res/font/` 里同时保留的 `google_sans_*.ttf` 不再是界面字体，
 * 它们是 FontStore 的种子字体（启动时补进下载目录供预览用），不要删。
 */
private fun variableFonts(latin: Int, weight: FontWeight): List<Font> {
    val variation = FontVariation.Settings(FontVariation.weight(weight.weight))
    return listOf(
        Font(latin, weight = weight, variationSettings = variation),
        Font(R.font.noto_sans_sc, weight = weight, variationSettings = variation),
    )
}

val InterFamily: FontFamily = FontFamily(
    listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold)
        .flatMap { variableFonts(R.font.inter, it) },
)
