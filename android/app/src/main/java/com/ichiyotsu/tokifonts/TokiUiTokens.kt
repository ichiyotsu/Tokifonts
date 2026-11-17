package com.ichiyotsu.tokifonts

import android.content.Context
import com.tokiui.semantic.SeedSpec
import com.tokiui.semantic.SemanticTokens

/**
 * 语义 token 的运行时入口：解析 vendored 的 `tokiui.semantic.tokens.json`
 *（见 android/TOKIUI_SYNC.md），解析一次后缓存。种子色列表只从这里读，
 * 本仓库不另抄一份色值；漂移由 `TokiUiSeedsTest` 在单元测试里锁住。
 */
object TokiUiTokens {
    @Volatile
    private var cached: SemanticTokens? = null

    fun load(context: Context): SemanticTokens {
        cached?.let { return it }
        return synchronized(this) {
            cached ?: SemanticTokens.parse(
                context.assets.open("tokiui.semantic.tokens.json").bufferedReader().readText(),
            ).also { cached = it }
        }
    }

    /** 品牌种子色（顺序即设置页点选顺序）。 */
    fun seeds(context: Context): List<SeedSpec> = load(context).seeds
}
