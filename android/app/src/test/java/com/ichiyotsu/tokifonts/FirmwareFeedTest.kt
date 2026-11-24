package com.ichiyotsu.tokifonts

import java.time.Instant
import java.time.temporal.ChronoUnit
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 每日固件支持 feed 的校验与转换（纯逻辑，见 docs/firmware-support-SCHEMA.md）。
 * 用构造的小 feed 测，不读真实 docs/firmware-support.json（测试资源里没有它）。
 */
class FirmwareFeedTest {
    private fun validFeedJson(updatedAt: String = Instant.now().minus(1, ChronoUnit.HOURS).toString()): String = """
        {
          "schemaVersion": 1,
          "updatedAt": "$updatedAt",
          "sources": [
            {"name": "root-my-s24 README", "url": "https://example.invalid/r", "version": "v3.7.0-alpha.1", "fetchedAt": "$updatedAt"},
            {"name": "Root-My-Galaxy-Payloads", "url": "https://example.invalid/t", "schemaVersion": 3, "fetchedAt": "$updatedAt"}
          ],
          "rules": {
            "patched43499": [{"model": "SM-S9280", "firmwareSuffixes": ["DZJ3"]}],
            "recommendationLevels": ["0", "1", "2", "3", "4"]
          },
          "entries": [
            {
              "id": "rms24-S9280ZCS6DZF2", "tool": "root-my-s24", "kind": "exact",
              "model": "SM-S9280", "modelLabel": "SM-S9280 国行",
              "firmwareBuild": "S9280ZCS6DZF2", "kernel": "6.1.145",
              "engine": "GhostLock", "driver": "KernelSU 3.3.0（实验）",
              "status": "已实测", "source": "rms24-readme:tested", "notes": "",
              "matchKeys": ["S9280ZCS6DZF2"]
            },
            {
              "id": "rmg-e3q-S9280ZCS6DZF2", "tool": "root-my-galaxy", "kind": "exact",
              "model": null, "firmwareBuild": null, "kernel": null,
              "engine": "GhostLock (CVE-2026-43499)", "driver": "随载荷提供（KernelSU 工件）",
              "status": "移植", "source": "rmg-targets-v3", "notes": "",
              "payloadId": "e3q-S9280ZCS6DZF2", "displayName": "Galaxy S24 Ultra China | Kernel 6.1.145 (S9280ZCS6DZF2)",
              "models": ["SM-S9280"], "kernelVersions": ["6.1.145"],
              "exploit": {"url": "https://raw.githubusercontent.com/x/a.so", "size": 104128},
              "kernelsu": {"url": "https://raw.githubusercontent.com/x/k", "size": 4879856},
              "requiresFreshP0Session": true
            },
            {
              "id": "rms24-ported-S24", "tool": "root-my-s24", "kind": "ported-series",
              "model": null, "firmwareBuild": "DZE2 / DZF2 / DZG1", "kernel": "6.1.145",
              "engine": null, "driver": null,
              "status": "移植", "source": "rms24-readme:ported", "notes": "",
              "series": "S24", "modelPrefixes": ["SM-S9210", "SM-S9260", "SM-S9280"],
              "firmwareSuffixes": ["DZE2", "DZF2", "DZG1"], "kernels": ["6.1"],
              "regions": "国行 DZE2 / DZF2 / DZG1，港台 DZG1"
            }
          ]
        }
    """.trimIndent()

    @Test fun validFeedPassesAndConverts() {
        val result = FirmwareFeed.validateFeed(validFeedJson(), previousEntryCount = 3)
        assertTrue(result is FirmwareFeed.FeedValidation.Valid)
        val json = (result as FirmwareFeed.FeedValidation.Valid).json
        val tables = FirmwareFeed.feedToTables(json)
        assertEquals(1, tables.entries.size)
        assertEquals("SM-S9280", tables.entries[0].model)
        assertEquals(FirmwareStatus.TESTED, tables.entries[0].status)
        assertEquals(1, tables.rmgPayloads.size)
        assertEquals("e3q-S9280ZCS6DZF2", tables.rmgPayloads[0].payloadId)
        assertTrue(tables.rmgPayloads[0].requiresFreshP0Session)
        assertEquals(1, tables.portedPayloads.size)
        assertEquals("S24", tables.portedPayloads[0].seriesLabel)
        assertEquals(setOf("DZJ3"), tables.patched43499["SM-S9280"])
    }

