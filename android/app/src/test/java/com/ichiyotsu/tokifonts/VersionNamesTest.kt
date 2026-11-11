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
        assertEquals("0.1.2", VersionNames.fromFileName("tokifonts-0.1.2-android.apk"))
        assertEquals("1.3.1", VersionNames.fromFileName("tokifonts-1.3.1-android.apk"))
        assertNull(VersionNames.fromFileName("tokifonts-update.apk"))
    }
}
