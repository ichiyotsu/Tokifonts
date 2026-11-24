package com.ichiyotsu.tokifonts

import java.time.Instant
import org.json.JSONArray
import org.json.JSONObject

/**
 * 每日固件支持 feed（`docs/firmware-support.json`）的拉取、校验、缓存与转换。
 *
 * 数据来源：Pro 分支的 `docs/firmware-support.json`，由
 * `.github/workflows/update-device-list.yml` 每天从上游重新生成。
 * 校验规则见 `docs/firmware-support-SCHEMA.md`“App 校验规则”一节：
 * 任何一条不通过就拒绝整份 feed、沿用上一次好的版本，绝不能用部分数据凑合；
 * 永远不要因为 feed 坏了就把设备判为支持或不支持——支持判定只用校验通过的
 * feed 做。
 *
 * 拉取与文件 IO 在 ViewModel 里（OkHttp + filesDir），这里只放纯逻辑，
 * 方便单测：[validateFeed]（校验）与 [feedToTables]（校验通过后转换）。
 */
object FirmwareFeed {
    /** Pro 分支上的 feed 地址（分支名以实际 Pro 分支为准）。 */
    const val FEED_URL = "https://raw.githubusercontent.com/ichiyotsu/Tokifonts/Pro/docs/firmware-support.json"

    /** 本地缓存文件名（filesDir 下）。 */
    const val CACHE_FILE_NAME = "firmware-support-cached.json"

    /** 本合并 feed 支持的 schema 版本（与 RMG feed 自身的 schemaVersion=3 是两回事）。 */
    const val SUPPORTED_SCHEMA_VERSION = 1

    /** 校验结果：通过时带解析好的 JSON；不通过时带第一条失败的规则说明。 */
    sealed interface FeedValidation {
        data class Valid(val json: JSONObject) : FeedValidation
        data class Invalid(val reason: String) : FeedValidation
    }

    /**
     * 按 SCHEMA 校验一整份 feed。
     *
     * @param text feed 全文。
     * @param previousEntryCount 上一次好版本的条目数（null = 还没有上一次，不做规模检查）。
     * @param now 当前时间（单测可注入；默认 [Instant.now]）。
     */
    fun validateFeed(
        text: String,
        previousEntryCount: Int?,
        now: Instant = Instant.now(),
    ): FeedValidation {
        val json = try {
            JSONObject(text)
        } catch (e: Exception) {
            return FeedValidation.Invalid("JSON 解析失败：${e.message}")
        }
        if (!json.has("schemaVersion") || json.optInt("schemaVersion", -1) != SUPPORTED_SCHEMA_VERSION) {
            return FeedValidation.Invalid("schemaVersion 不是 $SUPPORTED_SCHEMA_VERSION（不支持的版本，拒绝整份）。")
        }
        val updatedAtRaw = json.optString("updatedAt").ifBlank { null }
            ?: return FeedValidation.Invalid("updatedAt 缺失。")
        val updatedAt = try {
            Instant.parse(updatedAtRaw)
        } catch (e: Exception) {
            return FeedValidation.Invalid("updatedAt 不是合法的 ISO 8601 UTC 时间：$updatedAtRaw")
        }
        if (updatedAt.isAfter(now.plusSeconds(24 * 3600))) {
            return FeedValidation.Invalid("updatedAt 比当前时间晚超过 24 小时，可疑数据，拒绝。")
        }
        val entries = json.optJSONArray("entries")
            ?: return FeedValidation.Invalid("entries 缺失。")
        if (entries.length() == 0) {
            return FeedValidation.Invalid("entries 为空。")
        }
        if (previousEntryCount != null && entries.length() * 2 < previousEntryCount) {
            return FeedValidation.Invalid(
                "条目数 ${entries.length()} 跌破上次好版本 $previousEntryCount 的一半，疑似上游解析事故，拒绝。",
            )
        }
        val seenIds = mutableSetOf<String>()
        for (i in 0 until entries.length()) {
            val entry = entries.optJSONObject(i)
                ?: return FeedValidation.Invalid("entries[$i] 不是对象。")
            val id = entry.optString("id").ifBlank { null }
                ?: return FeedValidation.Invalid("entries[$i] 的 id 为空。")
            if (!seenIds.add(id)) {
                return FeedValidation.Invalid("id 重复：$id。")
            }
            val tool = entry.optString("tool")
            if (tool != "root-my-s24" && tool != "root-my-galaxy") {
                return FeedValidation.Invalid("entries[$i]（$id）的 tool 非法：$tool。")
            }
            val status = entry.optString("status")
            if (status != "已实测" && status != "待验证" && status != "移植") {
                return FeedValidation.Invalid("entries[$i]（$id）的 status 非法：$status。")
            }
            val kind = entry.optString("kind")
            if (kind != "exact" && kind != "ported-series") {
                return FeedValidation.Invalid("entries[$i]（$id）的 kind 非法：$kind。")
            }
            val bad = checkEntryFields(entry, id, tool, kind)
            if (bad != null) return FeedValidation.Invalid(bad)
        }
        val sources = json.optJSONArray("sources")
            ?: return FeedValidation.Invalid("sources 缺失。")
        val sourceNames = (0 until sources.length()).mapNotNull { sources.optJSONObject(it)?.optString("name") }.toSet()
        if ("root-my-s24 README" !in sourceNames || "Root-My-Galaxy-Payloads" !in sourceNames) {
            return FeedValidation.Invalid("sources 缺少 root-my-s24 README 或 Root-My-Galaxy-Payloads。")
        }
        for (i in 0 until sources.length()) {
            val fetchedAt = sources.optJSONObject(i)?.optString("fetchedAt").orEmpty()
            if (fetchedAt.isBlank()) {
                return FeedValidation.Invalid("sources[$i] 的 fetchedAt 缺失。")
            }
        }
        val rules = json.optJSONObject("rules")
            ?: return FeedValidation.Invalid("rules 缺失。")
        val patched = rules.optJSONArray("patched43499")
            ?: return FeedValidation.Invalid("rules.patched43499 缺失。")
        for (i in 0 until patched.length()) {
            val row = patched.optJSONObject(i) ?: return FeedValidation.Invalid("rules.patched43499[$i] 不是对象。")
            if (row.optString("model").isBlank()) return FeedValidation.Invalid("rules.patched43499[$i] 的 model 为空。")
        }
        val levels = rules.optJSONArray("recommendationLevels")
        if (levels != null && levels.length() != 5) {
            return FeedValidation.Invalid("rules.recommendationLevels 不是 5 条等级说明。")
        }
        return FeedValidation.Valid(json)
    }

