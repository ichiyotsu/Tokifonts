package com.ichiyotsu.tokifonts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 更新检查的分轨：Main 轨只认 tag `v*`，Pro 轨（`pro-v*`）一律跳过。
 * [AppUpdate.latest] 的网络部分无法在单测里跑，这里只测纯函数
 * [AppUpdate.isMainTrackTag]；Pro 树对应的是 `pro-v` 前缀规则。
 */
class AppUpdateTrackTest {
    @Test fun mainTrackTagsAccepted() {
        assertTrue(AppUpdate.isMainTrackTag("v0.1.10"))
        assertTrue(AppUpdate.isMainTrackTag("v0.2.0"))
    }

    @Test fun proTrackTagsIgnored() {
        assertFalse(AppUpdate.isMainTrackTag("pro-v0.1.0"))
        assertFalse(AppUpdate.isMainTrackTag("pro-v0.2.0"))
    }

    @Test fun otherTagsIgnored() {
        assertFalse(AppUpdate.isMainTrackTag("0.1.10"))
        assertFalse(AppUpdate.isMainTrackTag(""))
    }

    @Test fun proAssetFileNameVersion() {
        // Pro 资产名 TokiFontsPro-0.1.0.apk 里也能抠出版本号（共用 fromFileName），
        // 但 Main 轨不会去下它——分轨靠 tag 前缀，不靠资产名。
        assertEquals("0.1.0", VersionNames.fromFileName("TokiFontsPro-0.1.0.apk"))
        assertEquals("0.1.10", VersionNames.fromFileName("TokiFonts-0.1.10.apk"))
    }
}
