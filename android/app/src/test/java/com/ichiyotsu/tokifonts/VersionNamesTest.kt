package com.ichiyotsu.tokifonts

import org.junit.Assert.assertFalse
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
}