    /** 按 kind 检查一条 entry 的字段完整性；通过返回 null，否则返回失败原因。 */
    private fun checkEntryFields(entry: JSONObject, id: String, tool: String, kind: String): String? {
        fun nonBlank(field: String): Boolean = entry.optString(field).isNotBlank()
        fun nonEmptyArray(field: String): Boolean {
            val array = entry.optJSONArray(field) ?: return false
            return array.length() > 0
        }
        if (kind == "ported-series") {
            if (!nonEmptyArray("modelPrefixes")) return "entries $id：ported-series 的 modelPrefixes 为空。"
            if (!nonEmptyArray("kernels")) return "entries $id：ported-series 的 kernels 为空。"
            if (!entry.isNull("engine")) return "entries $id：ported-series 的 engine 必须为 null。"
            if (!entry.isNull("driver")) return "entries $id：ported-series 的 driver 必须为 null。"
            if (!nonBlank("series")) return "entries $id：ported-series 的 series 为空。"
            return null
        }
        // kind == exact
        if (tool == "root-my-s24") {
            if (!nonBlank("model")) return "entries $id：root-my-s24 精确行的 model 为空。"
            if (!nonBlank("firmwareBuild")) return "entries $id：root-my-s24 精确行的 firmwareBuild 为空。"
            if (!nonBlank("kernel")) return "entries $id：root-my-s24 精确行的 kernel 为空。"
            if (!nonBlank("engine")) return "entries $id：root-my-s24 精确行的 engine 为空。"
            if (!nonBlank("driver")) return "entries $id：root-my-s24 精确行的 driver 为空。"
            if (!nonEmptyArray("matchKeys")) return "entries $id：root-my-s24 精确行的 matchKeys 为空。"
            return null
        }
        // exact + root-my-galaxy
        if (!nonEmptyArray("models")) return "entries $id：root-my-galaxy 行的 models 为空。"
        if (!nonEmptyArray("kernelVersions")) return "entries $id：root-my-galaxy 行的 kernelVersions 为空。"
        val exploit = entry.optJSONObject("exploit")
            ?: return "entries $id：root-my-galaxy 行的 exploit 缺失。"
        val kernelsu = entry.optJSONObject("kernelsu")
            ?: return "entries $id：root-my-galaxy 行的 kernelsu 缺失。"
        if (!exploit.optString("url").startsWith("https://raw.githubusercontent.com/")) {
            return "entries $id：exploit.url 不是 raw.githubusercontent.com 直链。"
        }
        if (!kernelsu.optString("url").startsWith("https://raw.githubusercontent.com/")) {
            return "entries $id：kernelsu.url 不是 raw.githubusercontent.com 直链。"
        }
        if (exploit.optLong("size", 0) <= 0) return "entries $id：exploit.size 不是正整数。"
        if (kernelsu.optLong("size", 0) <= 0) return "entries $id：kernelsu.size 不是正整数。"
        if (!entry.isNull("firmwareBuild")) {
            return "entries $id：root-my-galaxy 行的 firmwareBuild 必须为 null（feed 里本来就没有固件号，有值即为脏数据）。"
        }
        return null
    }

