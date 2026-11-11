package com.ichiyotsu.tokifonts

import java.net.URLEncoder
import org.json.JSONObject

data class FontGroup(val id: String, val name: String, val code: String, val blurb: String)

data class FontSection(val id: String, val name: String, val group: String)

data class FontFace(
    val id: String,
    val fileName: String,
    val exportName: String,
    val weight: Int,
    val weightName: String,
    val weightLabel: String,
    val italic: Boolean,
    val optical: String,
    val opticalLabel: String,
    val variable: Boolean,
    val format: String,
    val innerName: String,
    val bytes: Long,
    val repo: String,
    val ref: String,
    val path: String,
    val mirrorRepo: String,
    val mirrorRef: String,
    val mirrorPath: String,
) {
    fun usableExt(): String = when {
        innerName.endsWith(".otf", true) -> "otf"
        format == "otf" -> "otf"
        else -> "ttf"
    }

    fun urls(): List<String> {
        val primary = FontUrls.raw(repo, ref, path)
        if (mirrorRepo.isBlank() || mirrorPath.isBlank()) return listOf(primary)
        return listOf(primary, FontUrls.raw(mirrorRepo, mirrorRef, mirrorPath))
    }
}

data class FontFamily(
    val id: String,
    val name: String,
    val nameEn: String,
    val group: String,
    val section: String,
    val summary: String,
    val license: String,
    val alias: String,
    val tags: List<String>,
    val status: String,
    val statusDetail: String,
    val credit: String,
    val sources: List<String>,
    val faces: List<FontFace>,
) {
    fun initialFace(): FontFace? {
        val ranked = faces.sortedWith(
            compareBy<FontFace> { it.variable }
                .thenBy { it.italic }
                .thenBy { if (it.optical == "Medium" || it.optical.isEmpty()) 0 else 1 }
                .thenBy { kotlin.math.abs(it.weight - 400) },
        )
        return ranked.firstOrNull()
    }
}

data class Catalog(
    /** 目录数据版本（不是应用版本；生成脚本里写死在 catalog.json 里）。 */
    val dataVersion: String,
    val mergeNote: String,
    val groups: List<FontGroup>,
    val sections: List<FontSection>,
    val families: List<FontFamily>,
    /** 解析时跳过的损坏条目数（坏 family / face 各计 1）。 */
    val skipped: Int = 0,
) {
    fun family(id: String) = families.firstOrNull { it.id == id }
    fun face(id: String) = families.asSequence().flatMap { it.faces }.firstOrNull { it.id == id }

    companion object {
        /**
         * 容错解析：坏掉的 group / section / family / face 只跳过该条并计入 [Catalog.skipped]，
         * 不再因为一条坏记录让整个字体库打不开。只有根 JSON 本身非法时才抛异常。
         */
        fun parse(text: String): Catalog {
            val root = JSONObject(text)
            var skipped = 0
            val onSkip = { skipped++ }
            val groups = root.optJSONArray("groups")?.let { arr ->
                buildList {
                    for (i in 0 until arr.length()) {
                        val item = runCatching {
                            val o = arr.getJSONObject(i)
                            FontGroup(o.getString("id"), o.getString("name"), o.getString("code"), o.getString("blurb"))
                        }.getOrNull()
                        if (item == null) onSkip() else add(item)
                    }
                }
            } ?: emptyList()
            val sections = root.optJSONArray("sections")?.let { arr ->
                buildList {
                    for (i in 0 until arr.length()) {
                        val item = runCatching {
                            val o = arr.getJSONObject(i)
                            FontSection(o.getString("id"), o.getString("name"), o.getString("group"))
                        }.getOrNull()
                        if (item == null) onSkip() else add(item)
                    }
                }
            } ?: emptyList()
            val families = root.optJSONArray("families")?.let { arr ->
                buildList {
                    for (i in 0 until arr.length()) {
                        val item = runCatching { parseFamily(arr.getJSONObject(i), onSkip) }.getOrNull()
                        if (item == null) onSkip() else add(item)
                    }
                }
            } ?: emptyList()
            // 逐条容错不等于根级也吞：families 缺失或全坏时返回空库会让 UI 只剩一片空白、
            // 且 catalog != null 把重试挡死。根级损坏必须抛错，走 loadError + 重试路径。
            if (families.isEmpty()) throw org.json.JSONException("catalog 缺少 families 或全部损坏")
            return Catalog(
                dataVersion = root.optString("version"),
                mergeNote = root.optString("mergeNote"),
                groups = groups,
                sections = sections,
                families = families,
                skipped = skipped,
            )
        }

        private fun parseFamily(o: JSONObject, onSkip: () -> Unit): FontFamily {
            val facesJson = o.optJSONArray("faces")
            val faces = facesJson?.let { arr ->
                buildList {
                    for (i in 0 until arr.length()) {
                        val face = runCatching { parseFace(arr.getJSONObject(i)) }.getOrNull()
                        if (face == null) onSkip() else add(face)
                    }
                }
            } ?: emptyList()
            val tagsJson = o.optJSONArray("tags")
            val sourcesJson = o.optJSONArray("sources")
            return FontFamily(
                id = o.getString("id"),
                name = o.getString("name"),
                nameEn = o.optString("nameEn"),
                group = o.getString("group"),
                section = o.getString("section"),
                summary = o.optString("summary"),
                license = o.optString("license"),
                alias = o.optString("alias"),
                tags = tagsJson?.let { arr -> List(arr.length()) { arr.getString(it) } } ?: emptyList(),
                status = o.optString("status"),
                statusDetail = o.optString("statusDetail"),
                credit = o.optString("credit"),
                sources = sourcesJson?.let { arr -> List(arr.length()) { arr.getString(it) } } ?: emptyList(),
                faces = faces,
            )
        }

        private fun parseFace(o: JSONObject) = FontFace(
            id = o.getString("id"),
            fileName = o.getString("fileName"),
            exportName = o.text("exportName").ifBlank { o.getString("fileName") },
            weight = o.optInt("weight", 400),
            weightName = o.text("weightName"),
            weightLabel = o.text("weightLabel"),
            italic = o.optBoolean("italic"),
            optical = o.text("optical"),
            opticalLabel = o.text("opticalLabel"),
            variable = o.optBoolean("variable"),
            format = o.text("format"),
            innerName = o.text("innerName"),
            bytes = o.optLong("bytes"),
            repo = o.getString("repo"),
            ref = o.getString("ref"),
            path = o.getString("path"),
            mirrorRepo = o.text("mirrorRepo"),
            mirrorRef = o.text("mirrorRef"),
            mirrorPath = o.text("mirrorPath"),
        )

        private fun JSONObject.text(key: String): String {
            if (!has(key) || isNull(key)) return ""
            return optString(key)
        }
    }
}

object FontUrls {
    fun raw(repo: String, ref: String, path: String): String {
        val encoded = path.split("/").joinToString("/") { segment ->
            URLEncoder.encode(segment, Charsets.UTF_8.name()).replace("+", "%20")
        }
        return "https://raw.githubusercontent.com/$repo/$ref/$encoded"
    }
}

fun Long.toSizeLabel(): String {
    if (this < 1024) return "$this B"
    val kb = this / 1024.0
    if (kb < 1024) return String.format("%.0f KB", kb)
    return String.format("%.1f MB", kb / 1024.0)
}
