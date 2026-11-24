package com.ichiyotsu.tokifonts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 更新检查的分轨：Pro 轨只认 tag `pro-v*`，Main 轨（`v*`）一律跳过。
 * [AppUpdate.latest] 的网络部分无法在单测里跑，这里只测纯函数
 * [AppUpdate.isProTrackTag]；Main 树对应的是 `v` 前缀规则。
 */
class AppUpdateTrackTest {
    @Test fun proTrackTagsAccepted() {
        assertTrue(AppUpdate.isProTrackTag("pro-v0.1.0"))
        assertTrue(AppUpdate.isProTrackTag("pro-v0.2.0"))
    }

    @Test fun mainTrackTagsIgnored() {
        assertFalse(AppUpdate.isProTrackTag("v0.1.10"))
        assertFalse(AppUpdate.isProTrackTag("v0.1.9"))
    }

    @Test fun otherTagsIgnored() {
        assertFalse(AppUpdate.isProTrackTag("0.1.0"))
        assertFalse(AppUpdate.isProTrackTag(""))
    }

    @Test fun proAssetFileNameVersion() {
        assertEquals("0.1.0", VersionNames.fromFileName("TokiFontsPro-0.1.0.apk"))
        assertEquals("0.1.10", VersionNames.fromFileName("TokiFonts-0.1.10.apk"))
    }
}