    /**
     * 校验通过的 feed → App 内匹配表（[FirmwareTables]）。
     * 调用前必须先过 [validateFeed]；这里不再重复校验，只做转换。
     * 转换不到的字段一律用内置表兜底，绝不编数据。
     */
    fun feedToTables(json: JSONObject): FirmwareTables {
        val entriesJson = json.getJSONArray("entries")
        val rms24 = mutableListOf<FirmwareEntry>()
        val rmg = mutableListOf<RmgPayloadEntry>()
        val ported = mutableListOf<PortedPayloadEntry>()
        for (i in 0 until entriesJson.length()) {
            val entry = entriesJson.getJSONObject(i)
            val tool = entry.optString("tool")
            val kind = entry.optString("kind")
            val status = when (entry.optString("status")) {
                "已实测" -> FirmwareStatus.TESTED
                "待验证" -> FirmwareStatus.CALIBRATED
                else -> FirmwareStatus.PORTED
            }
            when {
                kind == "ported-series" -> {
                    ported += PortedPayloadEntry(
                        seriesLabel = entry.optString("series"),
                        modelPrefixes = entry.optJSONArray("modelPrefixes").toStringList(),
                        kernels = entry.optJSONArray("kernels").toStringList(),
                        kernelLabel = entry.optString("kernel"),
                        firmwareSuffixes = entry.optJSONArray("firmwareSuffixes")?.toStringList().orEmpty(),
                        regions = entry.optString("regions"),
                    )
                }
                tool == "root-my-s24" -> {
                    val firmwareBuild = entry.optString("firmwareBuild")
                    rms24 += FirmwareEntry(
                        model = entry.optString("model"),
                        modelLabel = entry.optString("modelLabel").ifBlank { entry.optString("model") },
                        firmwareBuild = firmwareBuild,
                        kernel = entry.optString("kernel"),
                        engine = entry.optString("engine"),
                        status = status,
                        tool = RootTool.ROOT_MY_S24,
                        driver = entry.optString("driver"),
                        note = entry.optString("notes"),
                        matchKeys = entry.optJSONArray("matchKeys")?.toStringList()
                            ?.takeIf { it.isNotEmpty() } ?: listOf(firmwareBuild),
                    )
                }
                tool == "root-my-galaxy" -> {
                    val exploit = entry.optJSONObject("exploit")
                    val kernelsu = entry.optJSONObject("kernelsu")
                    rmg += RmgPayloadEntry(
                        payloadId = entry.optString("payloadId"),
                        displayName = entry.optString("displayName"),
                        models = entry.optJSONArray("models").toStringList(),
                        kernelVersions = entry.optJSONArray("kernelVersions").toStringList(),
                        exploitUrl = exploit?.optString("url").orEmpty(),
                        exploitSize = exploit?.optLong("size", 0) ?: 0,
                        kernelsuUrl = kernelsu?.optString("url").orEmpty(),
                        kernelsuSize = kernelsu?.optLong("size", 0) ?: 0,
                        requiresFreshP0Session = entry.optBoolean("requiresFreshP0Session", false),
                    )
                }
            }
        }
        val patched = mutableMapOf<String, Set<String>>()
        json.optJSONObject("rules")?.optJSONArray("patched43499")?.let { array ->
            for (i in 0 until array.length()) {
                val row = array.optJSONObject(i) ?: continue
                val model = row.optString("model").ifBlank { continue }
                patched[model] = row.optJSONArray("firmwareSuffixes")?.toStringList().orEmpty().toSet()
            }
        }
        return FirmwareTables(
            entries = rms24,
            rmgPayloads = rmg,
            portedPayloads = ported,
            patched43499 = patched,
        )
    }

    /** feed 的 updatedAt（展示用“已更新到某日”）；解析不到返回 null。 */
    fun updatedAtOf(json: JSONObject): String? =
        json.optString("updatedAt").ifBlank { null }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return (0 until length()).map { optString(it) }.filter { it.isNotBlank() }
    }
}