    @Test fun wrongSchemaVersionRejected() {
        val bad = validFeedJson().replace("\"schemaVersion\": 1", "\"schemaVersion\": 2")
        val result = FirmwareFeed.validateFeed(bad, previousEntryCount = 3)
        assertTrue(result is FirmwareFeed.FeedValidation.Invalid)
        assertTrue((result as FirmwareFeed.FeedValidation.Invalid).reason.contains("schemaVersion"))
    }

    @Test fun missingUpdatedAtRejected() {
        val json = JSONObject(validFeedJson())
        json.remove("updatedAt")
        val result = FirmwareFeed.validateFeed(json.toString(), previousEntryCount = 3)
        assertTrue(result is FirmwareFeed.FeedValidation.Invalid)
    }

    @Test fun futureUpdatedAtRejected() {
        val future = Instant.now().plus(2, ChronoUnit.DAYS).toString()
        val result = FirmwareFeed.validateFeed(validFeedJson(future), previousEntryCount = 3)
        assertTrue(result is FirmwareFeed.FeedValidation.Invalid)
    }

    @Test fun emptyEntriesRejected() {
        val json = JSONObject(validFeedJson())
        json.put("entries", org.json.JSONArray())
        val result = FirmwareFeed.validateFeed(json.toString(), previousEntryCount = 3)
        assertTrue(result is FirmwareFeed.FeedValidation.Invalid)
    }

    @Test fun duplicateIdRejected() {
        val json = JSONObject(validFeedJson())
        val entries = json.getJSONArray("entries")
        entries.put(JSONObject(entries.getJSONObject(0).toString()))
        val result = FirmwareFeed.validateFeed(json.toString(), previousEntryCount = 3)
        assertTrue((result as FirmwareFeed.FeedValidation.Invalid).reason.contains("id 重复"))
    }

    @Test fun entryCountCollapseRejected() {
        // 上次 10 条，这次 3 条（跌破一半）→ 拒绝整份。
        val result = FirmwareFeed.validateFeed(validFeedJson(), previousEntryCount = 10)
        assertTrue(result is FirmwareFeed.FeedValidation.Invalid)
    }

    @Test fun rmgFirmwareBuildMustBeNull() {
        val json = JSONObject(validFeedJson())
        json.getJSONArray("entries").getJSONObject(1).put("firmwareBuild", "S9280ZCS6DZF2")
        val result = FirmwareFeed.validateFeed(json.toString(), previousEntryCount = 3)
        assertTrue((result as FirmwareFeed.FeedValidation.Invalid).reason.contains("firmwareBuild"))
    }

    @Test fun rmgExploitUrlMustBeRawGithub() {
        val json = JSONObject(validFeedJson())
        json.getJSONArray("entries").getJSONObject(1).getJSONObject("exploit")
            .put("url", "https://example.invalid/a.so")
        val result = FirmwareFeed.validateFeed(json.toString(), previousEntryCount = 3)
        assertTrue(result is FirmwareFeed.FeedValidation.Invalid)
    }

    @Test fun portedSeriesEngineMustBeNull() {
        val json = JSONObject(validFeedJson())
        json.getJSONArray("entries").getJSONObject(2).put("engine", "GhostLock")
        val result = FirmwareFeed.validateFeed(json.toString(), previousEntryCount = 3)
        assertTrue(result is FirmwareFeed.FeedValidation.Invalid)
    }

    @Test fun missingSourceRejected() {
        val json = JSONObject(validFeedJson())
        val sources = json.getJSONArray("sources")
        sources.remove(1)
        val result = FirmwareFeed.validateFeed(json.toString(), previousEntryCount = 3)
        assertTrue(result is FirmwareFeed.FeedValidation.Invalid)
    }

    @Test fun unparseableRejected() {
        val result = FirmwareFeed.validateFeed("{not json", previousEntryCount = null)
        assertTrue(result is FirmwareFeed.FeedValidation.Invalid)
        assertFalse(FirmwareFeed.validateFeed(validFeedJson(), previousEntryCount = null) is FirmwareFeed.FeedValidation.Invalid)
    }
}
