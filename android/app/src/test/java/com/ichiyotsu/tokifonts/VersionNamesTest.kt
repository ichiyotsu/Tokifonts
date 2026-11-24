package com.ichiyotsu.tokifonts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionNamesTest {
    @Test fun stableCompare() {
        assertTrue(VersionNames.isNewer("0.1.1", "0.1.0"))
        assertTrue(VersionNames.isNewer("v0.2.0", "0.1.9"))
        assertFalse(VersionNames.isNewer("0.1.1", "0.1.1"))
        assertFalse(VersionNames.isNewer("0.1.0", "0.1.1"))
        assertFalse(VersionNames.isNewer("0.1.2-b1", "0.1.1"))
        assertFalse(VersionNames.isNewer("not-a-version", "0.1.0"))
    }

    @Test fun betaBuildStillGetsStableRelease() {
        // 装的是 beta 包时，同底版的正式版也算更新，否则 beta 用户永远收不到正式版。
        assertTrue(VersionNames.isNewer("0.1.2", "0.1.2-b1"))
        assertFalse(VersionNames.isNewer("0.1.1", "0.1.2-b1"))
    }

    @Test fun fileNameVersion() {
        // Pro 轨资产名 TokiFontsPro-X.Y.Z.apk（单个 pro）；Main 轨 TokiFonts-X.Y.Z.apk。
        assertEquals("0.1.0", VersionNames.fromFileName("TokiFontsPro-0.1.0.apk"))
        assertEquals("0.1.10", VersionNames.fromFileName("TokiFonts-0.1.10.apk"))
        assertNull(VersionNames.fromFileName("TokiFontsPro-update.apk"))
    }
}
